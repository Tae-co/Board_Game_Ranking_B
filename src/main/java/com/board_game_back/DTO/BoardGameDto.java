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

    /** 관리자 공식 게임 추가·수정. 인원수를 안 보내면 2~6명 */
    public record AdminRequest(
        String name,
        String imageUrl,
        Integer minPlayers,
        Integer maxPlayers,
        String schemaJson
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
        boolean chipGame
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
                game.isChipGame()
            );
        }
    }
}
