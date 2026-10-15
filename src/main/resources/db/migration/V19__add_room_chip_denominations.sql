-- 칩 게임 방에서 쓰는 칩 종류(액면가). 큰 값부터 쉼표로 이어 저장한다 ("1000,500,100,10").
-- 그룹을 만들 때 방장이 정한다. 없으면 프론트가 기본값(1000/500/100/10)을 쓴다.
ALTER TABLE room ADD COLUMN IF NOT EXISTS chip_denominations VARCHAR(100);
