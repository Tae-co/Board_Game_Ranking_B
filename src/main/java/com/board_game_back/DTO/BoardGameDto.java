package com.board_game_back.DTO;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.RatingType;

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

    /** 엔티티를 그대로 내보내던 때와 같은 필드 (chipGame은 isChipGame() 게터가 만들던 값) */
    public record Response(
        Long id,
        String name,
        String imageUrl,
        int minPlayers,
        int maxPlayers,
        String schemaJson,
        Long communityId,
        Long createdByMemberId,
        RatingType ratingType,
        boolean chipGame,
        Integer minMinutes,  // 매장 기록 시간(분). null이면 매장에서 시작할 수 없다
        Integer maxMinutes
    ) {
        public static Response from(BoardGame game) {
            return new Response(
                game.getId(),
                game.getName(),
                game.getImageUrl(),
                game.getMinPlayers(),
                game.getMaxPlayers(),
                game.getSchemaJson(),
                game.getCommunityId(),
                game.getCreatedByMemberId(),
                game.getRatingType(),
                game.isChipGame(),
                game.getMinMinutes(),
                game.getMaxMinutes()
            );
        }
    }
}
