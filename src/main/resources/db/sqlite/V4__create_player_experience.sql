CREATE TABLE IF NOT EXISTS player_experience
(
    uuid
    TEXT
    PRIMARY
    KEY
    NOT
    NULL,
    rpg_level
    INTEGER
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
    version INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
    )
