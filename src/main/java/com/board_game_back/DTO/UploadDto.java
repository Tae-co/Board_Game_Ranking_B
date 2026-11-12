package com.board_game_back.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;

public class UploadDto {

    // 📤 [Response] 업로드 결과. 성공이면 {"url": ...}, 실패면 {"error": ...} (Map으로 내보내던 때와 같음)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(String url, String error) {
        public static Response ok(String url) {
            return new Response(url, null);
        }

        public static Response fail(String error) {
            return new Response(null, error);
        }
    }
}
