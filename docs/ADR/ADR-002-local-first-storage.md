# ADR-002: Local-First Storage and Offline Capability

**Date:** 2026-11-21

---

## 1. Status

**Accepted** — Confirmed baseline in PRD v1.4 (Sections 1.4, 2, 4.7, 5, and 19).

---

## 2. Relevant PRD Requirement References

- **PRD Section 1.4 (Non-goals):** Requiring a cloud account or backend; multi-device synchronization.
- **PRD Section 2 (Principles):** Local-first, Privacy-first, Offline-capable.
- **PRD Section 4.7:** `DATA-01` (store locally), `DATA-02` (offline usable), `DATA-05` (full backup), `DATA-08` (disable automatic cloud/device-transfer backup), `DATA-09` (atomic restore on fresh install).
- **PRD Section 4.2:** `ING-05` (process supported events locally).
- **PRD Section 9.4:** Export, deletion, and backup controls.
- **PRD Section 16:** MVP Acceptance Criteria 11 and 15.
- **PRD Section 19 (Confirmed Decisions):** Local-first storage/processing, no mandatory backend, automatic Android backup disabled.

---

## 3. Context and Problem

The application aggregates highly sensitive personal financial events (transaction amounts, counterparties, account hints, balances, and raw notification payloads). Requiring a remote backend would introduce server operational costs, authentication UX friction, data-breach liability, regulatory/privacy complexity, and network dependency for core flows such as capturing notifications and checking spending.

Conversely, purely local persistence creates a major data-durability problem: Android notifications are ephemeral. If a device is lost, reset, or replaced, missed notifications cannot be re-fetched from bank servers (PRD Section 20). Furthermore, because database encryption keys are bound to the device's Android Keystore (ADR-005), standard Android Auto Backup or Device-to-Device (D2D) migration would copy an unreadable SQLCipher file onto a new device.

---

## 4. Decision

### Confirmed Baseline (PRD v1.4)

1. **Strictly Local Persistence & Execution:**
   - All financial records, event inbox records, categorization rules, transfer matches, and aggregates are stored and evaluated on-device using **Room / SQLite** (encrypted via SQLCipher per ADR-005).
   - Non-financial user preferences and app settings (theme selection, retention window duration, backup reminder thresholds, allowlist metadata mirrored or linked, heartbeat timestamp, onboarding flags) are stored locally via **Jetpack DataStore (Preferences)**.
   - The MVP declares **zero** `INTERNET` permission requirement for core product behavior and has **no** mandatory backend, user account, or cloud sync.
2. **Offline-First Reactivity:**
   - All Compose UI state derives from reactive Room `Flow` queries and DataStore `Flow`s (`DATA-02`). No feature blocks on network reachability.
3. **Disable Android Auto Backup & Device Transfer (`DATA-08`):**
   - `AndroidManifest.xml` explicitly sets `android:allowBackup="false"`, and additionally configures explicit `android:dataExtractionRules` (API 31+) and `android:fullBackupContent` (API < 31) excluding the Room database, DataStore files, and wrapped key material from cloud backup and device-to-device transfer.
   - Rationale: An automatically transferred database cannot be decrypted without the original device's hardware-backed Android Keystore key, and copying financial data to Google Drive violates the user-controlled backup model (`DATA-10`).
4. **User-Initiated Encrypted Full Backup (`DATA-05`, `DATA-09`, `DATA-10`):**
   - Data portability and device replacement recovery are handled exclusively through user-initiated, passphrase-encrypted full backups saved via Android Storage Access Framework (SAF) and restored atomically on a fresh install (detailed in ADR-006).

---

## 5. Alternatives Considered

| Alternative | Description | Reason Rejected |
| :--- | :--- | :--- |
| **Cloud-synced backend (e.g., Firebase / REST API)** | Upload parsed transactions or encrypted blobs to a remote backend for multi-device sync and backup. | Rejected. Violates PRD Section 1.4 non-goals and privacy-first positioning; adds auth, backend hosting, and external transmission attack surface to MVP. |
| **Allow Android Auto Backup (`allowBackup="true"`)** | Rely on OS cloud backup or D2D transfer to migrate app data to new phones. | Rejected (`DATA-08`). Device-bound Android Keystore keys do not transfer cleanly across devices, causing silent unreadable-DB failures on restore; also transfers financial records to third-party cloud storage without user passphrase encryption. |
| **SQLDelight or NoSQL (Realm / ObjectBox)** | Replace Room/SQLite with an alternative local persistence engine. | Rejected. PRD Section 5 specifies Room/SQLite; Room provides first-class Kotlin Coroutines/`Flow` reactivity, compile-time SQL verification, mature schema migration testing (`MigrationTestHelper`), and direct SQLCipher `SupportOpenHelperFactory` integration. |
| **Storing all preferences inside SQLite vs. DataStore** | Put app settings and allowlist exclusively in Room vs. splitting settings in DataStore. | Hybrid decision: Financial tables, allowlist (`transaction_sources`), and `capture_gaps` live in encrypted Room (so single-DB transactional integrity and SQL queries apply and full backup exports one consistent snapshot); lightweight UI/lifecycle preferences (`theme`, `last_heartbeat_at`, `retention_days`, `backup_reminder_prefs`) live in DataStore and are explicitly serialized into the full backup (`DATA-05`). |

---

## 6. Technical Justification

- **Zero-Network Threat Reduction:** Keeping ingestion, parsing, categorization, and reporting completely local eliminates network interception, server compromise, and credential storage risks.
- **Deterministic Offline Performance:** Local SQLite queries with indexed columns (`account_id`, `occurred_at`, `category_id`, `processing_status`) deliver sub-frame reactive updates via Kotlin `Flow` regardless of connectivity.
- **Clean Separation of Concerns for Recovery:** Disabling OS backup avoids partial/corrupt Keystore states on new devices and forces explicit user ownership of encrypted `.wtrackbackup` archives (`DATA-05`, `DATA-09`, `DATA-12`, `DATA-13`).

---

## 7. Consequences and Trade-Offs

- **Positive:**
  - Complete offline availability and zero cloud infrastructure dependency or recurring cost.
  - Strong privacy guarantee: financial payloads never leave the device unless the user explicitly runs backup or CSV export (`DATA-10`, `DATA-11`).
- **Negative / Risks:**
  - Device loss, OS factory reset, or app uninstallation without a recent full backup permanently destroys transaction history (`PRD Section 20`). Mitigated by backup reminders (`DATA-12`), backup self-verification (`DATA-13`), and clear onboarding/settings disclosures.
  - Post-restore capture gap: Android does not restore `NotificationListenerService` grants across installs, so users must re-grant Notification Access after restoring (`DATA-09`, PRD Section 4.7).
- **Future Extensibility:**
  - Keeping all persistence behind `wtrack.app.domain.repository` interfaces enables adding optional, opt-in cloud sync or desktop export plugins post-MVP without rewriting domain logic or UI.
