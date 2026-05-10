-- member 테이블에 best_display_score 컬럼 추가
ALTER TABLE member
    ADD COLUMN IF NOT EXISTS best_display_score DOUBLE PRECISION NOT NULL DEFAULT 1500.0;

-- 기존 player_game_rating 데이터로 백필
-- play_count > 0 인 행 중 게임별 최고 displayScore를 계산해 member에 저장
UPDATE member m
SET best_display_score = subq.best
FROM (
    SELECT member_id,
           MAX((rating - 3.0 * rating_deviation) * 50.0 + 1500.0) AS best
    FROM player_game_rating
    WHERE play_count > 0
    GROUP BY member_id
) subq
WHERE m.member_id = subq.member_id;
