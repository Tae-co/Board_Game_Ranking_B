package com.board_game_back.Exception;

/**
 * 무료 커뮤니티 인원 한도 초과.
 *
 * <p>IllegalStateException을 상속해 기존 409 처리를 그대로 물려받되, 핸들러가
 * <b>에러 코드와 숫자</b>를 함께 실어 보낸다. 프론트 기본 언어가 en인데 서버
 * 메시지는 한국어라, 문구를 그대로 띄우면 영어 사용자가 한글을 본다.
 * getMessage()는 코드를 모르는 소비자를 위한 폴백으로 남겨둔다.
 */
public class CommunityFullException extends IllegalStateException {

    public static final String CODE = "COMMUNITY_FULL";

    private final long memberCount;
    private final int limit;

    public CommunityFullException(long memberCount, int limit) {
        super("이 모임은 인원이 가득 찼어요 (" + memberCount + "/" + limit + ").\n모임장에게 문의해 주세요.");
        this.memberCount = memberCount;
        this.limit = limit;
    }

    public long getMemberCount() {
        return memberCount;
    }

    public int getLimit() {
        return limit;
    }
}
