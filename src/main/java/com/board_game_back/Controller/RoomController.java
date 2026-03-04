package com.board_game_back.Controller;

import com.board_game_back.DTO.RoomDto;
import com.board_game_back.DTO.RoomDto.Response;
import com.board_game_back.Entity.Room;
import com.board_game_back.Service.RoomService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // React 테스트를 위해 모든 도메인 허용
public class RoomController {

    private final RoomService roomService;

    /**
     * 1. 방 만들기 POST /api/rooms
     */
    @PostMapping
    public ResponseEntity<RoomDto.Response> createRoom(@RequestBody RoomDto.CreateRequest request) {
        Room room = roomService.createRoom(request.roomName(), request.memberId());
        return ResponseEntity.ok(
            new RoomDto.Response(room.getId(), room.getName(), room.getInviteCode()));
    }

    /**
     * 2. 초대 코드로 방 입장하기 POST /api/rooms/join
     */
    @PostMapping("/join")
    public ResponseEntity<String> joinRoom(@RequestBody RoomDto.JoinRequest request) {
        roomService.joinRoom(request.inviteCode(), request.memberId());
        return ResponseEntity.ok("방 가입에 성공했습니다.");
    }

    /**
     * 3. 내가 참여 중인 방 목록 가져오기 GET /api/rooms/my/{memberId}
     */
    @GetMapping("/my/{memberId}")
    public ResponseEntity<List<Response>> getMyRooms(@PathVariable Long memberId) {
        List<RoomDto.Response> responses = roomService.getMyRooms(memberId).stream()
            .map(r -> new RoomDto.Response(r.getId(), r.getName(), r.getInviteCode()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * 4. 특정 방의 멤버 리스트 조회 (수정됨!) 프론트엔드에서 승패를 기록하려면 memberId가 무조건 필요해. 닉네임(String)만 주던 것을 id와
     * nickname이 둘 다 있는 형태로 바꿔주자.
     */
    @GetMapping("/{roomId}/members")
    public ResponseEntity<List<java.util.Map<String, Object>>> getRoomMembers(
        @PathVariable Long roomId) {
        // 간편하게 Map을 사용해서 JSON 형태로 변환 (나중에 MemberDto 같은 클래스로 빼면 더 좋아!)
        List<java.util.Map<String, Object>> members = roomService.getMembersInRoom(roomId).stream()
            .map(m -> {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("memberId", m.getId());
                map.put("nickname", m.getNickname());
                return map;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(members);
    }

    /**
     * 5. 특정 방 상세 정보 조회 (새로 추가됨!) Invite.jsx 화면에 들어갈 때 방 이름과 초대 코드를 보여주기 위해 필요해. GET
     * /api/rooms/{roomId}
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDto.Response> getRoomDetail(@PathVariable Long roomId) {
        // 방금 RoomService에 추가했던 getRoomById 메서드를 사용!
        Room room = roomService.getRoomById(roomId);
        return ResponseEntity.ok(
            new RoomDto.Response(room.getId(), room.getName(), room.getInviteCode()));
    }
}
