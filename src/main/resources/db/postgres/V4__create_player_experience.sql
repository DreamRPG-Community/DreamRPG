CREATE TABLE IF NOT EXISTS player_experience
(
    uuid
    TEXT
    PRIMARY
    KEY
    NOT
    NULL,
    rpg_level
    BIGINT
    NOT
    NULL
    DEFAULT
    0,
    current_experience
    DECIMAL
(
    30,
    6
) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
    )
