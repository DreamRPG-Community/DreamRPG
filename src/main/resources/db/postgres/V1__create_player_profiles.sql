CREATE TABLE IF NOT EXISTS player_profiles
(
    uuid
    VARCHAR
(
    36
) PRIMARY KEY,
    career_id VARCHAR
(
    64
) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
    )
