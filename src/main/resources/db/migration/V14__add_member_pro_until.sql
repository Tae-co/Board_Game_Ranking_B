-- 모임장 Pro 구독 (프로토타입 — 실제 결제 없음).
--
-- 계정 단위다. 커뮤니티 단위로 잡으면 "Pro = 커뮤니티 무제한"과 모순이고,
-- 아직 없는 2번째 커뮤니티의 구독을 어디에 매달지도 정의되지 않는다.
--
-- 서버에 있어야 하는 이유: 커뮤니티에 들어오려는 사람의 앱은 그 모임 운영자가
-- Pro인지 알 방법이 없다. 인원 한도를 join에서 막으려면 서버가 구독을 알아야 한다.
ALTER TABLE member ADD COLUMN IF NOT EXISTS pro_until TIMESTAMP;      -- NULL = 무료. 이 시각까지 Pro
ALTER TABLE member ADD COLUMN IF NOT EXISTS pro_billing VARCHAR(10);  -- MONTHLY | YEARLY (표시용)
-- 해지 예약. 해지해도 pro_until은 안 당긴다 — 낸 돈만큼은 쓰게 한다.
ALTER TABLE member ADD COLUMN IF NOT EXISTS pro_canceled BOOLEAN NOT NULL DEFAULT FALSE;
