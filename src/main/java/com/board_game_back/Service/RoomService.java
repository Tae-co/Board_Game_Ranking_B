package com.board_game_back.Service;

import com.board_game_back.Exception.AppException;
import com.board_game_back.Exception.ErrorCode;
import com.board_game_back.DTO.RoomDto;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.CommunityAdmin;
import com.board_game_back.Entity.CommunityMember;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.MemberRole;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomMember;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MatchParticipantRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.board_game_back.Utils.InviteCodeUtil;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MemberRepository memberRepository;
    private final PlayerGameRatingRepository playerGameRatingRepository;
    private final BoardGameRepository boardGameRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final CommunityAdminRepository communityAdminRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final CommunityRepository communityRepository;
    private final RoomSeasonService roomSeasonService;
    private final SeasonRankSnapshotRepository seasonRankSnapshotRepository;

    @Transactional
    public Room createRoom(String roomName, Long memberId, Long boardGameId, Long communityId,
                           String seasonName, LocalDate seasonEndDate) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (communityId != null && !communityAdminRepository.existsByCommunityIdAndMemberId(communityId, memberId)) {
            throw new AppException(ErrorCode.COMMUNITY_ADMIN_ONLY_CREATE_ROOM);
        }

        String inviteCode = InviteCodeUtil.generateUnique(roomRepository::existsByInviteCode);
        Room room = new Room(roomName, inviteCode, boardGameId);
        if (communityId != null) room.assignCommunity(communityId);
        Room savedRoom = roomRepository.save(room);

        roomMemberRepository.save(new RoomMember(savedRoom, member, MemberRole.HOST));
        roomSeasonService.createFirstSeason(savedRoom.getId(), communityId, seasonName, seasonEndDate);

        if (boardGameId != null) {
            boardGameRepository.findById(boardGameId).ifPresent(game -> {
                boolean exists = playerGameRatingRepository
                    .findByMemberAndBoardGameAndRoom(member, game, savedRoom).isPresent();
                if (!exists) {
                    playerGameRatingRepository.save(
                        PlayerGameRating.builder().member(member).boardGame(game).room(savedRoom).build()
                    );
                }
            });
        }

        return savedRoom;
    }

    @Transactional
    public Room joinRoom(String inviteCode, Long memberId) {
        Room room = roomRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new AppException(ErrorCode.INVALID_INVITE_CODE));

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean isAlreadyMember = roomMemberRepository.findByRoomIdAndMemberId(room.getId(), memberId).isPresent();
        if (isAlreadyMember) return room;

        roomMemberRepository.save(new RoomMember(room, member, MemberRole.MEMBER));

        if (room.getBoardGameId() != null) {
            boardGameRepository.findById(room.getBoardGameId()).ifPresent(game -> {
                boolean exists = playerGameRatingRepository
                    .findByMemberAndBoardGameAndRoom(member, game, room).isPresent();
                if (!exists) {
                    playerGameRatingRepository.save(
                        PlayerGameRating.builder().member(member).boardGame(game).room(room).build()
                    );
                }
            });
        }

        return room;
    }

    @Transactional(readOnly = true)
    public List<Room> getMyRooms(Long memberId) {
        return roomMemberRepository.findByMemberId(memberId).stream()
            .map(RoomMember::getRoom)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoomDto.RoomMemberResponse> getRoomMembers(Long roomId) {
        return roomMemberRepository.findByRoomId(roomId).stream()
            .map(rm -> new RoomDto.RoomMemberResponse(
                rm.getMember().getId(),
                rm.getMember().getNickname(),
                rm.getRole() == MemberRole.HOST,
                rm.getMember().getProfileImage()
            ))
            .collect(Collectors.toList());
    }

    @Transactional
    public void updateRoomName(Long roomId, Long requesterId, String newName) {
        RoomMember rm = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_ROOM_MEMBER));
        if (rm.getRole() != MemberRole.HOST) {
            throw new AppException(ErrorCode.ROOM_OWNER_ONLY_EDIT);
        }
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        room.rename(newName);
    }

    public Room getRoomById(Long roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
    }

    public Room getRoomByInviteCode(String inviteCode) {
        return roomRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new AppException(ErrorCode.INVALID_INVITE_CODE));
    }

    @Transactional
    /** 나가면 이 방의 내 경기 기록·지난 시즌 순위는 '나간 멤버'로 남는다 (다른 멤버 점수 유지, 다시 들어오면 새로 시작). */
    public void leaveRoom(Long roomId, Long memberId, Long requesterId) {
        if (!requesterId.equals(memberId)) {
            RoomMember requester = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_ROOM_MEMBER));
            if (requester.getRole() != MemberRole.HOST) {
                throw new AppException(ErrorCode.ROOM_OWNER_ONLY_KICK);
            }
        }

        RoomMember rm = roomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            .orElseThrow(() -> new AppException(ErrorCode.TARGET_NOT_ROOM_MEMBER));

        if (rm.getRole() == MemberRole.HOST) {
            List<RoomMember> others = roomMemberRepository.findByRoomId(roomId).stream()
                .filter(m -> !m.getMember().getId().equals(memberId))
                .collect(Collectors.toList());

            if (others.isEmpty()) {
                deleteRoomInternal(roomId);
                return;
            }

            // 커뮤니티 소유권과 같은 기준 — 이 방에서 경기를 가장 많이 한 사람, 동점·전원 0판이면 먼저 들어온 사람
            Map<Long, Long> playCounts = new HashMap<>();
            for (Object[] row : matchParticipantRepository.countByMemberInRoom(roomId)) {
                playCounts.put((Long) row[0], (Long) row[1]);
            }
            RoomMember nextHostMember = others.stream()
                .min(Comparator.<RoomMember>comparingLong(other -> -playCounts.getOrDefault(other.getMember().getId(), 0L))
                    .thenComparing(RoomMember::getId))
                .orElseThrow();
            nextHostMember.promoteToHost();
            roomMemberRepository.save(nextHostMember);
        }

        playerGameRatingRepository.deleteByMember_IdAndRoom_Id(memberId, roomId);
        roomMemberRepository.deleteByRoomIdAndMemberId(roomId, memberId);
        anonymizeRecordsInRoom(roomId, memberId);
    }

    private void anonymizeRecordsInRoom(Long roomId, Long memberId) {
        boolean hasMatches = matchParticipantRepository.existsByMemberInRoom(memberId, roomId);
        boolean hasSnapshots = seasonRankSnapshotRepository.existsByMemberIdAndRoomId(memberId, roomId);
        if (!hasMatches && !hasSnapshots) return;
        Member ghost = createLeftPlaceholder();
        if (hasMatches) matchParticipantRepository.reassignMemberInRoom(memberId, ghost, roomId);
        if (hasSnapshots) seasonRankSnapshotRepository.reassignMemberInRoom(memberId, ghost.getId(), roomId);
    }

    /**
     * 나간 사람의 기록을 대신 들고 있을 '나간 멤버' 계정. 어느 방의 멤버도 아니라 재계산이 끝나면 점수가 지워지고
     * 랭킹에 안 나온다. 닉네임은 UNIQUE라 계정 id를 붙인다 — 무작위 번호는 쌓이면 고갈된다.
     * '#'은 일반 닉네임에 못 쓰므로 실제 회원과 겹치지 않는다. NULL 닉네임은 UNIQUE 인덱스에서 빠지므로 먼저 저장해 id를 받는다.
     */
    public Member createLeftPlaceholder() {
        Member ghost = memberRepository.save(Member.builder().role(Member.ROLE_LEFT).build());
        ghost.updateNickname("나간 멤버 #" + ghost.getId());
        return ghost;
    }

    @Transactional
    public void deleteRoom(Long roomId, Long requesterId) {
        RoomMember rm = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_ROOM_MEMBER));
        if (rm.getRole() != MemberRole.HOST) {
            throw new AppException(ErrorCode.ROOM_OWNER_ONLY_DELETE);
        }
        deleteRoomInternal(roomId);
    }

    /** 커뮤니티가 삭제될 때 그 커뮤니티의 방도 경기 기록·점수와 함께 지운다. */
    @Transactional
    public void deleteRoomsOfCommunity(Long communityId) {
        for (Room room : roomRepository.findByCommunityId(communityId)) {
            deleteRoomInternal(room.getId());
        }
    }

    private void deleteRoomInternal(Long roomId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        matchRecordRepository.deleteByRoomId(roomId);
        playerGameRatingRepository.deleteByRoomId(roomId);
        roomRepository.delete(room);
    }

    @Transactional
    public void deleteMember(Long memberId) {
        List<RoomMember> myRooms = new java.util.ArrayList<>(roomMemberRepository.findByMemberId(memberId));
        for (RoomMember rm : myRooms) {
            leaveRoom(rm.getRoom().getId(), memberId, memberId);
        }
        // 이미 나간 방에 실명으로 남은 기록도 '나간 멤버'로 옮긴다. 지우면 재계산 때 다른 멤버 점수가 바뀐다
        java.util.Set<Long> leftoverRoomIds = new java.util.LinkedHashSet<>(matchParticipantRepository.findRoomIdsByMemberId(memberId));
        leftoverRoomIds.addAll(seasonRankSnapshotRepository.findRoomIdsByMemberId(memberId));
        for (Long roomId : leftoverRoomIds) anonymizeRecordsInRoom(roomId, memberId);

        // 방 없는 옛 경기 줄만 남는다
        matchParticipantRepository.deleteByMemberId(memberId);
        playerGameRatingRepository.deleteByMember_Id(memberId);
        roomMemberRepository.deleteByMember_Id(memberId);

        // 소유한 커뮤니티는 다음 소유자에게 넘기고, 남은 멤버가 아무도 없을 때만 지운다
        List<Community> ownedCommunities = communityRepository.findAllByCreatedBy(memberId);
        for (Community community : ownedCommunities) {
            Optional<Long> nextOwnerId = findNextOwner(community, memberId);
            if (nextOwnerId.isPresent()) {
                community.transferOwnership(nextOwnerId.get());
                communityRepository.save(community);
                continue;
            }
            deleteRoomsOfCommunity(community.getId());
            communityAdminRepository.deleteByCommunityId(community.getId());
            communityMemberRepository.deleteByCommunityId(community.getId());
            communityRepository.delete(community);
        }

        communityAdminRepository.deleteByMemberId(memberId);
        communityMemberRepository.deleteByMemberId(memberId);

        memberRepository.deleteById(memberId);
    }

    /**
     * 탈퇴하는 소유자의 커뮤니티를 넘겨받을 사람. 다른 어드민이 있으면 가장 오래된 어드민,
     * 없으면 그 커뮤니티 방들에서 경기를 가장 많이 한 멤버(동점·전원 0판이면 먼저 가입한 멤버)를 어드민으로 올려 넘긴다.
     */
    private Optional<Long> findNextOwner(Community community, Long leavingMemberId) {
        Optional<CommunityAdmin> oldestAdmin = communityAdminRepository.findByCommunityId(community.getId()).stream()
            .filter(admin -> !admin.getMember().getId().equals(leavingMemberId))
            .min(CommunityAdmin.OLDEST_FIRST);
        if (oldestAdmin.isPresent()) return Optional.of(oldestAdmin.get().getMember().getId());

        Map<Long, Long> playCounts = new HashMap<>();
        for (Object[] row : matchParticipantRepository.countByMemberInCommunity(community.getId())) {
            playCounts.put((Long) row[0], (Long) row[1]);
        }
        Optional<CommunityMember> mostActive = communityMemberRepository.findByCommunityId(community.getId()).stream()
            .filter(cm -> !cm.getMember().getId().equals(leavingMemberId))
            .min(Comparator.<CommunityMember>comparingLong(cm -> -playCounts.getOrDefault(cm.getMember().getId(), 0L))
                .thenComparing(CommunityMember::getJoinedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CommunityMember::getId));
        mostActive.ifPresent(cm -> communityAdminRepository.save(new CommunityAdmin(community, cm.getMember())));
        return mostActive.map(cm -> cm.getMember().getId());
    }

    @Transactional
    public void updateMemberRating(Long roomId, Long memberId, Long requesterId, double rating) {
        RoomMember requesterMember = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_ROOM_MEMBER));
        if (requesterMember.getRole() != MemberRole.HOST) {
            throw new AppException(ErrorCode.ROOM_OWNER_ONLY_EDIT_SCORE);
        }

        // 점수 줄은 (멤버, 게임, 방)마다 하나라, 한 방에 게임 여러 개를 기록하던 시절의 방에는
        // 같은 멤버 줄이 게임별로 남아 있다. 방의 게임으로 좁혀야 한 줄이 된다.
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        Optional<com.board_game_back.Entity.PlayerGameRating> found = room.getBoardGameId() != null
            ? playerGameRatingRepository.findByMember_IdAndBoardGame_IdAndRoom_Id(memberId, room.getBoardGameId(), roomId)
            : playerGameRatingRepository.findByMember_IdAndRoom_Id(memberId, roomId);
        com.board_game_back.Entity.PlayerGameRating pgr = found
            .orElseThrow(() -> new AppException(ErrorCode.RATING_NOT_FOUND));

        if (pgr.getPlayCount() > 0) {
            throw new AppException(ErrorCode.SCORE_LOCKED_BY_MATCHES);
        }

        pgr.updateInitialRating(rating);
        playerGameRatingRepository.save(pgr);
    }
}
