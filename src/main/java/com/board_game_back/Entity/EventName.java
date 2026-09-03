package com.board_game_back.Entity;

/**
 * 수집하는 행동 이벤트. 화이트리스트다 — 여기 없는 이름은 기록되지 않는다.
 *
 * <p><b>설계 규칙:</b> 퍼널 단계는 반드시 {@code _STARTED}/{@code _COMPLETED} 쌍으로 둔다.
 * 분모(시도) 없이 분자(완료)만 쌓으면 전환율을 계산할 수 없다.
 *
 * <p>가입은 이벤트로 찍지 않는다. {@code member.created_at}이 이미 그 기록이다.
 */
public enum EventName {

    // 활성화 퍼널: 가입 → 커뮤니티 → 방 → 첫 매치 기록
    COMMUNITY_CREATE_STARTED,
    COMMUNITY_CREATE_COMPLETED,
    COMMUNITY_JOIN_COMPLETED,
    ROOM_CREATE_STARTED,
    ROOM_CREATE_COMPLETED,
    MATCH_FORM_OPENED,
    SCORE_SHEET_OPENED,     // 분모: 점수판을 열었다
    MATCH_SUBMITTED,        // 분자: 끝까지 기록했다. 둘의 비가 게임별 이탈률
    RANKING_VIEWED,

    // 바이럴 퍼널: 초대 화면 → 초대받은 사람의 랜딩 → 가입
    INVITE_SCREEN_OPENED,   // 방장이 초대 화면에 들어옴
    INVITE_LANDING_OPENED,  // 초대받은 사람이 링크를 열었다 (로그인 전이라 member_id는 NULL)

    // 리텐션: 이게 없으면 "앱은 열었는데 아무것도 안 한 유저"를 영영 못 본다
    APP_OPENED
}
