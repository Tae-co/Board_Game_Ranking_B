package com.board_game_back.DTO;

public class BoardGameDto {

    // memberId는 클라이언트가 보내지 않는다. JWT 토큰에서 꺼낸다.
    public record CreateRequest(
        String name,
        Long communityId,
        String schemaJson,
        String imageUrl,
        Integer minPlayers,
        Integer maxPlayers
    ) {}
}
