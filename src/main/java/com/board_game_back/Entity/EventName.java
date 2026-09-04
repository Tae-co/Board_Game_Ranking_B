package com.board_game_back.Entity;

/**
 * 수집하는 행동 이벤트. 화이트리스트다 — 여기 없는 이름은 기록되지 않는다.
 *
 * <p><b>설계 규칙:</b> 퍼널 단계는 반드시 {@code _STARTED}/{@code _COMPLETED} 쌍으로 둔다.
 * 분모(시도) 없이 분자(완료)만 쌓으면 전환율을 계산할 수 없다.
 *
 * <p>가입은 이벤트로 찍지 않는다. {@code member.created_at}이 이미 그 기록이다.
 *
 * <p><b>이름은 화면이 아니라 사용자의 의도를 가리켜야 한다.</b> 초기 설계의
 * {@code INVITE_SCREEN_OPENED}는 경로가 {@code /invite/:roomId}라는 이유로 붙은 이름이었지만,
 * 그 화면은 랭킹·매치기록·기록 시작이 모두 모인 그룹 로비다. 초대 의도로 읽으면
 * 바이럴 퍼널이 통째로 부풀려지므로 {@link #GROUP_LOBBY_OPENED}로 바로잡고,
 * 진짜 초대 의도는 {@link #INVITE_SHARED}로 따로 잡는다.
 */
public enum EventName {

    // 활성화 퍼널: 가입 → 커뮤니티 → 방 → 첫 매치 기록
    COMMUNITY_CREATE_STARTED,
    COMMUNITY_CREATE_COMPLETED,
    COMMUNITY_JOIN_COMPLETED,
    ROOM_CREATE_STARTED,
    ROOM_CREATE_COMPLETED,
    GROUP_LOBBY_OPENED,     // 그룹의 메인 화면. 앱에서 가장 많이 열리는 곳이다
    SCORE_SHEET_OPENED,     // 분모: 기록하려고 점수판을 열었다 (수정·조회·미리보기는 제외)
    MATCH_SUBMITTED,        // 분자: 끝까지 기록했다. 둘의 비가 게임별 이탈률

    // 바이럴 퍼널: 공유 → 초대받은 사람의 랜딩 → 가입
    INVITE_SHARED,          // 공유 시트를 실제로 띄웠다. 화면 진입이 아니라 행동이다
    INVITE_LANDING_OPENED,  // 초대받은 사람이 링크를 열었다 (로그인 전이라 member_id는 NULL)

    // 리텐션: 이게 없으면 "앱은 열었는데 아무것도 안 한 유저"를 영영 못 본다
    APP_OPENED
}
