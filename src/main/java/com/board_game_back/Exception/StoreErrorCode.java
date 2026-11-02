package com.board_game_back.Exception;

import org.springframework.http.HttpStatus;

/** 매장 API 오류. 프론트는 문구가 아니라 이 코드로 화면을 나눈다 (plan-store-impl-p1 §3.7). */
public enum StoreErrorCode {
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "매장을 찾을 수 없어요."),
    STORE_CLOSED(HttpStatus.CONFLICT, "운영을 마친 매장이에요."),
    STAFF_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "이미 다른 매장에 지정된 STAFF예요."),
    TICKET_INVALID(HttpStatus.BAD_REQUEST, "매장 QR을 다시 찍어 주세요."),
    TICKET_EXPIRED(HttpStatus.GONE, "QR을 찍은 지 오래됐어요. 매장 QR을 다시 찍어 주세요."),
    CONSENT_OUTDATED(HttpStatus.BAD_REQUEST, "동의 내용이 바뀌었어요. 다시 확인해 주세요."),
    NOT_STAFF_ROLE(HttpStatus.BAD_REQUEST, "STAFF 역할 계정만 지정할 수 있어요."),
    INVALID_HOURS(HttpStatus.BAD_REQUEST, "영업 시작과 끝 시각이 같을 수 없어요."),
    INVALID_REGION(HttpStatus.BAD_REQUEST, "지원하지 않는 지역이에요."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    NOT_MEMBER(HttpStatus.FORBIDDEN, "이 매장 멤버가 아니에요."),
    NOT_STAFF_OF_STORE(HttpStatus.FORBIDDEN, "이 매장 STAFF만 할 수 있어요."),
    STAFF_CANNOT_JOIN(HttpStatus.FORBIDDEN, "STAFF 계정은 매장에 가입할 수 없어요."),
    STAFF_CANNOT_LEAVE(HttpStatus.FORBIDDEN, "지정된 매장은 나갈 수 없어요. 해제는 관리자에게 요청해 주세요.");

    private final HttpStatus status;
    private final String message;

    StoreErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }

    public String message() { return message; }
}
