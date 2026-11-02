package com.board_game_back.Exception;

public class StoreException extends RuntimeException {

    private final StoreErrorCode code;

    public StoreException(StoreErrorCode code) {
        super(code.message());
        this.code = code;
    }

    public StoreErrorCode code() { return code; }
}
