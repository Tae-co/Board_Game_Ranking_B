package com.board_game_back.Service;

import com.board_game_back.DTO.GateDto;
import com.board_game_back.Entity.GateEvent;
import com.board_game_back.Entity.GateKey;
import com.board_game_back.Repository.GateEventRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 페이크 도어 페이월 측정. 결제도, 권한 변경도 하지 않는다 — 기록만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GateEventService {

    private final GateEventRepository gateEventRepository;

    @Transactional
    public void record(GateDto.EventRequest request, Long memberId) {
        if (request.gateKey() == null || request.action() == null) {
            throw new IllegalArgumentException("gateKey와 action은 필수입니다.");
        }
        gateEventRepository.save(new GateEvent(
            memberId, request.communityId(), request.gateKey(), request.action()));
    }

    public GateDto.SummaryResponse summary() {
        Map<GateKey, Counts> byGate = new EnumMap<>(GateKey.class);
        for (GateKey key : GateKey.values()) {
            byGate.put(key, new Counts());
        }

        for (Object[] row : gateEventRepository.countByGateKeyAndAction()) {
            GateKey key = (GateKey) row[0];
            GateEvent.Action action = (GateEvent.Action) row[1];
            long count = ((Number) row[2]).longValue();
            long members = ((Number) row[3]).longValue();

            Counts counts = byGate.get(key);
            switch (action) {
                case HIT -> { counts.hits = count; counts.hitMembers = members; }
                case INTEREST -> { counts.interests = count; counts.interestMembers = members; }
            }
        }

        List<GateDto.GateStat> gates = new ArrayList<>();
        Map<GateKey.Axis, Counts> byAxis = new EnumMap<>(GateKey.Axis.class);
        for (GateKey.Axis axis : GateKey.Axis.values()) {
            byAxis.put(axis, new Counts());
        }

        for (Map.Entry<GateKey, Counts> entry : byGate.entrySet()) {
            GateKey key = entry.getKey();
            Counts c = entry.getValue();
            gates.add(new GateDto.GateStat(
                key.name(), key.getAxis().name(), c.hits, c.hitMembers, c.interests, c.interestMembers));

            Counts axis = byAxis.get(key.getAxis());
            axis.hits += c.hits;
            axis.interests += c.interests;
        }

        List<GateDto.AxisStat> axes = byAxis.entrySet().stream()
            .map(e -> new GateDto.AxisStat(
                e.getKey().name(), e.getValue().hits, e.getValue().interests))
            .toList();

        return new GateDto.SummaryResponse(gates, axes);
    }

    private static final class Counts {
        long hits;
        long hitMembers;
        long interests;
        long interestMembers;
    }
}
