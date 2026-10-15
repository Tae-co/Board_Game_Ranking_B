-- 칩 카테고리 게임 (포커류). 순위로 Elo를 매기지 않고 판마다 바이인 손익을 누적한다.
-- 커스텀 점수판을 만들 때 고르고, 바꿀 수 없다 (게임 수정 API가 없다).
-- 누적 손익은 player_game_rating.rating 컬럼에 담는다 (안 뛰었으면 0으로 본다).

ALTER TABLE board_game ADD COLUMN IF NOT EXISTS rating_type VARCHAR(10) NOT NULL DEFAULT 'ELO';

-- 첫 바이인 뒤에 다시 산 횟수의 누적 (판마다 바이인 횟수 - 1).
ALTER TABLE player_game_rating ADD COLUMN IF NOT EXISTS rebuy_count INTEGER NOT NULL DEFAULT 0;

-- 점수판에서 계산한 이번 판 손익과 바이인 횟수. 경기 수정·삭제 때 리플레이에 쓴다.
ALTER TABLE match_participant ADD COLUMN IF NOT EXISTS chip_delta INTEGER;
ALTER TABLE match_participant ADD COLUMN IF NOT EXISTS buy_ins INTEGER;
