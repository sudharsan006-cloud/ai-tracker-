CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,   -- NULL = system default
    name VARCHAR(60) NOT NULL,
    type VARCHAR(10) NOT NULL DEFAULT 'expense' CHECK (type IN ('expense','income')),
    icon VARCHAR(40),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_categories_owner_name
    ON categories (COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name));

CREATE TABLE unparsed_sms_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    raw_text TEXT NOT NULL,
    sender VARCHAR(40),
    received_at TIMESTAMPTZ NOT NULL,
    sms_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','resolved','discarded')),
    resolved_transaction_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, sms_fingerprint)
);

CREATE TABLE bank_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bank_account_id UUID NOT NULL REFERENCES bank_accounts(id) ON DELETE RESTRICT,
    currency_id INT NOT NULL REFERENCES currencies(id) ON DELETE RESTRICT,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    direction VARCHAR(6) NOT NULL CHECK (direction IN ('debit','credit')),
    merchant_raw VARCHAR(255),
    merchant_normalized VARCHAR(255),
    transaction_date TIMESTAMPTZ NOT NULL,
    source VARCHAR(10) NOT NULL CHECK (source IN ('sms','manual','statement')),
    sms_fingerprint VARCHAR(64),
    is_self_transfer BOOLEAN NOT NULL DEFAULT false,
    self_transfer_matched_id UUID REFERENCES bank_transactions(id) ON DELETE SET NULL,
    refund_matched_id UUID REFERENCES bank_transactions(id) ON DELETE SET NULL,
    review_status VARCHAR(30) NOT NULL DEFAULT 'none'
        CHECK (review_status IN ('none','possible_duplicate','possible_self_transfer','possible_refund_match')),
    review_partner_id UUID REFERENCES bank_transactions(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE unparsed_sms_queue
    ADD CONSTRAINT fk_resolved_transaction
    FOREIGN KEY (resolved_transaction_id) REFERENCES bank_transactions(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_tx_sms_fingerprint ON bank_transactions (user_id, sms_fingerprint)
    WHERE sms_fingerprint IS NOT NULL;
CREATE INDEX idx_tx_user_date ON bank_transactions (user_id, transaction_date DESC);
CREATE INDEX idx_tx_review ON bank_transactions (user_id) WHERE review_status <> 'none';
CREATE INDEX idx_tx_merchant_trgm ON bank_transactions USING gin (merchant_normalized gin_trgm_ops);

CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID REFERENCES categories(id) ON DELETE CASCADE,   -- NULL = overall budget
    currency_id INT NOT NULL REFERENCES currencies(id) ON DELETE RESTRICT,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    period VARCHAR(10) NOT NULL DEFAULT 'monthly' CHECK (period = 'monthly'),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_budget_active
    ON budgets (user_id, COALESCE(category_id, '00000000-0000-0000-0000-000000000000'::uuid), currency_id)
    WHERE is_active;

INSERT INTO categories (user_id, name, type, icon) VALUES
    (NULL, 'Food & Dining', 'expense', 'restaurant'),
    (NULL, 'Transport', 'expense', 'directions_car'),
    (NULL, 'Shopping', 'expense', 'shopping_bag'),
    (NULL, 'Bills & Utilities', 'expense', 'receipt'),
    (NULL, 'Entertainment', 'expense', 'movie'),
    (NULL, 'Health', 'expense', 'local_hospital'),
    (NULL, 'Transfer', 'expense', 'swap_horiz'),
    (NULL, 'Other', 'expense', 'category'),
    (NULL, 'Salary', 'income', 'payments'),
    (NULL, 'Other Income', 'income', 'attach_money');

INSERT INTO system_config (key, value, min_value, max_value, description) VALUES
    ('dedup_match_window_hours', 48, 1, 168, 'Window for flagging same-account same-amount transactions as possible duplicates'),
    ('self_transfer_match_window_hours', 24, 1, 72, 'Window for pairing a debit and a credit on two of the user''s own accounts as a possible self-transfer'),
    ('refund_match_similarity_threshold', 0.35, 0.1, 1.0, 'Minimum pg_trgm similarity between merchant names to suggest a refund match'),
    ('refund_match_window_days', 90, 1, 365, 'How many days back to look for the original purchase of a refund');
