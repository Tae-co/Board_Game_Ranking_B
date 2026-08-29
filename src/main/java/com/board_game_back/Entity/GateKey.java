package com.board_game_back.Entity;

/**
 * 페이크 도어 페이월의 게이트 지점.
 *
 * <p>전부 <b>규모 한도</b>다. 랭킹·기록·점수판은 유료가 아니다 — 그건 훅이자 해자라
 * 잠그면 제품이 죽는다. 잠기는 건 모임이 커졌을 때 운영자가 혼자 감당하는 쪽뿐이다.
 * — plan-monetization.md
 */
public enum GateKey {
    MEMBER_LIMIT,       // 참석자 8명 초과
    ROOM_LIMIT,         // 게임방 3개 초과
    SECOND_COMMUNITY,   // 커뮤니티 2개째
    CO_ADMIN            // 공동 관리자 추가
}
