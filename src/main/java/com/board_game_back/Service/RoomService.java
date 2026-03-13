package com.board_game_back.Service;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomMember;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import java.util.List;
import java.util.UUID;
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

    /** 1. 새로운 방 생성 */
    @Transactional
    public Room createRoom(String roomName, Long memberId, Long boardGameId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        String inviteCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Room room = new Room(roomName, inviteCode, boardGameId);
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
                        PlayerGameRating.builder().member(member).boardGame(game).room(savedRoom).build()
                    );
                }
            });
        }

        return savedRoom;
    }

    /** 2. 초대 코드로 방 가입 */
    @Transactional
    public Room joinRoom(String inviteCode, Long memberId) {
        Room room = roomRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 코드입니다."));

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        boolean isAlreadyMember = roomMemberRepository.findByRoomId(room.getId())
            .stream()
            .anyMatch(rm -> rm.getMember().getId().equals(memberId));

        if (!isAlreadyMember) {
            RoomMember roomMember = new RoomMember(room, member, "MEMBER");
            roomMemberRepository.save(roomMember);
        }

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

    /** 3. 내가 속한 방 목록 조회 */
    @Transactional(readOnly = true)
    public List<Room> getMyRooms(Long memberId) {
        return roomMemberRepository.findByMemberId(memberId).stream()
            .map(RoomMember::getRoom)
            .collect(Collectors.toList());
    }

    /** 4. 특정 방의 모든 멤버 조회 */
    @Transactional(readOnly = true)
    public List<Member> getMembersInRoom(Long roomId) {
        return roomMemberRepository.findByRoomId(roomId).stream()
            .map(RoomMember::getMember)
            .collect(Collectors.toList());
    }

    /** 5. 특정 방 상세 정보 조회 */
    @Transactional(readOnly = true)
    public Room getRoomById(Long roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));
    }

    /** 6. 방 나가기 / 강퇴 (memberId를 방에서 제거) */
    @Transactional
    public void leaveRoom(Long roomId, Long memberId) {
        RoomMember rm = roomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            .orElseThrow(() -> new IllegalArgumentException("해당 방의 멤버가 아닙니다."));

        if ("HOST".equals(rm.getRole())) {
            throw new IllegalStateException("방장은 방을 나갈 수 없습니다. 방 삭제를 이용해주세요.");
        }

        roomMemberRepository.deleteByRoomIdAndMemberId(roomId, memberId);
    }

    /** 7. 방 삭제 (방장만 가능) */
    @Transactional
    public void deleteRoom(Long roomId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));

        // PlayerGameRating FK 먼저 삭제
        playerGameRatingRepository.deleteByRoomId(roomId);

        // Room 삭제 (CascadeType.ALL로 RoomMember도 함께 삭제됨)
        roomRepository.delete(room);
    }

    /** 초기 LP 설정 (방장만 가능, 매치 기록 없는 멤버만) */
    @Transactional
    public void updateMemberRating(Long roomId, Long memberId, Long requesterId, double rating) {
        // 1. 요청자가 방장인지 확인
        RoomMember requesterMember = roomMemberRepository.findByRoomIdAndMemberId(roomId, requesterId)
            .orElseThrow(() -> new IllegalArgumentException("방 멤버가 아닙니다."));
        if (!"HOST".equals(requesterMember.getRole())) {
            throw new IllegalStateException("방장만 점수를 수정할 수 있습니다.");
        }

        // 2. 대상 멤버의 PlayerGameRating 조회
        com.board_game_back.Entity.PlayerGameRating pgr = playerGameRatingRepository.findByMember_IdAndRoom_Id(memberId, roomId)
            .orElseThrow(() -> new IllegalArgumentException("해당 멤버의 점수 정보가 없습니다."));

        // 3. 매치 기록이 있으면 수정 불가
        if (pgr.getPlayCount() > 0) {
            throw new IllegalStateException("매치 기록이 있는 멤버의 점수는 수정할 수 없습니다.");
        }

        // 4. LP 업데이트
        pgr.updateInitialRating(rating);
        playerGameRatingRepository.save(pgr);
    }
}
