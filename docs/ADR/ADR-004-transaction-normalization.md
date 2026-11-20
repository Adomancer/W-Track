# ADR-004: Transaction Normalization, Classification, and Monetary Representation

**Date:** 2026-11-21

---

## 1. Status

**Proposed — Core Canonical Model Confirmed; Recommended IDR Exponent `2` (ISO 4217), 1.3/1.4 Classification Details, and G1 Scope Items Pending Confirmation**

- **Confirmed Baseline (PRD v1.4):** Integer minor-unit storage (`Long`, never `Double`/`Float`), `INFLOW`/`OUTFLOW` direction semantics, deterministic `ParseResult` contract (no uncalibrated confidence scores), `NeedsReview` modeled as `NotificationEvent` (not `Transaction`), `posted_at` timestamp fallback (never `received_at`), locale-aware parsing (`PAR-12`) that never guesses ambiguous numbers, and composite 3-tier deduplication (never amount+timestamp alone).
- **Recommended Monetary Decision (Pending Owner Sign-Off Before M1):**
  - **IDR Storage Exponent `2` (ISO 4217 standard, `1 IDR = 100 sen`)** — storage, parsing, display, and accounting implications documented below.
- **Confirmed/Proposed Accounting Proposals (PRD 1.3/1.4):** `ADJUSTMENT`, `REFUND` netting, derived transfer fees, unpaired `CASH` classification, `original_json` snapshot vs revision history, void-not-delete.
- **Decided at Gate G1:** Multi-account per source (`ACC-05`/`PAR-02`/`REV-07`), credit card signed ledger (`ACC-10`), and one-payment-announced-by-two-apps rule (PRD Section 11).

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
2. Indonesian dot-thousands (`1.500`) vs English dot-decimals (`1.50`) can cause silent **1,000x misreads** (`PAR-12`), while mixing major-unit IDR (`50_000L`) with ISO 4217 minor units (`5_000_000L`) can cause **100x scaling errors** across storage, UI, and exports.
3. Internal top-ups (bank → e-wallet), transfer fees, balance corrections, failed attempts, and refunds would inflate income/expense totals or double-deduct balances.
4. Incomplete notifications forced into a `transactions` table would corrupt balance calculations or violate NOT NULL invariants.

---

## 4. Decision

### A. Confirmed Baseline (PRD v1.4)

1. **Canonical Transaction Model & Integer Minor Units (PRD Section 7.1):**
   - Monetary values are stored as `Long` integers in the currency's minor unit (`amount_minor`, `reported_balance_minor`, `initial_balance_minor`, `fee_minor`). Floating-point `Double`/`Float` is prohibited for money.
   - An explicit `CurrencySpec(code: String, minorUnitDigits: Int)` registry handles currency exponents explicitly (e.g., `USD = 2`, `JPY = 0`, `IDR = 2` recommended below) rather than hardcoding 2 blindly across all currencies.
   - Timestamps are persisted in UTC epoch milliseconds (`Long`) and rendered in the device's local timezone; notification text dates are parsed in the provider's verified timezone, falling back to `NotificationEvent.posted_at` — **never** `received_at` (`PAR-12`).
2. **Direction vs. Cash-Flow Semantics:**
   - `Direction` (`INFLOW`, `OUTFLOW`) describes account balance movement only, independent of whether an item is income or expense.
   - Parser contract `TransactionType` (`PURCHASE`, `TRANSFER`, `FEE`, `REFUND`, `CASH`, `OTHER`); Domain model adds `ADJUSTMENT` (created solely by `ACC-08`, never emitted by a parser).
3. **Deterministic Parser Contract & Needs Review Boundary (PRD Section 4.4, 6.3):**
   - Parsers emit deterministic `ParseResult.Success`, `ParseResult.NeedsReview(reason, partial)`, or `ParseResult.NotTransaction(reason)` — no numeric confidence scores in MVP.
   - `NeedsReview` items remain on `NotificationEvent` with `review_draft_json`; no `Transaction` row exists until parsing succeeds or the user explicitly confirms and saves the review item inside an atomic DB transaction.
4. **Locale-Aware Number Parsing & Ambiguity Guard (`PAR-12`):**
   - Parsers pass the **provider notification's verified language locale** (`id-ID` vs `en-US` / `en-ID`) — never the phone's `Locale.getDefault()`.
   - Any ambiguous separator pattern where provider fixtures have not established an unambiguous rule (e.g., `1.500` without decimal suffix across mixed-locale providers) returns `NeedsReview(AMBIGUOUS_FIELD)` instead of guessing.
5. **3-Tier Deduplication & Idempotency (PRD Section 8.1):**
   - Precedence: (1) `provider_reference` → (2) `stable_event_key` → (3) composite fingerprint (`account_id`, `amount_minor`, `currency`, `direction`, time bucket, normalized merchant/ref) inside a bounded matching window. Never deduplicate solely on `(amount, timestamp)`.

---

### B. Recommended IDR Currency Exponent (`2`, Following ISO 4217) & Implications

**Recommended Decision (`[CONFIRM BEFORE M1 / DB-002]`):** Store **`IDR` using exponent `2` (`1 IDR = 100 sen`)**, aligning with ISO 4217 (`Currency.getInstance("IDR").defaultFractionDigits == 2`) and supporting fractional notifications explicitly cited in PRD `PAR-12` (`Rp 1.250.000,50`).

#### Storage, Parsing, Display, Accounting & Export Implications

| Layer | Concrete Behavior (`IDR` Exponent `= 2`) | Example (`Rp 50.000`) | Example (`Rp 1.250.000,50`) | Invariant / Guardrail |
| :--- | :--- | :--- | :--- | :--- |
| **SQLite / Room Storage** | Stored as integer `Long` minor units (`sen`). `Long.MAX_VALUE` ($9.22 \times 10^{18}$) covers up to `Rp 92.2 quadrillion`, avoiding overflow. | `5_000_000L` | `125_000_050L` | Changing exponent after data exists requires multiplying/dividing every historical row by `100` — lock before M1! |
| **Parser & Normalization (`PAR-12`)** | `MoneyParser` parses exact decimal string via `BigDecimal` / integer math and scales by $10^{\text{CurrencySpec.minorUnitDigits}}$ (`100` for `IDR`). | `"Rp50.000"` → `5_000_000L` | `"Rp 1.250.000,50"` → `125_000_050L` | Unit tests must assert against **100x scaling errors** (forgetting `× 100` on integer Rupiah) as well as **1,000x separator misreads**. |
| **Display Formatting (UI)** | Format via `CurrencyFormatter`: when `amountMinor % 100L == 0L`, **omit trailing `,00`** for clean Indonesian readability; when `amountMinor % 100L != 0L`, render exact 2 decimal places (`Rp 1.250.000,50`). | `Rp50.000` | `Rp1.250.000,50` | UI components must **never** divide by `100` manually or format `amountMinor` directly as Rupiah without going through `CurrencyFormatter`. |
| **Manual Entry & Edits (`TX-03`, `TX-07`)** | Input accepts major-unit Rupiah (`50000`) with optional decimal cents (`,50`) and converts via `CurrencySpec` to minor units before persistence. | User types `50000` → stores `5_000_000L` | User types `1250000,50` → stores `125_000_050L` | Reject >2 fractional digits for `IDR` during manual validation. |
| **Aggregates, Balances & Transfers** | All sums, `initial_balance_minor`, `reported_balance_minor`, `ADJUSTMENT` diffs (`ACC-08`), and `fee_minor` (`TRF-04`) operate in uniform `Long` minor units with zero rounding drift. | Net fee `6.500` = `650_000L` | Exact sen balance checkpoints preserved | `TransferMatch.fee_minor = outgoing_minor - incoming_minor` remains exact even when fees/interest have sen. |
| **Backup & CSV Export (`DATA-05`, `DATA-11`)** | Full backup JSON stores exact integer `amountMinor` + `currency` (lossless). CSV export converts minor units to standard decimal string using `CurrencySpec` (`50000.00` or `50000` with explicit currency column). | Backup: `5000000`, CSV: `50000.00` (IDR) | Backup: `125000050`, CSV: `1250000.50` (IDR) | Backup schema records `currency` explicitly alongside `amountMinor` so cross-version restores remain unambiguous. |

---

### C. Proposed Accounting Rules & Gate G1 Scope Decisions (PRD 1.3 / 1.4)

1. **Single 16-Row Classification Table (PRD Section 4.6):** Governs Dashboard, Insights, and Account summaries identically (`PURCHASE`, `FEE`, paired vs unpaired `TRANSFER`, paired vs unpaired `CASH`, `INFLOW` `REFUND` netting against spending, `OUTFLOW` `REFUND` → `NeedsReview`, `ADJUSTMENT` → Unreconciled Difference only, `VOIDED` excluded, non-base currency stored/flagged/excluded).
2. **Failed Attempts (`PAR-09`):** `NotTransaction(FAILED_ATTEMPT)` with immediate payload purge; never auto-voids existing transactions.
3. **Edit Provenance (`TX-03`):** Immutable `original_json` snapshot created at capture time instead of a full revision history table.
4. **Categorization Precedence (`CAT-02`, Section 4.9):** User-set (`USER`) > most specific matching `TransactionRule` (`RULE`) > most recently created matching `RULE`. User-set categories are never overwritten.
5. **Gate G1 Deferred Scope Decisions:** Multi-account hints (`ACC-05`/`PAR-02`/`REV-07`), credit card signed ledger (`ACC-10`), and dual-notifier handling (Section 11) stay schema-supported in M1 and are confirmed or dropped from Gate G1 real-device evidence.

---

## 5. Alternatives Considered

| Alternative | Description | Reason Rejected |
| :--- | :--- | :--- |
| **`IDR` Storage Exponent `0` (Whole Rupiah)** | Store `Rp 50.000` as `50_000L` (1 IDR = 1 minor unit). | **Not preferred.** Cannot losslessly store `Rp 1.250.000,50` (explicitly required by PRD `PAR-12` example) without truncating/rounding or routing valid interest/admin/tax decimal notifications to `NeedsReview`. |
| **`Double` / `Float` for amounts** | Store decimal amounts directly in SQLite `REAL`. | **Rejected.** Floating-point precision loss corrupts sums and balance checkpoints. |
| **`PENDING_REVIEW` status on `Transaction` table** | Save incomplete parse results directly as pending transactions. | **Rejected (PRD 4.4 / Appendix A).** Risks leaking unconfirmed items into totals and breaks foreign-key invariants. |
| **Numeric parser confidence score (`0.0..1.0`)** | Heuristic score routing to Needs Review. | **Rejected for MVP (PRD 6.3).** Deterministic `ReviewReason` explains exact failure cause. |
| **Creating a 3rd `FEE` transaction on transfer link** | Split outgoing transfer into transfer + fee row. | **Rejected (`TRF-04`).** Double-counts outgoing balance; derived `fee_minor` on `TransferMatch` keeps captured legs intact. |

---

## 6. Technical Justification

- **ISO 4217 Compliance + Clean Formatting:** Exponent `2` eliminates data loss for any Indonesian bank/e-wallet alert that carries `,50` or `.50` sen (bank interest, tax, fee rounding) while UI elision of `,00` preserves familiar `Rp50.000` presentation.
- **Single Classification Table Truth:** Consolidating income/expense/netting rules into one pure-Kotlin domain calculator prevents report drift (`TEST-005`).
- **Immutability of Capture Provenance:** `original_json` + retained `event_id` rows preserve auditability across edits, voids, and post-restore catch-up scans.

---

## 7. Consequences and Trade-Offs

- **Positive:**
  - Exact lossless representation across `IDR` and standard 2-decimal currencies without schema divergence.
  - High parser auditability (`ReviewReason`, `original_json`, verification-window text).
- **Negative / Risks:**
  - Every parser, manual-entry viewmodel, formatter, and test must convert between major Rupiah display units and minor `sen` strictly through `CurrencySpec` / `CurrencyFormatter`; forgetting `× 100` in one spot introduces a **100x scaling bug** (mitigated by strict type wrappers or dedicated `TEST-001` assertions).
  - Locking IDR exponent `2` before `DB-002` avoids costly future `× 100` data migrations.
