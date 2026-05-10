CREATE INDEX IF NOT EXISTS idx_match_record_room_game_date
    ON match_record (room_id, board_game_id, played_at DESC);

CREATE INDEX IF NOT EXISTS idx_player_game_rating_room_game
    ON player_game_rating (room_id, board_game_id);

CREATE INDEX IF NOT EXISTS idx_match_participant_match_record
    ON match_participant (match_record_id);

CREATE INDEX IF NOT EXISTS idx_match_participant_member
    ON match_participant (member_id);

ALTER TABLE player_game_rating
    ADD CONSTRAINT uq_player_game_rating UNIQUE (member_id, board_game_id, room_id);
