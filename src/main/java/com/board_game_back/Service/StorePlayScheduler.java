package com.board_game_back.Service;

import com.board_game_back.Entity.StoreParty;
import com.board_game_back.Repository.StorePartyRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매장 판 자동 종료 (plan-store-impl §3.3). 30초마다:
 * 저장 마감이 지난 판 → EXPIRED(3m), 5분 확인이 지난 판 → RECORDED, 체크인이 끝난 사람 → 그룹에서 뺌.
 * 판·그룹마다 따로 트랜잭션이라 하나가 실패해도 나머지는 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorePlayScheduler {

    private final StoreMatchService matchService;
    private final StorePartyService partyService;
    private final StorePartyRepository partyRepository;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void tick() {
        LocalDateTime now = StorePartyService.now();
        for (Long matchId : matchService.openMatchIds()) {
            try {
                matchService.closeIfDue(matchId, now);
            } catch (Exception e) {
                log.error("매장 판 자동 종료 실패 match={}", matchId, e);
            }
        }
        for (StoreParty party : partyRepository.findByDisbandedAtIsNull()) {
            try {
                partyService.expireCheckins(party.getId(), now);
            } catch (Exception e) {
                log.error("매장 그룹 체크인 만료 처리 실패 party={}", party.getId(), e);
            }
        }
    }
}
