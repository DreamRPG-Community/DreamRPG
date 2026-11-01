CREATE TABLE IF NOT EXISTS player_settings (
    uuid VARCHAR(36) PRIMARY KEY,
    allow_mob_knockback BOOLEAN NOT NULL DEFAULT TRUE
)
