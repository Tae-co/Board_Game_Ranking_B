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
    STAFF_CANNOT_LEAVE(HttpStatus.FORBIDDEN, "지정된 매장은 나갈 수 없어요. 해제는 관리자에게 요청해 주세요."),
    // ── 오늘의 그룹 · 판 (2단계) ──
    STAFF_CANNOT_PLAY(HttpStatus.FORBIDDEN, "STAFF 계정은 매장 게임에 참가할 수 없어요."),
    NOT_CHECKED_IN(HttpStatus.FORBIDDEN, "체크인 시간이 끝났어요. 매장 QR을 다시 찍어 주세요."),
    PARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "코드가 맞지 않아요."),
    NOT_IN_PARTY(HttpStatus.FORBIDDEN, "오늘의 그룹에 없어요."),
    ALREADY_IN_PARTY(HttpStatus.CONFLICT, "이미 다른 그룹에 있어요."),
    PARTY_PLAYING(HttpStatus.CONFLICT, "게임 중에는 할 수 없어요. 판이 끝난 뒤에 해 주세요."),
    NOT_RECORDER(HttpStatus.FORBIDDEN, "기록자만 할 수 있어요."),
    ROOM_CLOSED(HttpStatus.CONFLICT, "이 매장에서 잠시 닫힌 게임이에요."),
    GAME_TIME_NOT_SET(HttpStatus.CONFLICT, "아직 매장에서 기록할 수 없는 게임이에요."),
    PLAYER_COUNT(HttpStatus.CONFLICT, "그룹 인원이 게임 인원에 맞지 않아요."),
    STORE_NOT_OPEN(HttpStatus.CONFLICT, "지금은 기록할 수 있는 시간이 아니에요."),
    TOO_CLOSE_TO_CLOSING(HttpStatus.CONFLICT, "영업 마감이 가까워서 시작할 수 없어요."),
    MEMBER_NOT_CHECKED_IN(HttpStatus.CONFLICT, "체크인 시간이 끝난 사람이 있어요."),
    MATCH_OPEN(HttpStatus.CONFLICT, "판이 끝난 뒤에 해 주세요."),
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "판을 찾을 수 없어요."),
    MATCH_STATE(HttpStatus.CONFLICT, "판 상태가 바뀌었어요. 화면을 새로 고쳐 주세요."),
    NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "이 판에 참가하지 않았어요."),
    SAVE_TOO_EARLY(HttpStatus.CONFLICT, "아직 저장할 수 없어요."),
    SAVE_DEADLINE_PASSED(HttpStatus.CONFLICT, "기록 시간이 지났어요."),
    INVALID_RESULT(HttpStatus.BAD_REQUEST, "결과를 확인해 주세요."),
    INVALID_RECORD_MINUTES(HttpStatus.BAD_REQUEST, "기록 시간을 확인해 주세요."),
    // ── 매장 기기 (3단계) ──
    PAIR_CODE_INVALID(HttpStatus.BAD_REQUEST, "코드가 맞지 않거나 10분이 지났어요."),
    DEVICE_LIMIT(HttpStatus.CONFLICT, "매장 기기는 최대 30대까지 등록할 수 있어요."),
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "기기를 찾을 수 없어요."),
    DEVICE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "등록이 해제된 기기예요."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요."),
    INVALID_TV_SETTING(HttpStatus.BAD_REQUEST, "TV 화면 설정을 확인해 주세요."),
    // ── 시즌 · 신고 · 점수판 요청 (4단계) ──
    INVALID_SEASON(HttpStatus.BAD_REQUEST, "시즌 이름과 날짜를 확인해 주세요."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 경기예요."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고를 찾을 수 없어요."),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "요청을 찾을 수 없어요."),
    NOT_IN_MATCH(HttpStatus.BAD_REQUEST, "그 경기를 같이 한 사람만 고를 수 있어요.");

    private final HttpStatus status;
    private final String message;

    StoreErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }

    public String message() { return message; }
}
