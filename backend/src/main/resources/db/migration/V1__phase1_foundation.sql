CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE user_profile (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(100),
    date_of_birth DATE,
    sex_at_birth VARCHAR(10),
    height_cm NUMERIC(5,1),
    weight_kg NUMERIC(5,1),
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by UUID REFERENCES refresh_tokens(id)
);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);

CREATE TABLE banks (
    id SERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    short_code VARCHAR(20) UNIQUE NOT NULL,
    country_code VARCHAR(2) NOT NULL DEFAULT 'IN',
    is_active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE currencies (
    id SERIAL PRIMARY KEY,
    code VARCHAR(3) UNIQUE NOT NULL,
    name VARCHAR(60) NOT NULL,
    symbol VARCHAR(5) NOT NULL,
    decimal_places SMALLINT NOT NULL DEFAULT 2
);

CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bank_id INT NOT NULL REFERENCES banks(id) ON DELETE RESTRICT,
    currency_id INT NOT NULL REFERENCES currencies(id) ON DELETE RESTRICT,
    account_last4 VARCHAR(4),
    nickname VARCHAR(60),
    account_type VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE system_config (
    key VARCHAR(80) PRIMARY KEY,
    value NUMERIC NOT NULL,
    min_value NUMERIC NOT NULL,
    max_value NUMERIC NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO currencies (code, name, symbol, decimal_places) VALUES ('INR', 'Indian Rupee', '₹', 2);

INSERT INTO system_config (key, value, min_value, max_value, description) VALUES
    ('bank_account_max_per_user', 20, 1, 100, 'Soft cap on bank accounts a single user can register');

-- Replace with the real banks from BANKS_TO_SEED (section 1):
INSERT INTO banks (name, short_code, country_code) VALUES ('Sample Bank', 'SAMPLE', 'IN');
