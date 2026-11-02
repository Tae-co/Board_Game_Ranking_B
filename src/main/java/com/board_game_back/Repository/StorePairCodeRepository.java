package com.board_game_back.Repository;

import com.board_game_back.Entity.StorePairCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StorePairCodeRepository extends JpaRepository<StorePairCode, Long> {

    Optional<StorePairCode> findByNonce(String nonce);

    /** 아직 살아 있는(안 지났고 입력 안 된) 같은 숫자 */
    @Query("SELECT p FROM StorePairCode p WHERE p.code = :code AND p.expiresAt > :now AND p.claimedDeviceId IS NULL")
    List<StorePairCode> findLive(@Param("code") String code, @Param("now") LocalDateTime now);

    /** 지난 숫자 정리. 입력됐지만 기기가 안 받아 간 줄도 하루 지나면 지운다 */
    @Modifying
    @Query("DELETE FROM StorePairCode p WHERE p.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") LocalDateTime before);
}
