-- V7 트랜잭션 롤백 대비: best_display_score 컬럼이 없는 경우 안전하게 추가
ALTER TABLE member
    ADD COLUMN IF NOT EXISTS best_display_score DOUBLE PRECISION NOT NULL DEFAULT 1500.0;
