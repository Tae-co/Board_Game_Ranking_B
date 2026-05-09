package com.board_game_back.Service;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.Member;
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

    /**
     * 1. 새로운 방 생성
     */
    @Transactional
    public Room createRoom(String roomName, Long memberId, Long boardGameId, Long communityId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        if (communityId != null && !communityAdminRepository.existsByCommunityIdAndMemberId(communityId, memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "커뮤니티 어드민만 그룹을 만들 수 있습니다.");
        }

        String inviteCode = InviteCodeUtil.generate();
        Room room = new Room(roomName, inviteCode, boardGameId);
        if (communityId != null) room.setCommunityId(communityId);
        Room savedRoom = roomRepository.save(room);

        RoomMember roomMember = new RoomMember(savedRoom, member, "HOST");
        roomMemberRepository.save(roomMember);

        // 방장 PlayerGameRating 즉시 생성 (랭킹에 바로 노출)
        if (boardGameId != null) {
            boardGameRepository.findById(boardGameId).ifPresent(game -> {
                boolean exists = playerGameRatingRepository
                    .findByMemberAndBoardGameAndRoom(member, game, savedRoom).isPresent();
                if (!exists) {
                    playerGameRatingRepository.save(
                        PlayerGameRating.builder().member(member).boardGame(game).room(savedRoom)
                            .build()
                    );
                }
            });
        }

        return savedRoom;
    }

    /**
     * 2. 초대 코드로 방 가입
     */
    @Transactional
    public Room joinRoom(String inviteCode, Long memberId) {
        Room room = roomRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 코드입니다."));

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        boolean isAlreadyMember = roomMemberRepository.findByRoomIdAndMemberId(room.getId(),
            memberId).isPresent();

        if (isAlreadyMember) {
            return room;
        }

        RoomMember roomMember = new RoomMember(room, member, "MEMBER");
        roomMemberRepository.save(roomMember);

        // PlayerGameRating 없으면 생성 (랭킹에 바로 노출)
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

    /**
     * 3. 내가 속한 방 목록 조회
     */
    @Transactional(readOnly = true)
    public List<Room> getMyRooms(Long memberId) {
        return roomMemberRepository.findByMemberId(memberId).stream()
            .map(RoomMember::getRoom)
            .collect(Collectors.toList());
    }

    /**
     * 4. 특정 방의 모든 멤버 조회
     */
    @Transactional(readOnly = true)
    public List<Member> getMembersInRoom(Long roomId) {
        return roomMemberRepository.findByRoomId(roomId).stream()
            .map(RoomMember::getMember)
            .collect(Collectors.toList());
    }

    /**
     * 5. 특정 방 상세 정보 조회
     */
    @Transactional
    public void updateRoomName(Long roomId, Long requesterId, String newName) {
        RoomMember rm = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
            .orElseThrow(() -> new IllegalArgumentException("방 멤버가 아닙니다."));
        if (!"HOST".equals(rm.getRole())) {
            throw new IllegalStateException("방장만 수정할 수 있습니다.");
        }
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));
        room.setName(newName);
    }

    public Room getRoomById(Long roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));
    }

    /**
     * 6. 방 나가기 (방장이면 1등 멤버에게 자동 위임)
     */
    @Transactional
    public void leaveRoom(Long roomId, Long memberId) {
        RoomMember rm = roomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            .orElseThrow(() -> new IllegalArgumentException("해당 방의 멤버가 아닙니다."));

        if ("HOST".equals(rm.getRole())) {
            // 방장 본인 제외한 다른 멤버 목록
            List<RoomMember> others = roomMemberRepository.findByRoomId(roomId).stream()
                .filter(m -> !m.getMember().getId().equals(memberId))
                .collect(Collectors.toList());

            if (others.isEmpty()) {
                // 혼자 남은 경우 방 전체 삭제
                deleteRoom(roomId);
                return;
            }

            // 방의 게임 ID 조회
            Long boardGameId = roomRepository.findById(roomId)
                .map(Room::getBoardGameId)
                .orElse(null);

            // 실제 방 멤버 중에서만 다음 방장을 고른다.
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
                        other -> rankingOrder.getOrDefault(other.getMember().getId(),
                            Integer.MAX_VALUE)
                    ))
                    .orElse(null);
            }

            // 랭킹이 없거나 랭킹 데이터가 꼬였으면 첫 번째 멤버에게 위임
            if (nextHostMember == null) {
                nextHostMember = others.get(0);
            }

            nextHostMember.setRole("HOST");
            roomMemberRepository.save(nextHostMember);
        }

        // PlayerGameRating(랭킹) 삭제
        playerGameRatingRepository.deleteByMember_IdAndRoom_Id(memberId, roomId);
        // RoomMember 삭제
        roomMemberRepository.deleteByRoomIdAndMemberId(roomId, memberId);
    }

    /**
     * 7. 방 삭제 (방장만 가능)
     */
    @Transactional
    public void deleteRoom(Long roomId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));

        // MatchRecord(+MatchParticipant cascade) 먼저 삭제
        matchRecordRepository.deleteByRoomId(roomId);

        // PlayerGameRating FK 삭제
        playerGameRatingRepository.deleteByRoomId(roomId);

        // Room 삭제 (CascadeType.ALL로 RoomMember도 함께 삭제됨)
        roomRepository.delete(room);
    }

    /**
     * 8. 회원 탈퇴 - 속한 모든 방에서 leaveRoom 처리 후 계정 삭제
     */
    @Transactional
    public void deleteMember(Long memberId) {
        // 속한 모든 방에서 순차적으로 나가기 (방장이면 위임 또는 방 삭제)
        List<RoomMember> myRooms = new java.util.ArrayList<>(
            roomMemberRepository.findByMemberId(memberId));
        for (RoomMember rm : myRooms) {
            leaveRoom(rm.getRoom().getId(), memberId);
        }

        // 비정상 데이터가 남아 있어도 탈퇴가 막히지 않도록 한 번 더 정리
        matchParticipantRepository.deleteByMemberId(memberId);
        playerGameRatingRepository.deleteByMember_Id(memberId);
        roomMemberRepository.deleteByMember_Id(memberId);

        // 생성한 커뮤니티 삭제 (community.created_by FK 제거)
        List<Community> ownedCommunities = communityRepository.findAllByCreatedBy(memberId);
        for (Community community : ownedCommunities) {
            communityAdminRepository.deleteByCommunityId(community.getId());
            communityMemberRepository.deleteByCommunityId(community.getId());
            communityRepository.delete(community);
        }

        // 다른 커뮤니티에서의 관리자/멤버 기록 삭제
        communityAdminRepository.deleteByMemberId(memberId);
        communityMemberRepository.deleteByMemberId(memberId);

        // Member 삭제
        memberRepository.deleteById(memberId);
    }

    /**
     * 초기 LP 설정 (방장만 가능, 매치 기록 없는 멤버만)
     */
    @Transactional
    public void updateMemberRating(Long roomId, Long memberId, Long requesterId, double rating) {
        // 1. 요청자가 방장인지 확인
        RoomMember requesterMember = roomMemberRepository.findByRoomIdAndMemberId(roomId,
                requesterId)
            .orElseThrow(() -> new IllegalArgumentException("방 멤버가 아닙니다."));
        if (!"HOST".equals(requesterMember.getRole())) {
            throw new IllegalStateException("방장만 점수를 수정할 수 있습니다.");
        }

        // 2. 대상 멤버의 PlayerGameRating 조회
        com.board_game_back.Entity.PlayerGameRating pgr = playerGameRatingRepository.findByMember_IdAndRoom_Id(
                memberId, roomId)
            .orElseThrow(() -> new IllegalArgumentException("해당 멤버의 점수 정보가 없습니다."));

        // 3. 매치 기록이 있으면 수정 불가
        if (pgr.getPlayCount() > 0) {
            throw new IllegalStateException("매치 기록이 있는 멤버의 점수는 수정할 수 없습니다.");
        }

        // 4. LP 업데이트 — 입력값은 display score, μ로 역산: μ = (display - 1500) / 50 + 3σ
        double currentSigma = pgr.getGameStats().getRatingDeviation();
        double newMu = (rating - 1500.0) / 50.0 + 3 * currentSigma;
        pgr.updateInitialRating(newMu);
        playerGameRatingRepository.save(pgr);
    }
}
