-- displayScore 공식의 offset을 1500 → 500으로 낮춤 (RatingConstants.DISPLAY_OFFSET)
--
-- displayScore = (μ - 3σ) × 50 + offset 는 매 조회 시 계산되므로 DB 손댈 필요가 없다.
-- 단 best_display_score만은 계산 결과를 저장해둔 컬럼이라 옛 스케일(1500 기준)로 남는다.
-- 그대로 두면 새 점수가 옛 최고 기록을 영원히 넘지 못해 기록이 박제된다.
-- 저장된 값도 동일하게 1000만큼 이동시킨다.

UPDATE member
    SET best_display_score = best_display_score - 1000.0;

ALTER TABLE member
    ALTER COLUMN best_display_score SET DEFAULT 500.0;
