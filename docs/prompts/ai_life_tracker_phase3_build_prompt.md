# AI Life Tracker — Phase 3 Build Prompt (step by step)
### Bank Statement Import: PDF text + OCR (Tesseract), Review Screen, Safe Commit

You are a coding agent. Phases 1 and 2 are built and working (auth, bank accounts, transactions, categories, review queue, budgets, SMS import, Flutter app for Android + Web). Now build Phase 3 exactly as written. Follow the steps IN ORDER. Do not skip a step. Do not add features that are not listed. After each step, run the check written under it. If a check fails, fix it before moving on. If you cannot fix it, stop and report the exact error.

---

## 0. RULES (read first)

1. Do not invent anything not in this file. If something is unclear, stop and ask.
2. Everything must cost $0. No paid OCR services, no cloud OCR, no billing accounts. OCR is the free open-source `tesseract` program running inside our own Docker image.
3. Do not build anything from "OUT OF SCOPE" (section 9).
4. Do NOT change Phase 1 or Phase 2 behavior. Only add. Never edit `V1__...` or `V2__...` migrations. All new SQL goes into `V3__phase3_statement_import.sql`.
5. **Never trust extracted data.** Anything the system is unsure about is highlighted for the human to check. Imported rows are NEVER saved as transactions until the human presses Commit.
6. **The backend calculates every number.** The Flutter app only displays numbers and counts it receives.
7. **Privacy:** never log file contents, extracted text, amounts, passwords or tokens. The uploaded file is deleted from disk as soon as processing ends. The statement password is used only in memory and is never stored or logged. No AI/Gemini in this phase.
8. No Lombok. Keep code simple and readable.
9. `kIsWeb` may only be used inside `PlatformCapabilities`.

---

## 1. FILL-IN BY THE HUMAN (the agent must not guess these)

```
STATEMENT_SAMPLES   (from the bank seeded in Phase 1; numbers may be changed, keep the exact FORMAT)
  bank short_code (same as in the banks table): SAMPLE
  statement file type from this bank: <text PDF from net banking | scanned/photo>
  column order: <fill in, e.g. Date | Narration | Ref No | Withdrawal | Deposit | Balance>
  date format used in rows: <fill in, e.g. dd/MM/yy>
  opening balance line (if the statement has one): <paste exact line>
  closing balance line (if the statement has one): <paste exact line>
  15 consecutive transaction lines copied from the PDF (at least 2 debits, 2 credits,
  and 1 transaction whose narration continues on a second line):
    <paste lines here>
  2 lines of page header/footer text that appear between pages: <paste lines>
```

**If any of this is still a `<...>` placeholder, build everything EXCEPT the concrete bank parser (step A8), and report which samples are missing. Do NOT write a parser from imagination.** The rest of the pipeline must work with an empty parser registry (the import then ends as `failed` with `NO_PARSER`).

---

## 2. WHAT PHASE 3 BUILDS

The user picks one of their bank accounts and uploads a statement file (PDF, PNG or JPG, max 5 MB). The backend reads it (PDF text if possible, OCR if not), turns it into rows with a per-bank parser, checks the rows against the printed balances, and shows the rows on a review screen. The user fixes or unchecks doubtful rows, then presses Commit. Only then are real transactions created.

### Decisions already made (do not change)

- The bank and currency come from the chosen bank account. The user never types them.
- NO generic parser. Only the tested per-bank parser reads statements. If the bank has no parser: import fails with `NO_PARSER` ("Statements from this bank are not supported yet").
- PDF with real text: read the text directly (fast, 100% confidence). Only if the PDF has almost no text, or the file is an image, use OCR.
- Processing runs in the background (OCR is slow on the free server). The app polls for the result.
- The uploaded file is never kept. Only the parsed rows are kept in a staging table until commit/discard/expiry. After commit the staging rows are deleted.
- A statement row that looks like a transaction already in the app (same account, amount, direction, date within 1 day) is UNCHECKED by default and shown as "already in your list". The user can still check it.
- Statement dates have no time. On commit, the time is set to 12:00 in the user's profile timezone.

---

## 3. FIRST: LOOK AT THE EXISTING CODE (step A0 / B0)

Before writing anything, list the existing backend packages and the Flutter `lib/` tree. Find and reuse: `ConfigService`, the error handler and JSON shape `{ "error", "message" }`, `normalizeMerchant(...)` from Phase 2, the Phase 2 transaction creation + matching service (the one that runs duplicate / self-transfer / refund checks after a transaction is created), `ApiClient`, `PlatformCapabilities`, `ExpenseRepository`. Do not rename or move them.

---

# PART A — BACKEND

New Java package: `com.lifetracker.statement`.

## A1. Migration `V3__phase3_statement_import.sql`

Create `src/main/resources/db/migration/V3__phase3_statement_import.sql` with exactly this:

```sql
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
```

Do not create any other table. Check: the app starts, Flyway applies V3 on a dev Neon database.

## A2. Configuration and Docker

`application.yml` additions (merge into the existing file):

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB
app:
  statement:
    tmp-dir: ${java.io.tmpdir}
    tesseract-command: tesseract
    ocr-page-timeout-seconds: 120
    job-deadline-minutes: 6
```

Add the Maven dependency `org.apache.pdfbox:pdfbox` version **3.0.x** (use the 3.x API: `Loader.loadPDF(...)`, `PDFTextStripper`, `PDFRenderer`).

Edit `backend/Dockerfile`: in the SECOND (runtime) stage, install Tesseract and lower the memory setting. The runtime stage becomes:

```dockerfile
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx256m"
ENV OMP_THREAD_LIMIT=1
CMD ["java", "-jar", "app.jar"]
```

(The free Render server has 512 MB RAM in total; Tesseract needs some of it, so the Java heap is reduced from 350m to 256m.)

Map `MaxUploadSizeExceededException` in the existing error handler to HTTP 400 `{ "error": "VALIDATION_ERROR", "message": "File is too large (max 5 MB)" }`.

Add `@EnableAsync` and an executor bean named `statementExecutor`: `ThreadPoolTaskExecutor` with core pool 1, max pool 1, queue capacity 5.

## A3. Upload endpoint

**POST /api/v1/statement-imports** — `multipart/form-data`, auth required. Parts: `file`, `bankAccountId` (text), `password` (text, optional).

Validation, in this order:
1. `bankAccountId` must be an ACTIVE account of the logged-in user, else 400.
2. File must be non-empty and at most 5 MB, else 400.
3. Detect the real file type from the first bytes. Do NOT trust the file name or content-type. `%PDF` → `pdf`; `89 50 4E 47` → `png`; `FF D8 FF` → `jpg`. Anything else → 400 "Only PDF, PNG or JPG files are supported".
4. If this user already has an import with status `processing` → 409 `CONFLICT` "Another import is still running".
5. Cleanup (lazy): delete this user's imports (and their rows) whose status is not `committed` and whose `created_at` is older than `statement_import_expiry_days`.

Then: create the `statement_imports` row (`status='processing'`). Save the file to a temp file inside `app.statement.tmp-dir`, named `UUID + "." + detectedType` (NEVER use the uploaded file name). Start the background job (A5), passing the temp file path and the password (in memory only). Return **202** with `{ "id": "...", "status": "processing" }`.

## A4. Read / edit / commit / discard endpoints (all auth required, only the user's own imports; others → 404)

**Import summary JSON** (used by the first two endpoints):
```json
{ "id", "bankAccountId", "fileType", "extractionMethod", "status", "errorCode",
  "pageCount", "rowCount", "flaggedCount", "openingBalance", "closingBalance", "gateStatus",
  "selectedCount", "blockingCount", "committedCount", "createdAt" }
```
- `selectedCount` = rows with `selected = true`.
- `blockingCount` = rows with `selected = true AND (needs_check = true OR txn_date IS NULL OR amount IS NULL OR direction IS NULL)`.
- Both are computed by the backend with a SQL count each time.

**GET /api/v1/statement-imports** → the user's 20 most recent imports (summary JSON), newest first.

**GET /api/v1/statement-imports/{id}** → summary JSON. Lazy stale check: if `status='processing'` and `updated_at` is older than 10 minutes, set `status='failed', error_code='INTERNAL_ERROR'` first, then return it.

**GET /api/v1/statement-imports/{id}/rows** → array ordered by `row_index`:
```json
{ "id", "rowIndex", "txnDate", "description", "amount", "direction", "balanceAfter",
  "confidence", "needsCheck", "checkReason", "selected",
  "existingTransaction": { "id", "amount", "direction", "transactionDate", "merchantRaw" } or null }
```

**PATCH /api/v1/statement-imports/{id}/rows/{rowId}** — only when the import `status='ready'`, else 409. Body may contain `txnDate` (`yyyy-MM-dd`), `description`, `amount`, `direction`, `selected`.
- Validate: `amount` > 0 with at most 2 decimals; `direction` is `debit` or `credit`; description max 255.
- If ANY of `txnDate, description, amount, direction` is changed: set `needs_check=false` and `check_reason=null` (the human vouches for the row), then recompute `existing_transaction_id` (A6 step 4, single row, no `selected` change).
- Changing only `selected` does NOT clear `needs_check`.
- Return the row JSON.

**DELETE /api/v1/statement-imports/{id}** → delete the import (rows cascade). 204. Transactions already committed are not touched.

**POST /api/v1/statement-imports/{id}/commit** — returns `{ "created": n, "skipped": n }`. Steps, all in ONE database transaction:
1. Run `UPDATE statement_imports SET status='committed', updated_at=now() WHERE id=:id AND user_id=:u AND status='ready'`. If 0 rows updated → 409 "Import is not ready or already committed". (This makes double-commit impossible.)
2. Load the rows with `selected=true`, ordered by `row_index`. If ANY of them has `needs_check=true` OR a null `txn_date`/`amount`/`direction` → roll back and return 409 `{ "error": "CONFLICT", "message": "Fix or uncheck N highlighted rows first" }` (N = number of such rows).
3. For each selected row, create a transaction exactly like manual entry in Phase 2, but: `source='statement'`, `bank_account_id` = the import's account, `currency_id` from that account, `merchant_raw` = description (max 255), `merchant_normalized` = `normalizeMerchant(description)`, `transaction_date` = the row's `txn_date` at 12:00 in the user's profile timezone converted to `Instant`, `category_id` = null.
4. After EACH created transaction, call the existing Phase 2 matching function (duplicate / self-transfer / refund flags). It only flags; it never links.
5. `created` = number of transactions created, `skipped` = rows with `selected=false`. Delete all rows of this import (`statement_import_rows`). Set `committed_count = created`.

## A5. Background job

`StatementProcessingService.process(importId, tempFilePath, password)` annotated `@Async("statementExecutor")`. Wrap EVERYTHING in try/catch/finally:
- `finally`: delete the temp file (and any page images). Always.
- Any unexpected exception: set `status='failed', error_code='INTERNAL_ERROR'` (log the exception class and the import id only, never content).
- Overall deadline: `job-deadline-minutes` from the start. Check it before every page; if exceeded → `failed`, `OCR_TIMEOUT`.

Pipeline steps (each failure sets `status='failed'` with the given `error_code` and stops):
1. Find the parser: look up the account's bank `short_code` in the `StatementParserRegistry`. None → `NO_PARSER`.
2. Extract lines (A6a). Possible errors: `PASSWORD_REQUIRED`, `WRONG_PASSWORD`, `UNREADABLE_FILE`, `TOO_MANY_PAGES`, `OCR_UNAVAILABLE`, `OCR_TIMEOUT`.
3. `parser.parse(lines)` → `ParsedStatement`. If it has 0 rows → `NO_ROWS_FOUND`.
4. Build the staging rows, apply flags and the balance check (A6b), look for existing transactions (A6c).
5. Save rows. Update the import: `status='ready'`, `extraction_method`, `page_count`, `row_count`, `flagged_count` (rows with `needs_check`), `opening_balance`, `closing_balance`, `gate_status`, `updated_at=now()`.

## A6. The processing pieces

### A6a. Text extraction (`TextExtractor`)

Output type: a list of `ExtractedLine { String text; double confidence; }` plus `method` (`pdf_text` or `ocr`) and `pageCount`.

**PDF:**
1. `Loader.loadPDF(file, password)`. If it throws `InvalidPasswordException`: when the password was null/empty → `PASSWORD_REQUIRED`, otherwise → `WRONG_PASSWORD`. Any other load error → `UNREADABLE_FILE`.
2. Page count > `statement_max_pages` (from `ConfigService`) → `TOO_MANY_PAGES`.
3. Extract text with `PDFTextStripper` using `setSortByPosition(true)`. Split by newline, trim each line, drop empty lines.
4. If the total number of non-whitespace characters divided by the page count is at least 100 → use this text: every line gets `confidence = 100`, method `pdf_text`.
5. Otherwise → OCR path (below), rendering the PDF pages to images first.

**Images (png/jpg):** go directly to the OCR path with the file itself (do not load the image in Java).

**OCR path** (`OcrService`): for PDFs, process ONE page at a time: render with `PDFRenderer.renderImageWithDPI(pageIndex, 200, ImageType.GRAY)`, write a temp PNG (UUID name) in the tmp dir, run Tesseract, delete the PNG, go to the next page. Run Tesseract with `ProcessBuilder` using a FIXED argument list (never put user-provided text into the command):

```
tesseract <path-to-image> stdout -l eng --psm 6 tsv
```

- Command name comes from `app.statement.tesseract-command`. Read stdout fully. Discard stderr.
- If the program cannot be started (IOException) → `OCR_UNAVAILABLE`.
- Wait at most `ocr-page-timeout-seconds`; on timeout `destroyForcibly()` and → `OCR_TIMEOUT`.
- Parse the TSV output: the columns are `level page_num block_num par_num line_num word_num left top width height conf text`. Keep only rows with `level == 5`, non-blank `text` and `conf >= 0`. Group words by `(page_num, block_num, par_num, line_num)` keeping the original order. Line text = words joined with one space. Line confidence = average `conf` of its words. Put this TSV-to-lines logic in its own pure function `parseTsv(String tsv)` so it can be unit tested without Tesseract.
- Method = `ocr`.

### A6b. Row building, flags and the balance check

The parser returns `ParsedRow` objects (A8). Convert each to a staging row, `row_index` = 0,1,2... in order. Then set flags with this priority (first rule that applies sets `check_reason`):
1. `parseError = true` → `needs_check=true`, reason `parse_error`.
2. Date, amount or direction is null → `needs_check=true`, reason `missing_fields`.
3. Balance check says mismatch (below) → `needs_check=true`, reason `balance_mismatch`.
4. `confidence < statement_ocr_min_confidence` → `needs_check=true`, reason `low_confidence`.

**Balance check** (a pure function with unit tests). `prev` starts as `openingBalance` (may be null). For each row in order:
```
if row.balanceAfter != null:
    if prev != null AND row.amount != null AND row.direction != null:
        expected = prev + (credit ? +amount : -amount)
        if abs(expected - row.balanceAfter) > 0.01  ->  this row is a balance mismatch
    prev = row.balanceAfter            // trust the printed balance and continue from it
else:
    if prev != null AND row.amount != null AND row.direction != null:
        prev = prev + (credit ? +amount : -amount)
    else:
        prev = null
```
**Closing check:** only if `openingBalance` AND `closingBalance` are not null AND every row has amount and direction: `expected = opening + sum(credits) - sum(debits)`. If `abs(expected - closingBalance) > 0.01` → closing mismatch.

**gate_status:**
- `failed` if any row is a balance mismatch OR the closing check mismatched.
- else `passed` if the opening+closing check ran OR at least one row had `balanceAfter` checked.
- else `not_checkable`.

### A6c. Matching rows to existing transactions

For each staging row (in `row_index` order) that has a date, amount and direction: query candidates (max 20) from `bank_transactions`:
```sql
SELECT id FROM bank_transactions
WHERE user_id = :userId AND bank_account_id = :accountId
  AND amount = :amount AND direction = :direction
  AND (transaction_date AT TIME ZONE :tz)::date BETWEEN :d - :tol AND :d + :tol
ORDER BY abs((transaction_date AT TIME ZONE :tz)::date - :d), transaction_date
LIMIT 20;
```
`:tz` is the user's profile timezone, `:tol` is `statement_dedup_date_tolerance_days`. In Java pick the FIRST candidate id that is not already in a `claimed` set for this import, add it to `claimed`, and store it as `existing_transaction_id`; set `selected=false` for that row. (One existing transaction can be matched by only one statement row, so two real identical purchases on one day are not wrongly merged.) Rows without a match: `selected=true`.

## A7. Parser interface and registry (no bank-specific code here)

```java
public interface BankStatementParser {
    String bankShortCode();                                   // must equal banks.short_code
    ParsedStatement parse(List<ExtractedLine> lines);
}

public class ParsedStatement {
    public BigDecimal openingBalance;   // may be null
    public BigDecimal closingBalance;   // may be null
    public List<ParsedRow> rows = new ArrayList<>();
}

public class ParsedRow {
    public java.time.LocalDate date;    // null if it could not be read
    public String description;          // may be null
    public BigDecimal amount;           // positive; null if it could not be read
    public String direction;            // "debit" | "credit" | null
    public BigDecimal balanceAfter;     // may be null
    public double confidence;           // lowest confidence among the lines used by this row
    public boolean parseError;          // true if the line looked like a row but could not be fully read
}
```

`StatementParserRegistry`: a Spring `@Component` that receives `List<BankStatementParser>` and offers `find(String bankShortCode)`. It must exist and be valid even when the list is empty.

## A8. The bank parser (ONLY if section 1 samples are filled in)

Write ONE parser class for the bank in the samples. Rules:
1. Write the regular expressions FROM THE SAMPLES. Do not support layouts you were not shown.
2. A line that STARTS like a transaction row (the date pattern from the samples) is a new row. Extract date, description, amount, direction (use the column order: which column holds the amount decides debit or credit), and balance.
3. A line that starts like a row but cannot be fully read → still create a `ParsedRow` with `parseError = true`, the fields that could be read, and the rest of the line as `description`.
4. A line that does NOT start like a row is either a continuation of the previous row's narration (append it with a space, keep within 255 characters) or page header/footer text (ignore it). Use the header/footer samples to decide.
5. Remove thousands separators from amounts. Amounts are always stored positive.
6. Read the opening and closing balance from the lines shown in the samples; if they are not present, leave them null.
7. `confidence` of a row = the lowest `confidence` of the lines it used.
8. Never "fix" suspicious digits (for example never change the letter O into zero). If a number does not match the pattern, the row becomes `parseError`.

Add a unit-test class that feeds the sample lines from section 1 (with confidence 100) and asserts the exact dates, descriptions, amounts, directions and balances of all rows, including the row with the wrapped narration, and that header/footer lines produce no rows.

## A9. Backend tests (Testcontainers Postgres, as before)

All must pass. In tests, register a FAKE `BankStatementParser` (test-only) that reads simple lines like `2026-10-01|Coffee Shop|250.00|debit|1000.00` so the pipeline can be tested without a real bank parser.

1. Upload a text file renamed `.pdf` → 400. Upload a file over 5 MB → 400. Another user's / inactive account → 400/404.
2. A second upload while one is `processing` → 409.
3. `parseTsv`: a canned TSV string (include a `-1` conf row and a blank-text row) gives the right lines and averaged confidences; the junk rows are ignored.
4. Balance check unit tests: all consistent → no flags and `passed`; one wrong amount → ONLY that row flagged `balance_mismatch` and the next row is not flagged (the printed balance re-syncs); closing balance mismatch with no row mismatch → `failed`; no balances at all → `not_checkable`.
5. End-to-end text PDF: generate a PDF in the test with PDFBox containing the fake-parser lines (enough text per page), upload, wait until `status='ready'`; `extraction_method='pdf_text'`, rows correct, confidence 100.
6. Password-protected PDF (create it with PDFBox `StandardProtectionPolicy`): no password → `failed` + `PASSWORD_REQUIRED`; wrong password → `WRONG_PASSWORD`; correct password → `ready`.
7. Bank with no registered parser → `failed` + `NO_PARSER`. A parser returning 0 rows → `NO_ROWS_FOUND`.
8. Existing-transaction matching: one existing transaction + two identical statement rows → only the FIRST row gets `existing_transaction_id` and `selected=false`; the second stays `selected=true`.
9. PATCH a flagged row's amount → `needs_check=false`; PATCH only `selected` → `needs_check` unchanged. PATCH on a non-`ready` import → 409.
10. Commit: blocked with 409 while a selected row has `needs_check` (and no transactions were created); after fixing/unchecking it succeeds; transactions have `source='statement'`, the account's currency, date = 12:00 in the user's timezone; a second commit → 409; staging rows are gone; `committed_count` is set.
11. Commit runs matching: a committed row with the same amount/direction/account as an existing SMS transaction within 48 hours gets flagged `possible_duplicate` (and is NOT silently dropped).
12. OCR integration test: draw a few lines of text into a PNG with Java `BufferedImage`/`Graphics2D`, run it through the real OCR path and assert the amount text is found. **Skip this test (JUnit `Assumptions.assumeTrue`) when the `tesseract` program is not installed.**
13. The temp file is gone after processing (success AND failure). Use a test temp dir via `app.statement.tmp-dir` and assert it is empty afterwards.
14. Lazy expiry: an old uncommitted import is deleted when the same user uploads a new file; a `committed` import is never deleted by expiry.
15. Stale `processing` import (updated 11 minutes ago) becomes `failed/INTERNAL_ERROR` on GET.
16. Regression: a user who has bank accounts, transactions AND statement imports can call `DELETE /api/v1/account` successfully (this proves the foreign key fix in V3).
17. Another user can never read, patch, commit or delete the first user's imports (404).

Check: `mvn test` passes. If Docker is not available for Testcontainers, still write the tests and say so in your report.

---

# PART B — FLUTTER APP

## B0. Look at the existing Flutter code first (see section 3).

## B1. Packages and `ApiClient` change
- Add package `file_picker` (works on Android and Web). Do not use any file path APIs; always use the file BYTES (`FilePicker.platform.pickFiles(type: FileType.custom, allowedExtensions: ['pdf','png','jpg','jpeg'], withData: true)`).
- The existing `ApiClient` only does JSON. Add ONE new method, `sendMultipart(...)`, with the SAME rules as normal requests: bearer token, 60-second timeout (use 120 seconds for this call), and on 401 refresh once (single-flight, as already implemented) then retry once. IMPORTANT: a multipart request can be sent only once, so the method must take a FUNCTION that builds a fresh `http.MultipartRequest` each time, and call that function again for the retry.

## B2. Add to `ExpenseRepository` (or a new `StatementRepository`)
Methods for: upload (multipart: field `file` with filename and bytes, text fields `bankAccountId` and optional `password`), `getImport(id)`, `listImports()`, `getRows(id)`, `patchRow(id, rowId, fields)`, `commit(id)`, `discard(id)`. Plus plain Dart models `StatementImport` (all summary fields) and `StatementRow` with `fromJson`. Do not add up money in Dart.

## B3. Screens (add under `lib/statements/`)

1. **Import screen** (`statement_import_screen.dart`): bank account dropdown (active accounts), a "Choose file" button showing the chosen file name and size, a password text field labelled "PDF password (leave empty if none)", and an "Upload" button. Client checks before upload: a file was chosen, size at most 5 MB, an account was chosen. Show API error messages. A short hint text: "For best results download the PDF statement from your bank's net banking. Photos and scans are slower and less accurate."
2. **Processing screen** (`statement_processing_screen.dart`): after upload, shows a spinner and "Reading your statement... this can take a minute". Polls `getImport` every 3 seconds. Stops after 10 minutes with a "Taking too long" message. When `status='ready'` → go to the Review screen. When `status='failed'` → show a message from the table below and a "Back" button.

   | errorCode | Message to show |
   |---|---|
   | PASSWORD_REQUIRED | This PDF needs a password. Go back and enter it. |
   | WRONG_PASSWORD | The password is wrong. Go back and try again. |
   | UNREADABLE_FILE | This file could not be read. |
   | TOO_MANY_PAGES | This statement has too many pages (max 10). Split it and import in parts. |
   | NO_PARSER | Statements from this bank are not supported yet. |
   | NO_ROWS_FOUND | No transactions were found in this file. |
   | OCR_UNAVAILABLE | Reading scanned files is not available right now. |
   | OCR_TIMEOUT | Reading this file took too long. Try a PDF from net banking. |
   | INTERNAL_ERROR (or anything else) | Something went wrong. Please try again. |

3. **Review screen** (`statement_review_screen.dart`):
   - Header: row count, flagged count, and the gate status. If `gateStatus == 'failed'` show a warning banner: "Totals do not add up. Check the highlighted rows." If `not_checkable`: "This statement could not be checked against balances. Review all rows carefully."
   - A scrollable list of rows. Each row: a checkbox (`selected`), date, description, amount (red with minus for debit, green with plus for credit). Rows with `needsCheck` have an orange background and a small text with the reason (`parse_error`: "Could not read this line", `missing_fields`: "Something is missing", `balance_mismatch`: "Does not match the balance", `low_confidence`: "Scan was unclear"). Rows with `existingTransaction` show a grey note "Looks like it is already in your list" with that transaction's merchant, amount and date, and are unchecked.
   - Tapping a row opens an edit dialog (date picker, description, amount, debit/credit toggle). Save → `patchRow`, then reload the summary and rows.
   - Toggling a checkbox → `patchRow` with `selected` only, then reload the summary.
   - Bottom bar: a "Commit" button labelled `Import {selectedCount} transactions`. It is DISABLED while `blockingCount > 0`, with the text under it "Fix or uncheck {blockingCount} highlighted rows". `selectedCount` and `blockingCount` come from the backend summary; do not count in Dart.
   - A "Discard" action in the app bar (confirm dialog → `discard` → back).
   - After a successful commit: show a SnackBar "Imported {created} transactions" and return to the Expenses tab, which must reload. Tell the user (in the SnackBar text) "Some may need review" only if `created > 0`; the normal review queue (Phase 2) shows any flagged duplicates.

4. **Entry points:** add an "Import statement" action to the Expenses tab app bar. Also add a small "Past imports" list on the Import screen (from `listImports`: status and date), tapping a `ready` one resumes the Review screen.

All screens work the same on Android and Web.

## B4. Flutter checks
- `flutter analyze` has no errors.
- `flutter build web` succeeds.
- Android and Web: pick a PDF, upload, see the processing screen, reach the review screen, edit a row, uncheck a row, commit; the transactions appear in the Expenses list.
- Uploading while another import is processing shows the API's error message.

---

# PART C — DEPLOY

Push the code. Render rebuilds the Docker image (it now installs Tesseract) and Flyway applies `V3` on boot. Redeploy the web build and rebuild the APK as before. No new services, no new cost.

**Real-world test (the human does this):** upload one real text-PDF statement from the seeded bank and compare the review screen with the PDF. Then upload a photo/scan once to see OCR behaviour. Report any rows that were read wrongly; these are fixed by improving the bank parser, never by making the app trust OCR more.

---

# 8. DEFINITION OF DONE

- [ ] `V3__phase3_statement_import.sql` applied; V1 and V2 untouched; the bank-account foreign key fix is in
- [ ] Upload works for PDF / PNG / JPG with real file-type checking, a 5 MB limit, and one running import per user
- [ ] Text PDFs are read directly; scanned files and images go through Tesseract; OCR confidence is stored per row
- [ ] Password-protected PDFs work with a typed password; the password is never stored or logged
- [ ] The uploaded file is deleted after processing (success and failure)
- [ ] The balance check flags the exact wrong rows and sets the gate status
- [ ] Rows that match existing transactions are unchecked by default (one-to-one)
- [ ] Commit is atomic, cannot run twice, is blocked while selected rows are flagged, and runs the Phase 2 matching
- [ ] Bank parser written from real samples and unit tested (or clearly reported as skipped because samples were missing)
- [ ] Flutter import → processing → review → commit works on Android AND web
- [ ] `mvn test` passes, `flutter analyze` is clean, `flutter build web` succeeds
- [ ] Deployed on the existing Render + Neon + Firebase Hosting setup. Total cost: $0

# 9. OUT OF SCOPE (do not build)

Fitness tables and screens (Phase 4). Anything Gemini/AI, mood log (Phase 5). Notifications and release polish (Phase 6). A generic or AI-based statement parser. Cloud OCR. Multi-file or multi-statement imports. Automatic categorization. Opening-balance tracking of accounts. Storing the original statement file. Languages other than English for OCR. Credit-card statements unless the human's samples are credit-card statements.

When every box in section 8 is checked, Phase 3 is done and Phase 4 can start.
