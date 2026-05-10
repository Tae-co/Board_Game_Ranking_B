package com.board_game_back.Service;

import com.board_game_back.DTO.RoomDto;
import com.board_game_back.Entity.Community;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Transactional
    public Room createRoom(String roomName, Long memberId, Long boardGameId, Long communityId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        if (communityId != null && !communityAdminRepository.existsByCommunityIdAndMemberId(communityId, memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "커뮤니티 어드민만 그룹을 만들 수 있습니다.");
        }

        String inviteCode = InviteCodeUtil.generate();
        Room room = new Room(roomName, inviteCode, boardGameId);
        if (communityId != null) room.assignCommunity(communityId);
        Room savedRoom = roomRepository.save(room);

        roomMemberRepository.save(new RoomMember(savedRoom, member, MemberRole.HOST));

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
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 코드입니다."));

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

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
            .orElseThrow(() -> new IllegalArgumentException("방 멤버가 아닙니다."));
        if (rm.getRole() != MemberRole.HOST) {
            throw new IllegalStateException("방장만 수정할 수 있습니다.");
        }
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));
        room.rename(newName);
    }

    public Room getRoomById(Long roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));
    }

    @Transactional
    public void leaveRoom(Long roomId, Long memberId, Long requesterId) {
        if (!requesterId.equals(memberId)) {
            RoomMember requester = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
                .orElseThrow(() -> new IllegalArgumentException("방 멤버가 아닙니다."));
            if (requester.getRole() != MemberRole.HOST) {
                throw new SecurityException("방장만 멤버를 강퇴할 수 있습니다.");
            }
        }

        RoomMember rm = roomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            .orElseThrow(() -> new IllegalArgumentException("해당 방의 멤버가 아닙니다."));

        if (rm.getRole() == MemberRole.HOST) {
            List<RoomMember> others = roomMemberRepository.findByRoomId(roomId).stream()
                .filter(m -> !m.getMember().getId().equals(memberId))
                .collect(Collectors.toList());

            if (others.isEmpty()) {
                deleteRoomInternal(roomId);
                return;
            }

            Long boardGameId = roomRepository.findById(roomId).map(Room::getBoardGameId).orElse(null);
            RoomMember nextHostMember = null;

            if (boardGameId != null) {
                List<PlayerGameRating> ratings = playerGameRatingRepository
                    .findByRoomIdAndBoardGameIdOrderByPlayedThenRating(roomId, boardGameId);
                Map<Long, Integer> rankingOrder = new HashMap<>();
                for (int i = 0; i < ratings.size(); i++) {
                    rankingOrder.put(ratings.get(i).getMember().getId(), i);
                }
                nextHostMember = others.stream()
                    .min(java.util.Comparator.comparingInt(
                        other -> rankingOrder.getOrDefault(other.getMember().getId(), Integer.MAX_VALUE)
                    ))
                    .orElse(null);
            }

            if (nextHostMember == null) nextHostMember = others.get(0);
            nextHostMember.promoteToHost();
            roomMemberRepository.save(nextHostMember);
        }

        playerGameRatingRepository.deleteByMember_IdAndRoom_Id(memberId, roomId);
        roomMemberRepository.deleteByRoomIdAndMemberId(roomId, memberId);
    }

    @Transactional
    public void deleteRoom(Long roomId, Long requesterId) {
        RoomMember rm = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
            .orElseThrow(() -> new IllegalArgumentException("방 멤버가 아닙니다."));
        if (rm.getRole() != MemberRole.HOST) {
            throw new SecurityException("방장만 방을 삭제할 수 있습니다.");
        }
        deleteRoomInternal(roomId);
    }

    private void deleteRoomInternal(Long roomId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));
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

        matchParticipantRepository.deleteByMemberId(memberId);
        playerGameRatingRepository.deleteByMember_Id(memberId);
        roomMemberRepository.deleteByMember_Id(memberId);

        List<Community> ownedCommunities = communityRepository.findAllByCreatedBy(memberId);
        for (Community community : ownedCommunities) {
            communityAdminRepository.deleteByCommunityId(community.getId());
            communityMemberRepository.deleteByCommunityId(community.getId());
            communityRepository.delete(community);
        }

        communityAdminRepository.deleteByMemberId(memberId);
        communityMemberRepository.deleteByMemberId(memberId);

        memberRepository.deleteById(memberId);
    }

    @Transactional
    public void updateMemberRating(Long roomId, Long memberId, Long requesterId, double rating) {
        RoomMember requesterMember = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
            .orElseThrow(() -> new IllegalArgumentException("방 멤버가 아닙니다."));
        if (requesterMember.getRole() != MemberRole.HOST) {
            throw new IllegalStateException("방장만 점수를 수정할 수 있습니다.");
        }

        com.board_game_back.Entity.PlayerGameRating pgr = playerGameRatingRepository
            .findByMember_IdAndRoom_Id(memberId, roomId)
            .orElseThrow(() -> new IllegalArgumentException("해당 멤버의 점수 정보가 없습니다."));

        if (pgr.getPlayCount() > 0) {
            throw new IllegalStateException("매치 기록이 있는 멤버의 점수는 수정할 수 없습니다.");
        }

        double currentSigma = pgr.getGameStats().getRatingDeviation();
        double newMu = (rating - 1500.0) / 50.0 + 3 * currentSigma;
        pgr.updateInitialRating(newMu);
        playerGameRatingRepository.save(pgr);
    }
}
