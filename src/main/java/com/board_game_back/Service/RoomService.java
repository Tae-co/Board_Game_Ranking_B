package com.board_game_back.Service;

import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomMember;
import com.board_game_back.Repository.MemberRepository;
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

    /**
     * 1. 새로운 방 생성
     */
    @Transactional
    public Room createRoom(String roomName, Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        String inviteCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // 방 엔티티 생성 (생성자에서 초대 코드 자동 생성)
        Room room = new Room(roomName, inviteCode);
        Room savedRoom = roomRepository.save(room);

        // 방 만든 사람을 방장(HOST)으로 등록
        RoomMember roomMember = new RoomMember(savedRoom, member, "HOST");
        roomMemberRepository.save(roomMember);

        return savedRoom;
    }

    /**
     * 2. 초대 코드로 방 가입
     */
    @Transactional
    public void joinRoom(String inviteCode, Long memberId) {
        Room room = roomRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 코드입니다."));

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 이미 가입된 방인지 체크
        boolean isAlreadyMember = roomMemberRepository.findByRoomId(room.getId())
            .stream()
            .anyMatch(rm -> rm.getMember().getId().equals(memberId));

        if (isAlreadyMember) {
            throw new IllegalStateException("이미 가입된 방입니다.");
        }

        // 일반 멤버(MEMBER)로 등록
        RoomMember roomMember = new RoomMember(room, member, "MEMBER");
        roomMemberRepository.save(roomMember);
    }

    /**
     * 3. 내가 속한 방 목록 조회
     */
    @Transactional(readOnly = true)
    public List<Room> getMyRooms(Long memberId) {
        List<RoomMember> myRoomMemberships = roomMemberRepository.findByMemberId(memberId);
        return myRoomMemberships.stream()
            .map(RoomMember::getRoom)
            .collect(Collectors.toList());
    }

    /**
     * 4. 특정 방의 모든 멤버 조회 (게임 참가자 선택용)
     */
    @Transactional(readOnly = true)
    public List<Member> getMembersInRoom(Long roomId) {
        List<RoomMember> roomMembers = roomMemberRepository.findByRoomId(roomId);
        return roomMembers.stream()
            .map(RoomMember::getMember)
            .collect(Collectors.toList());
    }

    /**
     * 5. 특정 방 상세 정보 조회 (Invite.jsx 용)
     */
    @Transactional(readOnly = true)
    public Room getRoomById(Long roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));
    }
}