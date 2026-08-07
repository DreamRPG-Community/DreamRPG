CREATE TABLE IF NOT EXISTS player_storage (
    uuid VARCHAR(36) PRIMARY KEY,
    format_version INTEGER NOT NULL,
    inventory_base64 TEXT NOT NULL,
    armor_base64 TEXT NOT NULL,
    extra_base64 TEXT NOT NULL,
    held_slot INTEGER NOT NULL,
    ender_chest_base64 TEXT NOT NULL,
    ender_chest_page INTEGER NOT NULL,
    version BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
)
