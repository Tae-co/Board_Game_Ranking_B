-- member.created_at 컬럼을 마이그레이션으로 정식화한다.
--
-- 이 컬럼은 Member 엔티티에 있으나 마이그레이션에 없었다. 운영 스키마에는
-- 과거 ddl-auto=update로 이미 추가돼 있어 동작했지만, 마이그레이션만으로
-- 스키마를 재현하는 새 환경(로컬 등)에서는 컬럼이 없어 validate가 실패했다.
--
-- IF NOT EXISTS라 운영에서는 no-op(이미 존재), 새 환경에서만 추가된다.
ALTER TABLE member
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP(6) NOT NULL DEFAULT now();
