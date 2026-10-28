# AI Life Tracker — Phase 2 Build Prompt (step by step)
### Expense Tracking Core: Categories, Transactions, Review Queue, Budgets, SMS Import (Android)

You are a coding agent. Phase 1 is already built and working (auth, banks, currencies, bank accounts, profile, Flutter app for Android + Web, onboarding). Now build Phase 2 exactly as written. Follow the steps IN ORDER. Do not skip a step. Do not add features that are not listed. After each step, run the check written under it. If a check fails, fix it before moving on. If you cannot fix it, stop and report the exact error.

---

## 0. RULES (read first)

1. Do not invent anything not in this file. If something is unclear, stop and ask.
2. Everything must cost $0. No paid services, no billing accounts.
3. Do not build anything from "OUT OF SCOPE" (section 9).
4. Do NOT change Phase 1 behavior (auth, token refresh, onboarding, bank accounts). Only add. Existing Flyway migration `V1__...` must never be edited; all new SQL goes into `V2__phase2_expense_core.sql`.
5. **The backend calculates every number** (totals, spent, remaining, percent). The Flutter app only displays numbers it receives. Never add up amounts in Dart.
6. **Ambiguous = manual review.** If a match or a parse is not certain, put the item in the review queue. Never guess and save silently.
7. No Lombok. Keep code simple and readable. Never log SMS text, tokens, or passwords.
8. `kIsWeb` may only be used inside `PlatformCapabilities`. SMS code must never be compiled into the web build (see B8).

---

## 1. FILL-IN BY THE HUMAN (the agent must not guess these)

```
BANK_SMS_SAMPLES   (at least 3 real SMS from the bank seeded in Phase 1; hide personal digits if you want, but keep the exact wording and format)
  bank short_code (same as in the banks table): SAMPLE
  sender id as shown on the phone (e.g. VM-XXXXBK): <fill in>
  debit sample 1:  <paste exact SMS text>
  debit sample 2:  <paste exact SMS text>
  credit sample 1: <paste exact SMS text>
  non-transaction sample (OTP or promo): <paste exact SMS text>
```

**If any of these are still `<fill in>` or `<paste ...>`, the agent must build everything EXCEPT step A7 (the bank parser), tell the human which samples are missing, and stop there.** A parser written without real samples is a guess and is forbidden.

---

## 2. WHAT PHASE 2 BUILDS

- Categories (system defaults + the user's own).
- Transactions: manual entry and SMS import. Paginated list with filters.
- Automatic flagging (never auto-linking) of: possible duplicates, possible self-transfers, possible refunds. A review queue to confirm or dismiss them.
- Monthly budgets (overall or per category) with a backend-computed status.
- SMS import on Android only: the app reads the phone's SMS inbox and sends bank messages to the backend, which parses them with a per-bank parser.

Web users get everything except SMS import (browsers cannot read SMS). That is permanent and intended.

### Decisions already made (do not change)

- There is NO generic/fallback SMS parser. Only the tested per-bank parser extracts data.
- SMS from a sender that no parser claims is IGNORED and NOT stored (this keeps OTPs, ads and personal messages out of the database).
- SMS from a known bank that is not a transaction (OTP, promo) is IGNORED and not stored.
- SMS from a known bank that LOOKS like a transaction but cannot be parsed, or whose account is ambiguous, goes to the review queue (`unparsed_sms_queue`).
- No live SMS listener in Phase 2. The app syncs the inbox when the Expenses tab opens and when the user taps "Sync SMS".
- A transaction's currency always comes from its bank account. The client never sends a currency.

---

## 3. FIRST: LOOK AT THE EXISTING CODE (step A0 / B0)

Before writing anything, list the existing backend packages and the Flutter `lib/` tree. Use the SAME names and style that Phase 1 used: `ApiClient`, `PlatformCapabilities`, `AuthService`, `ConfigService`, `HomeShell`, error JSON `{ "error", "message" }`, base path `/api/v1`. Do not rename or move Phase 1 files.

---

# PART A — BACKEND

Java package names: add `com.lifetracker.category`, `com.lifetracker.transaction`, `com.lifetracker.budget`, `com.lifetracker.sms`.

## A1. Migration `V2__phase2_expense_core.sql`

Create `src/main/resources/db/migration/V2__phase2_expense_core.sql` with exactly this:

```sql
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
```

Do not create any other table. Check: the app starts, Flyway applies V2 on a fresh/dev Neon database, `categories` has 10 rows.

## A2. Entities and repositories

Create JPA entities + Spring Data repositories for `categories`, `unparsed_sms_queue`, `bank_transactions`, `budgets`. Use `Instant` for TIMESTAMPTZ, `BigDecimal` for NUMERIC, `UUID` ids (`@GeneratedValue(strategy = GenerationType.UUID)`). For the SQL-heavy parts below (matching, budget sums) use `JdbcTemplate` / native queries exactly as written.

## A3. Categories

Endpoints (auth required):
- `GET /api/v1/categories` → system rows (`user_id IS NULL`) + the user's own rows, only `is_active = true`. Shape: `{ "id", "name", "type", "icon", "isSystem" }`.
- `POST /api/v1/categories` body `{ "name", "type", "icon"? }`. `type` is `expense` or `income`. Name 1–60 chars. If a system category or one of the user's categories already has the same name (case-insensitive) → 409 `CONFLICT`.
- `PATCH /api/v1/categories/{id}` body `{ "name"?, "icon"? }`. Only the user's own categories. A system or another user's id → 404. Same name rule.
- `DELETE /api/v1/categories/{id}` → soft delete (`is_active=false`), 204. Only own categories, else 404. Transactions keep their reference.

## A4. Transactions

### A4.1 Merchant normalization (one function, unit tested)
`normalizeMerchant(String raw)`: null/blank → null. Otherwise lowercase, replace every character that is not `a-z`, `0-9` or space with a space, collapse repeated spaces, trim. Example: `"SWIGGY*ORDER #123"` → `"swiggy order 123"`. Store the result in `merchant_normalized`.

### A4.2 Endpoints (auth required, only the user's own rows; other users' ids → 404)

**POST /api/v1/transactions** body:
```json
{ "bankAccountId": "uuid", "amount": 250.50, "direction": "debit",
  "merchant": "Swiggy", "transactionDate": "2026-10-20T14:30:00Z", "categoryId": "uuid or null" }
```
- `bankAccountId` must be an ACTIVE account of the user, else 400.
- `amount` must be > 0 with at most 2 decimals, else 400.
- `direction` is `debit` or `credit`.
- `categoryId` optional; if present it must be a visible, active category (system or own), else 400.
- `transactionDate` optional, defaults to now; must not be more than 1 day in the future.
- `currency_id` is copied from the bank account. `source = 'manual'`.
- After saving, run the matching function (A4.3). Return the transaction JSON (shape below).

**Transaction JSON shape** (used everywhere):
```json
{ "id", "bankAccountId", "currencyId", "categoryId", "amount", "direction", "merchantRaw",
  "transactionDate", "source", "isSelfTransfer", "reviewStatus", "reviewPartnerId",
  "selfTransferMatchedId", "refundMatchedId" }
```

**GET /api/v1/transactions** query params (all optional): `from`, `to` (dates `yyyy-MM-dd`, interpreted in the user's profile timezone, `to` inclusive), `categoryId`, `bankAccountId`, `direction`, `page` (default 0), `size` (default 50, max 200). Sorted by `transaction_date` descending. Response: `{ "items": [...], "page", "size", "total" }`.

**PATCH /api/v1/transactions/{id}** body may contain `categoryId`, `merchant`, `amount`, `direction`, `transactionDate`, `bankAccountId` (same validation as create). **If the transaction is linked (`is_self_transfer = true` OR `refund_matched_id` is not null) or has a review flag, only `categoryId` and `merchant` may be changed; any other field → 409 with message "Unlink or resolve the match first".** Matching is NOT re-run on PATCH.

**DELETE /api/v1/transactions/{id}** → 204. Do these steps in one transaction, in this order:
1. If `is_self_transfer` and `self_transfer_matched_id` is set: set the partner's `is_self_transfer=false` and its `self_transfer_matched_id=null`.
2. Set `review_status='none', review_partner_id=null` on every row whose `review_partner_id` = this id.
3. Delete the row (database `ON DELETE SET NULL` clears other references).

**POST /api/v1/transactions/{id}/unlink** → 204. If `is_self_transfer`: clear `is_self_transfer` and `self_transfer_matched_id` on both rows. If `refund_matched_id` is set: clear it on both rows. If neither → 409 "Nothing to unlink".

### A4.3 Matching (runs once, right after a transaction is created, from manual entry OR SMS)
Read thresholds from `ConfigService`. Run these checks IN ORDER and **stop at the first one that matches** (a row gets only one flag). Never auto-link; matching only sets `review_status` and `review_partner_id`.

**Check 1: possible duplicate.** Find ONE other row (the closest in time) with: same `user_id`, same `bank_account_id`, same `amount`, same `direction`, `id <> new.id`, `review_status = 'none'`, and `abs(extract(epoch from (transaction_date - :newDate))) <= dedup_match_window_hours * 3600`. If found: set BOTH rows to `review_status='possible_duplicate'` with `review_partner_id` pointing at each other.

**Check 2: possible self-transfer.** Find ONE row (closest in time) with: same user, **different** `bank_account_id`, same `currency_id`, same `amount`, OPPOSITE `direction`, `is_self_transfer = false`, `review_status = 'none'`, and time difference <= `self_transfer_match_window_hours * 3600`. If found: set BOTH to `possible_self_transfer` with partners pointing at each other.

**Check 3: possible refund (only if the new row is a credit).** Find ONE debit row with: same user, same `currency_id`, `refund_matched_id IS NULL`, `is_self_transfer = false`, `amount >= new.amount`, `transaction_date <= new.transaction_date` and `transaction_date >= new.transaction_date - refund_match_window_days days`, and `similarity(merchant_normalized, :newMerchantNormalized) >= refund_match_similarity_threshold`. Order by similarity descending, take 1. Skip this check if the new row's `merchant_normalized` is null. If found: set ONLY the new (credit) row to `possible_refund_match` with `review_partner_id` = the debit's id. The debit row is not flagged.

### A4.4 Review queue endpoints

**GET /api/v1/transactions/review-queue** →
```json
{ "duplicates":      [ { "transaction": {...}, "partner": {...} } ],
  "selfTransfers":   [ { "transaction": {...}, "partner": {...} } ],
  "refundMatches":   [ { "transaction": {...}, "partner": {...} } ],
  "unparsedSms":     [ { "id", "rawText", "sender", "receivedAt" } ] }
```
For duplicates and selfTransfers both rows are flagged, so show each pair ONCE: include only the row whose `id` (as text) is smaller than its partner's id. For refundMatches the flagged row is the credit and the partner is the debit. `unparsedSms` = rows with `status='pending'`.

**POST /api/v1/transactions/{id}/review/confirm** (the transaction must have a `review_status` other than `none`, else 409):
- `possible_duplicate`: DELETE this transaction using the delete steps in A4.2 (the partner stays, its flag is cleared by step 2).
- `possible_self_transfer`: on both rows set `is_self_transfer=true`, `self_transfer_matched_id` = the other row, `review_status='none'`, `review_partner_id=null`. If a row's `category_id` is null, set it to the system category named `Transfer`.
- `possible_refund_match`: set `refund_matched_id` on both rows (credit → debit, debit → credit), and clear `review_status`/`review_partner_id` on the credit.
Return 204.

**POST /api/v1/transactions/{id}/review/dismiss**: set `review_status='none', review_partner_id=null` on this row AND on its partner if the partner points back at this row. Return 204.

## A5. Budgets

Endpoints (auth required, own rows only, others → 404):

- `GET /api/v1/budgets` → active budgets, each WITH its current status (below). Shape:
```json
{ "id", "categoryId", "currencyId", "amount", "period",
  "spent", "remaining", "percentUsed", "overBudget", "periodStart", "periodEnd" }
```
- `POST /api/v1/budgets` body `{ "categoryId": uuid|null, "currencyId", "amount" }`. `currencyId` must exist. If `categoryId` is present it must be a visible `expense` category. `amount > 0`. If an active budget with the same (category, currency) exists → 409 `CONFLICT`.
- `PATCH /api/v1/budgets/{id}` body `{ "amount" }`.
- `DELETE /api/v1/budgets/{id}` → soft delete (`is_active=false`), 204.

### Status calculation (backend only)
Month window uses the user's `user_profile.timezone`: `periodStart` = first day of the current month at 00:00 in that zone, `periodEnd` = first day of the next month at 00:00 in that zone (convert both to `Instant` for the queries).

**Overall budget (`category_id` is null):**
```sql
SELECT COALESCE(SUM(CASE WHEN t.direction = 'debit' THEN t.amount ELSE -t.amount END), 0)
FROM bank_transactions t
WHERE t.user_id = :userId AND t.currency_id = :currencyId
  AND t.is_self_transfer = false
  AND t.transaction_date >= :start AND t.transaction_date < :end
  AND (t.direction = 'debit' OR t.refund_matched_id IS NOT NULL);
```

**Category budget:**
```sql
SELECT COALESCE(SUM(CASE WHEN t.direction = 'debit' THEN t.amount ELSE -t.amount END), 0)
FROM bank_transactions t
LEFT JOIN bank_transactions o ON o.id = t.refund_matched_id
WHERE t.user_id = :userId AND t.currency_id = :currencyId
  AND t.is_self_transfer = false
  AND t.transaction_date >= :start AND t.transaction_date < :end
  AND ( (t.direction = 'debit' AND t.category_id = :categoryId)
     OR (t.direction = 'credit' AND o.category_id = :categoryId) );
```

(Meaning: spending counts debits; a refund that the user confirmed reduces spending; self-transfers never count.)

`spent` = the sum (never below 0: use `max(sum, 0)`), `remaining` = `amount - spent`, `percentUsed` = `spent / amount * 100` rounded to 1 decimal (HALF_UP), `overBudget` = `spent > amount`.

## A6. SMS ingestion

**POST /api/v1/sms/ingest** (auth required). Body:
```json
{ "messages": [ { "sender": "VM-XXXXBK", "rawText": "...", "receivedAt": "2026-10-20T09:15:30.123Z" } ] }
```
Maximum 200 messages per request (more → 400). Response: `{ "received": n, "created": n, "queued": n, "ignored": n, "duplicates": n }`.

For EACH message, in this order:
1. **Fingerprint** = lowercase hex SHA-256 of `sender + "|" + receivedAtEpochMillis + "|" + rawText`. If a row with this fingerprint already exists for this user in `bank_transactions` OR in `unparsed_sms_queue` → count as `duplicates`, go to the next message.
2. Find a parser in the parser registry whose `supportsSender(sender)` is true. None → count as `ignored` (do NOT store), next message.
3. Call `parser.parse(rawText, receivedAt)`. The result is one of:
   - `NOT_A_TRANSACTION` → `ignored`, do not store.
   - `FAILED` → insert into `unparsed_sms_queue` (status `pending`, with the fingerprint) → `queued`.
   - `PARSED` (amount, direction, merchantRaw, accountLast4, transactionDate) → continue.
4. Find the user's ACTIVE bank accounts whose bank `short_code` equals `parser.bankShortCode()` AND whose `account_last4` equals the parsed `accountLast4`. **Exactly 1** → create the transaction (`source='sms'`, currency from the account, store the fingerprint, run matching A4.3) → `created`. **0, more than 1, or parsed last4 is null** → insert into `unparsed_sms_queue` → `queued`.

Process each message in its own try/catch so one bad message cannot fail the whole batch. On an unexpected exception: log a warning WITHOUT the SMS text and count the message as `ignored`.

**POST /api/v1/sms/unparsed/{id}/resolve** body `{ "bankAccountId", "amount", "direction", "transactionDate", "merchant"?, "categoryId"? }`. The queue item must be `pending` and belong to the user, else 404/409. Create the transaction with the same service and validation as `POST /transactions` but with `source='sms'`, run matching, set the queue item `status='resolved'` and `resolved_transaction_id`. Return the transaction JSON.

**POST /api/v1/sms/unparsed/{id}/discard** → status `discarded`, 204.

## A7. The bank parser (ONLY if section 1 samples are filled in)

Create the interface and registry:

```java
public interface BankSmsParser {
    String bankShortCode();                      // must equal banks.short_code
    boolean supportsSender(String sender);       // e.g. sender.toUpperCase().contains("XXXXBK")
    ParseOutcome parse(String text, java.time.OffsetDateTime receivedAt);
}

public class ParseOutcome {
    public enum Status { PARSED, NOT_A_TRANSACTION, FAILED }
    public final Status status;
    public final java.math.BigDecimal amount;   // only for PARSED
    public final String direction;              // "debit" or "credit"
    public final String merchantRaw;            // may be null
    public final String accountLast4;           // may be null
    public final java.time.Instant transactionDate;
    // add private constructor + static factories: parsed(...), notATransaction(), failed()
}
```

Register every `BankSmsParser` Spring `@Component` in a `ParserRegistry` (inject `List<BankSmsParser>`). Even when no parser exists yet, the registry must exist and be empty.

Write ONE parser class for the bank in the samples. Rules:
1. `NOT_A_TRANSACTION` unless the text contains one of the words `debited`, `credited`, `spent`, `withdrawn`, `received` (case-insensitive) AND an amount pattern. OTP and promo texts must give `NOT_A_TRANSACTION`.
2. If it looks like a transaction but any required field (amount, direction) cannot be extracted → `FAILED`.
3. Use regular expressions written FROM THE SAMPLES. Extract: amount (strip commas, handle `Rs.`, `Rs`, `INR`, `₹`), direction, last 4 digits of the account (may be null if not present), merchant text (may be null), date (if the SMS has a date, parse it in timezone `Asia/Kolkata`; otherwise use `receivedAt`).
4. Do not write regexes for formats you were not shown.

Add a unit test class that feeds each sample from section 1 and asserts the exact expected fields, plus one test for the non-transaction sample returning `NOT_A_TRANSACTION`.

## A8. Backend tests (Testcontainers Postgres, as in Phase 1)

All must pass:

1. `normalizeMerchant("SWIGGY*ORDER #123")` = `"swiggy order 123"`; null/blank → null.
2. Create a manual transaction: currency equals the account's currency; amount 0 or negative → 400; account of another user → 400/404.
3. Two transactions, same account, amount, direction, 1 hour apart → BOTH `possible_duplicate`, each pointing at the other.
4. Same two transactions but 3 days apart (default window 48h) → no flags.
5. Debit on account A + credit on account B, same amount, within window → both `possible_self_transfer`; confirm → both `is_self_transfer=true`, linked both ways, category `Transfer` set.
6. A credit whose merchant is similar to an earlier debit (e.g. "swiggy order 1" vs "swiggy refund") within 90 days → credit flagged `possible_refund_match`, the debit NOT flagged; confirm links both; dismiss in another case clears the flag without linking.
7. Confirming a `possible_duplicate` deletes that transaction and clears the partner's flag.
8. Deleting a linked self-transfer resets the partner's `is_self_transfer` to false.
9. Linked transaction: PATCH of `amount` → 409; PATCH of `categoryId` → 200.
10. Budget status: category budget counts only that category's debits in the current month; a confirmed refund reduces it; a self-transfer does not count; a transaction last month does not count; `overBudget` is true when spent > amount.
11. Budget duplicate (same user, category, currency, active) → 409.
12. SMS ingest (use a fake test parser registered only in tests): a known sender with a transaction text for an account with a matching last4 → `created`; same message sent again → `duplicates`; unknown sender → `ignored` and nothing stored; known sender OTP text → `ignored`; transaction-like text that cannot be parsed → `queued`; text whose last4 matches two of the user's accounts → `queued`.
13. Resolve a queued SMS creates a transaction and marks it `resolved`; discard marks it `discarded`.
14. Unlink clears both sides; unlink on a plain transaction → 409.
15. Categories: duplicate name (case-insensitive) with a system category → 409; deleting a category used by a transaction keeps the transaction.
16. Another user can never read or modify the first user's transactions, budgets, categories or queue items (404).

Check: `mvn test` passes.

---

# PART B — FLUTTER APP

## B0. Look at the existing Flutter code first (see section 3). Reuse `ApiClient`.

## B1. Packages
Add: `intl` (date/number formatting), `flutter_sms_inbox` (reads the SMS inbox, Android), `permission_handler` (SMS permission). Do NOT touch the existing `video_player`, `shared_preferences`, `provider` setup.

Android: add `<uses-permission android:name="android.permission.READ_SMS"/>` to `android/app/src/main/AndroidManifest.xml` (NOT `RECEIVE_SMS`; there is no live listener). If `flutter_sms_inbox` or `permission_handler` fails to resolve or build, stop and report; do not silently swap packages.

## B2. Folder structure (add)

```
lib/
  expenses/   models.dart, expense_repository.dart, expenses_tab.dart,
              transaction_form_screen.dart, review_queue_screen.dart
  categories/ categories_screen.dart
  budgets/    budgets_screen.dart
  sms/        raw_sms.dart, sms_reader.dart, sms_reader_stub.dart, sms_reader_io.dart, sms_sync_service.dart
```

## B3. Models and repository
`models.dart`: plain Dart classes with `fromJson` for: `Category`, `Transaction`, `TransactionPage`, `Budget`, `ReviewQueue` (with its pair/unparsed-SMS items). Money fields are read as `num` and shown with `toStringAsFixed(2)`. Do NOT add up amounts in Dart.

`expense_repository.dart`: one class that wraps `ApiClient` with methods for every endpoint in Part A (categories, transactions incl. review confirm/dismiss/unlink, budgets, sms ingest/resolve/discard).

## B4. Expenses tab (replaces the "Coming soon" body of the Expenses tab only)
- Top: a filter row: date range picker, category dropdown, account dropdown, direction chips (All / Debit / Credit). Changing a filter reloads the list.
- List of transactions (paginated: load the next page when scrolling near the end). Each row: merchant (or "No merchant"), category name (or "Uncategorized"), date (local time via `intl`), amount with currency symbol; debits in red with a minus sign, credits in green with a plus sign. A small badge on rows where `reviewStatus != 'none'` or `isSelfTransfer`.
- Floating "+" button → Transaction form (B5).
- App bar actions: Review Queue (with a count badge from the review-queue call), Categories, Budgets, and (Android only) "Sync SMS".
- Tapping a row opens the form in edit mode, with a Delete action (confirm dialog) and, for linked transactions, an "Unlink" action.
- Empty state text when there are no transactions.

## B5. Transaction form screen
Fields: account dropdown (user's active bank accounts, from Phase 1), direction toggle (Debit / Credit), amount (decimal keyboard, validate > 0 and max 2 decimals), merchant (optional), date and time picker (default now), category dropdown (optional). Save → POST (or PATCH in edit mode). Show the API error message under the form. When editing a linked/flagged transaction, disable everything except merchant and category and show a note "Unlink or resolve the match first".

## B6. Review queue screen
Four sections with headers (hide empty ones; show "Nothing to review" if all empty):
1. **Possible duplicates**: each item shows the two transactions side by side. Buttons: "Delete this one" (confirm dialog, then review/confirm) and "Keep both" (review/dismiss).
2. **Possible self-transfers**: two transactions shown. Buttons: "Yes, same money moved" (confirm) and "No" (dismiss).
3. **Possible refunds**: credit and the matched purchase shown. Buttons: "Yes, it's a refund" (confirm) and "No" (dismiss).
4. **SMS to review**: raw text, sender, date. Buttons: "Add transaction" (opens the transaction form pre-filled with the SMS date, saves through the resolve endpoint) and "Discard".
After any action, reload the queue.

## B7. Categories and Budgets screens
- **Categories**: list; system ones marked and not editable; "+" to add (name, type); edit name; delete (confirm).
- **Budgets**: list showing, for each budget, a label (category name or "Overall"), a progress bar from `percentUsed` (cap the bar at 100% visually; if `overBudget` use a red bar), the text `spent / amount` and `remaining`. "+" to add (category dropdown with an "Overall" option, currency from the currencies list, amount); edit amount; delete (confirm). Show the API error (e.g. duplicate budget).

## B8. SMS sync (Android only) — web must compile without it

Use a conditional export so the web build never sees the SMS plugin code.

`lib/sms/raw_sms.dart`:
```dart
class RawSms {
  final String sender;
  final String body;
  final DateTime date;
  RawSms(this.sender, this.body, this.date);
}
```

`lib/sms/sms_reader.dart` (this one line only):
```dart
export 'sms_reader_stub.dart' if (dart.library.io) 'sms_reader_io.dart';
```

Both `sms_reader_stub.dart` and `sms_reader_io.dart` must define the same two top-level functions (and import `raw_sms.dart`):
```dart
Future<bool> ensureSmsPermission();
Future<List<RawSms>> readInboxSince(DateTime since);
```
- `sms_reader_stub.dart`: returns `false` and `[]`. No plugin imports.
- `sms_reader_io.dart`: uses `permission_handler` (`Permission.sms.request()`) and `flutter_sms_inbox` (`SmsQuery().querySms(kinds: [SmsQueryKind.inbox])`), keeps only messages with `date` after `since`, and maps them to `RawSms`.

`sms_sync_service.dart`, method `Future<SyncResult?> sync()`:
1. If `!PlatformCapabilities.supportsSms` → return null.
2. If `!await ensureSmsPermission()` → return null (the UI shows "SMS permission needed").
3. `since` = value of `shared_preferences` key `sms_last_sync_ms` as a date; if missing, use now minus 30 days.
4. `readInboxSince(since)`, then keep ONLY messages whose sender contains at least one letter (regex `[A-Za-z]`). This drops personal phone-number messages so they never leave the phone.
5. Send in chunks of at most 100 to `POST /sms/ingest` with `receivedAt` as UTC ISO-8601 including milliseconds.
6. After ALL chunks succeed, store the newest message date (in milliseconds) as `sms_last_sync_ms`. If a chunk fails, store nothing (re-sending is safe because the backend ignores duplicates).
7. Return the summed counts; the UI shows a SnackBar like "Imported 3, 1 to review".

Call `sync()` automatically once when the Expenses tab first opens (Android only) and when the user taps "Sync SMS". The "Sync SMS" button is not shown when `!PlatformCapabilities.supportsSms`.

## B9. Flutter checks
- `flutter analyze` has no errors.
- `flutter build web` succeeds (this proves no SMS plugin leaked into web).
- On Android: add manual transactions; the list, filters, review queue, categories and budgets all work; "Sync SMS" asks for permission and runs.
- On web: everything works except there is no "Sync SMS" button.

---

# PART C — DEPLOY

Push the code. Render rebuilds automatically; Flyway applies `V2` on boot against Neon. Rebuild and redeploy the web build (`flutter build web --dart-define=API_BASE_URL=...` then `firebase deploy --only hosting`) and rebuild the APK. No new services, no new cost.

---

# 8. DEFINITION OF DONE

- [ ] `V2__phase2_expense_core.sql` applied cleanly on top of Phase 1; V1 untouched
- [ ] Categories: defaults visible, own CRUD, duplicate names rejected
- [ ] Manual transactions: create / list with filters and pagination / edit / delete / unlink working on Android AND web
- [ ] Duplicate, self-transfer and refund flags fire correctly and NEVER auto-link; confirm and dismiss behave exactly as in A4.4
- [ ] Budgets: create / edit / delete; status computed on the backend in the user's timezone; self-transfers excluded; confirmed refunds reduce spending
- [ ] SMS: ingest endpoint works; unknown senders and non-transaction SMS are ignored and not stored; unparseable or ambiguous bank SMS lands in the review queue; re-sending the same SMS creates no duplicates
- [ ] Bank parser written from real samples and unit tested (or clearly reported as skipped because samples were missing)
- [ ] Android: "Sync SMS" reads the inbox, sends only alphanumeric-sender messages, and shows the result
- [ ] `flutter build web` succeeds with no SMS code compiled in
- [ ] `mvn test` passes, `flutter analyze` is clean
- [ ] Deployed on the existing Render + Neon + Firebase Hosting setup. Total cost: $0

# 9. OUT OF SCOPE (do not build)

Bank statement upload and OCR (Phase 3). Fitness tables and screens (Phase 4). Anything Gemini/AI, mood log (Phase 5). Notifications and release polish (Phase 6). A live SMS listener or background sync. Automatic categorization. Charts and trend screens. Recurring transactions. CSV export. Budget periods other than monthly, rollover budgets. A local SQLite cache. Multi-currency conversion.

When every box in section 8 is checked, Phase 2 is done and Phase 3 can start.
