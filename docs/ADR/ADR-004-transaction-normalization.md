# ADR-004: Transaction Normalization, Classification, and Monetary Representation

**Date:** 2026-11-21

---

## 1. Status

**Proposed — Core Canonical Model Confirmed; IDR Exponent, 1.3/1.4 Classification Details, and G1 Scope Items Pending Confirmation**

- **Confirmed Baseline (PRD v1.4):** Integer minor-unit storage (`Long`, never `Double`/`Float`), `INFLOW`/`OUTFLOW` direction semantics, deterministic `ParseResult` contract (no uncalibrated confidence scores), `NeedsReview` modeled as `NotificationEvent` (not `Transaction`), `posted_at` timestamp fallback (never `received_at`), locale-aware parsing (`PAR-12`) that never guesses ambiguous numbers, and composite 3-tier deduplication (never amount+timestamp alone).
- **Pending Owner Confirmation Before M1:**
  1. **IDR Storage Exponent** (`2` vs `0`) — blocking `DB-002` / `PIPE-005` schema & fixture setup.
  2. Confirmation of PRD 1.3/1.4 accounting & edit proposals (`ADJUSTMENT`, `REFUND` netting, derived transfer fees, `CASH` unpaired classification, `original_json` snapshot vs revision history, void-not-delete).
- **Decided at Gate G1:**
  1. Multi-account per source & account-hint resolution (`ACC-05`, `PAR-02`, `REV-07`).
  2. Credit card signed-ledger scope (`ACC-10`).
  3. One-payment-announced-by-two-apps handling rule (PRD Section 11).

---

## 2. Relevant PRD Requirement References

- **PRD Section 4.1:** `ACC-04` (`initial_balance_at`), `ACC-05` (multi-account hints), `ACC-07` (reported balance checkpoints), `ACC-08` (balance adjustments), `ACC-10` (credit card ledger).
- **PRD Section 4.3:** `PAR-02` to `PAR-12` (extraction, validation, failed attempts, refunds, locale-aware parsing, `posted_at` fallback).
- **PRD Section 4.4:** `REV-01` to `REV-07` (Needs Review event model).
- **PRD Section 4.5:** `TX-03` (`original_json`), `TX-05` (provenance), `TX-08` (void/restore), `TX-09` (refund linking).
- **PRD Section 4.6:** 16-row Classification Table, single base currency rule, derived fee rows.
- **PRD Section 4.8:** `TRF-01`, `TRF-02`, `TRF-04` (transfer pairing and derived fees).
- **PRD Section 4.9:** `CAT-01` to `CAT-05` (rule precedence and user category protection).
- **PRD Section 6.3, 7.1, 8.1–8.3, 11, 19, Appendix A.**

---

## 3. Context and Problem

Different financial applications format amounts (`Rp50.000`, `Rp 1.250.000,50`, `IDR 50,000.00`), timestamps, transaction statuses, reversals, and transfer fees inconsistently. Without a strict normalization model and explicit accounting rules:
1. Floating-point arithmetic corrupts balances and aggregates.
2. Indonesian dot-thousands (`1.500`) vs English dot-decimals (`1.50`) can cause silent **1,000x misreads** (`PAR-12`).
3. Internal top-ups (bank → e-wallet), transfer fees, balance corrections, failed attempts, and refunds would inflate income/expense totals or double-deduct balances.
4. Incomplete notifications forced into a `transactions` table would corrupt balance calculations or violate NOT NULL invariants.

---

## 4. Decision

### A. Confirmed Baseline (PRD v1.4)

1. **Canonical Transaction Model & Integer Minor Units (PRD Section 7.1):**
   - Monetary values are stored as signed/non-negative `Long` integers in the currency's minor unit (`amount_minor`, `reported_balance_minor`, `initial_balance_minor`, `fee_minor`). Floating-point `Double`/`Float` is prohibited for money.
   - An explicit `CurrencySpec(code: String, minorUnitDigits: Int)` lookup handles currency exponents explicitly (e.g., `USD = 2`, `JPY = 0`, `IDR = [Owner Decision]`) rather than assuming every currency has 2 decimals.
   - Timestamps are persisted in UTC epoch milliseconds (`Long`) and rendered in the device's local timezone; notification text dates are parsed in the provider's verified timezone, falling back to `NotificationEvent.posted_at` — **never** `received_at` (`PAR-12`).
2. **Direction vs. Cash-Flow Semantics:**
   - `Direction` (`INFLOW`, `OUTFLOW`) describes account balance movement only, independent of whether an item is income or expense.
   - Parser contract `TransactionType` (`PURCHASE`, `TRANSFER`, `FEE`, `REFUND`, `CASH`, `OTHER`); Domain model adds `ADJUSTMENT` (created solely by `ACC-08`, never emitted by a parser).
3. **Deterministic Parser Contract & Needs Review Boundary (PRD Section 4.4, 6.3):**
   - Parsers emit deterministic `ParseResult.Success`, `ParseResult.NeedsReview(reason, partial)`, or `ParseResult.NotTransaction(reason)` — no numeric confidence scores in MVP.
   - `NeedsReview` items remain on `NotificationEvent` with `review_draft_json`; no `Transaction` row exists until parsing succeeds or the user explicitly confirms and saves the review item inside an atomic DB transaction.
4. **Locale-Aware Number Parsing & Ambiguity Guard (`PAR-12`):**
   - Parsers declare/select the locale matching the notification's language (`id-ID` vs `en-US` / `en-ID`) rather than reading `Locale.getDefault()` of the phone.
   - Any ambiguous separator pattern where provider fixtures have not established an unambiguous rule (e.g., `1.500` without currency decimals context across mixed-locale providers) returns `NeedsReview(AMBIGUOUS_FIELD)` instead of guessing.
5. **3-Tier Deduplication & Idempotency (PRD Section 8.1):**
   - Precedence order:
     1. `provider_reference` (scoped to `source_id` / `account_id`) when reliably present.
     2. `stable_event_key` derived from `package_name` + normalized notification identity (`notification_key`/`id`/`tag` per M3a evidence) + `posted_at` + SHA-256 content hash.
     3. Composite fingerprint (`account_id`, `amount_minor`, `currency`, `direction`, time bucket, normalized merchant/reference) inside a bounded matching window.
   - Deduplicating solely on `(amount, timestamp)` is **explicitly prohibited**.

---

### B. Owner Decisions & Proposed Rules

#### 1. IDR Storage Exponent (`[OWNER DECISION REQUIRED BEFORE M1]`)

Because PRD `PAR-12` explicitly mentions fractional IDR notation (`Rp 1.250.000,50`) while PRD Section 19 notes that most IDR notifications omit decimals, the storage exponent for `IDR` must be locked before creating the first `Transaction` table (`DB-002`):

| Option | Representation of `Rp 50.000` | Representation of `Rp 1.250.000,50` | Trade-Offs |
| :--- | :--- | :--- | :--- |
| **Option A (Recommended): Exponent `2` (ISO 4217 standard, 1 IDR = 100 sen)** | `5_000_000L` | `125_000_050L` | **Pros:** Lossless handling of notifications/bank statements with `,50` sen (e.g., interest/admin fees/micro-deductions); directly matches `java.util.Currency.getInstance("IDR").defaultFractionDigits == 2`. **Cons:** Stored integers are 100x larger (still easily fits `Long`, max ~92 quadrillion rupiah); UI formatter hides `,00` when remainder `% 100 == 0`. |
| **Option B: Exponent `0` (Whole Rupiah)** | `50_000L` | Cannot store losslessly (must round or route `,50` to Needs Review) | **Pros:** Human-readable numbers in raw SQLite inspection. **Cons:** Lossy if any provider reports sen (e.g., bank interest, tax, QRIS/transfer fees with decimals), violating `PAR-12`'s explicit `Rp 1.250.000,50` example. |

**Recommendation:** Adopt **Option A (`IDR` exponent = `2`)** so fractional amounts never cause precision loss or unnecessary `NeedsReview` routing, while formatting clean integer Rupiah (`Rp 50.000`) in the UI whenever `amountMinor % 100 == 0`.

#### 2. Proposed Accounting & Classification Rules (PRD 1.3 / 1.4 — Confirm or Change)

1. **Single 16-Row Classification Table (PRD Section 4.6):** Governs all Dashboard, Insights, and Account aggregate calculations consistently:
   - `OUTFLOW + PURCHASE` → Expense (its category).
   - `OUTFLOW + FEE` → Expense (Fees category).
   - `OUTFLOW + TRANSFER` (unpaired) → Expense; `INFLOW + TRANSFER` (unpaired) → Income.
   - `TRANSFER` (confirmed pair) → Excluded from Income/Expense; `fee_minor` (`outgoing - incoming >= 0`) reported as a **read-only derived row** in Fees under the outgoing leg's account and date (`TRF-04`, never a 3rd transaction row).
   - `CASH` paired with cash account → Excluded (internal transfer); unpaired `OUTFLOW + CASH` → Expense ("Cash withdrawal" category); unpaired `INFLOW + CASH` → Income.
   - `INFLOW + REFUND` → Netted against spending in linked original transaction's category (`TX-09`) or its own category if unlinked — never counted as Income. `OUTFLOW + REFUND` is never produced in MVP (parsers route outgoing reversals to `NeedsReview`).
   - `INFLOW + OTHER` → Income; `OUTFLOW + OTHER` → Expense.
   - `ADJUSTMENT` (`ACC-08`) → Excluded from Income/Expense/Cash-Flow totals; reported separately as **Unreconciled Difference** (`DASH-09`), while updating account estimated balance.
   - `VOIDED` (`TX-08`) → Excluded from totals, balances, and pairs. Captured transactions can only be voided (preserving `event_id` idempotency); manual transactions may be deleted.
   - **Non-base currency** → Stored and flagged; excluded from base-currency totals (no FX conversion in MVP).
2. **Failed Attempts (`PAR-09`):** Classified as `NotTransaction(FAILED_ATTEMPT)` with immediate payload purge. Never auto-voids previously confirmed transactions.
3. **Edit Provenance (`TX-03`):** Replace multi-row revision history with immutable `original_json` snapshot created at capture time, rendered alongside current values.
4. **Categorization Precedence (`CAT-02`, Section 4.9):** User-set category (`USER`) > most specific matching `TransactionRule` (`RULE`) > most recently created matching `RULE`. User-set categories are never overwritten by rules or re-parses.

#### 3. Gate G1 Scope Decisions (Deferred Until M3a Evidence)

- **Multi-Account Source Resolution (`ACC-05`, `PAR-02`, `REV-07`):** Keep `account_sources.account_hint` in schema from M1; confirm/drop UI & hint complexity at Gate G1 based on whether either initial provider serves multiple accounts on one app.
- **Credit Card Signed Ledger (`ACC-10`):** Keep `AccountType.CREDIT_CARD` enum; confirm/drop credit-card-specific UI & payment-transfer workflow at Gate G1 (`SHOULD` priority).
- **One Payment, Two Notifiers (Section 11):** Measure overlap during M3a; default MVP behavior creates separate transactions (user voids duplicate or links transfer) unless M3a justifies an automated suppression rule.

---

## 5. Alternatives Considered

| Alternative | Description | Reason Rejected |
| :--- | :--- | :--- |
| **`Double` / `Float` for amounts** | Store decimal amounts directly in SQLite `REAL`. | **Rejected.** Binary floating-point representation causes rounding drift in cumulative sums and balance checkpoints. |
| **`PENDING_REVIEW` status on `Transaction` table** | Save incomplete parse results directly as pending transactions and filter them out in every query. | **Rejected (PRD 4.4 / Appendix A).** Pollutes foreign-key/NOT-NULL constraints, risks accidental inclusion in balances/totals, and entangles parser retries with accounting state. |
| **Numeric parser confidence score (`0.0..1.0`)** | Threshold-based routing to Needs Review. | **Rejected for MVP (PRD 6.3).** Arbitrary heuristics obscure failure reasons; deterministic `ReviewReason` enums explain *why* an event stalled. |
| **Creating a 3rd `FEE` transaction row on transfer link** | Split outgoing transfer into transfer amount + separate fee transaction. | **Rejected (`TRF-04`).** Mutates the captured outgoing account balance twice or forces rewriting captured transaction amounts; derived `fee_minor` on `TransferMatch` preserves captured leg amounts while balancing categories cleanly. |
| **Full `TransactionRevision` history table** | Append-only audit log of every user edit. | **Cut in PRD 1.4 (`TX-03`).** `original_json` provides full capture-vs-current accountability with far less schema and backup overhead. |

---

## 6. Technical Justification

- **Single Classification Table Truth:** Consolidating income/expense/netting rules into one pure-Kotlin domain calculator shared across Dashboard, Insights, and Account summaries eliminates discrepancies between screen totals (`TEST-005`).
- **Immutability of Capture Provenance (`original_json` + `event_id` retention):** Users can freely fix misparsed categories, descriptions, merchants, or amounts (`TX-03`) or void duplicates (`TX-08`) without ever breaking catch-up scan idempotency or losing the original notification extract.
- **Strict Separation of `NotificationEvent` and `Transaction`:** Cleanly guarantees zero half-parsed financial records ever enter accounting math.

---

## 7. Consequences and Trade-Offs

- **Positive:**
  - Exact integer accounting across balances, checkpoints, adjustments, refunds, and derived transfer fees.
  - High parser auditability (`ReviewReason` + `original_json` + verification-window raw text).
- **Negative / Risks:**
  - If the owner later switches `IDR` exponent from `0` to `2` (or vice versa) after data exists, a risky data migration multiplying/dividing every historical amount and fee by `100` would be required — hence **locking the IDR exponent before M1** is mandatory.
  - Because `NeedsReview` items are excluded from full backups (`DATA-05`), unresolved review items should be highlighted to the user before creating a backup.
