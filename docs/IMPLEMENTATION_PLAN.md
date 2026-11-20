# Implementation Plan & Engineering Backlog — W-Track

**Document Status:** Master Engineering Plan (PRD v1.4 — Frozen for Ingestion PoC)  
**Total MVP Estimate:** **50–77 working days** (1 full-time developer, excludes unexpected OEM anomalies; calendar time includes 7–14 day Gate G1 capture window)  
**Last Updated:** 2026-11-21

---

## 1. Executive Phasing Overview

Development is strictly gated into **Phase 1 (Ingestion PoC & Gate G1)** and **Phase 2 (Post-Gate G1 MVP Completion)** per **PRD Section 17 & 17.1**. **Do not polish the full UI or build insights/backup screens before Gate G1 passes.**

| Phase | Milestone | Focus | Est. Effort | Exit Gate |
| :--- | :--- | :--- | :--- | :--- |
| **Phase 1 (PoC)** | **M0: Foundation** | 3-module project setup, Hilt, M3 theme, lint, architecture/test harness | 2–3 days | Builds clean; `domain` boundary test passes; ADR-003 SDK/devices locked |
| **Phase 1 (PoC)** | **M1: Data & Encryption** | SQLCipher (`sqlcipher-android`) + Keystore KEK/DEK + Room Schema v1 + repositories | 6–9 days | ADR-005 accepted; encrypted DB survives reboot; key-loss → Locked state verified |
| **Phase 1 (PoC)** | **M2: Notification Ingestion** | `NotificationListenerService`, rebind/catch-up, allowlist, inbox, pipeline, `ING-12`, `ING-13`, minimal PoC screen | 6–9 days | Real notifications from 2 apps captured into encrypted DB; stuck events recover |
| **Phase 1 (PoC)** | **M3a: Provider Discovery** | Multi-type real captures, `key`/`id`/`tag` evidence, 1 PoC parser + anonymized fixtures, start G1 window | 3–5 days (+7–14 cal. days) | At least 1 PoC parser live; anonymized fixtures for both apps; Appendix C log ready |
| **Gate G1** | **Go / No-Go Gate** | Quantitative verification vs pre-locked thresholds in ADR-003 | — | Coverage ≥ 90%, Completeness ≥ 95%, 0 unexplained wrong amounts (`Go` / `Re-scope` / `No-go`) |
| **Phase 2 (MVP)** | **M3b: Initial Providers** | Both production provider parsers, `PAR-12` locale/date parsing, dedup, refunds, failed attempts, hints | 5–8 days | Parser unit & integration suites pass across Indonesian/English & failure fixtures |
| **Phase 2 (MVP)** | **M3c: Data Completion** | `AccountSource`, `CaptureGap` + heartbeat (`ING-14`), `original_json`, `Merchant`/rules, checkpoints (`ACC-07`), aggregate engine, edit/void | 6–10 days | Aggregate 16-row table, derived transfer fees, edits, voids & adjustments 100% tested |
| **Phase 2 (MVP)** | **M4: Full UI** | Onboarding + battery guidance, Dashboard, Accounts + reconciliation, Activity + edit/void/transfers, Needs Review, Rules, Insights, Settings | 13–19 days | All MVP screens wired via `domain` contracts with accessible M3 light/dark themes |
| **Phase 2 (MVP)** | **M5: Reliability & Safety** | Retention purge (`SEC-002`), encrypted backup & atomic restore (`ADR-006`), CSV export (`SHOULD`), reminders (`SHOULD`), device lifecycle tests | 9–14 days | All 26 MVP Acceptance Criteria verified; backup round-trip & lifecycle suite pass |
| **Total** | **M0 – M5** | **Complete MVP** | **50–77 days** | **MVP Acceptance Criteria 1–26 satisfied** |

---

## 2. First PoC Milestone Breakdown (`PRD Section 17.1`)

**Objective:** Prove that real notifications from the owner's initial financial app targets (**1. Bank Jago**, **2. DANA**, **3. BCA**, tracked in [PROVIDERS.md](file:///home/adomancer/Documents/project/W-Track/docs/PROVIDERS.md)) can be safely captured, stored encrypted, parsed incrementally (starting with Priority 1 Bank Jago after ingestion foundation is ready), deduplicated, recovered across failures, and measured objectively at Gate G1 — **without polishing full dashboard UI** and **without delaying M0 or M1**.

### Step-by-Step PoC Execution Checklist (M0 → Gate G1)
1. **Pre-M0 Gate:** Owner resolves `compileSdk = 36` vs `37` GA alignment (keeping `minSdk = 26`, `targetSdk = 36`, no Android 17 beta) in `ADR-003` (confirmed primary test device: `vivo / iQOO Z10R (I2505)`, Android 16 / API 36), confirms `IDR` exponent `2` (ISO 4217) in `ADR-004`, and confirms final acceptance of `ADR-005` (`sqlcipher-android:4.20.0` vs `4.17.0` + AFU locked-screen Keystore policy). Provider discovery does **not** block M0 or M1.
2. **M0 (`CORE-001`..`003`):** Recreate clean 3-module Gradle setup (`app`, `integration:api`, `integration:providers`, `build-logic`), Hilt, theme, detekt/lint, and `domain` purity JUnit test.
3. **M1 (`SEC-001`, `SEC-003`, `DB-001`..`005`, `TEST-004`):** Implement `DatabaseKeyManager` (`net.zetetic:sqlcipher-android` `4.20.0` or `4.17.0` per approved `compileSdk` — never SQLCipher 5 beta or EOL `4.5.4` — plus Keystore AES-GCM wrapping with `setUnlockedDeviceRequired(false)` for AFU locked-screen capture), verify locked-screen capture, reboot reopen & `LOCKED_KEY_MISSING_OR_INVALID` state **before** storing real events, and lock Room Schema v1 (`allowBackup="false"`).
4. **M2 (`PIPE-001`..`008`, `PIPE-015`):** Build provider-agnostic `WtrackNotificationListenerService` with synchronous allowlist filter, rebind, `activeNotifications` catch-up, encrypted `notification_events` inbox recording `posted_at` + `notification_key` + `notification_id` + `notification_tag`, `ING-12` startup/connect recovery, `ING-13` `NO_PARSER` ignore/count rule, and a **minimal PoC Compose verification screen** (allowlist toggle, pending Needs Review count, transaction list, capture status, locked-DB screen). Measure default battery/Doze baseline on `iQOO Z10R` **without** whitelisting first.
5. **M3a (`INT-001`, `INT-002` PoC, `TEST-007`):** Lock Gate G1 thresholds (`>=90%` coverage, `>=95%` completeness, `0` unexplained wrong amounts/directions, `7–14d`, `>=30 tx/provider`) in `ADR-003` **before** measurement starts. Verify installed package IDs (`com.jago.digitalBanking`, `id.dana`, `com.bca` vs `com.bca.mybca.omni.android`) on `iQOO Z10R`, inspect real notification structures before writing parsers, anonymize fixtures (strictly excluding OTPs/PINs/credentials), implement **Priority 1 (Bank Jago)** first followed incrementally by **DANA** and **BCA**, verify deduplication on real updates/re-posts, and populate the Appendix C / `PROVIDERS.md` evidence log.

**PoC Sprint Success Condition (`Section 17.1`):**
- Notifications from the verified target financial apps are received, identified (with `key`/`id`/`tag` + `posted_at`), and stored encrypted without hardcoding provider logic into the listener.
- Incremental provider validation starts with Bank Jago and expands across DANA and BCA, displaying parsed, deduplicated transactions without manual entry.
- Incomplete/redacted/unparseable events route to `NEEDS_REVIEW` status and recover via `ING-12` (full review UI deferred to M4).
- Gate G1 evidence log (`PROVIDERS.md` / Appendix C) is complete for go/no-go decision.

---

## 3. Complete Engineering Backlog (`PRD Section 18`)

All 45 PRD Section 18 backlog IDs are preserved verbatim with exact requirement mappings, priority labels (`MUST` / `SHOULD` / `COULD`), dependencies, acceptance criteria, tests, and definitions of done.

---

### Milestone M0: Foundation (`2–3 working days`)

#### `CORE-001` — Initialize Android Project
- **Priority:** `MUST` | **PRD Refs:** Sections 5, 12, 17.1
- **Objective:** Configure buildable multi-module Kotlin/Compose Android project (`build-logic`, `app`, `integration:api`, `integration:providers`) targeting `minSdk = 26`, `targetSdk = 36` (no Android 17 beta), and approved `compileSdk` (`36` vs `37` GA).
- **Dependencies:** Owner confirms `ADR-003` Option A (`compileSdk = 36` strict, `core-ktx:1.18.0`, `sqlcipher:4.17.0` if `4.20.0` rejects 36) vs Option B (`compileSdk = 37` GA, `targetSdk = 36`, `sqlcipher:4.20.0`, `core-ktx:1.19.1`), and AGP (`8.13.x` vs `9.0.x` built-in Kotlin).
- **Deliverables:** `settings.gradle.kts`, `gradle/libs.versions.toml`, `build-logic` convention plugins, baseline `MainActivity` & `WtrackApplication`.
- **Acceptance Criteria:** Debug APK compiles cleanly without AAR metadata suppression flags and launches on `iQOO Z10R (I2505)`; JVM unit tests run cleanly.
- **Required Tests:** Smoke launch test; Gradle build check (`./gradlew assembleDebug testDebugUnitTest`).
- **Definition of Done:** Clean build on CI/local with zero lint blockers.

#### `CORE-002` — Set Up Three-Module Structure & Package Boundaries
- **Priority:** `MUST` | **PRD Refs:** Section 12, `PAR-08`, `ADR-001`
- **Objective:** Enforce `:app` (`feature/`, `domain/`, `data/`, `core/`), `:integration:api`, and `:integration:providers` dependencies and pure-Kotlin `domain` isolation.
- **Dependencies:** `CORE-001`.
- **Deliverables:** `:integration:api` contracts (`TransactionParser`, `ParseResult`, enriched `NotificationEvent`, `ParsedTransaction`, `PartialTransaction`, `Enums`); `domain` package skeleton (`DomainTransactionType` including `ADJUSTMENT`).
- **Acceptance Criteria:** `:integration:providers` depends only on `:integration:api`; `app.domain` compiles without Android/Room/Compose/provider imports.
- **Required Tests:** Automated JUnit architecture check scanning `wtrack.app.domain` imports/bytecode.
- **Definition of Done:** Architecture boundary test passes in unit test suite.

#### `CORE-003` — Configure Dependency Injection & Base Theme
- **Priority:** `MUST` | **PRD Refs:** Sections 5, 10.1
- **Objective:** Wire Hilt DI (`@HiltAndroidApp`, `@AndroidEntryPoint`, coroutine dispatchers, clock abstraction) and base Material 3 light/dark theme.
- **Dependencies:** `CORE-001`, `CORE-002`.
- **Deliverables:** Hilt DI modules (`CoreModule`, `IntegrationModule` binding `Set<TransactionParser>`), `Theme.kt`, `Color.kt`, `Type.kt`.
- **Acceptance Criteria:** Hilt graph validates at compile time; theme switches cleanly across System/Light/Dark.
- **Required Tests:** Hilt test runner smoke test / DI graph compilation check.
- **Definition of Done:** Injectable `Clock`, `DispatcherProvider`, and `Set<TransactionParser>` resolve cleanly.

---

### Milestone M1: Data Foundation & Encryption (`6–9 working days`)

#### `SEC-001` — Implement Database Encryption Before Any Schema Work
- **Priority:** `MUST` | **PRD Refs:** `DATA-03`, `DATA-04`, Section 9.2, 17.1, `ADR-005`
- **Objective:** Implement Android Keystore AES-GCM KEK (`setUserAuthenticationRequired(false)`, `setUnlockedDeviceRequired(false)` for AFU locked-screen capture) + wrapped 256-bit DEK + `net.zetetic:sqlcipher-android` (`4.20.0` or `4.17.0` per approved SDK alignment; never 5 beta or EOL `4.5.4`) `SupportOpenHelperFactory` and startup key-failure state machine.
- **Dependencies:** `CORE-003`, **Final Owner acceptance of `ADR-005`**.
- **Deliverables:** `DatabaseKeyManager`, `DatabaseStartupState` (`FRESH_INSTALL`, `READY`, `CORRUPT_INITIAL_STATE`, `LOCKED_KEY_MISSING_OR_INVALID`), SQLCipher `SupportOpenHelperFactory` provider.
- **Acceptance Criteria:** DB file header is encrypted (not plaintext `"SQLite format 3"`); missing/invalid key enters `LOCKED_KEY_MISSING_OR_INVALID` without deleting/overwriting DB file; `arm64-v8a`/16KB native loading succeeds on `iQOO Z10R`; KEK unwraps cleanly in locked-screen AFU background context.
- **Required Tests:** Unit tests for state classification; instrumented tests (`TEST-004`) for locked-screen AFU capture, reboot + post-unlock reopen, deleted wrapped key, corrupted DEK, invalidated Keystore alias.
- **Definition of Done:** Encryption verified on disk and locked-screen/reboot/key-loss tests pass before any schema stores real events.

#### `SEC-003` — Configure Android Auto Backup Rules
- **Priority:** `MUST` | **PRD Refs:** `DATA-08`, Section 9.4, `ADR-002`
- **Objective:** Disable OS cloud backup and device-to-device transfer for encrypted DB and wrapped keys.
- **Dependencies:** `CORE-001`.
- **Deliverables:** `AndroidManifest.xml` (`allowBackup="false"`), `res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml`.
- **Acceptance Criteria:** Merged manifest sets `allowBackup="false"`; `databases/` and `files/keystore/` explicitly excluded.
- **Required Tests:** Manifest merge / APK analyzer verification.
- **Definition of Done:** Automatic backup completely disabled.

#### `DB-001` — Create Account Schema (with `initial_balance_at`)
- **Priority:** `MUST` | **PRD Refs:** `ACC-01` to `ACC-05`
- **Objective:** Implement `accounts` Room entity with opening balance timestamp check constraint and ISO 4217 minor-unit currency discipline (`IDR` exponent `2`).
- **Dependencies:** `SEC-001`, **Owner confirmation of `ADR-004` IDR exponent `2`**.
- **Deliverables:** `AccountEntity`, mappers to `domain.model.Account`, `CurrencySpec` registry.
- **Acceptance Criteria:** Enforces `initial_balance_at` whenever `initial_balance_minor` is non-null.
- **Required Tests:** Room schema + constraint unit/instrumented test.
- **Definition of Done:** Entity compiles into exported Room schema v1 JSON.

#### `DB-002` — Create Transaction Schema
- **Priority:** `MUST` | **PRD Refs:** `TX-03`, `TX-05`, `TX-08`, `TX-09`, `PAR-10`, Section 7.1
- **Objective:** Implement `transactions` Room entity with integer minor units (`IDR` exponent `2`, `1 IDR = 100 sen`), `original_json`, `provider_reference`, `related_transaction_id`, `reported_balance_minor`, and partial unique `event_id` index.
- **Dependencies:** `DB-001`, `DB-003`, `DB-007`.
- **Deliverables:** `TransactionEntity`, indexes, FKs, mappers.
- **Acceptance Criteria:** All Appendix A indexes present; `amount_minor > 0` check enforced; at most one transaction per `event_id`; formatter/parser tests prevent 100x scaling mistakes.
- **Required Tests:** Constraint, 100x minor-unit scaling, & duplicate `event_id` failure tests.
- **Definition of Done:** Exported into Room schema v1.

#### `DB-003` — Create NotificationEvent & TransactionSource Schemas
- **Priority:** `MUST` | **PRD Refs:** `ING-05`, `ING-12`, `ING-13`, `REV-01`, `DATA-07`, Section 7.3, 8.1
- **Objective:** Implement `notification_events` and `transaction_sources` entities including `posted_at`, `notification_key`, `notification_id`, `notification_tag`, `stable_event_key`, `attempt_count`, `purge_after`, `review_draft_json`, and `sample_collection_until`.
- **Dependencies:** `SEC-001`.
- **Deliverables:** `NotificationEventEntity`, `TransactionSourceEntity`, indexes (`notification_key`, `processing_status`, `purge_after`, unique `stable_event_key`).
- **Acceptance Criteria:** Captures all identity evidence fields from PoC build 1; separates `processing_status` from `Transaction`.
- **Required Tests:** Unique `stable_event_key` & nullable `payload_json` purge test.
- **Definition of Done:** Exported into Room schema v1.

#### `DB-004` — Create TransferMatch Schema
- **Priority:** `MUST` | **PRD Refs:** `TRF-01`, `TRF-02`, `TRF-04`, Appendix A
- **Objective:** Implement `transfer_matches` entity with `fee_minor >= 0` check and partial unique indexes restricting each transaction to at most one `CONFIRMED` pair.
- **Dependencies:** `DB-002`.
- **Deliverables:** `TransferMatchEntity`, partial unique indexes on `outgoing_transaction_id` & `incoming_transaction_id`.
- **Acceptance Criteria:** SQLite rejects negative `fee_minor`, self-links, and linking an already-confirmed leg.
- **Required Tests:** DB constraint test verifying `CHECK` and partial unique index behavior.
- **Definition of Done:** Exported into Room schema v1.

#### `DB-005` — Add DAOs, Migrations & Core Repositories
- **Priority:** `MUST` | **PRD Refs:** `DATA-01`, `DATA-02`, `CAT-01`
- **Objective:** Implement Room DAOs, `WtrackDatabase`, `exportSchema = true`, pre-seeded `"Uncategorized"` / `"Fees"` categories (`DOM-005`), and initial repository implementations.
- **Dependencies:** `DB-001`..`DB-004`, `DB-006`, `DB-007`.
- **Deliverables:** DAOs, `WtrackDatabase` (encrypted via `SEC-001`), schema JSON snapshot `1.json`, core repository implementations.
- **Acceptance Criteria:** Reactive `Flow` queries work offline; schema JSON committed; seed categories created on DB creation.
- **Required Tests:** Instrumented SQLCipher Room DAO & migration harness test.
- **Definition of Done:** All M1 persistence tests pass under SQLCipher.

#### `DB-006` — Create AccountSource Schema
- **Priority:** `MUST` | **PRD Refs:** `ACC-05`, `PAR-02`, Appendix A
- **Objective:** Link accounts to sources with optional `account_hint` and partial unique indexes enforcing at most one `NULL`-hint default account per source.
- **Dependencies:** `DB-001`, `DB-003`.
- **Deliverables:** `AccountSourceEntity`, partial unique indexes.
- **Acceptance Criteria:** Supports 1:1 and 1:N source-to-account mappings; rejects two empty hints on one source.
- **Required Tests:** SQLite index test with duplicate `NULL` vs distinct non-null hints.
- **Definition of Done:** Verified in Room DAO test suite.

#### `DB-007` — Create Category, Merchant, TransactionRule, Tag & Budget Schemas
- **Priority:** `MUST` (Category/Merchant/Rule) / `SHOULD` (Tag) / `COULD` (Budget) | **PRD Refs:** `CAT-01`, `CAT-02`, `TX-06`, `DASH-07`
- **Objective:** Implement supporting classification/categorization entities (`Category`, `Merchant`, `TransactionRule`, plus `Tag`/`TransactionTag`/`Budget`).
- **Dependencies:** `SEC-001`.
- **Deliverables:** Entities with unique `merchants.key` index and FK cascades.
- **Acceptance Criteria:** Unique merchant key enforced; system categories protected by `is_system`.
- **Required Tests:** Unique `merchants.key` conflict test.
- **Definition of Done:** Verified in Room schema.

#### `DB-008` — Create CaptureGap Schema
- **Priority:** `SHOULD` | **PRD Refs:** `ING-11`, `ING-14`, `ACC-09`
- **Objective:** Persist capture interruption intervals (`ACCESS_REVOKED`, `LISTENER_DISCONNECTED`, `HEARTBEAT_STALE`).
- **Dependencies:** `DB-005`.
- **Deliverables:** `CaptureGapEntity`, `CaptureGapDao`.
- **Acceptance Criteria:** Supports open (`ended_at IS NULL`) and closed intervals.
- **Required Tests:** DAO query tests for overlapping date range against account history.
- **Definition of Done:** Integrated into database schema (v1 or v2 with tested migration).

---

### Milestone M2: Notification Ingestion (`6–9 working days`)

#### `PIPE-001` — Implement `NotificationListenerService` with Rebind
- **Priority:** `MUST` | **PRD Refs:** `ING-01`, `ING-02`, `ING-09`
- **Objective:** Implement `WtrackNotificationListenerService` declared in `AndroidManifest.xml`, lightweight callback handoff, and `requestRebind` on disconnect.
- **Dependencies:** `CORE-003`, `DB-005`.
- **Deliverables:** `WtrackNotificationListenerService`, permission check helper (`NotificationManagerCompat.getEnabledListenerPackages`).
- **Acceptance Criteria:** Callback finishes synchronously without DB blocking; detects connect/disconnect and triggers rebind.
- **Required Tests:** Service unit/Robolectric or instrumented tests for connect/disconnect/rebind calls.
- **Definition of Done:** Service binds reliably when Notification Access is enabled.

#### `PIPE-002` — Implement Package Allowlist Filter
- **Priority:** `MUST` | **PRD Refs:** `ING-03`, `ING-04`, Section 9.1
- **Objective:** Maintain in-memory allowlist snapshot synchronized with `transaction_sources` and drop disallowed packages before allocation/persistence.
- **Dependencies:** `PIPE-001`, `DB-003`.
- **Deliverables:** `AllowlistManager`, UI list of installed candidate packages.
- **Acceptance Criteria:** Disallowed packages produce zero `notification_events` rows and zero log output containing notification text.
- **Required Tests:** Unit/integration tests feeding allowed + disallowed packages.
- **Definition of Done:** Strict package isolation verified.

#### `PIPE-003` — Implement Event Inbox & Enriched Envelope Extraction
- **Priority:** `MUST` | **PRD Refs:** `ING-05`, `ING-06`, Section 6.2, 8.1
- **Objective:** Safely extract `title`/`text`/`bigText`/`subText`/`channelId`/`isGroupSummary`/`isContentRedacted`, `posted_at`, `notification_key`, `notification_id`, `notification_tag`, and persist `RECEIVED` row inside encrypted DB before parsing.
- **Dependencies:** `PIPE-002`, `DB-003`.
- **Deliverables:** `NotificationEnvelopeExtractor`, `EventInboxRepository`.
- **Acceptance Criteria:** Missing extras, group summaries, and Android 15 redacted payloads never crash; `RECEIVED` row exists before pipeline step 8.
- **Required Tests:** Malformed/empty/redacted/grouped notification extraction tests (`TEST-002`).
- **Definition of Done:** Inbox persistence survives simulated mid-pipeline termination.

#### `PIPE-004` — Implement Parser Registry
- **Priority:** `MUST` | **PRD Refs:** `PAR-01`, `PAR-08`
- **Objective:** Resolve registered `Set<TransactionParser>` by `sourceId` and `supports(event)`.
- **Dependencies:** `CORE-002`, `CORE-003`.
- **Deliverables:** `ParserRegistry` in `domain`/`data`.
- **Acceptance Criteria:** Deterministic parser selection; cleanly returns `null` when source has no compatible parser (`ING-13`).
- **Required Tests:** Unit tests with fake parsers (matching, non-matching, unsupported).
- **Definition of Done:** Registry wired via Hilt `@IntoSet`.

#### `PIPE-005` — Implement Normalizer & Validator with Locale-Aware Parsing
- **Priority:** `MUST` | **PRD Refs:** `PAR-03` to `PAR-05`, `PAR-12`
- **Objective:** Implement shared Indonesian/English number/date/currency normalizer, ambiguity guard (`AMBIGUOUS_FIELD`), `posted_at` timestamp fallback, and strict field validation (`amount > 0`, valid ISO currency, non-null account).
- **Dependencies:** `PIPE-004`, `DB-002`.
- **Deliverables:** `LocaleNumberParser` (`integration:providers` utility), `TransactionValidator` (`domain` / `pipeline`).
- **Acceptance Criteria:** `Rp50.000`, `Rp 1.250.000,50`, `IDR 50,000.00` parse accurately under explicit provider locale; ambiguous `1.500` routes to `NeedsReview(AMBIGUOUS_FIELD)` unless unambiguous by rule; `occurred_at` falls back to `posted_at`, never `received_at`.
- **Required Tests:** Exhaustive parameterized unit tests for `PAR-12` (`TEST-001`).
- **Definition of Done:** Zero floating-point math; thousand-fold misread tests pass 100%.

#### `PIPE-006` — Implement 3-Tier Deduplication & Idempotency
- **Priority:** `MUST` | **PRD Refs:** Section 8.1, `TX-08`
- **Objective:** Implement precedence chain (`provider_reference` → `stable_event_key` → composite fingerprint within bounded window) and verify against real update/re-post evidence.
- **Dependencies:** `PIPE-003`, `PIPE-005`.
- **Deliverables:** `DeduplicationEngine`, `FingerprintGenerator`.
- **Acceptance Criteria:** Replaying an identical notification or catching up post-restore creates 0 duplicate transactions; voided event never re-creates a transaction; two distinct purchases with identical amount + near-identical timestamp but different refs/keys are **not** collapsed.
- **Required Tests:** Unit & integration dedup tests covering all 3 tiers + anti-amount-only-dedup test (`TEST-002`, `TEST-004`).
- **Definition of Done:** Idempotency proven against synthetic and real PoC events.

#### `PIPE-007` — Implement Needs Review Handler
- **Priority:** `MUST` | **PRD Refs:** `PAR-07`, `REV-01` to `REV-05`
- **Objective:** Route `NeedsReview` results, validation failures, and account ambiguities to `NotificationEvent(NEEDS_REVIEW)` with `review_draft_json` and 30-day payload deadline; implement atomic Save, Ignore, and Retry transitions.
- **Dependencies:** `PIPE-003`, `PIPE-005`.
- **Deliverables:** `NeedsReviewRepositoryImpl`, atomic save/ignore/retry use cases.
- **Acceptance Criteria:** Incomplete events never enter `transactions`; Save creates a `Transaction` and marks event `PROCESSED` inside one DB transaction; Ignore clears payload immediately.
- **Required Tests:** Integration tests verifying transaction atomicity and zero partial `Transaction` leaks (`TEST-002`).
- **Definition of Done:** PoC displays pending review count accurately.

#### `PIPE-008` — Implement Capture Status Derivation & Catch-Up Scan
- **Priority:** `MUST` | **PRD Refs:** `ING-07`, `ING-09`, `ACC-06`
- **Objective:** Derive app and per-account `CaptureStatus` (`ACTIVE`, `ATTENTION`, `DISCONNECTED`, `UNSUPPORTED`) from observable signals and run `activeNotifications` catch-up scan on connect/reconnect.
- **Dependencies:** `PIPE-001`, `PIPE-006`.
- **Deliverables:** `CaptureStatusTracker`, `CatchUpScanner`.
- **Acceptance Criteria:** Status transitions immediately on permission revoke, listener disconnect, missing source, or missing parser; catch-up scan processes shade items idempotently without duplicating existing transactions.
- **Required Tests:** Truth-table unit tests + catch-up integration test (`Section 14.2`).
- **Definition of Done:** Status verified on real device during PoC.

#### `PIPE-015` — Implement Event Recovery & Unsupported-Source Handling
- **Priority:** `MUST` | **PRD Refs:** `ING-12`, `ING-13`, Appendix A
- **Objective:** Sweep `RECEIVED`, stale `PROCESSING`, and `FAILED` events on app start and listener connect with retry escalation (`attempt_count >= 3` → `NEEDS_REVIEW(PIPELINE_ERROR)`); handle `NO_PARSER` sources as `IGNORED` + immediate purge + 30-day count.
- **Dependencies:** `PIPE-003`, `PIPE-004`, `PIPE-007`.
- **Deliverables:** `EventRecoveryCoordinator`, `UnsupportedSourceCounter`, plus **minimal PoC Compose verification screen** (M2/M3a test UI).
- **Acceptance Criteria:** Killed-mid-pipeline events auto-recover on restart; 3 failures escalate to `NEEDS_REVIEW(PIPELINE_ERROR)`; allowlisted source without parser creates 0 `NEEDS_REVIEW` rows, purges payload, and increments 30-day count.
- **Required Tests:** Recovery & escalation unit/integration tests + `NO_PARSER` test (`Section 14.1`, `14.2`).
- **Definition of Done:** No accepted event can remain permanently stuck.

---

### Milestone M3a: Provider Discovery & Gate G1 Window (`3–5 working days + 7–14 calendar days`)

#### `INT-001` — Capture & Anonymize Real-Device Fixtures (+ M3a Sample Collection)
- **Priority:** `MUST` | **PRD Refs:** Section 11, 14.3, 17, 17.1
- **Objective:** Verify installed package IDs (`Bank Jago`, `DANA`, `BCA`: `BCA mobile` vs `myBCA`) on `iQOO Z10R`, inspect real notification structures (`title`, `text`, `bigText`, `subText`, `channelId`, directions, amount formats, ambiguities) via opt-in sample collection (max 14d, encrypted, strictly excluding OTPs/PINs/credentials), record `key`/`id`/`tag` update/re-post behavior (`TEST-004`), and commit anonymized fixtures in [PROVIDERS.md](file:///home/adomancer/Documents/project/W-Track/docs/PROVIDERS.md) / `:integration:providers`.
- **Dependencies:** `PIPE-008`, `PIPE-015`, **Gate G1 thresholds locked in `ADR-003` before capture starts**. Does **not** block M0 or M1.
- **Deliverables:** Anonymized fixture files in `integration/providers/src/test/resources/...`; completed [PROVIDERS.md](file:///home/adomancer/Documents/project/W-Track/docs/PROVIDERS.md) evidence tables (`key`/`id`/`tag`, language, timezone, dual-notifier overlap).
- **Acceptance Criteria:** Zero real PII/OTPs/PINs/account numbers committed to git; fixtures cover all transaction classes observed on `iQOO Z10R`.
- **Required Tests:** Fixture deserialization & validation tests; git privacy audit.
- **Definition of Done:** Verified anonymized fixtures exist starting with Priority 1 (`Bank Jago`) and extending to `DANA` and `BCA`.

#### `INT-002` (PoC Scope) — Implement Priority 1 Provider Parser (`Bank Jago`, Incremental PoC)
- **Priority:** `MUST` | **PRD Refs:** `PAR-02`, `PAR-06`, Section 17.1
- **Objective:** After ingestion foundation M0–M2 is ready and `Bank Jago` real notifications are inspected on `iQOO Z10R`, implement working v1 `JagoTransactionParser` inside `:integration:providers` (`wtrack.integration.providers.jago`) so PoC displays parsed, deduplicated real transactions without hardcoding provider logic into the listener.
- **Dependencies:** `INT-001` (Bank Jago verified package & samples), `PIPE-005`.
- **Deliverables:** `JagoTransactionParser` (version = 1), unit fixture tests (`TEST-001` subset).
- **Acceptance Criteria:** Captures, parses, deduplicates, and persists live `Bank Jago` notifications on `iQOO Z10R` while preserving pure `TransactionParser` abstraction.
- **Required Tests:** Unit tests covering valid, promo, failed, partial, pocket-hint, and duplicate notifications.
- **Definition of Done:** PoC Sprint success condition achieved for Priority 1 (`Bank Jago`) without waiting on `DANA` or `BCA`.

#### `TEST-007` — Run Gate G1 Measurement & Populate Appendix C / `PROVIDERS.md` Evidence Log
- **Priority:** `MUST` | **PRD Refs:** Section 17, Appendix C, `ADR-003`
- **Objective:** Run ≥7–14 consecutive day capture (≥ 30 transactions per active provider across **Bank Jago**, **DANA**, and **BCA** as available/verified), reconcile transaction-by-transaction against official provider statements, and record Coverage (`D/A`), Completeness (`C/B`), Correctness, identifiability, dual-notifier overlaps, and update/duplicate findings.
- **Dependencies:** `INT-001`, `INT-002`, **ADR-003 thresholds locked beforehand**.
- **Deliverables:** Completed Appendix C / `PROVIDERS.md` Evidence Log; formal Gate G1 Go/Re-scope/No-go memo + Gate G1 scope decisions (multi-account, credit card, dual-notifier rule).
- **Acceptance Criteria:** Sample ≥ 30 per evaluated provider; metrics calculated against pre-locked thresholds without post-hoc adjustment.
- **Required Tests:** Cross-verification against statement ground truth.
- **Definition of Done:** Gate G1 decision signed off by owner before starting M3b–M5.

---

### Milestone M3b: Initial Providers (`5–8 working days`, Incremental across Bank Jago → DANA → BCA)

#### `INT-002` (Full) & `INT-003` — Harden `Bank Jago` & Implement `DANA` and `BCA` Parsers Incrementally
- **Priority:** `MUST` | **PRD Refs:** `PAR-02`, `PAR-06`, `PAR-09`, `PAR-10`, `PAR-12`, Criterion 4
- **Objective:** Harden Priority 1 (`Bank Jago`) and incrementally implement Priority 2 (`DANA`) and Priority 3 (`BCA`: `BCA mobile` and/or `myBCA` per M3a ADB verification) in isolated `:integration:providers` subpackages covering incoming, outgoing, fees, failed attempts (`FAILED_ATTEMPT`), refunds/reversals (`REFUND` + `provider_reference`), account/pocket hints, reported balances, multi-stage announcements, and Indonesian/English formats.
- **Dependencies:** Gate G1 `Go` (or `Re-scope`), `INT-001` per-provider evidence.
- **Deliverables:** Versioned `JagoTransactionParser`, `DanaTransactionParser`, and `BcaTransactionParser` in `:integration:providers`, complete anonymized test suites and ambiguity notes in `PROVIDERS.md`.
- **Acceptance Criteria:** All parsers remain isolated in `:integration:providers` (zero provider hardcoding in listener/pipeline); handle every observed M3a template deterministically across all three PoC target apps.
- **Required Tests:** `TEST-001` full regression suite per provider.
- **Definition of Done:** Verified parsers pass all fixture & on-device validation suites incrementally across `Bank Jago`, `DANA`, and `BCA`.

#### `PIPE-009` — Implement Account Resolver Using Account Hints
- **Priority:** `MUST` (deferred to post-MVP only if Gate G1 confirms 1:1 sources) | **PRD Refs:** `ACC-05`, `PAR-02`, `REV-07`
- **Objective:** Map `(source_id, account_hint)` to exact `Account`; route unmapped (`ACCOUNT_UNMAPPED`) or ambiguous (`ACCOUNT_AMBIGUOUS`) events to `NEEDS_REVIEW`; remember hint on user resolution (`REV-07`, `SHOULD`).
- **Dependencies:** `DB-006`, `PIPE-007`.
- **Deliverables:** `AccountResolver`, hint-learning update inside atomic review save.
- **Acceptance Criteria:** Single-account source resolves via null hint; multi-account source routes via masked hint; ambiguous/missing match enters Needs Review cleanly.
- **Required Tests:** Unit/integration tests for 1:1, 1:N, unmapped, ambiguous, and learned-hint flows.
- **Definition of Done:** Verified against Gate G1 multi-account decision.

#### `PIPE-010` — Handle Failed Attempts & Refunds in Pipeline
- **Priority:** `MUST` (`PAR-09`, `PAR-10`) / `SHOULD` (auto-link `TX-09`) | **PRD Refs:** `PAR-09`, `PAR-10`, `TX-09`, Section 8.2
- **Objective:** Ensure `FAILED_ATTEMPT` becomes `IGNORED` with immediate purge (never auto-voiding saved rows); ensure `REFUND` saves as `INFLOW` and auto-links `related_transaction_id` on exact `provider_reference` match (`TX-09`); outgoing reversal routes to `NEEDS_REVIEW`.
- **Dependencies:** `PIPE-006`, `DB-002`.
- **Deliverables:** Pipeline refund linker & failure classifier.
- **Acceptance Criteria:** Failed attempts alter zero totals/balances; matched refunds net against original transaction's category.
- **Required Tests:** Unit & integration tests for failed attempt, linked refund, unlinked refund, and outgoing reversal.
- **Definition of Done:** Verified in pipeline & aggregate suites.

#### `TEST-001` — Complete Parser Unit Test Suite (Indonesian & English Formats)
- **Priority:** `MUST` | **PRD Refs:** `PAR-01`..`PAR-10`, `PAR-12`, Section 14.1, Criterion 26
- **Objective:** Comprehensive JVM unit coverage for both providers, locale number parsing, ambiguity guards, timezones, `posted_at` fallback, failed attempts, and refunds.
- **Dependencies:** `INT-002`, `INT-003`, `PIPE-005`.
- **Deliverables:** Pure-JVM unit test suites in `:integration:providers` and `:app`.
- **Acceptance Criteria:** 100% pass rate on JVM without Android emulator.
- **Required Tests:** Parameterized tests for `Rp50.000`, `Rp 1.250.000,50`, `IDR 50,000.00`, `1.500` ambiguity, WIB/WITA/WIT/UTC conversions.
- **Definition of Done:** Zero flaky tests; high edge-case coverage.

---

### Milestone M3c: Data Model & Domain Completion (`6–10 working days`, Post-Gate G1)

#### `DOM-001` — Implement Aggregate & 16-Row Classification Engine
- **Priority:** `MUST` | **PRD Refs:** Section 4.6, `TRF-02`, `TRF-04`, `DASH-01`, `DASH-05`, `DASH-09`, `CAT-03`
- **Objective:** Implement pure-Kotlin `AggregateCalculator` covering every row of the Section 4.6 table: paired transfer exclusions, derived fee rows, refund netting, adjustment isolation, voided/foreign-currency exclusions, and Uncategorized slicing.
- **Dependencies:** `DB-005`, `DOM-002`, `DOM-005`.
- **Deliverables:** `ObserveDashboardUseCase`, `ObserveInsightsUseCase`, `AggregateCalculator`.
- **Acceptance Criteria:** Fees category total equals sum of explicit `FEE` rows plus derived `TransferMatch` fee rows; refunds net spending without inflating income; adjustments excluded from cash flow.
- **Required Tests:** `TEST-005` validating every single row of the 16-row classification table.
- **Definition of Done:** Single shared calculator drives all UI reports.

#### `DOM-002` — Implement Transfer Pair Validation & Fee Derivation
- **Priority:** `MUST` | **PRD Refs:** `TRF-01`, `TRF-02`, `TRF-04`, `ACC-10`, Section 8.3
- **Objective:** Implement `LinkTransferUseCase` & `UnlinkTransferUseCase` enforcing distinct accounts, opposite directions, matching currency, `outgoing >= incoming`, `fee_minor` computation, and pair re-evaluation on leg edit.
- **Dependencies:** `DB-004`, `DB-005`.
- **Deliverables:** Transfer linking/unlinking use cases & repository logic.
- **Acceptance Criteria:** Invalid links (`incoming > outgoing`, mismatched currency, already paired) rejected cleanly; balances never change twice.
- **Required Tests:** `TEST-005` invariant tests across linking, unlinking, and invalid pairs.
- **Definition of Done:** All transfer math invariants pass.

#### `DOM-003` — Implement Balance Correction Use Case
- **Priority:** `MUST` | **PRD Refs:** `ACC-08`, `DASH-09`
- **Objective:** Compute current estimated balance (`occurred_at >= initial_balance_at`) and insert `ADJUSTMENT` transaction with delta between entered actual balance and estimate, updating `last_reconciled_at`.
- **Dependencies:** `DB-001`, `DB-002`, `DOM-001`.
- **Deliverables:** `CorrectBalanceUseCase`.
- **Acceptance Criteria:** Subsequent missed-transaction recovery + second correction nets earlier adjustment cleanly; income/expense unchanged.
- **Required Tests:** Integration test verifying estimated balance updates while income/expense totals stay invariant (`Section 14.2`).
- **Definition of Done:** End-to-end balance correction verified.

#### `DOM-004` — Implement Transaction Edit & Void Use Cases
- **Priority:** `MUST` | **PRD Refs:** `TX-03`, `TX-08`, Section 4.5
- **Objective:** Implement edits preserving immutable `original_json`, transfer-pair re-evaluation blocking invalidating edits, void/restore for captured transactions, and deletion restricted to manual transactions.
- **Dependencies:** `DB-002`, `DOM-002`.
- **Deliverables:** `UpdateTransactionUseCase`, `VoidTransactionUseCase`, `RestoreTransactionUseCase`, `DeleteManualTransactionUseCase`.
- **Acceptance Criteria:** `original_json` never mutates after capture; captured transactions cannot be deleted; voiding a transfer leg prompts/unlinks pair cleanly.
- **Required Tests:** Integration test for edit-with-original-preserved, void/restore balance exclusion, and blocked invalid pair edit (`Section 14.2`).
- **Definition of Done:** Full edit/void integrity verified.

#### `DOM-005` — Seed Default Categories & Category Management
- **Priority:** `MUST` | **PRD Refs:** `CAT-01`
- **Objective:** Seed default income/expense taxonomy including immutable system `"Uncategorized"` and `"Fees"` (plus `"Cash withdrawal"` if confirmed), and support add/rename/archive.
- **Dependencies:** `DB-007`, **Owner confirmation of default category taxonomy**.
- **Deliverables:** `DefaultCategorySeeder`, `CategoryRepositoryImpl`.
- **Acceptance Criteria:** Fresh DB and restored DB always contain valid system categories; archiving category prevents deletion of historical transactions.
- **Required Tests:** Seeder & category CRUD unit tests.
- **Definition of Done:** Verified on fresh DB and restore flows.

#### `PIPE-011` — Record Capture Gaps & Heartbeat Gaps
- **Priority:** `SHOULD` | **PRD Refs:** `ING-11`, `ING-14`, `ACC-09`
- **Objective:** Record `ACCESS_REVOKED` / `LISTENER_DISCONNECTED` intervals and implement 15-min heartbeat + stale threshold (`3h` proposed) creating `HEARTBEAT_STALE` gaps.
- **Dependencies:** `DB-008`, `PIPE-008`.
- **Deliverables:** `HeartbeatMonitor`, `CaptureGapTracker`.
- **Acceptance Criteria:** Revoking/restoring access logs exact gap; stale gap fires only when threshold exceeded; `Possible gap` badge surfaces on overlapping account history.
- **Required Tests:** Unit tests with fake `Clock` for threshold edge cases (`Section 14.1`) + device kill test (`Section 14.3`).
- **Definition of Done:** Verified against lifecycle suite.

#### `PIPE-012` — Implement Balance Checkpoint Comparison
- **Priority:** `SHOULD` | **PRD Refs:** `ACC-07`, `ACC-09`
- **Objective:** Compare consecutive `reported_balance_minor` changes on an account against intermediate net transactions (no initial balance needed), strictly skipping equal-timestamp or out-of-order reports.
- **Dependencies:** `DB-002`, `DOM-001`.
- **Deliverables:** `BalanceCheckpointEvaluator`.
- **Acceptance Criteria:** Flags account when mismatch occurs; stays quiet when ordered delta matches or reports are unordered/same-post-time.
- **Required Tests:** Unit tests for matching, mismatch, missing initial balance, and unordered timestamps (`Section 14.1`).
- **Definition of Done:** All checkpoint cases pass.

#### `PIPE-013` — Implement Merchant Key Normalization & Category Rule Engine
- **Priority:** `MUST` | **PRD Refs:** `CAT-02`, `CAT-04` (`SHOULD`), `CAT-05`, Section 4.9
- **Objective:** Normalize merchant keys (strip case/spacing/trailing terminal/branch IDs), match rules by specificity + recency, protect `USER` categories from overwrite, and support batch backfill preview (`CAT-04`).
- **Dependencies:** `DB-007`, `DOM-005`.
- **Deliverables:** `MerchantKeyNormalizer`, `CategoryRuleEngine`, `RuleRepositoryImpl`.
- **Acceptance Criteria:** Auto-applies rule on incoming tx; never overwrites `categorySource == USER`; disable/delete works immediately.
- **Required Tests:** Normalization, precedence tie-breaker, and user-override protection unit tests (`Section 14.1`).
- **Definition of Done:** Verified in pipeline integration tests.

#### `PIPE-014` — Implement Re-Parse of Retained Events
- **Priority:** `COULD` | **PRD Refs:** `PAR-11`
- **Objective:** After parser version bump, re-parse retained `payload_json` (within verification window) and surface changes as user proposals without overwriting user-edited transactions.
- **Dependencies:** `PIPE-004`, `DOM-004`.
- **Deliverables:** `RetainedEventReparser`.
- **Acceptance Criteria:** Only runs on retained non-null payloads; never auto-overwrites edited fields.
- **Required Tests:** Unit test with bumped parser version & user-edited guard.
- **Definition of Done:** Deferrable without impacting MVP acceptance.

#### `TEST-002` & `TEST-005` — Ingestion Integration & Aggregate/Fee Invariant Suites
- **Priority:** `MUST` | **PRD Refs:** Sections 4.6, 14.2, `TRF-04`
- **Objective:** End-to-end Room-backed SQLCipher integration suite (`NotificationEvent` → parser → deduplication → Room / Needs Review) plus exhaustive aggregate/fee/edit/void/refund/checkpoint verification.
- **Dependencies:** `M3b`, `M3c`.
- **Deliverables:** Instrumented & JVM test suites.
- **Acceptance Criteria:** All Section 14.2 ingestion & accounting scenarios pass with zero double-counting.
- **Required Tests:** As specified in Section 5 below.
- **Definition of Done:** 100% green before starting M4 polishing.

---

### Milestone M4: Full Material 3 Compose UI (`13–19 working days`, Post-Gate G1)

#### `UI-001` — Build Onboarding with Permission, Allowlist & Battery Guidance
- **Priority:** `MUST` (`ING-02`, `ING-03`) / `SHOULD` (`ING-08`, `ING-10`) | **PRD Refs:** `ING-02`, `ING-03`, `ING-08`, `ING-10`, Section 10.3
- **Objective:** Multi-step onboarding: product/privacy rationale → Notification Access → restricted-settings & OEM battery/autostart guidance → package allowlist → account creation + hints + optional initial balance → test-capture verification.
- **Dependencies:** `M3c`, `PIPE-001`, `PIPE-002`.
- **Deliverables:** `OnboardingFlow` screens & ViewModel.
- **Acceptance Criteria:** User cannot proceed past permission silently without clear state; handles sideload restricted settings notice cleanly.
- **Required Tests:** Compose UI flow tests for granted/denied states.
- **Definition of Done:** Onboarding completes smoothly on real test device.

#### `UI-002` — Build Dashboard (`Home`)
- **Priority:** `MUST` (`DASH-01`..`04`) / `SHOULD` (`DASH-09`, `DATA-12`) | **PRD Refs:** `DASH-01`..`04`, `DASH-09`, `DATA-12`, Section 10.3
- **Objective:** Period selector, Income / Expense / Net Cash Flow cards, separate Unreconciled Difference row, account balance overview (explicit *"Estimated"* label), recent transactions, prominent Needs Review count badge, backup reminder banner.
- **Dependencies:** `DOM-001`.
- **Deliverables:** `DashboardScreen`, `DashboardViewModel`.
- **Acceptance Criteria:** Reflects `ObserveDashboardUseCase` changes reactively; locked-DB error state routes to recovery screen.
- **Required Tests:** Compose UI state rendering tests (normal, empty, pending reviews, backup reminder, locked DB).
- **Definition of Done:** Meets M3 accessibility & contrast guidelines.

#### `UI-003` — Build Accounts Screen
- **Priority:** `MUST` (`ACC-01`..`06`, `ACC-08`) / `SHOULD` (`ACC-09`, `ACC-10`) | **PRD Refs:** `ACC-01`..`10`, `ING-13`, Section 10.3, 10.4
- **Objective:** Account CRUD, archive, estimated balances, last reconciled date, Balance Correction modal (`ACC-08`), capture status labels + `Possible gap` badge, `AccountSource` hint editor, and `UNSUPPORTED` 30-day unparsed count (`ING-13`).
- **Dependencies:** `DOM-003`, `PIPE-008`, `PIPE-009`.
- **Deliverables:** `AccountsScreen`, `AccountDetailSheet`, `ReconcileDialog`.
- **Acceptance Criteria:** Disclaimers clearly distinguish estimated balances from bank ledgers; all 4 status labels match truth table.
- **Required Tests:** Compose tests for account creation, balance correction, and unsupported badge.
- **Definition of Done:** All account workflows operational offline.

#### `UI-004` — Build Activity Screen (Search, Filters, Detail, Edit, Void)
- **Priority:** `MUST` (`TX-01`..`05`, `TX-08`, `CAT-03`) / `SHOULD` (`TX-06`, `TX-09`) | **PRD Refs:** `TX-01`..`09`, `CAT-03`
- **Objective:** Chronological list with search, date/account/type/category/`Uncategorized`/`Show voided` filters; Transaction Detail showing original vs current values (`TX-03`), retained raw payload (`TX-05` during verification window), provenance metadata, edit form, void/restore toggle, refund linking (`TX-09`), and tags/notes (`TX-06`).
- **Dependencies:** `DOM-004`, `UI-010`.
- **Deliverables:** `ActivityScreen`, `TransactionDetailScreen`, `EditTransactionSheet`.
- **Acceptance Criteria:** Editing a leg that invalidates a `CONFIRMED` transfer pair displays clear block warning; original captured values remain visible side-by-side.
- **Required Tests:** Filter, edit, original-value display, and void/restore UI tests.
- **Definition of Done:** Full CRUD/provenance lifecycle verified.

#### `UI-005` — Build Needs Review Screen
- **Priority:** `MUST` (`REV-01`..`04`) / `SHOULD` (`REV-07`) | **PRD Refs:** `REV-01`..`07`, Section 10.3
- **Objective:** Queue displaying source app, `posted_at`, `ReviewReason`, draft fields, editable form with account picker + *"Remember account hint"* (`REV-07`), and Save / Retry / Ignore actions.
- **Dependencies:** `PIPE-007`, `PIPE-009`.
- **Deliverables:** `ReviewScreen`, `ReviewItemEditor`.
- **Acceptance Criteria:** Resolving item removes it from queue and creates confirmed transaction immediately; Ignore purges payload.
- **Required Tests:** Compose tests for Save, Retry, and Ignore flows.
- **Definition of Done:** Queue zero-inbox state works seamlessly.

#### `UI-006` — Build Manual Transaction Entry
- **Priority:** `MUST` | **PRD Refs:** `TX-07`, Section 10.3
- **Objective:** Fast manual transaction sheet (Cash accounts, unsupported sources, missed items) supporting amount, currency, direction, type, account, date/time, category, merchant/description.
- **Dependencies:** `DB-005`, `DOM-005`.
- **Deliverables:** `AddManualTransactionSheet`.
- **Acceptance Criteria:** Validates `amountMinor > 0`; supports deleting manual transactions later (`TX-08`); respects `initial_balance_at`.
- **Required Tests:** Validation & submission UI test.
- **Definition of Done:** Manual transactions appear immediately in Activity & balances.

#### `UI-007` — Build Transfer Pair Linking UI
- **Priority:** `MUST` | **PRD Refs:** `TRF-01`, `TRF-02`, `TRF-04`, `ACC-10`
- **Objective:** UI to select an `OUTFLOW` and matching `INFLOW` from another account, preview derived `fee_minor` (`outgoing - incoming`), confirm link, or unlink existing pair (plus view read-only derived fee row in Fees / outgoing detail).
- **Dependencies:** `DOM-002`.
- **Deliverables:** `LinkTransferSheet`, transfer badge on Activity/Detail.
- **Acceptance Criteria:** Blocks invalid pairs with explanatory error; immediately excludes paired legs from totals while adding derived fee to Fees.
- **Required Tests:** UI pairing & fee preview test.
- **Definition of Done:** End-to-end manual top-up/card-payment pairing works.

#### `UI-008` — Build Insights Screen
- **Priority:** `SHOULD` (`DASH-05`, `DASH-06`, `CAT-03`) / `COULD` (`DASH-07`, `DASH-08`) | **PRD Refs:** `DASH-05`..`08`, `CAT-03`
- **Objective:** Category spending breakdown with explicit `"Uncategorized"` slice, month-over-month comparison, plus optional merchant trends (`DASH-08`) and category budgets (`DASH-07`) if time permits.
- **Dependencies:** `DOM-001`.
- **Deliverables:** `InsightsScreen`, `InsightsViewModel`.
- **Acceptance Criteria:** Category breakdown totals match Dashboard Expenses 100% (including refund netting & derived fees).
- **Required Tests:** UI invariant check against `AggregateCalculator`.
- **Definition of Done:** Responsive charts/breakdowns in light & dark themes.

#### `UI-009` — Build Settings Screen
- **Priority:** `MUST` | **PRD Refs:** Section 10.3, `CAT-05`, `DATA-05`, `DATA-06`, `DATA-07`, `DATA-11`
- **Objective:** Controls for System/Light/Dark theme, Notification Access & allowlist management, categorization rules manager (`CAT-05`), backup/restore/verify/CSV export triggers, retention window slider (`0–30 days`), delete-all-data (`DATA-06`), privacy disclosures, and non-sensitive diagnostics viewer.
- **Dependencies:** `M5` backup/retention use cases (wired iteratively), `PIPE-013`.
- **Deliverables:** `SettingsScreen`, `RulesManagerScreen`, `DiagnosticsScreen`.
- **Acceptance Criteria:** Changing retention window updates policy immediately; diagnostics viewer exposes zero financial text/account numbers.
- **Required Tests:** Settings toggle & rules disable/delete UI tests.
- **Definition of Done:** Complete user privacy & control hub functional.

#### `UI-010` — Build Categorization Rule Prompt & Apply-Existing UI
- **Priority:** `MUST` (`CAT-02`, `CAT-05`) / `SHOULD` (`CAT-04`) | **PRD Refs:** `CAT-02`, `CAT-04`, `CAT-05`
- **Objective:** *"Remember for this merchant"* toggle (ON by default) when setting transaction category, plus *"Apply to N existing uncategorized transactions"* confirmation (`CAT-04`).
- **Dependencies:** `PIPE-013`, `UI-004`.
- **Deliverables:** `CategoryPickerSheet` with rule creation & batch preview prompt.
- **Acceptance Criteria:** Rule applies automatically on next matching notification; existing user-categorized items are never overwritten.
- **Required Tests:** UI + rule engine flow test.
- **Definition of Done:** Verified end-to-end from edit to next ingested event.

---

### Milestone M5: Reliability, Backup & Data Safety (`9–14 working days`, Post-Gate G1)

#### `SEC-002` — Implement Retention & Purge Worker
- **Priority:** `MUST` | **PRD Refs:** `DATA-07`, `REV-05`, Section 9.3, Criterion 14
- **Objective:** Implement `WorkManager` periodic job + startup sweep enforcing immediate ignore purge, configurable `0–30d` (default `7d`) saved-tx verification window purge, `30d` Needs Review cap, `14d` sample collection cap, and `7d` log cap.
- **Dependencies:** `DB-003`, `UI-009` retention preference.
- **Deliverables:** `RetentionPurgeWorker`, `LogPurger`.
- **Acceptance Criteria:** Expired `payload_json` becomes `NULL` while preserving `NotificationEvent` identity row; inside window, `TX-05` detail still shows source text.
- **Required Tests:** Instrumented time-travel retention test (`Section 14.2`).
- **Definition of Done:** Zero expired raw payloads remain on disk after job executes.

#### `SEC-004` — Finalize `ADR-006` (Backup Format & Cryptography)
- **Priority:** `MUST` | **PRD Refs:** `DATA-10`, Section 9.4, 19, `ADR-006`
- **Objective:** Confirm KDF (`Argon2id` via Bouncy Castle vs `PBKDF2`), work-factor benchmark on owner's physical device, binary envelope version `1`, and JSON export schema **before** writing `EXP-001`.
- **Dependencies:** Owner sign-off on `ADR-006`.
- **Deliverables:** Updated `Accepted` `ADR-006` with measured device KDF latency.
- **Acceptance Criteria:** KDF execution runs in reasonable time (~300–900ms) without OOM on target device.
- **Required Tests:** Benchmark + vector test.
- **Definition of Done:** `ADR-006` status transitions to `Accepted`.

#### `EXP-001` — Implement Encrypted Full Backup
- **Priority:** `MUST` | **PRD Refs:** `DATA-05`, `DATA-10`, Section 9.4, Criterion 15
- **Objective:** Export full backup via SAF encrypted with user passphrase (`ADR-006`), including accounts, `account_sources`, transactions (voided, adjustments, `original_json`), categories, merchants, rules, tags, pairs, capture gaps, budgets, allowlist, base currency, settings, and `eventIdentities` — strictly excluding `payload_json` and `NEEDS_REVIEW` rows.
- **Dependencies:** `SEC-004`, `DB-005`.
- **Deliverables:** `BackupExporter`, SAF export UI, pre-export Needs Review & passphrase warnings.
- **Acceptance Criteria:** Output binary matches `.wtrackbackup` spec; no plaintext JSON written to disk; zero raw payloads inside decrypted container.
- **Required Tests:** Container encryption/AAD/exclusion unit & integration tests (`TEST-006`).
- **Definition of Done:** Verified readable on second device/emulator.

#### `EXP-002` — Implement Atomic Restore from Full Backup
- **Priority:** `MUST` | **PRD Refs:** `DATA-09`, Section 4.7, 9.4, Criterion 15
- **Objective:** Decrypt, verify AAD + version (`<= CURRENT`), migrate older formats, validate FKs, atomically wipe-and-load DB + DataStore inside a single transaction, and route user to Notification Access grant (`ING-02`) with sources in `ATTENTION`.
- **Dependencies:** `EXP-001`.
- **Deliverables:** `BackupRestorer`, `RestoreFlow` UI (accessible from Onboarding, Settings, and `DATABASE_LOCKED` screen).
- **Acceptance Criteria:** Wrong passphrase, corrupt bytes, or newer version aborts with 0 partial writes; post-restore catch-up scan creates 0 duplicate transactions.
- **Required Tests:** `TEST-006` full suite.
- **Definition of Done:** Fresh-install restore passes all integrity & catch-up checks.

#### `EXP-003` — Implement Delete-Data Flow
- **Priority:** `MUST` | **PRD Refs:** `DATA-06`, Section 9.4
- **Objective:** Provide double-confirmed full wipe removing DB files, Keystore KEK, wrapped DEK, DataStore settings, and logs cleanly.
- **Dependencies:** `SEC-001`, `DB-005`.
- **Deliverables:** `WipeAllDataUseCase`, confirmation dialog in Settings & `DATABASE_LOCKED` screen.
- **Acceptance Criteria:** App returns cleanly to `FRESH_INSTALL` state with zero residual transaction data or orphaned keys.
- **Required Tests:** Instrumented wipe + restart test.
- **Definition of Done:** Verified on physical device.

#### `EXP-004` — Implement CSV Export
- **Priority:** `SHOULD` | **PRD Refs:** `DATA-11`, Section 9.4, 17
- **Objective:** Export accounts, transactions, and categories to RFC-4180 CSV via SAF after explicit unencrypted-output warning.
- **Dependencies:** `DB-005`.
- **Deliverables:** `CsvExporter`, warning confirmation modal.
- **Acceptance Criteria:** Properly escapes quotes/commas/newlines and prevents CSV formula injection (`=`, `+`, `-`, `@` prefix neutralization where appropriate or safe quoting).
- **Required Tests:** CSV formatting & special-character unit tests.
- **Definition of Done:** Opens cleanly in spreadsheet tools.

#### `EXP-005` — Implement Backup Reminders & Post-Write Verification
- **Priority:** `SHOULD` | **PRD Refs:** `DATA-12`, `DATA-13`
- **Objective:** Dismissible, configurable backup reminder after `14 days` or `100 new transactions` (zero financial details shown); auto-verify every newly created backup by re-reading, decrypting, and verifying counts (`DATA-13`).
- **Dependencies:** `EXP-001`, `UI-002`.
- **Deliverables:** `BackupReminderTracker`, `BackupVerifier`.
- **Acceptance Criteria:** Verification detects simulated write corruption; reminder suppresses cleanly when dismissed or updated after verified backup.
- **Required Tests:** Reminder threshold & tampered-file verification failure tests.
- **Definition of Done:** Integrated into `EXP-001` flow.

#### `EXP-006` — Implement Scheduled Backup to SAF Folder
- **Priority:** `COULD` | **PRD Refs:** `DATA-14`
- **Objective:** Persist SAF tree URI (`takePersistableUriPermission`) and run scheduled passphrase-protected backup (note key handling constraint: user-entered or safely cached passphrase policy needed if unattended, else prompt/defer).
- **Dependencies:** `EXP-001`, `EXP-005`.
- **Deliverables:** `ScheduledBackupWorker` (only if scheduled post-MVP/buffer).
- **Acceptance Criteria:** Respects user-selected SAF folder without broad storage permissions.
- **Required Tests:** SAF persistence permission test.
- **Definition of Done:** First candidate to defer if schedule tightens.

#### `TEST-003`, `TEST-004`, `TEST-006` — Lifecycle, Key/Identity, and Backup Verification Suites
- **Priority:** `MUST` | **PRD Refs:** Sections 8.1, 9.2, 14.2, 14.3, Criteria 6, 13, 15, 16
- **Objective:** Execute complete device lifecycle matrix (`TEST-003`), real-device `key`/`id`/`tag` + key-failure suite (`TEST-004`), and backup/restore round-trip & failure suite (`TEST-006`).
- **Dependencies:** `M1`–`M5` components.
- **Deliverables:** Automated instrumented tests (`TEST-004`, `TEST-006`) + signed-off physical device lifecycle checklist (`TEST-003`).
- **Acceptance Criteria:** Every MVP Acceptance Criterion (1–26, subject to Gate G1 scope decisions on Criteria 22–23) verified green.
- **Required Tests:** Full matrix detailed in Section 5 below.
- **Definition of Done:** Zero open `MUST` defects.

---

## 4. Gate G1 Scope Decision & Schedule De-Scoping Levers

If Gate G1 or M3a reveals tighter timelines or unnecessary complexity, **PRD Section 17** designates these exact `SHOULD` / `COULD` items as **first deferral candidates** without compromising MVP acceptance:
1. **Gate G1 Conditional Cuts (per M3a real-device evidence):**
   - Multi-account hints (`ACC-05`, `PAR-02`, `REV-07`, Criterion 22) — drop UI/resolution complexity if neither app uses 1:N accounts (keep `account_hint` column).
   - Credit card signed ledger (`ACC-10`, Criterion 23) — defer if owner does not track a credit card.
2. **Schedule Buffer Cuts (`SHOULD` / `COULD`):**
   - `ACC-07`, `ACC-09` (Reported balance checkpoints)
   - `ING-11`, `ING-14` (Capture gap tracking & heartbeat)
   - `DASH-09` (Unreconciled difference card — keep `ADJUSTMENT` exclusion!)
   - `REV-07` (Auto-remember account hint)
   - `TX-09` (Automatic refund linking — keep manual link/type)
   - `CAT-04` (Apply new rule to existing transactions)
   - `DATA-11`, `DATA-12`, `DATA-13`, `DATA-14` (CSV export, reminders, verification, scheduled backup)
   - `PAR-11` (Re-parse retained events)
   - `DASH-07`, `DASH-08` (Budgets, merchant trends)

---

## 5. Risk-Driven Testing & Quality Verification Strategy

Every high-risk requirement and architectural decision maps directly to automated or device-verified test suites:

| High-Risk Area | Specific Failure Modes Targeted | Test Type & Layer | Concrete Verification Criteria |
| :--- | :--- | :--- | :--- |
| **1. Financial Amount & Locale Parsing (`PAR-12`, `ADR-004`)** | Thousand-fold misread (`Rp50.000` vs `1.500`), **100x IDR exponent scaling error** (`50_000L` vs `5_000_000L`), floating-point drift, wrong timezone / `received_at` instead of `posted_at` | Pure-JVM Unit Tests (`:integration:providers`, `:app`) | Parameterized vectors for `id-ID` & `en-US` formats; assert `Rp50.000` → `5_000_000L` & `Rp 1.250.000,50` → `125_000_050L` under IDR exponent `2`; assert `,00` UI elision; assert `1.500` ambiguous input → `NeedsReview(AMBIGUOUS_FIELD)`; verify `occurredAt == postedAt`. |
| **2. Deduplication & Idempotency (`Section 8.1`)** | Catch-up scan or post-restore replay creates 2nd tx; two real purchases with same amount collapsed wrongly; voided tx resurrected | Unit + Room Integration (`TEST-002`, `TEST-006`) | Replay identical event → 1 tx; void tx + replay → stays voided; distinct `provider_reference` / `notification_key` same-minute same-amount → 2 distinct txs; post-restore catch-up → 0 new txs. |
| **3. Notification Updates & Re-posts (`Section 8.1`, `14.3`)** | Bank updates pending alert to settled alert, Live Update / `ProgressStyle` ticks, or re-posted summaries with mutated `key`/`id`/`tag` | Device Capture + Provider Fixture Tests (`TEST-004`) | Real-device log of `notification_key`, `notification_id`, `notification_tag`, `posted_at` across updates on Android 16; verify frequent updates never trigger ANRs or duplicate transactions. |
| **4. Process Death & Event Recovery (`ING-12`)** | App killed while event is in `RECEIVED` or `PROCESSING`; recurring crash loop hides accepted event | Coroutine/Room Integration + `adb shell am kill` Device Test | Seed DB with `RECEIVED`, stale `PROCESSING` (`>60s`), and `FAILED` (`attempt_count = 2` vs `3`) rows → trigger startup recovery → assert completion or escalation to `NEEDS_REVIEW(PIPELINE_ERROR)`. |
| **5. Needs Review & Accounting Invariants (`REV-01..05`, `Section 4.6`)** | Incomplete item leaks into dashboard totals; derived fee changes balance twice; edit breaks transfer pair; refund counted as income | Pure-Kotlin Domain + Room Tx Integration (`TEST-005`) | Verify `NEEDS_REVIEW` creates 0 `Transaction` rows; test all 16 rows of Section 4.6 table; test blocked edit invalidating `TransferMatch`; test `original_json` immutability across edits. |
| **6. Encryption, Locked-Screen Ingestion & Key Failure (`ADR-005`, `TEST-004`)** | Plaintext SQLite file; SQLCipher `4.20.0`/`4.17.0` `arm64-v8a` load fault; Keystore fails on locked AFU screen (`setUnlockedDeviceRequired` misconfig); key loss silently wipes DB | Instrumented + Physical Device Tests (`TEST-004`) | Verify raw `.db` header lacks `"SQLite format 3"`; verify **AFU locked-screen capture** cold-start/rebind unwraps KEK & persists to SQLCipher; verify **reboot + post-unlock reopen**; delete/corrupt wrapped DEK & Keystore alias → assert `LOCKED_KEY_MISSING_OR_INVALID` & original `.db` bytes untouched. |
| **7. Permission & Service Lifecycle (`ING-01..14`, `14.3`)** | Access revoked & restored; `onListenerDisconnected`; device reboot; Light/Deep Doze (`wait_for_unlock=true`) & vivo/iQOO background restrictions | Physical Device Lifecycle Checklist (`TEST-003`) | See dedicated **Physical-Device Test Plan (`iQOO Z10R`)** below; measure **default battery optimization settings BEFORE any whitelisting**. |
| **8. Android 15+/16 Hidden Content & Notification Behavior (`ING-06`, `Section 5`)** | Android 15/16 OTP redaction or lock-screen masking strips text; Android 16 `ProgressStyle`/Live Updates or Notification Cooldown disrupts capture | Physical `iQOO Z10R` (API 36) + Instrumented Tests (`Section 14.3`) | Feed redacted/empty-content financial notification (`isContentRedacted = true`) → assert zero crash, zero guessed fields, routes cleanly to `NEEDS_REVIEW`; verify `ProgressStyle` & cooldown bursts on Android 16. |
| **9. Retention, Backup & Deletion (`DATA-05..10`)** | Expired `payload_json` kept forever; backup leaks payloads/NeedsReview; wrong passphrase overwrites DB; wipe leaves residue | Instrumented Integration (`SEC-002`, `TEST-006`) | Verify `IGNORED` purged immediately, `PROCESSED` purged after window, `NEEDS_REVIEW` purged at 30d; inspect decrypted backup JSON for zero payloads/NeedsReview; wrong passphrase/corrupt/newer-version restore leaves DB untouched. |

### Physical-Device Testing Strategy (`vivo / iQOO Z10R`, Model `I2505`, Android 16 / API 36)
- **Confirmed Test Hardware:** Manufacturer `vivo`, Brand `iQOO`, Device `iQOO Z10R` (`I2505`), Android `16` (`API 36`), Build `PD2512LF_EX_A_16.1.20.0.W20`, Security Patch `2026-10-01`, CPU ABI `arm64-v8a`, via ADB on Fedora Linux.
- **Idle Diagnostics Baseline (`dumpsys deviceidle`):** Light Doze enabled, Deep Doze enabled, `wait_for_unlock=true`, Force idle disabled, confirmed prior transitions into `light-idle` and `deep-idle`.
- **Critical Diagnostic Guardrails:**
  1. The `ACTIVE` state observed during ADB bench inspection **must NOT be interpreted as proof of background reliability**.
  2. **Do NOT automatically whitelist the application or disable battery optimization as a workaround before baseline testing.** Default Android 16 + vivo/iQOO battery optimization behavior must be measured first; `ING-10` exemption flows are verified separately afterwards.
  3. **Additional Device Compatibility Unverified:** Passing tests on `iQOO Z10R` (Android 16) does **not** verify behavior on other OEM skins or Android versions (API 26–35, Android 17). Compatibility across additional devices remains explicitly **UNVERIFIED**.

**7 Mandatory Physical-Device Test Scenarios (`TEST-003`):**
1. **Notification delivery while screen is off and locked (AFU):** Unlock once post-boot, turn screen off & lock, trigger test notifications immediately and after 15+ minutes → confirm listener delivery, KEK unwrap, and SQLCipher DB persistence without opening the UI.
2. **`NotificationListenerService` connection persistence:** Keep app closed/swiped from recents across screen-off cycles and normal daily usage → track `onListenerConnected`/`onListenerDisconnected` transitions and `CaptureGap` precision.
3. **Listener recovery after process termination:** Kill app process (`adb shell am kill` / OS kill) → verify `ING-12` unfinished event sweep, `requestRebind()`, and `activeNotifications` catch-up scan on rebind/launch.
4. **Reboot and post-unlock recovery:** Power-cycle device → unlock once (BFU → AFU) → verify system automatically rebinds `NotificationListenerService`, encrypted SQLCipher DB reopens cleanly, and queued notifications still in shade are captured idempotently.
5. **Extended idle and Doze behavior:** Allow device to enter both Light Doze and Deep Doze unplugged/stationary (and test `dumpsys deviceidle step`), explicitly observing delays caused by `wait_for_unlock=true` before unlock vs after unlock, and calibrating `ING-14` `HEARTBEAT_STALE`.
6. **OEM-specific (`vivo` / `iQOO`) background restrictions:** Observe whether OEM power/process management terminates the listener without delivering `onListenerDisconnected()`, verifying whether `ING-14` heartbeat detects silent gaps.
7. **Battery optimization baseline vs `ING-10` exempt behavior:** Compare capture latency/drop rate under **default battery settings** first, then document exact user steps required under vivo/iQOO settings (`ING-10`) only after baseline evidence is recorded.
8. **Android 16 (API 36) notification behavior checks:** Verify lock-screen privacy masking/OTP redaction (`ING-06`), `Notification.ProgressStyle` / Live Updates handling, and Notification Cooldown under bursts.

### Architecture Decision → Verification Mapping
- **ADR-001 (Boundaries):** JUnit bytecode/import scan verifying `app.domain` has zero Android/Room/provider imports; `:integration:providers` compiles as pure `kotlin-jvm`.
- **ADR-002 (`allowBackup="false"`):** Merged `AndroidManifest.xml` inspection + fresh-install restore suite (`TEST-006`).
- **ADR-003 (Ingestion, `iQOO Z10R` & G1):** Physical device 7-scenario lifecycle checklist (`TEST-003`) + Android 16 checks + 7–14 day Appendix C Gate G1 measurement (`TEST-007`).
- **ADR-004 (Normalization & IDR Exponent `2`):** `PAR-12` locale + 100x scaling parser suite (`TEST-001`) + 16-row classification & fee invariant suite (`TEST-005`).
- **ADR-005 (SQLCipher `4.20.0`/`4.17.0` & Key Failure):** Disk-bytes check, AFU locked-screen capture, reboot reopen, `LOCKED_KEY_MISSING_OR_INVALID` test (`TEST-004`), and `arm64-v8a` verification on `iQOO Z10R`.
- **ADR-006 (Backup Container):** Argon2id/AES-GCM round-trip + tampered-AAD + atomic rollback instrumentation tests (`TEST-006`).

---

## 6. Owner Decisions & Unknowns Register

> Planning docs now incorporate the `iQOO Z10R` hardware confirmation and refined technical baselines, **while items marked Pending below remain explicitly unapproved until Owner confirmation**.

| # | Decision / Unknown | Status & Gate | Proposed / Confirmed Baseline & Trade-Offs | Owner Action Needed |
| :-: | :--- | :--- | :--- | :--- |
| **O1** | **Initial PoC financial applications (`Bank Jago`, `DANA`, `BCA`)** | **Targets CONFIRMED; Device Package IDs & Formats Pending Verification** (Non-blocking for **M0/M1**; Verified in **M3a**) | **Confirmed Incremental Priority (`docs/PROVIDERS.md`):** `1. Bank Jago` → `2. DANA` → `3. BCA`. **Google Play Reference Candidates (Unverified on `iQOO Z10R`):** `com.jago.digitalBanking` (Bank Jago), `id.dana` (DANA), and `com.bca` (`BCA mobile`) / `com.bca.mybca.omni.android` (`myBCA`). *Rule:* Does **not** block M0 or M1; exact package IDs and notification templates are inspected empirically on `iQOO Z10R` before building parsers. | **Verify installed package IDs (`adb shell pm list packages`) on `iQOO Z10R` and confirm `BCA mobile` vs `myBCA` usage in M3a.** |
| **O2** | **Primary physical test device & M0 Android SDK (`26 / 36 / 37`)** | **CONFIRMED for M0** (`CORE-001`) | **Confirmed Device:** `vivo / iQOO Z10R (I2505)`, Android `16` (`API 36`), Build `PD2512LF_EX_A_16.1.20.0.W20`, patch `2026-10-01`, `arm64-v8a` (other devices/OEMs explicitly **UNVERIFIED**). **M0 SDK Baseline:** `minSdk = 26`, `targetSdk = 36`, `compileSdk = 37` (Android 17 stable GA, zero beta/preview dependencies) with Gradle `9.9.1`, AGP `9.5.1`, Kotlin `2.2.21`, KSP `2.2.21-2.0.4`, Hilt `2.60.1`, Compose BOM `2026.09.00`. | **Resolved for M0 (`ADR-003` updated).** |
| **O3** | **Database encryption (`ADR-005`) & locked-screen Keystore policy** | **PENDING FINAL ACCEPTANCE** (Blocking **M1**) | Evaluated **`net.zetetic:sqlcipher-android:4.20.0`** (latest stable 4.x; **no SQLCipher 5 beta**, **no EOL `4.5.4`**; `4.17.0` fallback if strict `compileSdk = 36`). Keystore AES-GCM KEK with `setUserAuthenticationRequired(false)` and **`setUnlockedDeviceRequired(false)`** so AFU locked-screen capture works. *Trade-off:* Enables background locked-screen persistence (AFU) without prompting biometrics, trading per-unlock Keystore enforcement for reliable capture (mitigated by FBE, `allowBackup="false"`, retention purges). | **Final Owner acceptance of `ADR-005` (`4.20.0` vs `4.17.0` + AFU Keystore policy) before M1.** |
| **O4** | **Gate G1 baseline thresholds** | **Recommended Baseline** (Formal lock in `ADR-003` **before capture starts**) | Coverage `>= 90%`, Completeness `>= 95%`, `0` unexplained wrong amounts/directions, minimum `7` consecutive days (preferably `14`), minimum `30` statement transactions per provider. | **Formal sign-off before M3a measurement window opens.** |
| **O5** | **IDR currency storage exponent** | **Recommended `2` (ISO 4217)** (Confirm before **M1** `DB-002`) | Exponent `2` (`1 IDR = 100 sen`, `Rp50.000` = `5_000_000L`, `Rp1.250.000,50` = `125_000_050L`), UI hides `,00` when zero. *Trade-off:* Lossless for decimal notifications/fees; requires strict `CurrencySpec` conversion discipline to avoid 100x scaling mistakes. | **Confirm exponent `2` before M1.** |
| **O6** | **Retention & lifecycle operational defaults** | **Proposed** (`M2 / M5`) | Verification window `7 days` (`0–30d` configurable), Needs Review payload cap `30d`, debug logs `7d`, sample collection `14d`, `ING-12` max retries `3` / grace `60s`, `ING-14` heartbeat `15m` / stale `3h`, backup reminder `14d` or `100 tx`. | **Confirm or tune during M2/M5.** |
| **O7** | **Provider-specific update, `key`/`id`/`tag` & Android 16 behavior** | **Measured in M3a** | M3a records exact `key`/`id`/`tag` mutation, staging, timezone, mixed-language templates, and Android 16 redaction/cooldown behavior on `iQOO Z10R`. | **Measured empirically in M3a.** |
| **O8** | **Whether multiple accounts share one notification source** | **Confirmed at Gate G1** (`ACC-05`, `PAR-02`) | Schema keeps `account_sources.account_hint` in M1 regardless. Confirm/drop multi-account UI & `REV-07` from M3a evidence. | **Decide at Gate G1.** |
| **O9** | **Whether credit card support (`ACC-10`) is needed** | **Confirmed at Gate G1** (`ACC-10`, `SHOULD`) | Confirm/drop signed-ledger credit card UI & payment transfer pairing from owner's real provider usage. | **Decide at Gate G1.** |
| **O10** | **One payment announced by two apps** | **Confirmed at Gate G1** (`Section 11`) | M3a measures dual-notifier overlap; default MVP records separate transactions unless M3a justifies a deterministic rule. | **Decide at Gate G1.** |
| **O11** | **Backup KDF & container specifics** | **Deferred until before `EXP-001` (M5)** (`ADR-006`) | Recommended: Argon2id (`bcprov` pure-Java, 64 MiB / 32 MiB) + AES-256-GCM with AAD header; Fallback: PBKDF2-HMAC-SHA256 (600k iterations). Confirm CSV export (`DATA-11`, `SHOULD`). | **Confirm before `EXP-001` (M5).** |
| **O12** | **Default category taxonomy & product name / visual identity** | **Confirm before M3c / M4** (`DOM-005`) | Confirm final app name (placeholder `W-Track`) and starter categories (`Uncategorized`, `Fees`, `Cash withdrawal`, plus daily categories). | **Confirm before M3c / M4.** |

---

## 7. Gate G1 Risks (What Could Prevent PoC / Gate G1 Success)

| Risk | Why It Threatens Gate G1 | Mitigation Plan |
| :--- | :--- | :--- |
| **1. Provider sends silent, incomplete, or delayed notifications** | Some Indonesian/regional banking apps only send promos via push or omit direction/amount/account, driving Coverage `< 90%` or Completeness `< 95%`. | Gate G1 exists specifically to catch this early (M3a) before spending 40+ days on M3b–M5; allows clean **Re-scope** or provider swap. |
| **2. Android 15+/16 OTP redaction & lock-screen masking on transaction alerts** | If a provider includes 6-digit auth codes, virtual account numbers, or tokens inside a push body, Android 16 on `iQOO Z10R` may redact content before delivering to non-privileged listeners. | Test explicitly on `iQOO Z10R` (API 36) in M3a; detect redaction cleanly (`ING-06`) and measure redaction rate in Appendix C. |
| **3. `vivo` / `iQOO` default background restrictions + Deep Doze (`wait_for_unlock=true`)** | Confirmed ADB diagnostics show Light/Deep Doze active and `wait_for_unlock=true`. Under default battery settings (without whitelisting), the OS/OEM may delay idle exit or kill `NotificationListenerService` silently without calling `onListenerDisconnected()`. | Test default battery settings first (**no premature auto-whitelisting**); measure locked-screen delivery delays, `ING-12` recovery, and `ING-14` `HEARTBEAT_STALE` accuracy before applying `ING-10` guidance. |
| **4. Sideloaded APK restricted settings block** | Android 13–16 blocks toggling Notification Access on sideloaded debug builds until user unlocks "Allow restricted settings" in App Info. | Document and surface exact PoC manual setup step in M2 so dogfooding doesn't stall on permissions. |
| **5. Provider updates/re-posts mutate `key`/`id`/`posted_at` unpredictably** | Grouped summaries, Android 16 Live Updates (`ProgressStyle`), or notification cooldown bursts cause missed updates or duplicates. | Record `notification_key`, `notification_id`, `notification_tag`, `isGroupSummary`, and `posted_at` on every PoC event (`Section 8.1`) to establish empirical dedup rules before M3b. |
| **6. Low transaction volume (< 30 tx/provider in 14 days)** | Insufficient sample size skews Gate G1 percentages. | Plan 14-day window across active payday/bill cycles and allow extending window until ≥ 30 statement transactions accumulate per provider. |
| **7. Thousand-fold locale or 100x IDR exponent scaling bugs** | Mixing `Rp50.000` with `IDR 50,000.00` (1,000x) or confusing major Rupiah with `IDR` exponent `2` minor `sen` (100x) fails Gate G1 correctness (`0` tolerance). | Strict unit test gate (`TEST-001`) verifying both `PAR-12` ambiguity guard and IDR exponent `2` conversion before enabling PoC parser live. |
| **8. Multi-device generalizability blind spot** | `iQOO Z10R` (Android 16) pass results do not prove reliability on Samsung, Xiaomi, Oppo, or Android 8–15 devices. | Keep non-`iQOO Z10R` device compatibility explicitly flagged **UNVERIFIED** until additional physical test devices are tested post-PoC. |
| **9. Target-provider structural unknowns (`Bank Jago`, `DANA`, `BCA`)** | `BCA mobile` (`com.bca`) vs `myBCA` (`com.bca.mybca.omni.android`) overlap/differences, Bank Jago multi-pocket hints (`ACC-05`), and DANA payments funded by Bank Jago/BCA emitting **dual notifications** (`Section 11`) can distort coverage or double-count totals if guessed beforehand. | Discover installed packages via ADB, capture real `iQOO Z10R` fixtures per [PROVIDERS.md](file:///home/adomancer/Documents/project/W-Track/docs/PROVIDERS.md) before coding parsers, and establish explicit Gate G1 rules from observed evidence. |

---

## 8. PRD v1.5 Consideration List *(Out of Frozen v1.4 Scope)*

Per **PRD v1.4 (Frozen for Ingestion PoC)** and **Appendix B**, the following improvements are explicitly **excluded** from v1.4 MVP scope and tracked for v1.5 evaluation after MVP delivery:

1. **App Lock & Screen Protection:** Biometric / device-PIN lock screen on app resume plus `FLAG_SECURE` screenshot/app-switcher masking on financial screens.
2. **Privacy Notice Wording Refinement:** Clarifying Android's OS-level all-or-nothing `NotificationListenerService` scope vs W-Track's strict package allowlist.
3. **Post-G1 Quantitative MVP Quality Thresholds:** Defining numeric reliability/crash/latency SLAs beyond Gate G1.
4. **Excluded Foreign-Currency Indicator:** Explicit visual badge/summary pill highlighting non-base-currency transactions excluded from totals.
5. **Google Play Public Distribution Readiness:** Google Play sensitive-permissions declaration compliance review (`NotificationListenerService`, privacy policy, data-safety form).
6. **SMS Fallback for Non-Play / Optional Builds:** Opt-in `READ_SMS` ingestion adapter for banks that prefer SMS alerts over push notifications (isolated as a new `:integration` source without impacting Play policy compliance).
7. **Post-MVP Automatic Transfer Matching (`TRF-03`):** Confidence-scored automatic pairing suggestions building on M4 manual pairing patterns.
8. **Optional Open Banking / CSV Statement Import:** Importing historical bank PDF/CSV statements to backfill history prior to app installation.
