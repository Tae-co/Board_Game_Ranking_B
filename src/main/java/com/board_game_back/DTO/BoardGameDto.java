package com.board_game_back.DTO;

public class BoardGameDto {

    public record CreateRequest(
        String name,
        Long communityId,
        Long memberId,
        String schemaJson,
        Integer minPlayers,
        Integer maxPlayers
    ) {}
}
