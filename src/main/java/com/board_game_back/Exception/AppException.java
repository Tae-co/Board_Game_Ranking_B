package com.board_game_back.Exception;

/** 앱 공통 비즈니스 오류. 상태 코드와 문구는 {@link ErrorCode}가 정한다 */
public class AppException extends RuntimeException {

    private final ErrorCode code;

    /** args는 문구 속 %s·%d 자리에 들어간다 (예: ADMIN_LIMIT_EXCEEDED의 최대 인원) */
    public AppException(ErrorCode code, Object... args) {
        super(args.length == 0 ? code.message() : code.message().formatted(args));
        this.code = code;
    }

    public ErrorCode code() { return code; }
}
