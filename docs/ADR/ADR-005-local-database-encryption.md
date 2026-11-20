# ADR-005: Local Database Encryption and Key Management

**Date:** 2026-11-21

---

## 1. Status

**Proposed — MUST Be Confirmed & Accepted by Owner BEFORE M1 (First Room Schema Implementation)**

PRD Sections 5, 9.2, 17.1, and 19 require resolving database encryption and key lifecycle **before** creating the first persistent database schema, including during the ingestion proof of concept (no plaintext-to-encrypted migration path is permitted).

> **Critical Correction vs. Prior Commit (`38001da`):** The deleted initial setup in `38001da` specified `net.zetetic:android-database-sqlcipher:4.5.4` in `libs.versions.toml` and the draft ADR-005. That artifact is **end-of-life (EOL)**, lacks 16KB native memory page alignment, and crashes (`SIGSEGV`) on 16KB page-size Android 15+ devices/emulators. This ADR updates the proposed recommendation to **`net.zetetic:sqlcipher-android` (4.6.1+ / latest 4.x)** using `net.zetetic.database.sqlcipher.SupportOpenHelperFactory`.

---

## 2. Relevant PRD Requirement References

- **PRD Section 4.7:** `DATA-03` (maintained database encryption at rest), `DATA-04` (Keystore-protected key material), `DATA-06` (delete data), `DATA-08` (automatic backup disabled), `DATA-09` (restore as recovery path).
- **PRD Section 4.4:** `REV-05` (Needs Review payloads protected by database encryption).
- **PRD Section 9.2 & 9.3:** Encryption requirements, single encryption layer (no separate second payload-encryption layer), key-failure locked state behavior.
- **PRD Section 14.2 & 16:** Reboot reopen, key-missing/invalid locked-state tests (`Acceptance Criterion 13`).
- **PRD Section 17.1 & 18:** PoC step 2, `SEC-001`, `TEST-004`.

---

## 3. Context and Problem

The local SQLite database holds full personal transaction histories, account hints, balances, merchant patterns, and—for up to 7–30 days—raw financial notification payloads (`notification_events.payload_json`). Storing an unencrypted SQLite file exposes sensitive financial data to device extraction, root inspection, or backup artifacts, and migrating a plaintext database to an encrypted one later risks leaving unencrypted WAL/journal/freelist residue on flash storage.

Simultaneously, `NotificationListenerService` must persist incoming transaction notifications immediately in the background—even when the phone screen is locked after the user's initial device unlock (Direct Boot is not required, as financial apps post notifications post-unlock). Using `EncryptedSharedPreferences` (`androidx.security:security-crypto`) is no longer viable because Jetpack deprecated it due to main-thread blocking and widespread OEM Keystore corruption bugs.

---

## 4. Decision

### A. Proposed Database Encryption Stack (Pending Owner Confirmation Before M1)

1. **Encryption Engine:** Use **`net.zetetic:sqlcipher-android`** (version `4.6.1` or latest stable `4.x`, e.g., `4.17.0` / latest verified release at M0/M1) paired with `androidx.sqlite:sqlite-ktx`, wired into Room via `net.zetetic.database.sqlcipher.SupportOpenHelperFactory` (loading `System.loadLibrary("sqlcipher")`).
   - Provides native 16KB ELF page-size compliance required for Android 15+ (`targetSdk >= 35`) and uses AES-256-CBC with HMAC-SHA512 per page (SQLCipher 4 default).
   - Enabled from **Database Schema v1** in M1, including during all PoC captures.
2. **Single-Layer Encryption Scope (PRD v1.4 Section 9.2):**
   - SQLCipher protects the entire `.db`, `-wal`, and `-shm` database content at rest, including `notification_events.payload_json`.
   - No redundant second Keystore-backed application-level encryption layer is wrapped around `payload_json`.
3. **Key Architecture & Lifecycle (Two-Tier Wrapped Key Design):**
   - **Key Encryption Key (KEK):** A 256-bit `AES/GCM/NoPadding` secret key generated inside **Android Keystore** (`AndroidKeyStore`) under a fixed alias (e.g., `wtrack_db_kek_v1`).
     - Properties: `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, `KEY_SIZE = 256`, `setRandomizedEncryptionRequired(true)`.
     - Hardware backing: Prefer `StrongBox` (`setIsStrongBoxBacked(true)` on API 28+ with graceful fallback to standard TEE Keystore if `StrongBoxUnavailableException` is thrown).
     - Authentication binding: `setUserAuthenticationRequired(false)` and `setUnlockedDeviceRequired(false)` so background `NotificationListenerService` can open/append to Room while the device screen is locked after first user unlock. *(Trade-off documented below; owner must confirm.)*
   - **Database Encryption Key (DEK):** A cryptographically random 32-byte (256-bit) key generated via `java.security.SecureRandom` on first setup (passed as raw 32-byte passphrase/key material to `SupportOpenHelperFactory`, avoiding weak string encoding).
   - **Wrapped DEK Storage:** The KEK encrypts the DEK using AES-256-GCM (fresh 12-byte IV per wrap). The resulting `{wrap_version, iv, ciphertext_with_tag}` blob is stored atomically in app-private storage (`filesDir/keystore/db_key.pb` via Proto DataStore or an atomic `AtomicFile` protobuf/JSON file—never in deprecated `EncryptedSharedPreferences`).
   - **Memory Hygiene:** Raw DEK `ByteArray` copies passed to `SupportOpenHelperFactory` should be zeroized (`Arrays.fill(bytes, 0.toByte())`) once SQLCipher initializes, noting JVM/SQLCipher internal buffer limitations.
4. **Startup State Machine & Key-Failure Policy (PRD Section 9.2):**
   Before opening Room, `DatabaseKeyManager` inspects both the database file (`context.getDatabasePath(DB_NAME)`) and the wrapped DEK artifact, classifying startup into four explicit states:
   - `FRESH_INSTALL`: Neither DB file nor wrapped DEK exists → generate KEK + DEK, wrap and persist DEK atomically, then open new encrypted Room DB.
   - `READY`: Both DB file and valid wrapped DEK exist → unwrap DEK in memory, open Room DB, verify integrity (`SELECT count(*) FROM sqlite_master` or schema probe).
   - `CORRUPT_INITIAL_STATE`: Wrapped DEK exists without DB file (interrupted setup) → clean up orphan key ONLY when verified no DB file exists (`dbFile.exists() == false`), re-initialize cleanly.
   - `LOCKED_KEY_MISSING_OR_INVALID`: DB file **exists** (`dbFile.exists() && dbFile.length() > 0`), **AND** any of the following holds:
     - Wrapped DEK file is missing, unreadable, or corrupt.
     - Keystore alias is missing or throws `KeyPermanentlyInvalidatedException` / `AEADBadTagException` / `UnrecoverableKeyException`.
     - SQLCipher fails to decrypt/open the existing DB file.
     **Mandatory Rule:** **NEVER silently delete, recreate, or overwrite the existing database file.** Keep the app in an explicit `DATABASE_LOCKED` state, disable ingestion/UI queries cleanly, surface a clear Locked Screen explaining that the device key is unavailable/invalid, and offer two explicit actions: **(a) Restore from Encrypted Full Backup (`DATA-09`)**, or **(b) Explicitly Wipe & Start Fresh (`DATA-06`, double-confirmed destructive action)**.
5. **Key Rotation Policy:**
   - **KEK Rotation:** Generate new Keystore alias (`wtrack_db_kek_v2`), unwrap DEK with `v1`, re-wrap DEK with `v2`, atomically swap wrapped DEK file, then delete `v1` alias.
   - **DEK Rotation:** When scheduled or requested, execute SQLCipher `PRAGMA rekey` inside a controlled exclusive DB maintenance operation, wrap the new DEK, and verify open before discarding old wrapped key.
6. **Automatic Backup Exclusion (`DATA-08`):**
   - `allowBackup="false"`, `dataExtractionRules`, and `fullBackupContent` exclude both `databases/` and `files/keystore/` from OS cloud/D2D transfers because Keystore KEKs are device-bound.

### B. Owner Confirmation Checklist for ADR-005

| Decision Point | Proposed Choice | Alternative | Owner Action |
| :--- | :--- | :--- | :--- |
| SQLCipher artifact | `net.zetetic:sqlcipher-android:4.6.1+` | Commercial SQLite SEE / Jetpack Room unencrypted (violates `DATA-03`) | Confirm `sqlcipher-android` (Apache/BSD-style community license + 16KB support) |
| Key wrapping mechanism | Direct Android Keystore `AES/GCM` KEK + `AtomicFile`/DataStore wrapped DEK | Google Tink `AndroidKeysetManager` + `datastore-tink` | Confirm direct Keystore KEK (minimal dependencies) vs Tink (more deps, battle-tested keyset wrapper) |
| Screen-lock access | `userAuthenticationRequired = false`, `unlockedDeviceRequired = false` (background listener writes while locked after boot unlock) | `unlockedDeviceRequired = true` (blocks capture whenever screen turns off) | Confirm background capture priority over locked-screen DB access |

---

## 5. Alternatives Considered

| Alternative | Description | Reason Rejected |
| :--- | :--- | :--- |
| **`net.zetetic:android-database-sqlcipher:4.5.4`** | Legacy artifact referenced in commit `38001da`. | **Rejected (Critical Defect).** Officially deprecated/EOL; native `.so` binaries lack 16KB ELF alignment and crash on Android 15+ 16KB page-size devices. |
| **Android Keystore + Unencrypted Room** | Claim Keystore security while leaving SQLite file unencrypted. | **Rejected (PRD 9.2).** Explicitly forbidden by PRD (`DATA-03`): Keystore alone does not encrypt SQLite pages on disk. |
| **`androidx.security:security-crypto` (`EncryptedSharedPreferences`)** | Store DB passphrase in `EncryptedSharedPreferences`. | **Rejected.** Officially deprecated in AndroidX; prone to synchronous main-thread I/O hangs and OEM Keystore corruption crashes. |
| **Plaintext PoC DB → Encrypted M1 DB migration** | Start PoC unencrypted and apply `sqlcipher_export()` later. | **Rejected (PRD 9.2 & 17.1).** Real financial notifications enter the PoC; plaintext SQLite pages/WAL fragments must never touch disk. |
| **User PIN/biometric prompt required on every DB key unwrap** | Set `setUserAuthenticationRequired(true)` on the Keystore KEK. | **Rejected for MVP.** Causes `NotificationListenerService` to fail decrypting and persisting incoming events whenever the screen is locked or authentication timeout expires. (App-level biometric UI lock is deferred to PRD 1.5 considerations.) |
| **Second encryption layer on `payload_json`** | Encrypt raw payload column with a second Keystore key inside SQLCipher. | **Dropped in PRD 1.4.** Both keys share the same Keystore trust boundary; adds key-management failure modes with zero new security boundary. |

---

## 6. Technical Justification

- **16KB Compliance Future-Proofing:** Adopting `net.zetetic:sqlcipher-android` (>= 4.6.1) guarantees compatibility with Android 15/16+ devices and Google Play's mandatory 16KB page-size enforcement without ELF patching hacks.
- **No Silent Data Loss:** Separating `LOCKED_KEY_MISSING_OR_INVALID` from `FRESH_INSTALL` prevents catastrophic silent database recreation if Keystore glitches or app-data corruption occurs (`TEST-004`).
- **Tink vs. Direct Keystore Note:** Direct `AndroidKeyStore` AES-GCM wrapping of a 32-byte DEK via `AtomicFile` requires zero third-party crypto wrappers beyond standard JCA and keeps binary size small, while Google Tink (`com.google.crypto.tink:tink-android`) remains an easy drop-in upgrade if multi-keyset rotation is desired later.

---

## 7. Consequences and Trade-Offs

- **Positive:**
  - Encrypted persistence from schema v1 with zero plaintext exposure of transactions or payloads on disk.
  - Predictable, testable failure semantics when keys are corrupted, deleted, or invalidated.
- **Negative / Risks:**
  - **APK Size & Native ABI Footprint:** SQLCipher adds ~4–6 MB of native `.so` libraries across ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`).
  - **Unrecoverable Lost Keys:** If Android Keystore loses or invalidates the KEK (factory reset, OS bug, clearing app storage partially), the `.db` is permanently unreadable; user recovery relies strictly on recent encrypted backups (`ADR-006`).
  - **Memory-Resident DEK While Running:** Because `NotificationListenerService` operates in the background while the screen is locked (post-first-unlock), the unlocked SQLCipher DB handle / page cache remains open in app process RAM until the OS kills the process.
