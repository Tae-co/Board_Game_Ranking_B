ALTER TABLE player_game_rating
    ADD COLUMN IF NOT EXISTS last_played_at TIMESTAMP(6);
