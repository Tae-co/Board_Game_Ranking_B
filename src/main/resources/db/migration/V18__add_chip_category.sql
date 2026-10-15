-- 칩 카테고리 게임 (포커류). 순위로 Elo를 매기지 않고 판마다 딴/잃은 칩을 누적한다.
-- 커스텀 점수판을 만들 때 고르고, 바꿀 수 없다 (게임 수정 API가 없다).

ALTER TABLE board_game ADD COLUMN IF NOT EXISTS rating_type VARCHAR(10) NOT NULL DEFAULT 'ELO';

-- 방장이 정하는 시작 칩. 경기가 시작된 시즌에 바꾸면 pending에 두고 다음 시즌부터 적용한다.
ALTER TABLE room ADD COLUMN IF NOT EXISTS starting_chips INTEGER NOT NULL DEFAULT 1000;
ALTER TABLE room ADD COLUMN IF NOT EXISTS pending_starting_chips INTEGER;

-- 잔고가 0이 된 뒤 다음 판에 자동으로 시작 칩을 다시 받은 횟수. 순수익 계산에 쓴다.
ALTER TABLE player_game_rating ADD COLUMN IF NOT EXISTS rebuy_count INTEGER NOT NULL DEFAULT 0;

-- 점수판에 입력한 칩 증감. ratingChange는 0에서 멈춘 뒤의 실제 증감이라 리플레이에 쓸 수 없다.
ALTER TABLE match_participant ADD COLUMN IF NOT EXISTS chip_delta INTEGER;
