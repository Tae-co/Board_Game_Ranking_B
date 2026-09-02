package com.board_game_back.Utils;

/**
 * 무료 플랜 한도.
 *
 * <p><b>프론트 {@code src/constants/gates.js}의 FREE_LIMITS와 값이 같아야 한다.</b>
 * 한쪽만 고치면 프론트는 막는데 서버는 통과시키거나 그 반대가 된다.
 *
 * <p>서버가 강제하는 건 지금 MEMBER뿐이다. 나머지 한도는 운영자 본인 화면에서만
 * 발동해서 프론트 판정으로 충분하지만, 인원 한도는 <b>다른 사람이 들어오는</b>
 * 동작이라 그 사람 앱을 믿을 수 없다.
 */
public final class PlanLimits {
    private PlanLimits() {}

    /** 무료 커뮤니티 최대 인원. 대부분의 보드게임이 4~6인 상한이라 한 테이블의 경계와 맞춘 숫자다. */
    public static final int FREE_MEMBERS = 8;
}
