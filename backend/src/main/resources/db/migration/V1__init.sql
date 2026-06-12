-- Senda initial schema: users, categories, transactions

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT       NOT NULL REFERENCES users (id),
    name    VARCHAR(255) NOT NULL,
    type    VARCHAR(10)  NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    color   VARCHAR(7)   NOT NULL,
    active  BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_categories_user_name_type UNIQUE (user_id, name, type)
);

CREATE TABLE transactions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users (id),
    category_id BIGINT        NOT NULL REFERENCES categories (id),
    type        VARCHAR(10)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount      NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    date        DATE          NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_user_date ON transactions (user_id, date);
