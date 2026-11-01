CREATE TABLE IF NOT EXISTS mythic_membership_player_name (
    player_name_lower VARCHAR(16) PRIMARY KEY,
    player_name VARCHAR(16) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    last_seen_millis BIGINT NOT NULL
)
