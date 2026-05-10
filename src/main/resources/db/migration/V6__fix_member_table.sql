-- 1. 구 Glicko-2 값이 남아있는 멤버 TrueSkill 기본값으로 정리
--    (TrueSkill μ≈25 이므로 overall_rating > 100 이면 구 Glicko-2 스케일 값)
UPDATE member
SET overall_rating     = 25.0,
    overall_rd         = 8.3333,
    overall_volatility = 0.06
WHERE overall_rating > 100
   OR overall_rd > 20;

-- 2. V4 리셋으로 overall_volatility=0.0 이 된 미플레이 멤버 초기값 복원
--    (플레이한 멤버는 TrueSkillCalculator가 0.0으로 설정하므로 건드리지 않음)
UPDATE member
SET overall_volatility = 0.06
WHERE overall_volatility = 0.0
  AND overall_rating BETWEEN 24.5 AND 25.5
  AND overall_rd BETWEEN 8.0 AND 8.5;

-- 3. role 컬럼에 DEFAULT 'USER' 추가 (DB 레벨 안전장치)
ALTER TABLE member ALTER COLUMN role SET DEFAULT 'USER';

-- 4. 닉네임 중복 방지 — NOT NULL 닉네임에만 적용되는 부분 UNIQUE 인덱스
--    (OTP 가입 중간 상태 멤버는 nickname=NULL 이므로 제약에서 제외됨)
CREATE UNIQUE INDEX IF NOT EXISTS uq_member_nickname
    ON member (nickname)
    WHERE nickname IS NOT NULL;
