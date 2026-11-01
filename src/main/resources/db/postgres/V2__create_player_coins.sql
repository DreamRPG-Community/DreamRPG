CREATE TABLE IF NOT EXISTS player_coins
(
    uuid
    VARCHAR
(
    36
) PRIMARY KEY,
    balance DECIMAL
(
    19,
    2
) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
    )
