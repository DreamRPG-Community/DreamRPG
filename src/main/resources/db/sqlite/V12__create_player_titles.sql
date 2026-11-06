CREATE TABLE player_title_selection (
    uuid VARCHAR(36) PRIMARY KEY,
    title_id VARCHAR(128) NOT NULL DEFAULT ''
);
CREATE TABLE player_titles (
    uuid VARCHAR(36) NOT NULL,
    title_id VARCHAR(128) NOT NULL,
    expires_at BIGINT NOT NULL CHECK (expires_at >= 0),
    PRIMARY KEY (uuid, title_id)
);
