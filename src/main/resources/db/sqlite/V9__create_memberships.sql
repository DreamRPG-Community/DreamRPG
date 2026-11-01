CREATE TABLE IF NOT EXISTS mythic_membership (
    player_uuid VARCHAR(36) NOT NULL,
    membership_id VARCHAR(191) NOT NULL,
    scope VARCHAR(191) NOT NULL,
    expires_at_millis BIGINT,
    paused_by TEXT,
    paused_remaining_millis BIGINT,
    PRIMARY KEY (player_uuid, membership_id, scope)
)
