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
        List<Long> adminMemberIds
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
        String inviteCode
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
