ALTER TABLE board_game ADD COLUMN IF NOT EXISTS community_id BIGINT;
ALTER TABLE board_game ADD COLUMN IF NOT EXISTS created_by_member_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_board_game_community ON board_game (community_id);

-- 커뮤니티 안에서 같은 이름 중복 방지.
-- 공식 게임은 community_id가 NULL이고 NULL끼리는 서로 다른 값으로 취급되므로 제약을 받지 않는다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_board_game_community_name
    ON board_game (community_id, name);
