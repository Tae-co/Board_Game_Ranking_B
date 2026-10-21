package com.board_game_back.DTO;

import java.util.List;

public class CommunityDto {

    public record CreateRequest(
        String name,
        String region,
        String imageUrl,
        List<Long> adminMemberIds
    ) {}

    public record UpdateRequest(
        String name,
        String region,
        String imageUrl,
        List<Long> adminMemberIds,  // 소유자를 뺀 어드민. null이면 어드민은 그대로 둔다
        Boolean ownerLeavesAdmin    // 소유자 본인이 어드민을 내려놓을 때만 true (구버전 앱은 안 보낸다)
    ) {}

    public record Response(
        Long communityId,
        String name,
        String region,
        String imageUrl,
        String status,
        long memberCount,
        int groupCount,
        List<AdminInfo> admins,
        String inviteCode
    ) {}

    public record AdminInfo(
        Long memberId,
        String nickname,
        String profileImage
    ) {}

    public record DetailResponse(
        Long communityId,
        String name,
        String region,
        String imageUrl,
        String status,
        int groupCount,
        long memberCount,
        List<AdminInfo> admins,
        String inviteCode,
        Long createdBy  // 소유자(처음엔 생성자). 항상 어드민이고, 본인이 내려놓으면 가장 오래된 어드민에게 넘어간다
    ) {}

    public record RoomResponse(
        Long roomId,
        String roomName,
        String inviteCode,
        Long boardGameId,
        String imageUrl,
        boolean sessionActive,
        boolean isMember,
        long memberCount
    ) {}

    public record JoinRequest(String inviteCode) {}

    /**
     * 참가 응답. 필드는 {@link Response}와 같고 {@code alreadyMember}만 더한다.
     * 감싸지 않고 펼친 이유: 스토어에 나가 있는 구버전 앱이 응답을 커뮤니티로 그대로 읽는다.
     *
     * <p>{@code alreadyMember}: 참가 API는 이미 멤버여도 200이라, 이게 없으면 프론트가
     * "새로 들어왔다"와 "원래 있던 모임에 들어갔다"를 구분할 수 없다.
     */
    public record JoinResponse(
        Long communityId,
        String name,
        String region,
        String imageUrl,
        String status,
        long memberCount,
        int groupCount,
        List<AdminInfo> admins,
        String inviteCode,
        boolean alreadyMember
    ) {
        public static JoinResponse of(Response r, boolean alreadyMember) {
            return new JoinResponse(r.communityId(), r.name(), r.region(), r.imageUrl(), r.status(),
                r.memberCount(), r.groupCount(), r.admins(), r.inviteCode(), alreadyMember);
        }
    }

    public record MemberInfo(
        Long memberId,
        String nickname,
        String profileImage
    ) {}
}
