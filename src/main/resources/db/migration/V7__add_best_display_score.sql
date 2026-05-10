-- member 테이블에 best_display_score 컬럼 추가
ALTER TABLE member
    ADD COLUMN IF NOT EXISTS best_display_score DOUBLE PRECISION NOT NULL DEFAULT 1500.0;
