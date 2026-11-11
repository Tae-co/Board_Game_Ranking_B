package com.board_game_back.DTO;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.RatingType;
import java.util.Optional;

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

    /**
     * 관리자 공식 게임 추가·수정. 인원수를 안 보내면 2~6명.
     * 매장 기록 시간은 수정할 때 "보낸 경우만" 바꾼다. record로는 "안 보냄"과 "null로 보냄"을 구분할 수 없어서
     * (Jackson이 둘 다 같은 값으로 채운다) setter가 불렸는지로 판단하는 클래스로 둔다.
     */
    public static class AdminRequest {
        private String name;
        private String imageUrl;
        private Integer minPlayers;
        private Integer maxPlayers;
        private String schemaJson;
        private Integer minMinutes;
        private Integer maxMinutes;
        private boolean recordMinutesSent;

        // Jackson은 이 기본 생성자 + setter로 만든다. 인자 있는 생성자를 두면 Jackson이 그걸 골라
        // 빠진 필드까지 채워 버리므로(= "보냄"으로 표시) 코드에서 만들 때는 of()를 쓴다
        public AdminRequest() {}

        /** minMinutes·maxMinutes: null = 안 보냄, Optional.empty() = null로 보냄(지우기) */
        public static AdminRequest of(String name, String imageUrl, Integer minPlayers, Integer maxPlayers,
                                      String schemaJson, Optional<Integer> minMinutes, Optional<Integer> maxMinutes) {
            AdminRequest req = new AdminRequest();
            req.name = name;
            req.imageUrl = imageUrl;
            req.minPlayers = minPlayers;
            req.maxPlayers = maxPlayers;
            req.schemaJson = schemaJson;
            if (minMinutes != null) req.setMinMinutes(minMinutes.orElse(null));
            if (maxMinutes != null) req.setMaxMinutes(maxMinutes.orElse(null));
            return req;
        }

        public void setName(String name) { this.name = name; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public void setMinPlayers(Integer minPlayers) { this.minPlayers = minPlayers; }
        public void setMaxPlayers(Integer maxPlayers) { this.maxPlayers = maxPlayers; }
        public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }

        public void setMinMinutes(Integer minMinutes) {
            this.minMinutes = minMinutes;
            this.recordMinutesSent = true;
        }

        public void setMaxMinutes(Integer maxMinutes) {
            this.maxMinutes = maxMinutes;
            this.recordMinutesSent = true;
        }

        public String name() { return name; }
        public String imageUrl() { return imageUrl; }
        public Integer minPlayers() { return minPlayers; }
        public Integer maxPlayers() { return maxPlayers; }
        public String schemaJson() { return schemaJson; }
        public boolean hasRecordMinutes() { return recordMinutesSent; }
        public Integer minMinutesOrNull() { return minMinutes; }
        public Integer maxMinutesOrNull() { return maxMinutes; }
    }

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
