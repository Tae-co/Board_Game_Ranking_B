package com.board_game_back.Entity;

/**
 * 페이크 도어 페이월의 게이트 지점. 각 게이트가 운영 축인지 랭킹 축인지를 함께 들고 있다.
 *
 * <p>Codex 반박("랭킹이 유료 상품이 아니라는 건 아직 추론이다")을 해소하려고
 * 두 축을 한 페이월에 섞어 올린다. 어느 축에서 막혔을 때 업그레이드를 누르는지가
 * 그 반박의 답이다. — plan-monetization.md 전제 3
 */
public enum GateKey {

    // 운영 축 — 모임장이 혼자 하는 노동
    MEMBER_LIMIT(Axis.OPS),        // 9명째 멤버 추가
    SECOND_COMMUNITY(Axis.OPS),    // 두 번째 커뮤니티 생성
    CO_ADMIN(Axis.OPS),            // 공동 관리자 추가

    // 랭킹 축 — 기록/랭킹 자체의 확장
    CUSTOM_SHEET_LIMIT(Axis.RANKING),  // 커스텀 점수판 4개째
    SEASON_RECAP_SHARE(Axis.RANKING),  // 시즌 결산 카드 공유
    RECORD_EXPORT(Axis.RANKING);       // 기록 내보내기

    public enum Axis { OPS, RANKING }

    private final Axis axis;

    GateKey(Axis axis) {
        this.axis = axis;
    }

    public Axis getAxis() {
        return axis;
    }
}
