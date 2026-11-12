package com.board_game_back.Exception;

import org.springframework.http.HttpStatus;

/**
 * 앱 공통 오류 목록. 응답은 {"message", "code"} — 화면은 문구 대신 code로 나눌 수 있다.
 *
 * <p>⚠️ 상태 코드는 통일하기 전 응답과 똑같이 맞춰 뒀다 ("없음"이 400, "방장만"이 409인 것 등).
 * 404·403으로 바로잡으면 프론트의 403 토큰 갱신·점수판 저장 분기가 달라지므로 따로 한다.
 * 매장 오류는 {@link StoreErrorCode}가 따로 맡는다.
 */
public enum ErrorCode {

    // ── 공통 ──
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
    SELF_ONLY(HttpStatus.FORBIDDEN, "본인 정보만 볼 수 있습니다."),
    ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 게임방을 볼 권한이 없습니다."),
    COMMUNITY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 커뮤니티를 볼 권한이 없습니다."),
    COMMUNITY_MEMBER_ONLY_JOIN_ROOM(HttpStatus.FORBIDDEN, "커뮤니티 멤버만 이 게임방에 참가할 수 있습니다."),

    // ── 회원 ──
    USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 유저입니다."),
    MEMBER_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 멤버입니다."),
    ACCOUNT_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 회원입니다."),
    NICKNAME_TAKEN(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),

    // ── 커뮤니티 ──
    COMMUNITY_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 커뮤니티입니다."),
    INVALID_INVITE_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 초대 코드입니다."),
    NOT_COMMUNITY_MEMBER(HttpStatus.BAD_REQUEST, "이 커뮤니티의 멤버가 아닙니다."),
    COMMUNITY_ADMIN_ONLY_EDIT(HttpStatus.FORBIDDEN, "커뮤니티 어드민만 수정할 수 있습니다."),
    COMMUNITY_ADMIN_ONLY_LINK_ROOM(HttpStatus.FORBIDDEN, "커뮤니티 어드민만 그룹을 연결할 수 있습니다."),
    COMMUNITY_ADMIN_ONLY_CREATE_ROOM(HttpStatus.FORBIDDEN, "커뮤니티 어드민만 그룹을 만들 수 있습니다."),
    COMMUNITY_ADMIN_ONLY_KICK(HttpStatus.FORBIDDEN, "커뮤니티 어드민만 멤버를 제거할 수 있습니다."),
    COMMUNITY_ADMIN_ONLY_CREATE_GAME(HttpStatus.FORBIDDEN, "커뮤니티 어드민만 점수판을 만들 수 있습니다."),
    COMMUNITY_ADMIN_ONLY_DELETE_GAME(HttpStatus.FORBIDDEN, "커뮤니티 어드민만 삭제할 수 있습니다."),
    COMMUNITY_ADMIN_ONLY_UPLOAD(HttpStatus.FORBIDDEN, "커뮤니티 어드민만 이미지를 올릴 수 있습니다."),
    COMMUNITY_OWNER_ONLY_DELETE(HttpStatus.FORBIDDEN, "커뮤니티 생성자만 삭제할 수 있습니다."),
    COMMUNITY_OWNER_CANNOT_BE_KICKED(HttpStatus.FORBIDDEN, "커뮤니티 소유자는 내보낼 수 없습니다."),
    COMMUNITY_OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT,
        "커뮤니티를 만든 사람은 나갈 수 없어요. 관리에서 소유권을 넘기거나 커뮤니티를 삭제해 주세요."),
    LAST_ADMIN_CANNOT_RESIGN(HttpStatus.BAD_REQUEST, "다른 어드민이 1명 이상 있어야 어드민을 내려놓을 수 있습니다."),
    ADMIN_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "어드민은 최대 %d명입니다."),

    // ── 방(그룹) ──
    ROOM_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 방입니다."),
    ROOM_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "방 이름은 비워둘 수 없습니다."),
    NOT_ROOM_MEMBER(HttpStatus.BAD_REQUEST, "방 멤버가 아닙니다."),
    TARGET_NOT_ROOM_MEMBER(HttpStatus.BAD_REQUEST, "해당 방의 멤버가 아닙니다."),
    ROOM_OWNER_ONLY_EDIT(HttpStatus.CONFLICT, "방장만 수정할 수 있습니다."),
    ROOM_OWNER_ONLY_EDIT_SCORE(HttpStatus.CONFLICT, "방장만 점수를 수정할 수 있습니다."),
    ROOM_OWNER_ONLY_KICK(HttpStatus.FORBIDDEN, "방장만 멤버를 강퇴할 수 있습니다."),
    ROOM_OWNER_ONLY_DELETE(HttpStatus.FORBIDDEN, "방장만 방을 삭제할 수 있습니다."),
    RATING_NOT_FOUND(HttpStatus.BAD_REQUEST, "해당 멤버의 점수 정보가 없습니다."),
    SCORE_LOCKED_BY_MATCHES(HttpStatus.CONFLICT, "매치 기록이 있는 멤버의 점수는 수정할 수 없습니다."),

    // ── 시즌 ──
    SEASON_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "시즌 이름을 입력해주세요."),
    SEASON_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "시즌 이름은 %d자 이하로 입력해주세요."),
    SEASON_END_DATE_REQUIRED(HttpStatus.BAD_REQUEST, "시즌 종료일을 입력해주세요."),
    SEASON_END_DATE_TOO_EARLY(HttpStatus.BAD_REQUEST, "시즌 종료일은 내일 이후로 정해주세요."),

    // ── 매치 ──
    MATCH_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 매치입니다."),
    MATCH_TO_DELETE_NOT_FOUND(HttpStatus.BAD_REQUEST, "매치를 찾을 수 없습니다."),
    MATCH_WITHOUT_ROOM(HttpStatus.BAD_REQUEST, "방 정보가 없는 매치는 삭제할 수 없습니다."),
    ROOM_MEMBER_ONLY_RECORD(HttpStatus.FORBIDDEN, "방 멤버만 매치를 등록할 수 있습니다."),
    PARTICIPANT_NOT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "방 멤버가 아닌 참가자는 등록할 수 없습니다."),
    MATCH_GAME_NOT_ROOM_GAME(HttpStatus.BAD_REQUEST, "이 방의 게임이 아닌 경기는 기록할 수 없습니다."),
    MATCH_REQUEST_INCOMPLETE(HttpStatus.BAD_REQUEST, "게임과 방 정보가 필요합니다."),
    MATCH_TOO_FEW_PARTICIPANTS(HttpStatus.BAD_REQUEST, "참가자는 2명 이상이어야 합니다."),
    MATCH_DUPLICATE_PARTICIPANT(HttpStatus.BAD_REQUEST, "같은 참가자가 두 번 들어갈 수 없습니다."),
    MATCH_INVALID_PLACEMENT(HttpStatus.BAD_REQUEST, "순위는 1등부터 참가자 수 사이여야 합니다."),
    ROOM_OWNER_ONLY_EDIT_MATCH(HttpStatus.FORBIDDEN, "방장만 매치를 수정할 수 있습니다."),
    ROOM_OWNER_ONLY_DELETE_MATCH(HttpStatus.FORBIDDEN, "방장만 삭제할 수 있습니다."),
    CHIP_DELTA_REQUIRED(HttpStatus.BAD_REQUEST, "칩 손익을 입력해주세요."),
    BUY_INS_INVALID(HttpStatus.BAD_REQUEST, "바이인 횟수는 1 이상이어야 합니다."),

    // ── 게임(점수판) ──
    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 게임입니다."),
    /** GAME_NOT_FOUND와 같은 뜻인데 경기 기록·관리자 수정에서는 지금 400으로 나간다 */
    UNKNOWN_GAME(HttpStatus.BAD_REQUEST, "존재하지 않는 게임입니다."),
    GAME_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "게임 이름을 입력해주세요."),
    COMMUNITY_ID_REQUIRED(HttpStatus.BAD_REQUEST, "커뮤니티 정보가 필요합니다."),
    DUPLICATE_GAME_NAME(HttpStatus.CONFLICT, "이미 같은 이름의 게임이 있습니다."),
    INVALID_PLAYER_COUNT(HttpStatus.BAD_REQUEST, "인원수 설정이 올바르지 않습니다."),
    OFFICIAL_GAME_UNDELETABLE(HttpStatus.FORBIDDEN, "기본 제공 게임은 삭제할 수 없습니다."),
    GAME_IN_USE_BY_ROOMS(HttpStatus.CONFLICT, "%s 그룹에서 사용 중이에요.\n그룹을 먼저 삭제한 뒤 점수판을 삭제해주세요."),
    GAME_HAS_MATCHES(HttpStatus.CONFLICT, "플레이 기록이 남아 있어 삭제할 수 없어요."),
    IMAGE_FILE_ONLY(HttpStatus.BAD_REQUEST, "이미지 파일만 올릴 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다."),
    SCHEMA_REQUIRED(HttpStatus.BAD_REQUEST, "점수판 정보가 없습니다."),
    SCHEMA_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "점수판 형식이 올바르지 않습니다."),
    SCHEMA_UNSUPPORTED_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 점수판 형식입니다."),
    SCHEMA_CATEGORY_REQUIRED(HttpStatus.BAD_REQUEST, "점수 항목을 1개 이상 추가해주세요."),
    SCHEMA_CATEGORY_LABEL_REQUIRED(HttpStatus.BAD_REQUEST, "항목 이름을 모두 입력해주세요."),
    ADMIN_SCHEMA_PARSE_ERROR(HttpStatus.BAD_REQUEST, "schema_json 파싱 오류: %s"),
    ADMIN_SCHEMA_INVALID_TYPE(HttpStatus.BAD_REQUEST, "유효하지 않은 schema type: %s");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }

    public String message() { return message; }
}
