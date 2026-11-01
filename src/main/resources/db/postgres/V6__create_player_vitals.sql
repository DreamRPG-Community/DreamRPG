CREATE TABLE IF NOT EXISTS player_vitals (
    uuid VARCHAR(36) PRIMARY KEY,
    health DOUBLE PRECISION NOT NULL,
    food_level INTEGER NOT NULL,
    saturation FLOAT NOT NULL,
    exhaustion FLOAT NOT NULL
)
