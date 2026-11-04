-- 매장 이벤트 상세 화면의 "사용 방법" (예: 계산할 때 이 화면을 카운터에 보여주세요)
ALTER TABLE store_event ADD COLUMN IF NOT EXISTS how_to_use VARCHAR(500);
