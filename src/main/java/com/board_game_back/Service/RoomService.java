package com.board_game_back.Service;

import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomMember;
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

        return savedRoom;
    }

    /** 2. 초대 코드로 방 가입 */
    @Transactional
    public void joinRoom(String inviteCode, Long memberId) {
        Room room = roomRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 코드입니다."));

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        boolean isAlreadyMember = roomMemberRepository.findByRoomId(room.getId())
            .stream()
            .anyMatch(rm -> rm.getMember().getId().equals(memberId));

        if (isAlreadyMember) {
            throw new IllegalStateException("이미 가입된 방입니다.");
        }

        RoomMember roomMember = new RoomMember(room, member, "MEMBER");
        roomMemberRepository.save(roomMember);
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
}
