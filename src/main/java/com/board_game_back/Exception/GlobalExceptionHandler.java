package com.board_game_back.Exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /** 정원 초과. 프론트가 언어에 맞게 문구를 만들 수 있도록 코드와 숫자를 같이 보낸다. */
    @ExceptionHandler(CommunityFullException.class)
    public ResponseEntity<Map<String, Object>> handleCommunityFull(CommunityFullException e) {
        return ResponseEntity.status(409).body(Map.of(
            "message", e.getMessage(),
            "code", CommunityFullException.CODE,
            "memberCount", e.getMemberCount(),
            "limit", e.getLimit()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(SecurityException e) {
        return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
    }

    // ResponseStatusException은 RuntimeException을 상속하므로 RuntimeException 핸들러보다 먼저 선언
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        String msg = e.getReason() != null ? e.getReason() : e.getMessage();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", msg));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e, HttpServletRequest req) {
        log.error("Unhandled RuntimeException at {}", req.getRequestURI(), e);
        return ResponseEntity.status(500).body(Map.of("message", "서버 오류가 발생했습니다."));
    }
}
