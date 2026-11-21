# W-Track

**Local-first, privacy-focused Android personal finance aggregator powered by on-device notification ingestion and SQLCipher encryption.**

W-Track automatically collects transaction notifications from explicitly allowlisted Indonesian banking and e-wallet apps, normalizes them into a single local ledger, highlights uncertain alerts in **Needs Review**, and helps you track balances, transfers, categories, and cash flow without uploading your financial data or storing banking credentials.

- **Status:** MVP Implemented (`M0`–`M5`) — Active Gate G1 Daily-Driver Validation on `iQOO Z10R (Android 16)`
- **Version:** `0.1.0-poc` | **SDK:** `minSdk 26` · `targetSdk 36` · `compileSdk 37`
- **Core Stack:** Kotlin `2.2.21` · Jetpack Compose (Material 3) · Hilt `2.60.1` · Room `2.8.5` · SQLCipher `4.20.0`

---

## Privacy & Security Boundaries

- **100% Local-First & Offline:** No cloud account, no telemetry, and no mandatory backend.
- **Zero Banking Credentials:** Never asks for or stores bank logins, passwords, PINs, OTPs, or CVVs.
- **No Screen Scraping or Accessibility Service:** Reads only standard Android `NotificationListenerService` events from apps you explicitly enable.
- **Encrypted Storage & Backups:** Local SQLite database is encrypted with `sqlcipher-android` + Android Keystore AES-GCM key wrapping; full `.wtrackbackup` archives (`WTBK` v1) use Argon2id + AES-256-GCM.
- **Safe Failure Handling:** Unseen formats or Android-redacted security alerts never guess amounts or directions — they route cleanly to **Needs Review** (or discard payloads for `SECURITY_NOTICE`).

---

## Current Progress (What's Done)

| Milestone | Scope & Implemented Features | Verification Status |
| :--- | :--- | :--- |
| **M0: Foundation** | 3-module Gradle setup (`:app`, `:integration:api`, `:integration:providers`) + `build-logic` conventions, Hilt DI, Material 3 Light/Dark theme, pure-Kotlin boundary tests. | Complete (Unit tests passing) |
| **M1: Encrypted Data Foundation** | Room Schema v1 + SQLCipher `4.20.0` + `DatabaseKeyManager` (KEK/DEK lifecycle + `LOCKED_KEY_MISSING_OR_INVALID` lock screen safety). | **Hardware-verified** on `iQOO Z10R` (`SqlCipherInstrumentedTest` passing; encrypted `wtrack.db` header verified) |
| **M2: Notification Ingestion** | Provider-agnostic `WtrackNotificationListenerService`, synchronous allowlist filter, `activeNotifications` catch-up, 3-tier deduplication, `ING-12`/`13`/`14` recovery & heartbeat, M3a sample capture protection, anonymized JSON export, purge, and re-parse. | Complete & live on daily driver |
| **M3: Providers & Domain Engines** | Production parsers for Bank Jago (`v2`), myBCA (`v1`), and DANA (`v1`); 16-row Section 4.6 aggregate engine; balance correction & checkpoints; manual transfer pairing with derived fees; auto-categorization rules with backfill; `GateG1Evaluator`. | Complete & tested against anonymized captures |
| **M4: Material 3 UI** | Onboarding + Notification Access & battery guidance, Dashboard, Accounts (grouped IDR/USD/JPY input & balance correction), Activity (filtering, edit, void, transfer linking), Needs Review, Rules, Insights, and Settings. | Complete & running on device |
| **M5: Reliability & Safety** | Encrypted `.wtrackbackup` (`WTBK` v1 Argon2id / PBKDF2 fallback + AES-256-GCM AAD) export/verify/atomic restore, CSV export with unencrypted warning, `RetentionPurgeWorker`, and complete data wipe. | Implemented & unit/Robolectric-tested |

---

## Supported Providers (Verified on `iQOO Z10R` / Android 16)

| Priority | Provider | Package ID | Parser | What's Supported |
| :-: | :--- | :--- | :--- | :--- |
| **1** | **Bank Jago** | `com.jago.digitalBanking` (`v8.99.1`) | `JagoTransactionParser` **v2** | Incoming & outgoing transfers, Android 16 group summaries, and **`Main Pocket`-only** pocket-to-pocket monitoring (movements solely between non-Main sub-pockets are ignored cleanly). |
| **2** | **DANA** | `id.dana` (`v2.152.0`) | `DanaTransactionParser` **v1** | Incoming transfers, quest/mission promo filtering on the shared FCM channel, and Android 16 group summaries. |
| **3** | **myBCA** | `com.bca.mybca.omni.android` (`v2.23.0`) | `BcaTransactionParser` **v1** | Financial Diary received & spent pushes and Android 16 group summaries. *(BCA mobile `com.bca` v4.9.5 is verified installed; zero Financial Diary pushes observed from `com.bca`.)* |

> Any notification template not yet covered (such as QRIS payments, outgoing DANA transfers, refunds, or failed attempts) automatically routes to **Needs Review** without guessing fields and can be re-parsed later as parsers expand.

---

## What's Next (Remaining Work & Roadmap)

### 1. Gate G1 Empirical Validation (In Progress)
- Collect **7–14 consecutive days** of real usage with **≥ 30 statement transactions per provider** against official provider statements as ground truth (`docs/PROVIDERS.md` Appendix C).
- Validate Gate G1 acceptance thresholds: **Coverage ≥ 90%**, **Completeness ≥ 95%**, and **0** unexplained wrong amounts or directions.

### 2. Provider Parser Expansion
- Capture and add anonymized templates for **QRIS merchant payments**, **outgoing DANA transfers**, **refunds & reversals**, **failed attempts**, and **transfer admin fees**.
- Observe and handle **dual-notifier overlap** (e.g., a DANA QRIS purchase funded by Bank Jago or myBCA debit firing notifications from both apps).

### 3. On-Device Hardware & OEM Reliability Checklist
Full execution tracking lives in [docs/PHYSICAL_DEVICE_TEST_CHECKLIST.md](docs/PHYSICAL_DEVICE_TEST_CHECKLIST.md), including:
- **SQLCipher & Keystore edge cases:** StrongBox vs TEE audit, `LOCKED_KEY_MISSING_OR_INVALID` recovery, and BFU/AFU reboot reopen tests.
- **OriginOS / Android 16 lifecycle:** Locked-screen AFU ingestion, `am kill` listener recovery, default battery/Doze delay baseline vs `ING-10` exemption, permission revocation (`ACCESS_REVOKED`) gap recording, Android 16 OTP redaction, and rapid burst cooldown.
- **Backup, Purge & Release QA:** On-device `.wtrackbackup` round-trip restore verification, `RetentionPurgeWorker` schedule check, Light/Dark theme audit, and release APK verification.

### 4. Post-MVP Direction
- Additional Indonesian banks & e-wallets isolated cleanly inside `:integration:providers`.
- Multi-OEM testing beyond `vivo / iQOO Z10R` and refined credit-card workflows.

---

## Architecture & Modules

```text
W-Track/
├── app/                     # Android app: Compose Material 3 UI, domain use-cases, Room/SQLCipher data, WorkManager, NLS ingestion
├── integration/
│   ├── api/                 # Pure Kotlin parser contracts (TransactionParser, ParseResult, NotificationEvent, ParsedTransaction)
│   └── providers/           # Isolated provider parsers (jago, dana, bca), shared locale/date helpers, catalog & anonymized fixtures
├── build-logic/             # Shared Gradle convention plugins
└── docs/                    # PRD, Architecture, Technical Spec, Database ERD, Security, ADRs, Provider & Device checklists
```

**Tech Stack Details:**
- **UI & State:** Jetpack Compose (BOM `2026.09.00`), Material 3, Navigation Compose `2.10.2`, ViewModel + `StateFlow`
- **Persistence:** Room `2.8.5` (`KSP`), `net.zetetic:sqlcipher-android:4.20.0`, DataStore Preferences `1.3.0`
- **Background & Crypto:** WorkManager `2.12.0` (`hilt-work`), BouncyCastle `bcprov-jdk18on:1.80.2`, `kotlinx-serialization-json:1.9.0`
- **Testing:** JUnit 4, Google Truth, Turbine, Kotlinx Coroutines Test, Room Testing, Robolectric `4.18.1`, AndroidX Test

---

## Build & Test

Run unit and Robolectric tests:

```bash
./gradlew test testDebugUnitTest
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

Run instrumented Android tests (e.g., SQLCipher verification on a connected device):

```bash
./gradlew connectedDebugAndroidTest
```

---

## Documentation

| Document | Purpose |
| :--- | :--- |
| [docs/PRD.md](docs/PRD.md) | Frozen Product Requirements Document (v1.4) & Appendix C Gate G1 format |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Module boundaries, layer dependencies, and runtime architecture |
| [docs/TECHNICAL_SPEC.md](docs/TECHNICAL_SPEC.md) | Ingestion pipeline, normalization rules, deduplication, and calculation specs |
| [docs/DATABASE_ERD.md](docs/DATABASE_ERD.md) | Room schema entities, indices, foreign keys, and invariants |
| [docs/SECURITY.md](docs/SECURITY.md) | Threat model, SQLCipher KEK/DEK lifecycle, retention rules, and backup crypto |
| [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) | Full `M0`–`M5` milestone breakdown and engineering backlog (`45` items) |
| [docs/PROVIDERS.md](docs/PROVIDERS.md) | Installed package registry, observed templates, and Gate G1 evidence table |
| [docs/PHYSICAL_DEVICE_TEST_CHECKLIST.md](docs/PHYSICAL_DEVICE_TEST_CHECKLIST.md) | Physical `iQOO Z10R` verification checklist |
| [docs/ADR/](docs/ADR/) | Architecture Decision Records (`ADR-001` through `ADR-006`) |
