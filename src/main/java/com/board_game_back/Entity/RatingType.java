package com.board_game_back.Entity;

/** 게임의 랭킹 방식. CHIP은 순위 대신 판마다 딴/잃은 칩을 누적한다. */
public enum RatingType {
    ELO,
    CHIP
}
