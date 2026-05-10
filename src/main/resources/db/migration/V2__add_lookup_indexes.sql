CREATE INDEX IF NOT EXISTS idx_room_community_id ON room (community_id);
CREATE INDEX IF NOT EXISTS idx_room_board_game_id ON room (board_game_id);
CREATE INDEX IF NOT EXISTS idx_room_member_member_id ON room_member (member_id);
CREATE INDEX IF NOT EXISTS idx_community_member_member_id ON community_member (member_id);
CREATE INDEX IF NOT EXISTS idx_community_created_by ON community (created_by);
