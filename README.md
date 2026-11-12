  # Personal Finance Aggregator

## Product Requirements Document (PRD) & Technical Specification

**Status:** Ready for the ingestion proof of concept (frozen for the proof of concept: further feedback goes to the 1.5 list, Appendix B); open decisions in section 19  
**Platform:** Android  
**Product stage:** Personal product, designed to remain extensible for a future public release  
**Architecture:** Local-first, offline-capable, no mandatory backend  
**Document version:** 1.4  
**Date:** 11 November 2026

**Revision history**

- **1.4:** Review pass on 1.3 using outside feedback. The proof of concept gets sharper and the scope gets smaller. Fixes: the payload-retention contradiction in section 19, a backup that left out the source mappings and settings (DATA-05), a balance checkpoint that no longer needs an initial balance (ACC-07), and a transaction-time fallback that used the wrong timestamp (`posted_at`). Adds: measurable Gate G1 criteria with an evidence log (section 17, Appendix C), an event-recovery rule (ING-12), the behavior of an unsupported source (ING-13), a heartbeat for unobserved capture gaps (ING-14), locale-aware number and date parsing (PAR-12), a classification table for direction and type (section 4.6), a rule for where a transfer fee appears (TRF-04), `INFLOW`/`OUTFLOW` direction names, platform and device decisions (section 5), and notification key/ID/tag evidence from the first proof-of-concept build. Cuts: the full revision history is replaced by stored original values (TX-03), the separate payload-encryption layer is dropped (section 9.2), CSV export (DATA-11) and credit cards (ACC-10) become SHOULD, and multi-account sources are confirmed or dropped at Gate G1. See Appendix B.
- **1.3:** Feedback pass on 1.2. Adds a reconciliation loop (balance correction, balance checkpoint, capture-gap tracking); a short verification window for the payloads of saved transactions; an encrypted full backup with a defined format, CSV export, and backup reminders; categorization that learns from the user's corrections; and data-model rules for failed attempts, refunds and reversals, transfer fees, credit cards, multi-account sources, and editing or voiding captured transactions. Roadmap re-estimated to 50--77 working days; the proof of concept and Gate G1 are not delayed. Several 1.3 decisions are proposals to confirm (section 19). See Appendix B.
- **1.2:** Second review pass after external validation. Restore raised to MUST; catch-up limits stated; dedup identity and key-failure behavior added as proof-of-concept tests; proof of concept widened to two real providers; modules reduced from five to three. See Appendix B.
- **1.1:** Review pass before the proof of concept. Encryption moved to M1; manual transfer pairing, manual entry, export, and listener catch-up added to the MVP; Needs Review model and parser contract clarified; modules reduced; roadmap, backlog, and acceptance criteria re-aligned. See Appendix B.
- **1.0:** Initial version.

------------------------------------------------------------------------

## 1. Product overview

### 1.1 Product vision

Build an Android application that automatically collects transaction information from supported banks and e-wallets, consolidates it into one personal finance view, and helps the user understand where their money goes.

The app should reduce the friction of checking multiple banking and wallet applications or manually recording every expense. It should prioritize privacy by processing and storing financial data locally on the device.

### 1.2 Problem statement

A person may use several bank accounts and e-wallets. Transaction histories are scattered across separate applications, and manually checking or recording them is inconvenient. Some providers do not expose public APIs suitable for personal aggregation.

The MVP therefore uses transaction notifications as its primary ingestion source. This approach is useful but inherently incomplete: the app can only capture events that a supported provider actually sends to Android notifications, while the app has the required access and the notification contains enough information.

### 1.3 Product goals

1.  Consolidate supported transaction events from multiple financial applications.
2.  Minimize manual transaction entry, including categorization that learns from the user's corrections.
3.  Keep financial data on the user's device by default.
4.  Make parsing failures, capture gaps, and balance drift visible and correctable.
5.  Provide a useful overview of spending, income, accounts, and cash flow.
6.  Keep the architecture modular enough to add providers and capabilities later.

### 1.4 Non-goals for the MVP

- Logging in to bank accounts on the user's behalf.
- Collecting or storing bank passwords, PINs, OTPs, or other credentials.
- Scraping bank application screens.
- Using Accessibility Service to extract financial data.
- Requiring a cloud account or backend.
- Multi-device synchronization.
- Payment processing or money transfers.
- Investment and portfolio tracking.
- An AI chatbot as a primary product feature.
- Guaranteeing complete transaction history or 100% capture.
- Identifying *which* transactions were missed. The MVP can only flag that a gap or a balance mismatch may exist (ACC-07) and let the user correct the balance (ACC-08).
- Automatic matching of internal transfers (manual pairing is in scope; see section 4.8).
- Currency conversion (the MVP aggregates in a single base currency).

------------------------------------------------------------------------

## 2. Product principles

| Principle       | Meaning                                                                                           |
|:----------------|:--------------------------------------------------------------------------------------------------|
| Automatic-first | Prefer supported automatic capture over manual entry.                                             |
| Local-first     | Core features work without an account or backend.                                                 |
| Privacy-first   | Capture only explicitly selected sources and retain only necessary data.                          |
| Transparent     | Show capture status, parsing confidence, and limitations clearly.                                 |
| Recoverable     | Uncertain or failed parsing goes to Needs Review instead of disappearing silently.                |
| Reconcilable    | The user can compare the app's numbers with the provider's and correct the difference explicitly. |
| Source-agnostic | Normalize provider-specific data into one canonical transaction model.                            |
| Offline-capable | Dashboard, history, accounts, and review work locally.                                            |
| Extensible      | New providers should be added through isolated parser integrations.                               |

------------------------------------------------------------------------

## 3. Target users and usage

### 3.1 Initial target

The initial target is an individual Android user who has multiple bank accounts and/or e-wallets and wants a consolidated personal finance record.

### 3.2 Future direction

The architecture should not assume that the app will remain a private prototype. It should support future distribution to other users, additional provider integrations, improved onboarding, and optional services without making those services mandatory for the MVP.

### 3.3 Primary user journey

1.  The user installs the app.
2.  The app explains what notification access means and what data it will process.
3.  The user grants Android Notification Access.
4.  The user explicitly selects the financial apps that may be monitored.
5.  The user creates the corresponding accounts in the app.
6.  A supported bank or wallet posts a transaction notification.
7.  The app filters the event by the user's allowlist, parses it, validates it, and checks for duplicates.
8.  A reliable transaction is saved automatically.
9.  An uncertain transaction appears in Needs Review.
10. The user reviews the dashboard and transaction history.

------------------------------------------------------------------------

## 4. Scope and functional requirements

Priority labels:

- **MUST:** required for MVP acceptance.
- **SHOULD:** valuable for MVP, but can be deferred if needed.
- **COULD:** future enhancement.

### 4.1 Account management

| ID     | Priority | Requirement                                                                                                                                                                                                                                                                                                                                                                    |
|:-------|:---------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ACC-01 | MUST     | Create, edit, archive, and view financial accounts.                                                                                                                                                                                                                                                                                                                            |
| ACC-02 | MUST     | Support bank, e-wallet, cash, credit card, and other account types.                                                                                                                                                                                                                                                                                                            |
| ACC-03 | MUST     | Store account name, provider, account type, and currency.                                                                                                                                                                                                                                                                                                                      |
| ACC-04 | SHOULD   | Allow an optional opening/initial balance together with the date it applies from (`initial_balance_at`). Transactions dated before that date, including manual ones, do not change the estimated balance.                                                                                                                                                                                                                                                                                                                                     |
| ACC-05 | MUST     | Associate a supported notification source with an account. When several accounts share one source, an account hint (for example the last digits shown in the notification) tells them apart (PAR-02, section 6.2). The multi-account part is confirmed or dropped at Gate G1 (section 17); the schema supports it either way.                                                                                                                                                   |
| ACC-06 | MUST     | Show, for each account, whether automatic capture is active, needs attention, disconnected, or unsupported.                                                                                                                                                                                                                                                                    |
| ACC-07 | SHOULD   | Use provider-reported balances, when notifications include them, as checkpoints: compare the change between two consecutive reported balances of the same account with the net amount of the transactions captured between them. If they differ, flag the account as having a possible gap or a possible misparse. No initial balance is needed. When two reports cannot be put in order with certainty (same post time, or delivered out of order), no comparison is made. The app does not guess which transaction is missing.                                                                                  |
| ACC-08 | MUST     | Let the user correct an account balance by entering its actual balance now. The app records the difference between the entered balance and the current estimate as an `adjustment` transaction (section 8.2). An adjustment changes the estimated balance but is excluded from income, expense, and cash-flow totals and is shown as an unreconciled difference (section 4.6). |
| ACC-09 | SHOULD   | Show, for each account, the date of its last balance correction or matching checkpoint and any capture gaps (ING-11) that overlap its recent history.                                                                                                                                                                                                                          |
| ACC-10 | SHOULD   | Credit card accounts (MUST only if the owner uses one; decided at Gate G1) use the same signed ledger as other accounts: purchases and fees are expenses, the balance is shown as an amount owed, and a card payment from another account is an internal transfer (section 8.3). A provider's "available credit" is never treated as the balance.                                                                                    |

An account balance calculated from the initial balance and captured transactions is an **estimate**, not a guaranteed bank balance. The UI must make this clear because notifications may be missing or incomplete.

Reconciliation (ACC-07 to ACC-09) is how the app admits and repairs drift. A mismatch between a reported balance change and the transactions captured in between is ambiguous by nature: it can mean a missed notification or a misparsed one. The app therefore reports the difference and asks the user to check; it never edits transactions on its own. A balance correction compares the entered balance with the current estimate, so adding missed transactions later and correcting again nets the earlier adjustment out. The unreconciled difference is presented separately so that it never silently distorts spending categories.

### 4.2 Notification capture

| ID     | Priority | Requirement                                                                                                                                                                                                                                                                                                                                                                                                                  |
|:-------|:---------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ING-01 | MUST     | Use Android `NotificationListenerService` as the primary MVP ingestion mechanism.                                                                                                                                                                                                                                                                                                                                            |
| ING-02 | MUST     | Require the user to explicitly grant Notification Access.                                                                                                                                                                                                                                                                                                                                                                    |
| ING-03 | MUST     | Let the user select which installed apps are allowed as sources.                                                                                                                                                                                                                                                                                                                                                             |
| ING-04 | MUST     | Ignore notifications from packages not on the allowlist.                                                                                                                                                                                                                                                                                                                                                                     |
| ING-05 | MUST     | Process supported events locally.                                                                                                                                                                                                                                                                                                                                                                                            |
| ING-06 | MUST     | Handle notifications with missing, hidden, or grouped content safely.                                                                                                                                                                                                                                                                                                                                                        |
| ING-07 | MUST     | Show app-level capture status: whether Notification Access is granted and the listener service is connected.                                                                                                                                                                                                                                                                                                                 |
| ING-08 | SHOULD   | Provide a test-capture flow during onboarding.                                                                                                                                                                                                                                                                                                                                                                               |
| ING-09 | MUST     | On listener connect or reconnect, request a rebind when needed and scan active notifications through the idempotent pipeline. This recovers only notifications still in the drawer, not missed events.                                                                                                                                                                                                                       |
| ING-10 | SHOULD   | Guide the user through battery-optimization, autostart, and (for sideloaded builds) restricted-settings steps during onboarding.                                                                                                                                                                                                                                                                                             |
| ING-11 | SHOULD   | Record capture gaps: the interval between a loss of capture (Notification Access revoked, or `onListenerDisconnected`) and its recovery (access granted, or `onListenerConnected` followed by the catch-up scan). Tell affected accounts that transactions in that interval may be missing (ACC-09). Interruptions the app cannot observe, such as the process being killed without a disconnect callback, are not recorded here; ING-14 estimates them. |
| ING-12 | MUST     | Recover events that were accepted but not finished. On app start and on every listener connect, re-run events in `RECEIVED` or `PROCESSING` (older than a short grace period) and events in `FAILED` through the idempotent pipeline. After a limited number of failed attempts (proposed: 3), move the event to Needs Review with reason `PIPELINE_ERROR`. No accepted event stays stuck without being visible. |
| ING-13 | MUST     | Define what happens to a source on the allowlist that has no compatible parser (the *Unsupported* state, ACC-06). Its events are not parsed and do not go to Needs Review, so that promotions cannot flood the queue. They are marked `IGNORED` with reason `NO_PARSER`, their payloads are purged immediately (section 9.3), and the source shows a count of unparsed notifications for the last 30 days with a prompt to add transactions manually (TX-07). Sample collection for building fixtures is the only exception (section 11). |
| ING-14 | SHOULD   | Keep a heartbeat. While capture is running, store a "last alive" timestamp periodically (best effort, for example every 15 minutes and whenever an event is processed). On listener connect or app start, if the time since that timestamp exceeds a threshold (proposed: 3 hours, configurable and tuned in the lifecycle tests), record a `CaptureGap` with reason `HEARTBEAT_STALE` and treat it as a possible gap (ACC-09). This covers a process killed without a disconnect callback, the case ING-11 cannot observe. It cannot tell a killed process from a switched-off phone, so it only ever says "possible". |

The listener callback must remain lightweight. It should perform minimal filtering and hand off the event to the ingestion pipeline. Heavy parsing and database operations should not block the callback. WorkManager may be used for deferred maintenance or retry work, but it must not be treated as a guaranteed real-time execution mechanism.

Capture status is derived from observable signals, not assumed: (1) whether Notification Access is granted to the app, (2) whether the listener reported `onListenerConnected` and has not since reported `onListenerDisconnected`, and (3) for each source, whether a compatible parser exists. A source that has produced no events for a long time may be flagged as informational only, because silence can also mean that no transactions occurred.

### 4.3 Transaction parsing and normalization

| ID     | Priority | Requirement                                                                                                                                                                             |
|:-------|:---------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| PAR-01 | MUST     | Identify the source application/package.                                                                                                                                                |
| PAR-02 | MUST     | Resolve the source to a configured account where possible. Where a source has several accounts (ACC-05, decided at Gate G1), use the parser's account hint. An unresolved or ambiguous account goes to Needs Review. |
| PAR-03 | MUST     | Extract amount, currency, direction, timestamp, and available merchant/description data. Amounts and dates are read by the provider's verified locale (PAR-12).                                                                                                |
| PAR-04 | MUST     | Normalize provider-specific events into a canonical transaction model.                                                                                                                  |
| PAR-05 | MUST     | Validate parsed values before saving.                                                                                                                                                   |
| PAR-06 | MUST     | Distinguish transaction notifications from promotions and unrelated notifications.                                                                                                      |
| PAR-07 | MUST     | Route likely transactions with incomplete or uncertain data to Needs Review.                                                                                                            |
| PAR-08 | MUST     | Keep provider parsers isolated and versioned.                                                                                                                                           |
| PAR-09 | MUST     | Treat failed, declined, or cancelled payment attempts as `NotTransaction` with reason `FAILED_ATTEMPT`: no transaction is created and no total changes.                                 |
| PAR-10 | MUST     | Parse refunds and reversals as transactions of type `refund`, with the provider reference when present, so that they can be linked to the original (TX-09, section 8.2).                |
| PAR-11 | COULD    | Re-parse retained events after a parser version change and present the differences as proposals. A re-parse never overwrites a transaction the user has edited.                         |
| PAR-12 | MUST     | Parse numbers and dates by the locale and language of the notification, not of the device. Indonesian and English formats must both be handled where a provider uses both (for example `Rp50.000`, `Rp 1.250.000,50`, `IDR 50,000.00`). A number whose separators could be read in two ways (for example `1.500`) is never guessed: it goes to Needs Review with reason `AMBIGUOUS_FIELD`, unless the provider's fixtures establish one reading. A notification time is read in the time zone the provider uses; when the notification has no usable time, the pipeline uses the event's `posted_at`, never its received time. A wrong confident parse is the failure Needs Review cannot catch (section 9.3), and a thousand-fold misread is the most likely form of it. |

A parser must distinguish:

- `Success`: a transaction was parsed sufficiently to be saved.
- `NeedsReview`: the event probably represents a transaction, but required information is missing or uncertain.
- `NotTransaction`: the event is not a transaction, such as a promotion, a general announcement, a security notice, or a failed payment attempt (PAR-09).

### 4.4 Needs Review

| ID     | Priority | Requirement                                                                                                                                             |
|:-------|:---------|:--------------------------------------------------------------------------------------------------------------------------------------------------------|
| REV-01 | MUST     | Show failed or uncertain transaction events in a dedicated queue.                                                                                       |
| REV-02 | MUST     | Show the source and reason the event needs review.                                                                                                      |
| REV-03 | MUST     | Let the user correct account, amount, direction/type, category, and description where applicable.                                                       |
| REV-04 | MUST     | Let the user save, ignore, or retry an event.                                                                                                           |
| REV-05 | MUST     | Retain the source payload of Needs Review events (maximum 30 days, section 9.3), protected by the database encryption (DATA-03), so they can be corrected or retried.                          |
| REV-06 | SHOULD   | Use user corrections to create optional user-specific rules without silently changing global parser behavior.                                           |
| REV-07 | SHOULD   | When the user resolves an ambiguous or unmapped account, offer to remember the account hint for that source so that later events resolve automatically. |

A failed parser must never silently discard an event that appears to be a transaction.

**Review item model.** A Needs Review item is a `NotificationEvent` with status `NEEDS_REVIEW` and an optional partial draft (`review_draft_json`). It is not a `Transaction` row, so incomplete data never has to satisfy transaction constraints and never reaches aggregates. A `Transaction` is created only when the user saves the item, or when the parser returns `Success`, in the same database transaction that marks the event `PROCESSED`.

### 4.5 Transaction management

| ID    | Priority | Requirement                                                                                                                                                                                                                                                                                                                          |
|:------|:---------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| TX-01 | MUST     | Show a chronological transaction list.                                                                                                                                                                                                                                                                                               |
| TX-02 | MUST     | Open a transaction detail view.                                                                                                                                                                                                                                                                                                      |
| TX-03 | MUST     | Allow edits to the category and description of any transaction, and to its amount, account, direction, type, and date. The transaction keeps the values it was created with (`original_json`, section 7.1), shown next to the current values in the detail view, so an edit never destroys the captured evidence. No per-edit history is kept in the MVP.                                                            |
| TX-04 | MUST     | Support search and filters by date, account, type, and category.                                                                                                                                                                                                                                                                     |
| TX-05 | MUST     | Preserve source/provenance information (source, event reference, provider reference when available, and parser version) for captured transactions. While the source payload is still retained (section 9.3), show it in the detail view so the user can verify the extracted values.                                                 |
| TX-06 | SHOULD   | Support tags and transaction notes.                                                                                                                                                                                                                                                                                                  |
| TX-07 | MUST     | Allow user-created manual transactions; required for cash accounts and unsupported sources.                                                                                                                                                                                                                                          |
| TX-08 | MUST     | Allow the user to void a transaction and to restore it. A voided transaction is excluded from totals, balances, and transfer pairs and stays visible under a "Show voided" filter. Captured transactions are voided, not deleted, so that their event is never turned into a second transaction; manual transactions may be deleted. |
| TX-09 | SHOULD   | Link a refund or reversal to the transaction it reverses: automatically when its provider reference exactly matches an existing transaction's, otherwise by the user.                                                                                                                                                                |

Voiding or deleting a transaction that belongs to a confirmed transfer pair unlinks the pair after the user confirms. Editing the amount, currency, or account of a leg re-evaluates the pair (TRF-04): the edit is blocked if it would make the pair invalid, and the user must unlink the pair first. A category set by a rule (CAT-02) never replaces a category the user has set.

### 4.6 Dashboard and insights

| ID      | Priority | Requirement                                                                                                                     |
|:--------|:---------|:--------------------------------------------------------------------------------------------------------------------------------|
| DASH-01 | MUST     | Show income, expenses, and net cash flow for the selected period.                                                               |
| DASH-02 | MUST     | Show recent transactions.                                                                                                       |
| DASH-03 | MUST     | Show configured accounts and estimated balances where available.                                                                |
| DASH-04 | MUST     | Show the number of pending Needs Review items.                                                                                  |
| DASH-05 | SHOULD   | Show spending by category, including an explicit "Uncategorized" amount (CAT-03).                                               |
| DASH-06 | SHOULD   | Compare spending with the previous month.                                                                                       |
| DASH-07 | COULD    | Support category budgets and budget progress.                                                                                   |
| DASH-08 | COULD    | Show merchant-level spending trends.                                                                                            |
| DASH-09 | SHOULD   | Show the unreconciled difference (the sum of adjustments, ACC-08) for the selected period, separately from income and expenses. |

**Classification table.** Direction is the movement of money on the account (`INFLOW` or `OUTFLOW`); it does not say whether the movement is income. `TRANSFER` means money sent to or received from another account, person, or provider; it is internal only once the user confirms a pair. `CASH` means an ATM or teller cash withdrawal or deposit. How each combination is reported:

| Direction | Type | Link or pair status | Reported as |
|:----------|:-----|:--------------------|:------------|
| `OUTFLOW` | `PURCHASE` | none | Expense, in its category |
| `OUTFLOW` | `FEE` | none | Expense, category Fees |
| `OUTFLOW` | `TRANSFER` | not paired | Expense (money sent to an outside party) |
| `INFLOW` | `TRANSFER` | not paired | Income (money received from an outside party) |
| either | `TRANSFER` | confirmed pair (TRF-01) | Excluded from income and expense; the pair's fee, if any, is an expense in Fees (TRF-04) |
| either | `CASH` | paired with a cash account | Excluded, like any internal transfer |
| `OUTFLOW` | `CASH` | not paired | Expense, category "Cash withdrawal" (new in 1.4, proposed) |
| `INFLOW` | `CASH` | not paired | Income (new in 1.4, proposed) |
| `INFLOW` | `REFUND` | linked to the original (TX-09) | Reduces spending in the original's category; not income |
| `INFLOW` | `REFUND` | not linked | Reduces spending in its own category; not income |
| `OUTFLOW` | `REFUND` | none | Not produced in the MVP: a refund is always `INFLOW`, and a parser that sees an outgoing reversal returns `NeedsReview` |
| `INFLOW` | `OTHER` | none | Income |
| `OUTFLOW` | `OTHER` | none | Expense |
| either | `ADJUSTMENT` | none | Excluded; shown as the unreconciled difference (DASH-09). Direction follows the sign of the difference |
| any | any | voided | Excluded from totals, balances, and pairs (TX-08) |
| any | any | currency other than the base currency | Stored and flagged; excluded from totals |

Aggregate rules, shared by every report:

- **Internal transfers.** Confirmed transfer pairs (section 4.8) are excluded from income and expense totals. A transfer fee is a separate expense (TRF-04).
- **Refunds and reversals.** Transactions of type `refund` are netted against spending: in the original transaction's category when linked (TX-09), otherwise in their own category. They are not counted as income.
- **Adjustments.** Adjustment transactions (ACC-08) change the estimated balance but are excluded from income, expense, and cash-flow totals. They are shown separately as the unreconciled difference (DASH-09).
- **Voided transactions** are excluded from totals and balances (TX-08).
- **Uncategorized** transactions are included in totals and appear as their own slice in category reports (CAT-03).
- **Derived fee rows.** A transfer fee derived from a pair (TRF-04) is reported in the Fees category as a read-only derived row (not a transaction), dated and attributed to the pair's outgoing leg, so the total of the Fees category always equals the rows listed under it.

Aggregate totals use a single base currency in the MVP, chosen during onboarding (default: the currency of the first account). Transactions in other currencies are stored with their original currency, flagged, and excluded from aggregate totals; no exchange-rate conversion is performed.

### 4.7 Local data, backup, and export

| ID      | Priority | Requirement                                                                                                                                                                                                                                                                                                                                                                |
|:--------|:---------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DATA-01 | MUST     | Store core application data locally.                                                                                                                                                                                                                                                                                                                                       |
| DATA-02 | MUST     | Keep core features usable offline.                                                                                                                                                                                                                                                                                                                                         |
| DATA-03 | MUST     | Protect financial data at rest using a maintained encryption implementation.                                                                                                                                                                                                                                                                                               |
| DATA-04 | MUST     | Protect encryption key material using Android Keystore.                                                                                                                                                                                                                                                                                                                    |
| DATA-05 | MUST     | Provide a user-initiated full backup: a versioned file with everything needed to restore the user's data (DATA-09), namely accounts, account-to-source mappings with their account hints, transactions (including voided ones, adjustments, and their original captured values), categories, merchants, rules, tags, transfer pairs, budgets, capture gaps, event identity records (section 9.4), the source allowlist, the base currency, and app settings. It excludes raw notification payloads and Needs Review items. |
| DATA-06 | MUST     | Provide a way to delete stored data.                                                                                                                                                                                                                                                                                                                                       |
| DATA-07 | MUST     | Apply retention limits to raw notification payloads and processing logs.                                                                                                                                                                                                                                                                                                   |
| DATA-08 | MUST     | Disable automatic cloud and device-transfer backup of app data; user-initiated export is the supported backup path.                                                                                                                                                                                                                                                        |
| DATA-09 | MUST     | Restore a full backup on a fresh install. Restore is atomic (a failed or wrong-passphrase restore leaves no partial data), refuses a backup from a newer format version than the app supports, and migrates older versions.                                                                                                                                                |
| DATA-10 | MUST     | Encrypt every full backup with a key derived from a user-chosen passphrase. No plaintext full backup is offered. The passphrase cannot be recovered.                                                                                                                                                                                                                       |
| DATA-11 | SHOULD   | Provide a CSV export of accounts, transactions, and categories for analysis and portability, after an explicit warning that the file is not encrypted. It is not needed for recovery (the full backup does that) and can slip if the schedule needs room.                                                                                                                                                                                                                     |
| DATA-12 | SHOULD   | Remind the user to back up when the last full backup is older than a threshold of days or of new transactions (proposed defaults: 14 days or 100 transactions). The reminder is dismissible, configurable, and shows no financial details.                                                                                                                                 |
| DATA-13 | SHOULD   | Verify a new full backup after writing it (re-read, decrypt, and check integrity) and report the result.                                                                                                                                                                                                                                                                   |
| DATA-14 | COULD    | Write full backups on a schedule to a folder the user chose through the Android document picker, so that a folder synced by another app gives an off-device copy without a backend.                                                                                                                                                                                        |

Android Keystore does not automatically encrypt a SQLite database. It should protect key material used by the selected database-encryption solution.

The database key is bound to the device's Keystore, so an automatically restored copy of the database on another device would be unreadable. This is why automatic backup is disabled (DATA-08) and why backup and restore are MUST: the app cannot re-capture transactions it missed (section 20), so the user's own full backup, restored on a fresh install, is the only recovery path. Because that path depends on the user remembering to use it, 1.3 adds reminders (DATA-12) and verification (DATA-13), and leaves room for scheduled backups to a user-chosen folder (DATA-14).

The backup passphrase is independent of the database key, which is bound to the device. Losing the passphrase makes the backup unreadable, by design (DATA-10).

Android does not carry Notification Access across installs. After a restore, the allowlist and the account-to-source mappings are back, but every source shows *Attention* until the user grants Notification Access again; the restore flow ends by sending the user to that step (ING-02). Capture does not resume before that.

### 4.8 Internal transfers

| ID     | Priority | Requirement                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
|:-------|:---------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| TRF-01 | MUST     | Let the user manually link an outgoing and an incoming transaction from two different accounts as an internal transfer pair. Either leg may be a manual transaction, which is how transfers to or from cash or an unsupported source are recorded.                                                                                                                                                                                                               |
| TRF-02 | MUST     | Exclude confirmed transfer pairs from income and expense totals; any transfer fee is counted as a separate expense (TRF-04).                                                                                                                                                                                                                                                                                                                                     |
| TRF-03 | COULD    | Suggest transfer pairs automatically (section 8.3); the user confirms unless confidence is high.                                                                                                                                                                                                                                                                                                                                                                 |
| TRF-04 | MUST     | When a pair is linked, require both legs to share a currency and the outgoing amount to be at least the incoming amount. A positive difference is the transfer fee: the pair stores it (`fee_minor`) and aggregates report it as an expense in the fees category, without creating a third transaction, so no balance is changed twice. The fee belongs to the account and the date of the outgoing leg and is shown as a read-only derived row (not a transaction) in the Fees category and in the outgoing leg's detail; editing the outgoing leg's date or account moves the fee with it. If the provider reports the fee as its own notification, that is an ordinary fee transaction and the pair's fee is zero. |

Without manual pairing, a bank-to-e-wallet top-up would be counted as both an expense and income and would distort every aggregate. A transaction belongs to at most one confirmed pair. Because the fee is derived from the difference between the two legs, editing either leg re-evaluates the pair (section 4.5). Paying a credit card from another account is linked the same way (ACC-10).

### 4.9 Categorization

Spending insights are only useful if most transactions have a category, and the goal of minimizing manual entry (section 1.3) applies to categorization as well. The MVP therefore learns from the user's own corrections instead of relying on a global model.

| ID     | Priority | Requirement                                                                                                                                                                                             |
|:-------|:---------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| CAT-01 | MUST     | Ship a default set of income and expense categories, including a system "Uncategorized" and a "Fees" category. The user can add, rename, and archive categories.                                        |
| CAT-02 | MUST     | When the user sets a category on a transaction, offer "Remember for this merchant" (on by default). This creates a user rule, and later transactions that match it receive that category automatically. |
| CAT-03 | MUST     | Make uncategorized transactions visible: an "Uncategorized" filter in Activity and an "Uncategorized" slice with its amount in category reports.                                                        |
| CAT-04 | SHOULD   | After a rule is created, offer to apply it to existing uncategorized transactions of the same merchant, showing how many would change.                                                                  |
| CAT-05 | MUST     | Let the user list, disable, and delete rules, so that a wrong rule can be undone.                                                                                                                       |

Rules are user-specific and local. A rule matches on the normalized merchant key (case, spacing, and trailing branch or terminal numbers removed) or, when a transaction has no merchant, on a description fragment. It can be limited to an account or a direction. Precedence: a category set by the user always wins; otherwise the most specific matching rule applies; ties go to the most recently created rule. The MVP has no global or learned model.

------------------------------------------------------------------------

## 5. Platform and technology decisions

| Area                     | Decision                                                                                               |
|:-------------------------|:-------------------------------------------------------------------------------------------------------|
| Platform                 | Android                                                                                                |
| Android versions         | `minSdk` and `targetSdk` are fixed in ADR-003 before M0, from the owner's own devices; ADR-003 lists each test device (model, OEM skin, Android version). |
| Language                 | Kotlin                                                                                                 |
| UI                       | Jetpack Compose                                                                                        |
| Design system            | Material 3                                                                                             |
| Local database           | Room / SQLite                                                                                          |
| Database encryption      | SQLCipher for Android via Room (proposed; ADR-005)                                                     |
| Backup encryption        | Key derived from a user passphrase (memory-hard KDF) with authenticated encryption (proposed; ADR-006) |
| Dependency injection     | Hilt                                                                                                   |
| Async work               | Kotlin Coroutines and Flow                                                                             |
| Serialization            | Kotlin Serialization                                                                                   |
| Preferences              | DataStore                                                                                              |
| Deferred background work | WorkManager                                                                                            |
| Key protection           | Android Keystore                                                                                       |
| Architecture             | Modular monolith with pragmatic Clean Architecture                                                     |
| Backend                  | Not required for MVP                                                                                   |

These are the selected implementation defaults, not a reason to introduce extra abstractions before the first working ingestion flow exists.

Listener behavior differs across Android versions and OEM skins, so two cases are verified on the target devices (section 14.3). First, from Android 15 the system can redact the content of notifications it detects as containing a one-time code for listeners that are not privileged, so a transaction notification that contains a code-like value may arrive without content; the hidden-content path (ING-06, Needs Review) must handle it without guessing fields. Second, an app installed outside Google Play needs the restricted-settings step before Notification Access can be enabled (ING-10).

------------------------------------------------------------------------

## 6. High-level architecture

``` mermaid
flowchart TD
    A[Bank and e-wallet apps] --> B[Android notification system]
    B --> C[NotificationListenerService]
    C --> D[Package allowlist]
    D --> E[Lightweight event handoff]
    E --> F[Local event inbox]
    F --> G[Source resolver]
    G --> H[Parser registry]
    H --> I[Provider-specific parser]
    I --> I2[Account resolver using the account hint]
    I2 --> J[Normalizer and validator]
    J --> K[Deduplication and classification]
    K --> L{Processing result}
    L -->|Valid transaction| M[Room database]
    L -->|Uncertain or incomplete| N[Needs Review]
    L -->|Not a transaction| O[Ignore with minimal metadata]
    M --> P[Repositories and use cases]
    N --> P
    P --> Q[Jetpack Compose UI]
```

### 6.1 Architectural boundaries

- **Presentation:** Compose screens, UI state, navigation.
- **Domain:** business rules and use cases; independent of Android UI and Room.
- **Data:** Room entities, DAOs, repository implementations, local persistence.
- **Integration:** provider-specific notification parsers and compatibility fixtures.
- **Core:** shared models, security, database infrastructure, design system, utilities.

In the MVP these layers are packages inside the `app` module (section 12), not separate modules. The exception is the parser boundary (`integration`).

### 6.2 Ingestion pipeline

1.  Receive notification callback.
2.  Read package name and minimum required notification fields.
3.  Check the allowlist immediately.
4.  Ignore disallowed packages without persisting their content.
5.  Convert the allowed notification into a normalized internal event envelope.
6.  Hand off processing without doing expensive work in the listener callback.
7.  Persist the event record, including the notification text needed for parsing (protected by the database encryption), the notification's post time (`posted_at`), and its Android key, ID, and tag, so that process death cannot lose an accepted event.
8.  Resolve the source.
9.  Select a parser through the parser registry. If the allowlisted source has no parser, mark the event `IGNORED` with reason `NO_PARSER` and continue at step 16 (ING-13).
10. Parse. The parser returns the extracted fields and, when the notification shows one, an account hint.
11. Resolve the account from the source and the account hint (ACC-05). An unresolved or ambiguous account goes to Needs Review.
12. Normalize and validate.
13. Check idempotency and duplicates.
14. For a transaction about to be saved, apply category rules (CAT-02).
15. Save a transaction, route to Needs Review, or classify as not a transaction.
16. Schedule the payload purge (section 9.3): immediately for an ignore decision, at the end of the verification window for a saved transaction, and at the Needs Review limit for a review item. The event record itself stays as the idempotency record.

Events that a crash or an error leaves in `RECEIVED`, `PROCESSING`, or `FAILED` are picked up again by the recovery rule (ING-12).

### 6.3 Parser contract

``` kotlin
interface TransactionParser {
    val sourceId: String
    val version: Int
    fun supports(event: NotificationEvent): Boolean
    suspend fun parse(event: NotificationEvent): ParseResult
}

sealed interface ParseResult {
    data class Success(
        val transaction: ParsedTransaction
    ) : ParseResult

    data class NeedsReview(
        val reason: ReviewReason,
        val partial: PartialTransaction?
    ) : ParseResult

    data class NotTransaction(
        val reason: NotTransactionReason
    ) : ParseResult
}

// Direction is the movement of money on the account. It does not say whether the movement is income (section 4.6).
enum class Direction { INFLOW, OUTFLOW }

// The domain model adds ADJUSTMENT, which no parser can produce (ACC-08).
// TRANSFER: money sent to or received from another account, person, or provider (internal only once the user confirms a pair).
// CASH: ATM or teller cash withdrawal or deposit.  REFUND: always INFLOW in the MVP.
enum class TransactionType { PURCHASE, TRANSFER, FEE, REFUND, CASH, OTHER }

data class ParsedTransaction(
    val amountMinor: Long,
    val currency: String,                  // ISO 4217
    val direction: Direction,
    val type: TransactionType,
    val occurredAtEpochMillis: Long?,      // null: the pipeline uses the event's post time (posted_at), not its received time
    val merchantRaw: String?,
    val description: String?,
    val accountHint: String?,              // as shown by the provider, e.g. last digits; never a full account number
    val providerReference: String?,        // provider transaction/reference ID, only when reliably present
    val reportedBalanceMinor: Long?        // only when the notification reports the account balance
)

data class PartialTransaction(             // same fields as ParsedTransaction, all nullable
    val amountMinor: Long?,
    val currency: String?,
    val direction: Direction?,
    val type: TransactionType?,
    val occurredAtEpochMillis: Long?,
    val merchantRaw: String?,
    val description: String?,
    val accountHint: String?,
    val providerReference: String?,
    val reportedBalanceMinor: Long?
)

enum class ReviewReason {
    MISSING_AMOUNT, MISSING_DIRECTION, INVALID_VALUE, AMBIGUOUS_FIELD, UNKNOWN_FORMAT,
    ACCOUNT_UNMAPPED, ACCOUNT_AMBIGUOUS, VALIDATION_FAILED,
    PIPELINE_ERROR          // set by the pipeline (ING-12), never by a parser
}

enum class NotTransactionReason {
    PROMOTION, ANNOUNCEMENT, SECURITY_NOTICE, FAILED_ATTEMPT, OTHER
}
```

The MVP uses deterministic rules instead of numeric confidence scores. The parser alone decides the result:

- **Success:** amount, currency, and direction were all extracted, each is individually valid, and the notification matches a known transaction pattern of the provider.
- **NeedsReview:** the event looks like a transaction, but a required field is missing, invalid, or ambiguous.
- **NotTransaction:** the event matches an explicit non-transaction pattern (promotion, announcement, security notice, failed attempt).

Parsers must never put a full account number in `accountHint`. A notification that reports an available credit limit instead of a balance leaves `reportedBalanceMinor` empty (ACC-10). Providers that announce a payment in stages (for example pending, then completed or failed) are handled per provider from the M3a evidence: only the notification that states completion is `Success`, earlier stages are `NotTransaction(OTHER)`, and a failure notice is `NotTransaction(FAILED_ATTEMPT)`.

The pipeline then applies account resolution and cross-field validation (PAR-05). A `Success` that fails validation, or whose account cannot be resolved, is routed to Needs Review with the reason recorded. The pipeline does not re-score parser output. A numeric confidence value may be added later, but only with a documented way to compute it. The parser `version` is stored on every transaction the parser creates (TX-05).

------------------------------------------------------------------------

## 7. Canonical data model

### 7.1 Transaction model

A canonical transaction should include at least:

| Field                    | Description                                                                                                                            |
|:-------------------------|:---------------------------------------------------------------------------------------------------------------------------------------|
| `id`                     | Internal unique identifier                                                                                                             |
| `account_id`             | Account associated with the transaction                                                                                                |
| `source_id`              | Provider/source that produced the event                                                                                                |
| `event_id`               | Source event that produced the transaction                                                                                             |
| `amount_minor`           | Integer amount in the currency's minor unit                                                                                            |
| `currency`               | ISO 4217 currency code                                                                                                                 |
| `direction`              | Movement of money on the account: `INFLOW` or `OUTFLOW` (not the same as income, section 4.6)                                                                                                            |
| `transaction_type`       | Purchase, transfer, fee, refund, cash, adjustment, or other (definitions in sections 4.6 and 6.3)                                                                                |
| `merchant_id`            | Optional normalized merchant                                                                                                           |
| `description`            | Original or user-edited description                                                                                                    |
| `occurred_at`            | Transaction time: from the notification text when present, otherwise the event's `posted_at`                                                                                                                  |
| `category_id`            | Optional category                                                                                                                      |
| `category_source`        | How the category was set: `USER`, `RULE`, or empty                                                                                     |
| `applied_rule_id`        | Rule that set the category, when `category_source` is `RULE`                                                                           |
| `status`                 | Confirmed or voided state; review items are events, not transactions (section 4.4)                                                     |
| `fingerprint`            | Deduplication fingerprint                                                                                                              |
| `parser_version`         | Version of the parser that produced the transaction                                                                                    |
| `original_json`          | Values the transaction was created with (amount, currency, direction, type, occurred_at, account), kept for comparison after edits (TX-03); null for manual entries and adjustments |
| `provider_reference`     | Optional provider transaction/reference ID; first-choice deduplication key (section 8.1) and the key for linking refunds and reversals |
| `related_transaction_id` | Optional link from a refund or reversal to the transaction it reverses (TX-09)                                                         |
| `reported_balance_minor` | Optional balance reported by the provider notification                                                                                 |
| `created_at`             | Local record creation time                                                                                                             |
| `updated_at`             | Last local update time                                                                                                                 |

Use integer minor units rather than floating-point `Double` for money. Currency exponent rules should be handled explicitly; do not assume every currency has two decimal places. Store timestamps consistently (UTC) and display them in the user's local timezone.

`source_id`, `event_id`, `parser_version`, `provider_reference`, and `original_json` are null for manual entries and for adjustments (ACC-08). `reported_balance_minor` is nullable and set only when the notification reports a balance; it is used solely for reconciliation (ACC-07) and is never shown as an authoritative balance.

### 7.2 Proposed Room entities

| Entity                | Purpose                                                                             |
|:----------------------|:------------------------------------------------------------------------------------|
| `Account`             | Bank, wallet, cash, or card account configuration                                   |
| `Transaction`         | Canonical transaction record                                                        |
| `Category`            | Income/expense categories and optional hierarchy                                    |
| `Merchant`            | Normalized merchant identity                                                        |
| `NotificationEvent`   | Minimal source event and processing state                                           |
| `TransactionSource`   | Package/provider configuration and parser version                                   |
| `AccountSource`       | Link between an account and a notification source, with an optional account hint    |
| `TransferMatch`       | Suggested or confirmed transfer pair                                                |
| `Budget`              | Optional category/account budget                                                    |
| `Tag`                 | User-defined transaction tag                                                        |
| `TransactionTag`      | Many-to-many transaction/tag relation                                               |
| `TransactionRule`     | User-defined parsing/classification rules (categorization rules in the MVP, CAT-02) |
| `CaptureGap`          | Interval in which capture was interrupted (ING-11)                                  |

### 7.3 Suggested fields

- **Account:** `id`, `name`, `provider`, `account_type`, `currency`, `initial_balance_minor` (nullable), `initial_balance_at` (nullable; required when an initial balance is set), `last_reconciled_at` (nullable), `is_active`, `created_at`, `updated_at`
- **AccountSource:** `id`, `account_id`, `source_id`, `account_hint` (nullable; a masked fragment such as the last digits, never a full account number). At most one account per source may have an empty hint.
- **NotificationEvent:** `id`, `source_id`, `package_name`, `notification_key`, `notification_id`, `notification_tag` (nullable), as reported by Android and recorded from the first proof-of-concept build to learn how providers reuse them (section 8.1; they are identifiers the provider app chooses, not notification text, and if a tag turns out to carry personal data only its hash is stored), `posted_at` (the notification's post time), `payload` (set on receipt, cleared when purged; protected by the database encryption), `purge_after` (nullable; when the retention job clears the payload), `processing_status`, `attempt_count`, `failure_reason` (also the review reason and the ignore reason), `review_draft_json` (nullable partial draft for Needs Review), `received_at`, `processed_at`. The row stays after the payload is purged (section 9.3).
- **TransferMatch:** `id`, `outgoing_transaction_id`, `incoming_transaction_id`, `origin` (`MANUAL`, `AUTO`), `confidence` (nullable; empty for manual pairs), `status` (`SUGGESTED`, `CONFIRMED`, `REJECTED`), `fee_minor` (not negative; outgoing minus incoming amount, recomputed when a leg is edited), `created_at`
- **CaptureGap:** `id`, `started_at`, `ended_at` (nullable while the gap is open), `reason` (`ACCESS_REVOKED`, `LISTENER_DISCONNECTED`, `HEARTBEAT_STALE`)
- **Merchant:** `id`, `display_name`, `key` (unique normalized merchant key, section 4.9)
- **TransactionRule:** `id`, `name`, `condition_json`, `action_json`, `enabled`, `created_at`, `updated_at`. In the MVP, `condition_json` holds a merchant key or a description fragment with an optional `account_id` and `direction`, and `action_json` holds a `category_id`.

The ERD should enforce appropriate foreign keys and indexes. Store only fields that are needed by a real feature; do not persist raw notification content indefinitely just because storage is cheap.

------------------------------------------------------------------------

## 8. Deduplication, classification, and transfers

### 8.1 Deduplication

Preferred matching order:

1.  Provider transaction ID (`provider_reference`), when reliably available.
2.  Source event ID or stable notification identifier.
3.  A composite fingerprint using account, amount, direction, timestamp, merchant/reference, and a bounded matching window.

Do not deduplicate solely by amount and timestamp. Two legitimate purchases can have the same amount at nearly the same time.

Processing must be idempotent: replaying the same event should not create another transaction. The stable event identifier is derived from the package name, the notification post time, and a hash of the content, so that catch-up scans (ING-09) and duplicate deliveries resolve to the same event.

The event record stays after its payload is purged and after its transaction is voided (TX-08), and it is part of the full backup (DATA-05). A replayed notification, whether from a catch-up scan or after a restore, therefore never creates a second transaction for an event the app has already decided.

Event identity must be validated on real devices before it is trusted. Updated or re-posted notifications may carry a different post time or content, and identical-looking notifications may be legitimate separate events. The proof of concept must record how each provider behaves (update, re-post, retry), and the event identifier and fallback fingerprint are adjusted from that evidence. To make that evidence complete, every event records Android's notification key, ID, and tag from the first proof-of-concept build (section 7.3): recording them costs nothing now, and a week of missed evidence cannot be recovered later.

### 8.2 Transaction classification

The system should distinguish at least:

- Income
- Expense
- Internal transfer
- Refund/reversal
- Fee
- Adjustment
- Unknown / needs review

How each combination of direction, type, and link status is reported is defined in the classification table in section 4.6.

Rules for the less obvious classes:

- **Failed attempts** are not transactions (PAR-09). If a failure notice concerns a payment that was already saved, the user voids it (TX-08); the app does not void automatically.
- **Refund/reversal** has `INFLOW` direction and type `refund`. It is netted against spending (section 4.6), not counted as income. When it carries a provider reference that exactly matches an existing transaction's, it is linked to that transaction automatically; otherwise the user can link it (TX-09).
- **Fee** is an expense. A transfer fee derived from a transfer pair is not a separate transaction (TRF-04).
- **Adjustment** is created only by the balance-correction flow (ACC-08). A parser never produces one.

### 8.3 Internal transfer matching

In the MVP, transfer pairs are linked manually by the user (TRF-01, `origin = MANUAL`). Automatic matching, described below, is post-MVP (TRF-03).

**Linking rules (MVP).** A pair has one `OUTFLOW` and one `INFLOW` transaction in two different accounts and the same currency, and the outgoing amount is at least the incoming amount. The difference is the transfer fee and is stored as `fee_minor` on the pair (TRF-04). Balances always use the two legs as captured. In aggregates, the pair's legs are excluded and the fee is reported as an expense in the fees category, so no amount is counted twice. If the incoming amount is larger than the outgoing amount, the link is refused and the user is told why. Paying a credit card from a bank or wallet account is an internal transfer and is linked the same way (ACC-10).

A possible transfer pair consists of an outgoing transaction from one account and an incoming transaction to another account. Automatic matching signals can include:

- Same or near-equal amount, accounting for fees.
- Close timestamps.
- Compatible source/destination accounts.
- Matching reference or description.

Only high-confidence matches should be auto-confirmed. Medium-confidence matches should be suggested for user review. Low-confidence matches should remain ordinary transactions.

Confirmed internal transfers are excluded from income and expense totals. Transfer fees remain expenses.

------------------------------------------------------------------------

## 9. Security and privacy requirements

### 9.1 Permission and collection model

- Explain Notification Access before opening Android settings.
- Use an explicit per-app allowlist.
- Filter by package as early as possible.
- Do not store unrelated notification content.
- Never request bank credentials, PINs, OTPs, or passwords.
- Do not use Accessibility Service to inspect bank app screens.

Notification Access can expose notification content from selected packages, and notification formats can change. The app must be transparent about this capability and let users revoke access or change the allowlist.

### 9.2 Encryption

- Use a maintained SQLite/Room-compatible database encryption implementation. Proposed default: SQLCipher for Android through Room (confirm in ADR-005 before M1).
- Generate and protect key material using Android Keystore.
- Define key initialization, rotation, recovery, and failure behavior.
- Retained raw notification payloads are protected by the database encryption and are not encrypted a second time: the database key and a payload key would sit in the same Keystore chain, so a second layer adds code and key management without a new protection. Payloads never leave the database (backups exclude them, section 9.4), and a release build has no payload export.
- Do not claim the database is encrypted merely because Keystore is used.
- Enable encryption from the first schema version, including in the proof of concept, so no plaintext-to-encrypted migration is ever needed.
- Proposed key design (confirm in ADR-005): a random database passphrase, wrapped by a Keystore-protected AES-GCM key and stored in app-private storage.
- Define and test key-failure behavior before real data is stored: the database reopens after a reboot; if the key is missing or invalid (for example after app-data loss), the app shows a clear locked state, never silently creates a new empty database over existing data, and points to restore from a full backup. A lost key is unrecoverable by design.

### 9.3 Data minimization and retention

Initial retention policy:

- **Raw payloads of ignored events** (promotions, announcements, security notices, failed attempts): purged immediately after the ignore decision, so they are not retained.
- **Raw payloads of saved transactions:** kept, inside the encrypted database, for a short verification window, so that the user can check the extracted values (TX-05) and a parser fix can be re-parsed (PAR-11), then purged. Proposed default: 7 days, configurable from 0 (purge immediately) to 30 days.
- **Needs Review payloads:** kept so the item can be corrected or retried (REV-04, REV-05); default maximum 30 days unless the user resolves them earlier.
- **Event records without payload:** kept for as long as the data exists. They hold only the event identity, status, and timestamps, and they are the idempotency record (section 8.1).
- **Processing/debug logs:** default maximum 7 days.
- **Saved transactions and account data:** retain until the user deletes them.

A parse that is wrong but confident is the failure Needs Review cannot catch. The verification window with the source view (TX-05) exists for that case, and the balance checkpoint (ACC-07) catches it when the provider reports a balance. After the window, the user can still correct the transaction (TX-03), and each correction is recorded.

These are proposed defaults and should be configurable where practical. Logs must not contain full financial notification text, account numbers, credentials, or other sensitive content.

### 9.4 Export and deletion

- Backup and export must be initiated by the user.
- Explain the format and contents before creating a file. A full backup excludes raw notification payloads and Needs Review items, so show the pending Needs Review count and let the user resolve items first.
- Full backups are encrypted with a passphrase the user chooses (DATA-10). The format is versioned and carries the key-derivation parameters, salt, and nonce; the design is decided in ADR-006. Proposed: a memory-hard key-derivation function (Argon2id if a maintained library is available, otherwise PBKDF2-HMAC-SHA256 with a high iteration count) and AES-256-GCM. The passphrase cannot be recovered, and the app says so before the first backup.
- The user chooses the destination through the Android document picker, so the app needs no broad storage permission and nothing is written to shared storage without user intent.
- The CSV export (DATA-11) is not encrypted. Show a warning, and let the user choose the destination in the same way.
- A full backup includes the event identity records, so that after a restore a catch-up scan of notifications still in the drawer does not create duplicates. Restore is atomic (DATA-09).
- Provide a clear delete-data flow.
- Automatic Android backup and device-transfer are disabled for app data (DATA-08); the encrypted full backup is the supported backup path.

------------------------------------------------------------------------

## 10. UX and screen structure

### 10.1 Theme

- Adaptive light and dark theme.
- Follow Android system theme by default.
- Allow manual System / Light / Dark selection.
- Use Material 3 components and accessible contrast.

### 10.2 Primary navigation

Bottom navigation:

1.  **Home:** overview and recent activity.
2.  **Activity:** searchable transaction history.
3.  **Accounts:** configured accounts and capture status.
4.  **Insights:** category and time-based analysis.

Needs Review should be prominent and accessible from the dashboard, with a pending count.

### 10.3 Main screens

**Onboarding**

- Welcome and product explanation.
- Privacy and Notification Access explanation.
- Notification Access permission handoff.
- Battery-optimization and autostart guidance (and restricted-settings help for sideloaded builds).
- Financial app allowlist.
- Account creation and source association, including the account hint when one app serves several accounts.
- Optional initial balance.
- Test-capture step.
- Dashboard.

**Dashboard**

- Income, expenses, and net cash flow.
- Period selector.
- Account overview and estimated balances.
- Unreconciled difference for the period (DASH-09).
- Recent transactions.
- Pending review count.
- Backup reminder when due (DATA-12).
- Monthly spending chart.

**Activity**

- Chronological transaction list.
- Search and filters, including Uncategorized and Show voided.
- Transaction detail and edit, with the source text while it is retained and the original captured values next to the current ones.
- Set a category with "Remember for this merchant".
- Void or restore a transaction.
- Link a refund to its original transaction.
- Add manual transaction.
- Link or unlink a transfer pair, with the derived fee shown.

**Accounts**

- Account list.
- Provider and account type.
- Estimated balance, with the date of the last balance correction.
- Correct balance (reconcile).
- Capture status, capture gaps, and source configuration with account hints; for an unsupported source, the count of unparsed notifications (ING-13).

**Insights**

- Spending by category, including Uncategorized.
- Monthly comparison.
- Merchant analysis.
- Budgets when implemented.

**Needs Review**

- Source app and the notification's post time.
- Reason for review.
- Available extracted fields.
- Choose the account when it is ambiguous or unmapped.
- Edit and save.
- Retry.
- Ignore.

**Settings**

- Theme.
- Notification access and allowlist.
- Categorization rules (list, disable, delete).
- Backup and restore: full backup, CSV export, reminders, and verification.
- Data deletion.
- Retention settings, including the verification window.
- Privacy information.
- App diagnostics that avoid sensitive payloads.

### 10.4 Capture status labels

Use clear states:

- **Active:** access is available and the listener appears connected.
- **Attention:** permission or source configuration needs action.
- **Disconnected:** the app cannot currently receive events.
- **Unsupported:** the source is selected but no compatible parser exists.

In addition to its status, an account can show a **Possible gap** badge when capture was interrupted (ING-11, ING-14) or when reported balances do not match the transactions captured between them (ACC-07). The badge asks the user to check and to correct the balance if needed (ACC-08); it does not change the status.

Do not imply that Active means every transaction is captured.

------------------------------------------------------------------------

## 11. Provider integration strategy

Provider support must be evidence-based. Do not assume that a bank or e-wallet sends a notification for every transaction or that its notification contains enough information.

For each provider:

1.  Verify the installed app/package identity on a real device.
2.  Collect representative, anonymized notification fixtures for different transaction types, including failed attempts and refunds or reversals.
3.  Identify optional, hidden, localized, or grouped fields, and whether the notification identifies the account. Record each notification type's language (Indonesian, English, or both), number format (thousands and decimal separators), and time zone and clock format.
4.  Implement a provider-specific parser.
5.  Add unit tests for valid, invalid, partial, duplicate, and changed formats.
6.  Record parser version and known compatibility limitations.
7.  Test after provider app updates where possible.

**One payment, two notifiers.** A payment made through one app but funded by another (for example an e-wallet funded from a bank) may be announced by both apps. It is neither a transfer between the owner's accounts nor a per-account duplicate, so it can be counted twice. M3a records, for each pair of providers in use, whether this happens and what each notification says. Until a rule is written from that evidence, the MVP treats the notifications as separate transactions, and the user voids the extra one (TX-08) or pairs them as a transfer when both are real movements of money (TRF-01).

**Sample collection.** Building fixtures needs real notification text. During the proof of concept and M3a the owner may switch on sample collection for one source: its payloads are kept (inside the encrypted database) for at most 14 days, the source is clearly marked while collecting, and samples are anonymized before they become fixtures (section 14.3). It is off by default, and its payloads are excluded from backups.

Initial integrations should be selected based on the user's actual installed financial apps and the availability of useful transaction notifications. Provider names and package IDs must be verified on-device rather than copied from assumptions.

Future ingestion sources may include transaction emails or official provider APIs where available and permitted. They are not MVP dependencies.

------------------------------------------------------------------------

## 12. Project structure

The MVP starts with three Gradle modules. The parser boundary is the one that pays off immediately: parsers are pure Kotlin, can be tested on the JVM with fixtures, and stay isolated per provider. The layers from section 6.1 are packages inside `app` and are extracted into modules only when a boundary is repeatedly violated or build times justify it.

``` text
app/                      # Android app module, organized by package:
                          #   feature/  Compose UI and ViewModels
                          #   domain/   models, use cases, repository
                          #             interfaces (pure Kotlin)
                          #   data/     Room database, encryption, key
                          #             management, repositories,
                          #             notification listener, ingestion
                          #             pipeline, retention jobs
                          #   core/     design system, utilities
integration/
  api/                    # TransactionParser, ParseResult,
                          # NotificationEvent (pure Kotlin)
  providers/              # provider parsers and fixtures (one module
                          # until three or more providers exist)
build-logic/
docs/
  PRD.md
  ARCHITECTURE.md
  TECHNICAL_SPEC.md
  DATABASE_ERD.md
  SECURITY.md
  IMPLEMENTATION_PLAN.md
  ADR/
```

Dependency direction:

- `app` depends on `integration:api` and `integration:providers` and wires them together.
- `integration:providers` depends only on `integration:api`.
- Inside `app`, the `domain` package must not import UI, Room, Android framework, or provider-specific code. Enforce this in code review or with an architecture test.

Avoid creating empty modules just to make the architecture diagram look impressive. Extract `domain` and `data` into modules when the boundary is repeatedly violated, incremental build time becomes painful, or a part needs independent testing.

------------------------------------------------------------------------

## 13. Reliability and failure handling

The app must handle:

- Notification Access revoked.
- Listener service disconnected or restarted.
- Device reboot.
- Battery optimization and background restrictions.
- App process killed.
- Events left unfinished by a crash or a pipeline error (ING-12).
- Interruptions the app cannot observe directly (ING-14).
- Notification grouping or summary notifications.
- Hidden notification content.
- Rapid notification bursts.
- Duplicate delivery or retry.
- Provider notification format changes.
- Missing or ambiguous account mapping.
- Database or encryption-key errors.

Built-in mitigations: the listener requests a rebind when the system disconnects it and runs a catch-up scan of active notifications on every connect (ING-09). This is safe because processing is idempotent, but it can only recover notifications that are still present in the notification drawer. Onboarding walks the user through battery-optimization and autostart settings (ING-10). The system normally rebinds an enabled listener after a reboot; this must be verified on the target devices (section 14.3). Capture status is derived from observable signals (section 4.2). Missed events cannot be recovered, but they are made visible: capture gaps are recorded (ING-11, ING-14), balance checkpoints flag drift (ACC-07), and the user repairs the numbers with a balance correction (ACC-08).

Events that a crash or an error leaves in `RECEIVED`, `PROCESSING`, or `FAILED` are re-run on the next start or connect and, after repeated failure, surface in Needs Review (ING-12), so that "nothing disappears silently" also holds for pipeline faults.

Failures should be observable through capture status and Needs Review where appropriate. The app must not fabricate missing transactions or silently report a complete balance.

------------------------------------------------------------------------

## 14. Testing strategy

### 14.1 Unit tests

- Amount and currency parsing, including Indonesian and English number formats (`Rp50.000`, `Rp 1.250.000,50`, `IDR 50,000.00`), ambiguous separators, and a guard against thousand-fold misreads.
- Direction and transaction type classification.
- Timestamp parsing and timezone conversion, including the `posted_at` fallback.
- Provider parser fixtures.
- Normalization and validation.
- Deduplication and idempotency.
- Transfer matching.
- User-defined rule evaluation.
- Manual transfer pairing and exclusion from totals.
- Category rule matching and precedence.
- Transfer pair validation and fee derivation, including that balances are never changed twice.
- Aggregate rules for transfers, refunds, adjustments, voided transactions, and uncategorized transactions.
- Balance checkpoint comparison: consecutive reports, unordered or same-time reports, and accounts without an initial balance.
- Account hint resolution, including ambiguous and unmapped accounts.
- Parsing of failed attempts and refunds or reversals.
- Backup container: key derivation, encryption, and format version round trip.
- Event recovery: events stuck in `RECEIVED`, `PROCESSING`, or `FAILED`, attempt counting, and escalation to Needs Review.
- Unsupported source: no parser leads to `IGNORED` with `NO_PARSER`, the payload is purged, and the counter increases.
- Classification table: every row of section 4.6 as an aggregate test.
- Heartbeat gap detection, including the threshold.

### 14.2 Integration tests

- Notification event → parser → normalized transaction → Room.
- Incomplete event → Needs Review.
- Duplicate event → no duplicate transaction.
- User correction → saved transaction with its original captured values preserved.
- Database migrations.
- Encryption initialization and failure handling.
- Catch-up scan of active notifications → no duplicate transactions.
- Full backup → restore on a fresh install gives logically identical data, and a catch-up scan afterward creates no duplicate transactions.
- Restore → source mappings, allowlist, base currency, and settings are back, and every source shows Attention until Notification Access is granted again.
- Process killed in the middle of the pipeline → the event is re-run at the next start without creating a duplicate.
- Restore with a wrong passphrase, a damaged file, or a newer format version → refused, and no partial data is written.
- Edit and void of a captured transaction → balances, totals, transfer pairs, and original values stay consistent.
- Balance correction → an adjustment that changes the estimate but not the totals.
- Verification window → the payload of a saved transaction is present inside the window and cleared after it.
- Encrypted database reopens after reboot; missing or invalid key → defined locked state, no silent new database.
- Updated, re-posted, and retried provider notifications → no duplicate transactions (per provider, on device).

### 14.3 Device/lifecycle tests

- Notification permission enabled and disabled.
- Listener disconnected and reconnected.
- Device reboot.
- Process killed.
- Battery restrictions.
- Grouped notifications.
- Hidden notification content.
- Multiple rapid notifications.
- App update and parser compatibility.
- Listener rebind after the system disconnects it.
- Capture gap recorded when Notification Access is revoked and granted again.
- Heartbeat: process killed by battery saver → a possible gap is recorded at the next start.
- Android 15 or later: a notification containing a code-like value arrives with hidden content → Needs Review, no crash, no guessed fields.
- Notification key, ID, and tag behavior on update, re-post, and retry, recorded per provider.

Use synthetic or anonymized fixtures. Do not commit real financial notification payloads, account numbers, or personal transaction histories to source control.

------------------------------------------------------------------------

## 15. Risks and mitigations

| Risk                                                | Impact                                                      | Mitigation                                                                                                               |
|:----------------------------------------------------|:------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------|
| Provider changes notification format                | Parser stops working or misreads data                       | Versioned parsers, fixtures, validation, Needs Review                                                                    |
| Provider does not send transaction notifications    | Transactions are not captured                               | Be transparent; support manual entry and future permitted sources                                                        |
| Notification permission revoked                     | Capture stops                                               | Visible capture status and recovery instructions                                                                         |
| Notification content is hidden                      | Incomplete data                                             | Needs Review; never guess critical fields                                                                                |
| Duplicate notifications                             | Inflated totals                                             | Idempotency and composite deduplication                                                                                  |
| False transfer match                                | Incorrect analytics                                         | Confidence-based matching and user confirmation                                                                          |
| Raw financial data leaks into logs                  | Privacy incident                                            | Data minimization, redaction, retention limits                                                                           |
| Database encryption misconfigured                   | Financial data exposed                                      | Maintained encryption library, Keystore-protected keys, tests                                                            |
| Background restrictions                             | Delayed or missed processing                                | Lightweight listener path, rebind and catch-up scan, onboarding guidance, lifecycle tests, status indicators             |
| Device loss or reset                                | Permanent loss of history that cannot be re-captured        | User-initiated encrypted full backup and restore (MUST); reminders and verification; automatic backup disabled by design |
| Transfers double-counted                            | Income and expenses are overstated                          | Manual transfer pairing in the MVP; automatic suggestions later                                                          |
| Missed transactions go unnoticed                    | Balances and totals drift without the user knowing          | Capture-gap tracking, balance checkpoints, balance correction (ING-11, ACC-07 to ACC-09)                                 |
| Parser reads a notification wrongly but confidently | Wrong amounts or directions are saved silently              | Verification window with source view, balance checkpoint, edit and void with original values kept, parser fixtures                  |
| Backup forgotten, or passphrase lost                | History is lost with the device or the backup is unreadable | Reminders, backup verification, optional scheduled backup, explicit passphrase warning                                   |
| Wrong categorization rule                           | Persistent miscategorization                                | Rules never override a user-set category; rules list with disable and delete; Uncategorized stays visible                |
| Refunds, failed payments, or fees distort totals    | Spending is over- or understated                            | Explicit rules in sections 4.6, 8.2, and 8.3                                                                             |
| One payment announced by two apps | Spending is counted twice | Observed in M3a; the user voids one or pairs them; a rule is written from the evidence (section 11) |
| Amount read with the wrong number format | Amount off by a factor of 1000 and not caught by Needs Review | Provider-specific locale rules, ambiguity goes to Needs Review, format tests (PAR-12) |
| Capture stops without a trace | Missed events nobody sees | Event recovery (ING-12), heartbeat gaps (ING-14), capture gaps (ING-11) |
| The system hides notification content (Android 15 and later) | Incomplete data | Hidden-content path to Needs Review (ING-06), tested on the target devices (section 14.3) |
| Gate G1 judged by feel | The whole product rests on an unmeasured assumption | Measurable criteria written down before measuring (section 17) |

------------------------------------------------------------------------

## 16. MVP acceptance criteria

The MVP is acceptable when all of the following are true:

1.  The user can create and manage financial accounts, including cash accounts.
2.  The user can grant Notification Access and select allowed source apps.
3.  An event from an allowlisted supported app reaches the ingestion pipeline.
4.  At least two provider parsers are integrated, based on verified real-device notification formats.
5.  Parsed events are normalized into the canonical transaction model.
6.  Duplicate processing is idempotent, including after a listener reconnect catch-up scan, and event identity is verified against real provider update and re-post behavior.
7.  Likely transactions that cannot be parsed safely appear in Needs Review.
8.  The user can correct and save a review item, or retry or ignore it.
9.  The user can add a manual transaction.
10. The user can link an internal transfer pair, and confirmed pairs are excluded from income and expense totals. A fee difference is reported as a separate expense without changing balances.
11. Transactions persist locally and remain available offline.
12. Dashboard, accounts, activity, and review screens work.
13. Financial data at rest is encrypted from the first schema version using a verified implementation, with key material protected by Keystore. Key-missing and key-invalid behavior is defined and tested.
14. Raw event payloads and logs follow retention rules: payloads of ignored events are purged immediately, payloads of saved transactions after the verification window, and Needs Review payloads after resolution or 30 days.
15. The user can create an encrypted full backup and restore it on a fresh install, getting back accounts, source mappings, the allowlist, the base currency, settings, and transactions; capture resumes once Notification Access is granted again. A wrong passphrase or a damaged file restores nothing, a catch-up scan after a restore creates no duplicates, and automatic Android backup is disabled (DATA-05, DATA-08 to DATA-10). The CSV export (DATA-11) is SHOULD.
16. Permission revocation, reboot, process death, listener reconnect, and other lifecycle cases are tested.
17. The UI clearly communicates capture limitations and estimated balances.
18. The user can correct an account balance; the difference is recorded as an adjustment that changes the estimated balance but not income, expense, or cash-flow totals (ACC-08).
19. Setting a category with "Remember for this merchant" categorizes the next matching transaction automatically, a user-set category is never overwritten, rules can be disabled and deleted, and uncategorized transactions are visible (CAT-01 to CAT-03, CAT-05).
20. A captured transaction can be edited (amount, account, direction, type, date) with the original captured values preserved and can be voided; voided transactions are excluded from totals, balances, and pairs, and an edit that would invalidate a transfer pair is blocked (TX-03, TX-08).
21. Failed payment attempts create no transaction, and refunds and reversals are netted against spending rather than counted as income (PAR-09, PAR-10).
22. If kept at Gate G1: when one source serves several accounts, the account is resolved from the account hint, and an ambiguous account goes to Needs Review (ACC-05, PAR-02).
23. If kept at Gate G1: credit card accounts follow the signed-ledger rule, and a card payment pairs with its source transaction as a transfer (ACC-10).
24. An accepted event is never stuck unseen: events left in `RECEIVED`, `PROCESSING`, or `FAILED` are re-run at the next start and, after repeated failure, appear in Needs Review (ING-12).
25. An allowlisted source without a parser creates no Needs Review items; its unparsed notifications are counted and visible (ING-13).
26. Parser fixtures cover Indonesian and English notification text and number formats, and an ambiguous amount goes to Needs Review (PAR-12).

------------------------------------------------------------------------

## 17. Implementation roadmap

Estimates are for one developer working full time, in working days. They now include a provider-discovery step, because notification investigation is the main schedule risk. They still exclude unexpected Android or OEM behavior. For part-time work, convert to calendar time accordingly.

| Milestone                          | Scope                                                                                                                                                                                                                                                    | Estimate                |
|:-----------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:------------------------|
| M0: Foundation                     | Project setup, three-module structure, DI, theme, lint and test setup                                                                                                                                                                                    | 2--3 days               |
| M1: Data foundation and encryption | Entities (including TransferMatch), DAOs, migrations, repositories, use cases, encrypted database from the first schema (ADR-005); includes the 1.3 columns on core entities (sections 7.1 and 7.3)                                                      | 6--9 days               |
| M2: Notification ingestion         | Listener with rebind and catch-up scan, allowlist, event inbox, parser registry, pipeline, review handling, capture status, event recovery (ING-12), unsupported-source rule (ING-13)                                                                                                                               | 6--9 days               |
| M3a: Provider discovery            | Extend PoC captures to all transaction types (incoming, outgoing, failed, refund or reversal, hidden content); verify package IDs and formats, including whether notifications identify the account, their language and number format, and whether one payment is announced by two apps; record notification key/ID/tag behavior; anonymized fixtures; run the Gate G1 measurement window                                 | 3--5 days               |
| Gate G1                            | Go/no-go on notification quality (see below)                                                                                                                                                                                                             | --                      |
| M3b: Initial providers             | Implement two parsers, normalization, deduplication, parser tests; failed-attempt, refund, account-hint, and balance extraction; locale-aware number and date parsing (PAR-12)                                                                                                                          | 5--8 days               |
| M3c: Data model completion         | AccountSource and account resolution, capture-gap recording and heartbeat (ING-14), original-value snapshot, merchant keys and category rules, balance checkpoint and adjustment logic, aggregate rules and the classification table (transfers and derived fee rows, refunds, adjustments, voided), edit and void use cases | 6--10 days              |
| M4: UI                             | Onboarding with battery guidance, dashboard, accounts with balance correction, activity and details with edit and void, review, manual entry, transfer linking with fee, categorization (remember, uncategorized, rules list), basic analytics, settings | 13--19 days             |
| M5: Reliability and data safety    | Retention job with verification window, encrypted full backup and restore, CSV export (SHOULD), reminders and verification, backup rules, lifecycle and regression tests                                                                                          | 9--14 days              |
| **Total**                          | **MVP implementation estimate**                                                                                                                                                                                                                          | **50--77 working days** |

**Gate G1 (go/no-go), after M3a and the ingestion proof of concept.** The question is whether real notifications from the intended providers carry enough information to be parsed reliably. In 1.4 that is measured instead of judged by feel.

*Procedure.* Run capture on the owner's own phone for at least 7 and preferably 14 consecutive days, for each of the two intended providers. At the end, compare the provider's own statement (the transaction history in its app) with what was captured, transaction by transaction, and record the result in the evidence log (Appendix C). The sample needs at least 30 transactions per provider; if the period yields fewer, extend it.

*Measures.* (1) Coverage: statement transactions that have a usable notification, divided by all statement transactions. A notification is usable when amount, direction, and a time (its post time is enough) can be read from it. (2) Completeness: usable notifications, divided by transaction-type notifications captured. (3) Correctness: wrong amounts or directions found when the parsed fixtures are checked against the statement. (4) Account identifiability, where one source serves several accounts. (5) Duplicates and updates observed, and how each provider behaves (section 8.1).

*Thresholds (proposed; the owner sets the final numbers and writes them into ADR-003 before the measurement window starts).* Coverage of at least 90%, completeness of at least 95%, and no unexplained wrong amount in the sample. Numbers chosen after seeing the data defeat the purpose of the gate.

*Outcomes.* **Go:** every provider meets its thresholds, and M3b to M5 (including M3c) are re-estimated from the proof-of-concept evidence. **Re-scope:** a provider misses coverage but not completeness (it sends fewer notifications), so it stays as a partly automatic source with manual entry or is replaced by another provider; or a provider misses completeness, so the choice is a different provider, email ingestion, or manual-first. **No-go:** neither provider meets its thresholds, so work after M3a stops and is re-planned before more is spent on M3c, M4, and M5. The measurement window is calendar time that runs while M3a work continues; it adds no working days of its own.

**Scope decisions at Gate G1.** Together with the go/no-go, the owner decides from the M3a evidence: (a) whether any source serves more than one of the owner's accounts (if not, the multi-account part of ACC-05, the hint resolution in PAR-02, REV-07, and criterion 22 are deferred, and the schema keeps the hint column); (b) whether the owner uses a credit card (if not, ACC-10 and criterion 23 are deferred); (c) the rule for a payment announced by two apps (section 11).

**Effect of 1.3 on the schedule.** The 1.3 additions raise the estimate from 34--54 to 50--77 working days. Everything before Gate G1 (M0 to M3a) grows by one day at most, so the proof of concept and the go/no-go decision are not delayed; the new data-model work is collected in M3c, after G1. These figures are rough and are re-estimated at G1. If the total has to come down, the SHOULD and COULD items added in 1.3 are the first candidates to defer: ACC-07, ACC-09, ING-11, DASH-09, REV-07, TX-09, CAT-04, DATA-12, DATA-13, DATA-14, and PAR-11. The backlog marks the tasks that cover only such items.

**Effect of 1.4 on the schedule.** 1.4 adds ING-12 to ING-14, PAR-12, the classification table, and the measurable G1, and removes the revision history (TX-03), the separate payload-encryption layer (section 9.2), and the CSV export (DATA-11) and credit cards (ACC-10) from the MUST set. By a rough, unvalidated estimate the additions (about 3 to 4 days, mostly in M2, M3b, and M3c) and the removals (about 3 to 5 days across M1, M3c, and M5) cancel out, so the ranges in the table are unchanged; they are re-estimated at G1. DATA-11 and ACC-10 join the list of first candidates to defer. The PRD is frozen for the proof of concept: further feedback goes to the 1.5 list in Appendix B, not into this version.

### 17.1 Recommended first engineering sprint: ingestion proof of concept

Do not begin by polishing the dashboard. First prove that the core product can capture, parse, and safely store real notifications from the two financial apps the user actually uses. Real notifications are used, so there is no plaintext storage even in the proof of concept.

1.  Create the Kotlin Android project (`app`, `integration:api`, `integration:providers`). Write the Gate G1 thresholds into ADR-003 before any measured capture starts (section 17), and fix `minSdk`, `targetSdk`, and the test devices there (section 5).
2.  Accept ADR-005 and set up the encrypted Room database. Verify that it reopens after a reboot and that a missing or invalid key leads to a defined locked state (section 9.2).
3.  Implement `NotificationListenerService` with rebind and package allowlisting.
4.  Capture real notifications from two financial apps. Record the available fields, hidden or grouped content, and how each app behaves on update, re-post, and retry. Store `posted_at` and the notification key, ID, and tag with every event.
5.  Build one provider parser, plus anonymized fixtures for both apps covering incoming, outgoing, failed, refund or reversal, and hidden-content cases, and record whether each notification identifies the account. Note each notification's language and number format.
6.  Implement event identity and deduplication; test them with real duplicates, updates, and re-posts.
7.  Route unparseable or incomplete events to `NEEDS_REVIEW` status (no review screen yet). Re-run events left unfinished at app start (ING-12).
8.  Display captured transactions and the pending review count in a basic Compose screen.
9.  Record the findings per provider in the evidence log (Appendix C); this is the input to Gate G1.

**Sprint success condition:** notifications from both apps are received, identified, and stored encrypted; at least one provider's notifications are parsed, deduplicated, and displayed without manual entry; and the findings needed for Gate G1 are recorded.

------------------------------------------------------------------------

## 18. Initial engineering backlog

Backlog IDs use their own prefixes so they cannot be confused with requirement IDs. The Covers column maps each task to the requirements it satisfies. Tasks marked (SHOULD) or (COULD) cover only requirements of that priority and can be deferred without affecting MVP acceptance.

| ID       | Task                                                               | Outcome                                                                                                   | Covers                               |
|:---------|:-------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------|:-------------------------------------|
| CORE-001 | Initialize Android project                                         | Buildable Kotlin/Compose app                                                                              | --                                   |
| CORE-002 | Set up the three-module structure and package boundaries           | Enforced architecture boundaries                                                                          | Section 12                           |
| CORE-003 | Configure dependency injection                                     | Hilt setup                                                                                                | --                                   |
| SEC-001  | Implement database encryption (ADR-005) before any schema work     | Encrypted local data from the first schema                                                                | DATA-03, DATA-04                     |
| DB-001 | Create account schema (with `initial_balance_at`) | Account persistence | ACC-01 to ACC-05 |
| DB-002 | Create transaction schema | Canonical transaction persistence, including provider reference, related transaction, category source, and original values | TX-05, TX-08, section 7.1 |
| DB-003 | Create notification event schema (with review draft, post time, notification key/ID/tag, attempt count) | Event inbox, processing state, and payload purge deadline | ING-05, REV-01, ING-12, DATA-07 |
| DB-004   | Create TransferMatch schema                                        | Transfer pair persistence with derived fee                                                                | TRF-01, TRF-02, TRF-04               |
| DB-005   | Add DAOs and migrations                                            | Reliable local data access                                                                                | DATA-01, DATA-02                     |
| DB-006   | Create AccountSource schema                                        | Several accounts per source, told apart by account hint                                                   | ACC-05, PAR-02                       |
| DB-007 | Create Merchant and TransactionRule schemas | Merchant keys and category rules | CAT-02 |
| DB-008 | Create CaptureGap schema (SHOULD) | Capture-interruption intervals, including heartbeat gaps | ING-11, ING-14 |
| PIPE-001 | Implement notification listener with rebind                        | Receive Android notification events                                                                       | ING-01, ING-02                       |
| PIPE-002 | Implement allowlist                                                | Filter by user-selected package                                                                           | ING-03, ING-04                       |
| PIPE-003 | Implement event inbox                                              | Persist event and payload until processed                                                                 | ING-05, ING-06                       |
| PIPE-004 | Implement parser registry                                          | Resolve provider parsers                                                                                  | PAR-01, PAR-08                       |
| PIPE-005 | Implement normalizer and validator with locale-aware number and date parsing | Canonical transaction data; ambiguous formats go to Needs Review | PAR-03 to PAR-05, PAR-12 |
| PIPE-006 | Implement deduplication                                            | Idempotent processing                                                                                     | Section 8.1                          |
| PIPE-007 | Implement review handler                                           | Recover incomplete parsing                                                                                | PAR-07, REV-01 to REV-04             |
| PIPE-008 | Implement capture status and catch-up scan                         | Status labels; no gaps after reconnect                                                                    | ING-07, ING-09, ACC-06               |
| PIPE-009 | Implement account resolver using account hints                     | Correct account per event; ambiguity goes to Needs Review                                                 | PAR-02, ACC-05, REV-07               |
| PIPE-010 | Handle failed attempts and refunds in the pipeline                 | No transaction for failed attempts; refunds typed and linked                                              | PAR-09, PAR-10, TX-09                |
| PIPE-011 | Record capture gaps and heartbeat gaps (SHOULD) | Intervals in which capture was interrupted or may have been | ING-11, ING-14, ACC-09 |
| PIPE-012 | Implement balance checkpoint comparison (SHOULD) | Possible-gap flag per account, from changes between consecutive reported balances | ACC-07, ACC-09 |
| PIPE-013 | Implement merchant key normalization and category rule engine      | Automatic categorization from user rules                                                                  | CAT-02, CAT-05, section 4.9          |
| PIPE-014 | Implement re-parse of retained events (COULD)                      | Parser-fix proposals that never overwrite user edits                                                      | PAR-11                               |
| PIPE-015 | Implement event recovery and unsupported-source handling | Unfinished events are re-run and escalated after repeated failure; sources without a parser are counted, not reviewed | ING-12, ING-13 |
| DOM-001 | Implement aggregate rules | Totals follow the classification table and derived fee rows (transfers, fees, refunds, adjustments, voided) | Section 4.6, TRF-02, TRF-04 |
| DOM-002  | Implement transfer pair validation and fee derivation              | Valid pairs and a stored fee; balances never changed twice                                                | TRF-01, TRF-04                       |
| DOM-003  | Implement balance correction use case                              | Adjustment excluded from totals                                                                           | ACC-08                               |
| DOM-004 | Implement edit and void use cases | Edits that keep original values, void semantics, pair re-evaluation | TX-03, TX-08 |
| DOM-005  | Seed default categories                                            | Default taxonomy with Uncategorized and Fees                                                              | CAT-01                               |
| INT-001  | Capture and anonymize real-device fixtures                         | Verified notification formats                                                                             | Section 11                           |
| INT-002  | Implement first provider parser                                    | First verified provider integration                                                                       | PAR-02, PAR-06                       |
| INT-003  | Implement second provider parser                                   | Second verified provider integration                                                                      | PAR-02, PAR-06                       |
| UI-001   | Build onboarding with battery guidance                             | Permission, source setup, and guidance                                                                    | ING-02, ING-03, ING-08, ING-10       |
| UI-002   | Build dashboard                                                    | Financial overview, unreconciled difference, backup reminder                                              | DASH-01 to DASH-04, DASH-09, DATA-12 |
| UI-003   | Build accounts screen                                              | Account management, status, and balance correction                                                        | ACC-01 to ACC-06, ACC-08 to ACC-10   |
| UI-004 | Build activity screen with search, filters, detail, edit, and void | Transaction history, edits with original values shown, and void | TX-01 to TX-05, TX-08, TX-09, CAT-03 |
| UI-005   | Build Needs Review screen                                          | Manual correction flow, including account choice                                                          | REV-02 to REV-04, REV-07             |
| UI-006   | Build manual transaction entry                                     | Cash and unsupported sources                                                                              | TX-07                                |
| UI-007   | Build transfer pair linking                                        | Manual transfers with derived fee, excluded from totals                                                   | TRF-01, TRF-02, TRF-04               |
| UI-008   | Build insights screen                                              | Category and monthly analysis, including Uncategorized                                                    | DASH-05, DASH-06, CAT-03             |
| UI-009   | Build settings screen                                              | Theme, allowlist, retention, rules list, backup, privacy                                                  | Section 10.3, CAT-05                 |
| UI-010   | Build categorization UI                                            | Remember for merchant, apply to existing transactions                                                     | CAT-02, CAT-04                       |
| SEC-002  | Implement retention and purge job                                  | Bounded payload storage (with verification window) and log storage                                        | DATA-07                              |
| SEC-003  | Configure backup rules                                             | Automatic backup disabled                                                                                 | DATA-08                              |
| SEC-004  | Write ADR-006 (backup format and encryption) before EXP-001        | Decided key derivation, cipher, and file format                                                           | DATA-10                              |
| EXP-001 | Implement encrypted full backup | User-controlled, passphrase-encrypted backup file, including source mappings, allowlist, and settings | DATA-05, DATA-10 |
| EXP-002 | Implement restore from full backup | Atomic recovery on a fresh install; capture resumes after Notification Access is granted again | DATA-09 |
| EXP-003  | Implement delete-data flow                                         | User-controlled deletion                                                                                  | DATA-06                              |
| EXP-004 | Implement CSV export (SHOULD) | Portable analysis export with warning | DATA-11 |
| EXP-005  | Implement backup reminders and verification (SHOULD)               | Backups are made and known to be readable                                                                 | DATA-12, DATA-13                     |
| EXP-006  | Implement scheduled backup to a chosen folder (COULD)              | Off-device copy without a backend                                                                         | DATA-14                              |
| TEST-001 | Add parser unit tests, including Indonesian and English number formats | Parser regression coverage | PAR-01 to PAR-10, PAR-12 |
| TEST-002 | Add ingestion integration tests                                    | End-to-end pipeline coverage                                                                              | Section 14.2                         |
| TEST-003 | Run device and lifecycle tests                                     | Verified reliability cases                                                                                | Section 14.3                         |
| TEST-004 | Verify event identity (including notification key/ID/tag) and key-failure behavior on real devices | Evidence for dedup and encryption decisions | Sections 8.1, 9.2 |
| TEST-005 | Test aggregates and fee invariants                                 | Balances and totals never count an amount twice                                                           | Section 4.6, TRF-04                  |
| TEST-006 | Test backup round trip and restore failure cases | Lossless restore; source mappings and settings restored; no duplicates after catch-up; failed restore writes nothing | DATA-05, DATA-09, DATA-10 |
| TEST-007 | Run the Gate G1 measurement and keep the evidence log | Coverage, completeness, and correctness per provider, against thresholds fixed beforehand | Section 17, Appendix C |

------------------------------------------------------------------------

## 19. Product decisions and open decisions

### Confirmed decisions

- Android application.
- Local-first storage and processing for the MVP.
- Personal use as the initial target, with future product potential.
- No mandatory backend.
- Short, modern product name is preferred; final name is not selected yet.
- Adaptive theme: system default with manual light/dark override.
- Notification capture is restricted to apps explicitly selected by the user.
- Failed or uncertain parsing goes to Needs Review.
- Notification-based ingestion is the primary MVP strategy.
- No bank credential collection or Accessibility-based screen scraping.
- Needs Review items are notification events with an optional draft; a transaction is created only on confirmation.
- Manual transaction entry and manual transfer pairing are part of the MVP.
- Database encryption is enabled from the first schema version.
- Automatic Android backup is disabled; a user-initiated, encrypted full backup is the supported backup path.
- The MVP aggregates in a single base currency.

### Superseded decisions

- *Raw payloads are purged after successful processing* (confirmed in 1.1 and 1.2) is replaced by the verification window proposed in 1.3 and carried into 1.4: payloads of ignored events are purged immediately, payloads of saved transactions after the window (proposed 7 days, configurable from 0 to 30), and Needs Review payloads after resolution or 30 days (section 9.3). The window itself is still to be confirmed in the next list.

### Proposed in 1.3 (confirm or change)

- Adjustments (ACC-08) are excluded from income, expense, and cash-flow totals and are shown as an unreconciled difference.
- Refunds and reversals are netted against spending, not counted as income.
- A transfer fee is derived from the difference between the two legs and is not a separate transaction.
- Payloads of saved transactions are kept, inside the encrypted database, for a 7-day verification window, configurable from 0 to 30 days.
- Full backups exclude raw payloads and Needs Review items.
- Full backups are always encrypted with a user passphrase; the CSV export is the only unencrypted export.
- Backup reminder thresholds: 14 days or 100 new transactions.
- Categories are learned from the user's own corrections through local rules; there is no global model.
- Captured transactions are voided, never deleted.
- Credit card accounts use the signed ledger, and card payments are internal transfers.
- Providers that announce a payment in stages are handled per provider from the M3a evidence.

### Proposed in 1.4 (confirm or change)

- Direction is `INFLOW` or `OUTFLOW`, and the classification table in section 4.6 is the single rule for how each combination is reported, including the new `CASH` rows (an unpaired cash withdrawal is an expense in "Cash withdrawal", an unpaired cash deposit is income).
- A transfer fee belongs to the account and date of the outgoing leg and is shown as a read-only derived row.
- An allowlisted source without a parser produces `IGNORED` events (reason `NO_PARSER`), not Needs Review items; they are counted and shown.
- Unfinished events are re-run at start and connect; after 3 failed attempts they go to Needs Review with `PIPELINE_ERROR`.
- A heartbeat gap is recorded after 3 hours without a heartbeat (configurable).
- Gate G1 uses the measures and proposed thresholds in section 17 (coverage 90%, completeness 95%, no unexplained wrong amount; 7 to 14 days; at least 30 transactions per provider); the owner sets the final numbers before measuring.
- Original captured values replace the full revision history, and the separate payload-encryption layer is dropped.
- CSV export (DATA-11) and credit-card accounts (ACC-10) are SHOULD; multi-account per source is decided at Gate G1.
- The backup includes source mappings, the allowlist, the base currency, settings, and capture gaps; Notification Access has to be granted again after a restore.

### Decisions to make during implementation

- Final product name and visual identity.
- Which two financial providers to support first, based on actual installed apps and verified notification formats.
- Confirm the database encryption library (SQLCipher is proposed) and the key lifecycle design in ADR-005, before the first persistence step of the proof of concept.
- Backup container details: key-derivation parameters, cipher, and file layout (direction decided in 1.3: encrypted, versioned JSON full backup plus CSV export; ADR-006).
- Final retention defaults and user controls.
- Exact category taxonomy and default categories.
- `minSdk`, `targetSdk`, and the test devices (ADR-003, before M0).
- The Gate G1 thresholds (ADR-003, before any measured capture).
- The rule for a payment announced by two apps (from the M3a evidence).
- The storage exponent for IDR (ADR-004): ISO 4217 lists two decimals, although notifications usually show none.

------------------------------------------------------------------------

## 20. Key limitations to communicate to users

This product aggregates available transaction events; it is not a direct connection to a bank's ledger.

- It cannot retrieve historical transactions that occurred before capture was configured unless another supported import source is added.
- It cannot capture transactions for which the provider sends no usable notification.
- It may miss events when Notification Access is disabled, the service is unavailable, or the notification hides its content.
- A catch-up scan can only recover notifications that are still in the notification drawer.
- Provider updates may break parsers and require maintenance.
- Estimated account balances may differ from actual balances.
- A successful notification capture does not prove that every transaction has been captured.
- Data lives only on this device. Without a full backup, it cannot be recovered after loss, reset, or replacement of the device. A backup is only as recent as the last time the user made one, and a lost backup passphrase cannot be recovered.
- A balance correction records an unexplained difference. It does not recover the missed transactions or their categories.
- Automatic categories reflect the user's own earlier choices and can be wrong; rules can be changed or deleted.
- A payment funded from one provider and announced by another may appear twice; the user can void the extra one.
- Capture gaps caused by a killed process are only estimated (heartbeat), and the same flag can appear when the phone was simply switched off.
- After a restore, capture does not resume until Notification Access is granted again.

The product should make these limitations visible rather than hiding them behind a misleading "connected" status.

------------------------------------------------------------------------

## Appendix A: Initial technical notes

### Suggested processing statuses

For `NotificationEvent`:

- `RECEIVED`
- `PROCESSING`
- `PROCESSED`
- `NEEDS_REVIEW`
- `IGNORED`
- `FAILED` (the last attempt raised an error; it will be retried, ING-12)

Transitions for `NotificationEvent`:

- `RECEIVED` to `PROCESSING` when the pipeline takes the event.
- `PROCESSING` to `PROCESSED`, `NEEDS_REVIEW`, or `IGNORED` (reason such as `PROMOTION` or `NO_PARSER`) in the same database transaction that saves the result.
- `PROCESSING` to `FAILED` when the pipeline raises an error; `attempt_count` increases.
- `RECEIVED`, `PROCESSING`, or `FAILED` are re-run on app start and listener connect (ING-12); when `attempt_count` reaches the limit (proposed 3), the event goes to `NEEDS_REVIEW` with reason `PIPELINE_ERROR`.
- `NEEDS_REVIEW` to `PROCESSED` (the user saves), `IGNORED` (the user ignores), or back to `PROCESSING` (retry).

For `Transaction`:

- `CONFIRMED`
- `VOIDED` (set by the user, TX-08; excluded from totals, balances, and transfer pairs)

Keep event-processing status separate from transaction business status. Review items live on `NotificationEvent` (`NEEDS_REVIEW`); `Transaction` has no pending state.

### Suggested indexes and constraints

- Unique event/source identifier where the provider exposes a stable ID.
- Index `Transaction.account_id`.
- Index `Transaction.occurred_at`.
- Index `Transaction.category_id`.
- Index `Transaction.fingerprint`.
- Index `NotificationEvent.processing_status`.
- Foreign keys for account, category, merchant, and event relationships.
- Use database transactions when saving an event and its resulting transaction/review state.
- A transaction belongs to at most one confirmed `TransferMatch`; index both transaction references.
- Index `Transaction.provider_reference`.
- Index `Transaction.related_transaction_id`.
- Unique `Merchant.key`.
- Unique (`AccountSource.source_id`, `AccountSource.account_hint`); at most one account per source may have an empty hint.
- Index `NotificationEvent.purge_after`.
- Index `NotificationEvent.notification_key` (non-unique, for the key/ID evidence in section 8.1).
- Check `TransferMatch.fee_minor >= 0`.

### Architecture decision records to create

- `ADR-001-modular-architecture.md`
- `ADR-002-local-first-storage.md`
- `ADR-003-notification-ingestion.md`
- `ADR-004-transaction-normalization.md`
- `ADR-005-local-database-encryption.md`
- `ADR-006-backup-format-and-encryption.md`

Each ADR should record the context, decision, alternatives considered, consequences, and date. ADR-004 is updated to record the refund, fee, adjustment, and failed-attempt rules (sections 4.6, 8.2, and 8.3), the classification table, and the currency-exponent decision. ADR-003 also records `minSdk`, `targetSdk`, the test devices, and the Gate G1 thresholds (written before measurement begins).

## Appendix B: Change log

**Version 1.4 (from 1.3)**

Fixes to 1.3

- Section 19 no longer holds two rules for payload retention: the old decision is listed as superseded and the verification window is the rule (sections 9.3, 19).
- The full backup now includes account-to-source mappings, the allowlist, the base currency, settings, and capture gaps; a restore ends at the step where Notification Access is granted again (DATA-05, sections 4.7, 9.4, acceptance criterion 15).
- The balance checkpoint compares changes between consecutive reported balances, so it works without an initial balance; accounts gain `initial_balance_at` (ACC-04, ACC-07, section 7.3).
- The time fallback uses the notification's post time (`posted_at`), not the time the event was received (PAR-12, sections 6.2, 6.3, 7.3).
- Events stuck in `RECEIVED`, `PROCESSING`, or `FAILED` are re-run, and escalate to Needs Review after repeated failure (ING-12, Appendix A).
- Direction is `INFLOW`/`OUTFLOW`, `TRANSFER` and `CASH` are defined, and one classification table says how each combination is reported (sections 4.6, 6.3, 8.2).
- A transfer fee belongs to the outgoing leg's account and date and appears as a read-only derived row, so category totals match their listed rows (TRF-04, section 4.6).
- The behavior of an allowlisted source without a parser is defined: ignored and counted, not sent to Needs Review (ING-13).

Additions

- Gate G1 has a procedure, five measures, proposed thresholds, and three outcomes, plus an evidence log template (section 17, Appendix C).
- Locale-aware number and date parsing, with Indonesian and English fixtures and ambiguity routed to Needs Review (PAR-12, sections 11, 14.1).
- Android's notification key, ID, and tag are recorded from the first proof-of-concept build (sections 7.3, 8.1, 17.1).
- A heartbeat estimates capture gaps the app cannot observe (ING-14).
- `minSdk`, `targetSdk`, and the test devices are decided in ADR-003; the Android 15 redaction of notifications that contain one-time codes is a named test (sections 5, 14.3).
- A payment announced by two apps is observed in M3a and ruled on from evidence; sample collection for fixtures is defined (section 11).

Cuts

- Full revision history (`TransactionRevision`) is replaced by `original_json` on the transaction (TX-03, sections 7.1 to 7.3).
- The separate payload-encryption layer is dropped; the database encryption covers payloads (section 9.2).
- CSV export (DATA-11) and credit-card accounts (ACC-10) move from MUST to SHOULD; multi-account per source and the credit-card rules are confirmed or dropped at Gate G1 (section 17).

Planning

- Roadmap ranges are unchanged because additions and removals roughly cancel out; the PRD is frozen for the proof of concept (section 17).
- Acceptance criteria 15, 20, 22, and 23 are revised, and 24 to 26 are added (section 16). Backlog tasks are updated, with PIPE-015 and TEST-007 added (section 18).

Considered and not adopted as proposed

- Dropping CSV export entirely: it stays as SHOULD, because it serves portability and analysis rather than recovery.
- Backing up the allowlist without conditions: it is included, but Android still requires the user to grant Notification Access again, so capture never resumes by itself after a restore.

Review feedback not addressed in 1.4 (candidates for 1.5)

- Privacy: wording of section 9.1 (Notification Access is all-or-nothing at the OS level), and app lock and screen protection.
- Planning: measurable acceptance thresholds for the MVP beyond Gate G1.
- Smaller points: a visible indicator for excluded foreign-currency transactions, Google Play policy for a public release, and SMS as a fallback source for non-Play builds.

**Version 1.3 (from 1.2)**

Reconciliation and missed transactions

- Balance correction added (ACC-08, MUST): it records an adjustment that changes the estimated balance but is excluded from income, expense, and cash-flow totals. The balance checkpoint (ACC-07) is raised to SHOULD, and ACC-09, ING-11, and DASH-09 are added (sections 4.1, 4.2, 4.6, 7, 8.2, 10, 13).
- Aggregate rules for transfers, refunds, adjustments, voided, and uncategorized transactions are stated in one place (section 4.6).

Silent wrong parses

- Payloads of saved transactions are kept encrypted for a verification window (proposed 7 days) instead of being purged at once; ignored events are still purged immediately (sections 6.2, 9.3). The source text is shown in the detail view while it is retained (TX-05). Re-parse after a parser fix is added as COULD (PAR-11).
- Captured transactions can be edited and voided, with revisions for financial fields (TX-03, TX-08).

Backup

- DATA-05 is now a versioned full backup, DATA-09 is atomic and version-aware, and DATA-10 (passphrase encryption) and DATA-11 (CSV export) are MUST. DATA-12 to DATA-14 add reminders, verification, and optional scheduled backup.
- The backup includes event identity records so that a catch-up scan after a restore creates no duplicates (sections 8.1, 9.4). ADR-006 is added, and the export-format open decision is narrowed.

Categorization

- New section 4.9 (CAT-01 to CAT-05): default categories, rules learned from user corrections, visible Uncategorized, and a rules list. Transaction gains `category_source` and `applied_rule_id`, and Merchant gains a unique `key` (sections 7.1 to 7.3).

Data model rules

- Failed attempts (PAR-09), refunds and reversals (PAR-10, TX-09, `provider_reference`, `related_transaction_id`), transfer fees (TRF-04, `fee_minor`), credit cards (ACC-10), and several accounts per source (ACC-05, PAR-02, REV-07, `AccountSource`, new pipeline step 11).
- The parser contract now defines `ParsedTransaction`, `PartialTransaction`, `ReviewReason`, and `NotTransactionReason` (section 6.3).

Planning

- Roadmap re-estimated to 50--77 working days with a new M3c; M0 to M3a grow by one day at most, so Gate G1 is not delayed (section 17). Backlog extended and marked for SHOULD and COULD tasks (section 18). Acceptance criteria 10, 14, and 15 revised and 18 to 23 added (section 16). Five risks added (section 15).
- Editorial: inline lists restored as proper lists, and tables converted to pipe tables.

Review feedback not addressed in 1.3 (candidates for 1.4)

- Event identity: use the notification key and ID as extra signals, and record whether providers reuse notification IDs (section 8.1).
- Privacy: wording of section 9.1 (Notification Access is all-or-nothing at the OS level), one-time-code content from allowlisted apps, app lock and screen protection.
- Planning: too many MUST items, mapping of the proof of concept to milestones, measurable success metrics, and measurable acceptance thresholds.
- Smaller points: local number formats and time zones in parsers, a visible indicator for excluded foreign-currency transactions, target devices and OEM list, Google Play policy for a public release, and SMS as a fallback source for non-Play builds.

**Version 1.2 (from 1.1)**

- Restore (DATA-09) raised to MUST, because export without restore is not a recovery path (sections 4.7, 15, 16).
- Catch-up scan limits stated in ING-09 and section 20: it recovers only notifications still in the drawer.
- Event identity must be validated on real devices; added to section 8.1, the test plan (section 14.2), and backlog TEST-004.
- Key-failure behavior defined and tested before real data is stored (section 9.2, acceptance criterion 13).
- Proof of concept widened to two real providers, deduplication, Needs Review status, and key tests (section 17.1); M3a scope extended; re-estimate at Gate G1.
- Modules reduced from five to three: `app`, `integration:api`, `integration:providers`; domain and data are packages inside `app` (section 12).

**Version 1.1 (from 1.0)**

- Status changed from "Finalized" to "Ready for the ingestion proof of concept".
- Database encryption is decided before M1 and enabled from the first schema (sections 5, 9.2, 17).
- Manual transfer pairing is now in the MVP (new section 4.8, TRF-01 to TRF-03); automatic matching is post-MVP (section 8.3).
- Needs Review items are events with a draft, not Transactions; `PENDING_REVIEW` removed (sections 4.4, 7.3, Appendix A).
- Payloads are purged after successful processing; Needs Review payloads kept up to 30 days; REV-05 is now MUST (sections 6.2, 9.3).
- Automatic backup disabled; export is MUST; restore is SHOULD, raised to MUST in 1.2 (DATA-05, DATA-08, DATA-09, section 9.4).
- Listener reliability: rebind, catch-up scan, derived status, onboarding guidance (ING-07, ING-09, ING-10, section 13).
- Manual entry is MUST (TX-07) so cash accounts work; per-account capture status is MUST (ACC-06).
- Parser contract uses deterministic rules instead of confidence scores; `version` added and stored (section 6.3, TX-05).
- Added single base currency rule (section 4.6) and a reconciliation checkpoint (ACC-07, COULD).
- Modules reduced from about 25 to 5 (section 12).
- Roadmap re-estimated to 34--54 days, with provider discovery and Gate G1 (section 17).
- Backlog rebuilt with requirement mapping; IDs renamed to avoid clashes with requirement IDs (section 18).
- Acceptance criteria expanded from 14 to 17 (section 16).

## Appendix C: Gate G1 evidence log

One row per provider and measurement period. Fill it from the provider's own statement, not from the app's database. Coverage is D divided by A; completeness is C divided by B. The thresholds are the ones written into ADR-003 before the period started.

| Provider | Days | A: transactions in the statement | B: transaction notifications captured | C: usable (amount, direction, time) | D: statement transactions with a usable notification | Wrong amount or direction | Duplicates or updates seen | Notes |
|:---------|:-----|:---------------------------------|:--------------------------------------|:------------------------------------|:-----------------------------------------------------|:--------------------------|:---------------------------|:------|
|          |      |                                  |                                       |                                     |                                                      |                           |                            |       |
|          |      |                                  |                                       |                                     |                                                      |                           |                            |       |

------------------------------------------------------------------------

**End of document**
