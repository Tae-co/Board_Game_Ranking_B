package com.board_game_back.DTO;

import java.util.List;

public class MatchDto {
    // 📩 [Request] React -> Spring: 게임 결과 제출
    public record ResultRequest(
        Long boardGameId,
        List<ParticipantRequest> participants // 참가자 명단과 등수 리스트
    ) {}

    // 📩 [Request] 위 리스트에 들어갈 개별 참가자 정보
    public record ParticipantRequest(
        Long memberId,
        int placement // 등수 (1등, 2등, 3등...)
    ) {}

    // 📤 [Response] Spring -> React: 결과 처리 후 점수 변동 폭 응답 (애니메이션용)
    public record ResultResponse(
        Long memberId,
        String nickname,
        int placement,
        double ratingChange // 예: +15.2 (올랐음), -8.4 (떨어졌음)
    ) {}

}
