CREATE TABLE IF NOT EXISTS player_first_join_rewards
(
    uuid TEXT PRIMARY KEY NOT NULL,
    claimed INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    claimed_at BIGINT
)
