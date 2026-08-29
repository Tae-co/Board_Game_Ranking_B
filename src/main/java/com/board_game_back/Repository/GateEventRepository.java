package com.board_game_back.Repository;

import com.board_game_back.Entity.GateEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GateEventRepository extends JpaRepository<GateEvent, Long> {

    /** gate_key × action 별 건수와, 중복을 뺀 사람 수 */
    @Query("""
        SELECT e.gateKey, e.action, COUNT(e), COUNT(DISTINCT e.memberId)
        FROM GateEvent e
        GROUP BY e.gateKey, e.action
        """)
    List<Object[]> countByGateKeyAndAction();
}
