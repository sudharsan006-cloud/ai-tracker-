-- Fix: Phase 2 used ON DELETE RESTRICT here, which makes "delete my account" fail for any user who has
-- transactions (RESTRICT is checked immediately; NO ACTION is checked after the cascade finishes).
DO $$
DECLARE c TEXT;
BEGIN
    SELECT conname INTO c FROM pg_constraint
     WHERE conrelid = 'bank_transactions'::regclass AND contype = 'f'
       AND confrelid = 'bank_accounts'::regclass;
    EXECUTE format('ALTER TABLE bank_transactions DROP CONSTRAINT %I', c);
END $$;
ALTER TABLE bank_transactions
    ADD CONSTRAINT fk_tx_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id);

CREATE TABLE statement_imports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bank_account_id UUID NOT NULL REFERENCES bank_accounts(id),
    file_type VARCHAR(10) NOT NULL CHECK (file_type IN ('pdf','png','jpg')),
    extraction_method VARCHAR(10) CHECK (extraction_method IN ('pdf_text','ocr')),
    status VARCHAR(20) NOT NULL DEFAULT 'processing'
        CHECK (status IN ('processing','ready','failed','committed','expired')),
    error_code VARCHAR(30),
    page_count INT,
    row_count INT,
    flagged_count INT,
    opening_balance NUMERIC(14,2),
    closing_balance NUMERIC(14,2),
    gate_status VARCHAR(20) CHECK (gate_status IN ('passed','failed','not_checkable')),
    committed_count INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_statement_imports_user ON statement_imports (user_id, created_at DESC);

CREATE TABLE statement_import_rows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_id UUID NOT NULL REFERENCES statement_imports(id) ON DELETE CASCADE,
    row_index INT NOT NULL,
    txn_date DATE,
    description VARCHAR(255),
    amount NUMERIC(14,2) CHECK (amount IS NULL OR amount > 0),
    direction VARCHAR(6) CHECK (direction IN ('debit','credit')),
    balance_after NUMERIC(14,2),
    confidence NUMERIC(5,2),
    needs_check BOOLEAN NOT NULL DEFAULT false,
    check_reason VARCHAR(30)
        CHECK (check_reason IN ('missing_fields','parse_error','balance_mismatch','low_confidence')),
    selected BOOLEAN NOT NULL DEFAULT true,
    existing_transaction_id UUID REFERENCES bank_transactions(id) ON DELETE SET NULL,
    UNIQUE (import_id, row_index)
);

INSERT INTO system_config (key, value, min_value, max_value, description) VALUES
    ('statement_ocr_min_confidence', 80, 0, 100, 'Rows read by OCR below this confidence (0-100) are highlighted for checking'),
    ('statement_max_pages', 10, 1, 30, 'Maximum number of pages accepted in one statement'),
    ('statement_dedup_date_tolerance_days', 1, 0, 5, 'Date difference allowed when matching a statement row to an existing transaction'),
    ('statement_import_expiry_days', 7, 1, 30, 'Uncommitted imports older than this are deleted');
