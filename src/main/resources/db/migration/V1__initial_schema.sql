CREATE TABLE IF NOT EXISTS member (
    member_id BIGSERIAL PRIMARY KEY,
    phone_number VARCHAR(255) UNIQUE,
    social_id VARCHAR(255) UNIQUE,
    username VARCHAR(255),
    nickname VARCHAR(255),
    password VARCHAR(255),
    role VARCHAR(255),
    profile_image TEXT,
    overall_rating DOUBLE PRECISION,
    overall_rd DOUBLE PRECISION,
    overall_volatility DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS board_game (
    board_game_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    image_url VARCHAR(255),
    min_players INTEGER NOT NULL,
    max_players INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS room (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    invite_code VARCHAR(255) UNIQUE,
    board_game_id BIGINT,
    community_id BIGINT,
    session_active BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS community (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    region VARCHAR(100),
    image_url TEXT,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    invite_code VARCHAR(6) UNIQUE
);

CREATE TABLE IF NOT EXISTS community_admin (
    id BIGSERIAL PRIMARY KEY,
    community_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_community_admin UNIQUE (community_id, member_id),
    CONSTRAINT fk_community_admin_community FOREIGN KEY (community_id) REFERENCES community (id),
    CONSTRAINT fk_community_admin_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE TABLE IF NOT EXISTS community_member (
    id BIGSERIAL PRIMARY KEY,
    community_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    joined_at TIMESTAMP(6),
    CONSTRAINT uq_community_member UNIQUE (community_id, member_id),
    CONSTRAINT fk_community_member_community FOREIGN KEY (community_id) REFERENCES community (id),
    CONSTRAINT fk_community_member_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE TABLE IF NOT EXISTS room_member (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT,
    member_id BIGINT,
    role VARCHAR(255),
    CONSTRAINT uq_room_member UNIQUE (room_id, member_id),
    CONSTRAINT fk_room_member_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT fk_room_member_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE TABLE IF NOT EXISTS match_record (
    match_record_id BIGSERIAL PRIMARY KEY,
    board_game_id BIGINT,
    room_id BIGINT,
    played_at TIMESTAMP(6),
    CONSTRAINT fk_match_record_board_game FOREIGN KEY (board_game_id) REFERENCES board_game (board_game_id),
    CONSTRAINT fk_match_record_room FOREIGN KEY (room_id) REFERENCES room (id)
);

CREATE TABLE IF NOT EXISTS match_participant (
    match_participant_id BIGSERIAL PRIMARY KEY,
    match_record_id BIGINT,
    member_id BIGINT,
    placement INTEGER NOT NULL,
    rating_change DOUBLE PRECISION NOT NULL,
    scores_json TEXT,
    CONSTRAINT fk_match_participant_match_record FOREIGN KEY (match_record_id) REFERENCES match_record (match_record_id),
    CONSTRAINT fk_match_participant_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE TABLE IF NOT EXISTS player_game_rating (
    player_game_rating_id BIGSERIAL PRIMARY KEY,
    member_id BIGINT,
    board_game_id BIGINT,
    room_id BIGINT,
    rating DOUBLE PRECISION,
    rating_deviation DOUBLE PRECISION,
    volatility DOUBLE PRECISION,
    play_count INTEGER NOT NULL,
    win_count INTEGER NOT NULL,
    lose_count INTEGER NOT NULL,
    CONSTRAINT fk_player_game_rating_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_player_game_rating_board_game FOREIGN KEY (board_game_id) REFERENCES board_game (board_game_id),
    CONSTRAINT fk_player_game_rating_room FOREIGN KEY (room_id) REFERENCES room (id)
);
