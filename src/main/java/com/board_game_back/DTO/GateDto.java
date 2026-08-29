package com.board_game_back.DTO;

import com.board_game_back.Entity.GateEvent;
import com.board_game_back.Entity.GateKey;
import java.util.List;

public class GateDto {

    public record EventRequest(
        GateKey gateKey,
        GateEvent.Action action,
        Long communityId
    ) {}

    /** 게이트 하나의 집계. 페이크 도어의 핵심 지표는 hits 대비 interests(전환율)다. */
    public record GateStat(
        String gateKey,
        long hits,
        long hitMembers,
        long interests,
        long interestMembers
    ) {}

    public record SummaryResponse(
        List<GateStat> gates
    ) {}
}
