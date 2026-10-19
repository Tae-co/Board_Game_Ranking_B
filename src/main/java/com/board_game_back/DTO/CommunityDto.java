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

    public record MemberInfo(
        Long memberId,
        String nickname,
        String profileImage
    ) {}
}
