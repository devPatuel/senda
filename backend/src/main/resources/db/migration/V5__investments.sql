-- Phase 3: investments. Asset classes group holdings (positions) by pricing
-- source. Quantities and prices use NUMERIC(20,8); fiat values use NUMERIC(14,2).

CREATE TABLE asset_classes (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id),
    name           VARCHAR(100) NOT NULL,
    pricing_source VARCHAR(10)  NOT NULL CHECK (pricing_source IN ('CRYPTO', 'METAL', 'FUND', 'MANUAL')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, name)
);

CREATE INDEX idx_asset_classes_user ON asset_classes (user_id);

CREATE TABLE holdings (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users (id),
    asset_class_id  BIGINT          NOT NULL REFERENCES asset_classes (id),
    symbol          VARCHAR(40)     NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    quantity        NUMERIC(20, 8)  NOT NULL DEFAULT 0,
    avg_cost        NUMERIC(20, 8)  NOT NULL DEFAULT 0,
    current_price   NUMERIC(20, 8),
    last_priced_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_holdings_user ON holdings (user_id);

CREATE TABLE holding_lots (
    id          BIGSERIAL PRIMARY KEY,
    holding_id  BIGINT         NOT NULL REFERENCES holdings (id) ON DELETE CASCADE,
    user_id     BIGINT         NOT NULL REFERENCES users (id),
    quantity    NUMERIC(20, 8) NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(20, 8) NOT NULL CHECK (unit_price >= 0),
    date        DATE           NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_holding_lots_holding ON holding_lots (holding_id);

CREATE TABLE nfts (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT         NOT NULL REFERENCES users (id),
    name                   VARCHAR(100)   NOT NULL,
    collection             VARCHAR(100),
    buy_crypto_symbol      VARCHAR(40)    NOT NULL,
    buy_crypto_amount      NUMERIC(20, 8) NOT NULL,
    fiat_value_at_purchase NUMERIC(14, 2) NOT NULL,
    our_current_value      NUMERIC(14, 2) NOT NULL,
    utility                VARCHAR(500),
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_nfts_user ON nfts (user_id);
