# ADR-005: Local Database Encryption, SQLCipher Evaluation, and Locked-Screen Keystore Policy

**Date:** 2026-11-21

---

## 1. Status

**Proposed — Pending Final Owner Acceptance (MUST Be Accepted Before M1 / First Room Schema Implementation)**

PRD Sections 5, 9.2, 17.1, and 19 require resolving database encryption and key lifecycle **before** creating the first persistent database schema, including during the ingestion proof of concept (no plaintext-to-encrypted migration is permitted).

- **Verified Library Evaluation (`4.20.0` vs `4.17.0`):** `net.zetetic:sqlcipher-android:4.20.0` is evaluated below as the latest stable 4.x release (with **SQLCipher 5 beta explicitly excluded** for production and **legacy `android-database-sqlcipher:4.5.4` rejected**). A verified `compileSdk` dependency alignment choice (`Option A` vs `Option B`) is presented for final owner sign-off alongside the locked-screen Keystore key policy.

---

## 2. Relevant PRD Requirement References

- **PRD Section 4.7:** `DATA-03` (maintained database encryption at rest), `DATA-04` (Keystore-protected key material), `DATA-06` (delete data), `DATA-08` (automatic backup disabled), `DATA-09` (restore as recovery path).
- **PRD Section 4.4:** `REV-05` (Needs Review payloads protected by database encryption).
- **PRD Section 9.2 & 9.3:** Encryption requirements, single encryption layer (no separate second payload-encryption layer), key-failure locked state behavior.
- **PRD Section 14.2 & 16:** Reboot reopen, key-missing/invalid locked-state tests (`Acceptance Criterion 13`).
- **PRD Section 17.1 & 18:** PoC step 2, `SEC-001`, `TEST-004`.

---

## 3. Context and Problem

The local SQLite database holds full personal transaction histories, account hints, balances, merchant patterns, and—for up to 7–30 days—raw financial notification payloads (`notification_events.payload_json`). Plaintext SQLite files or unencrypted WAL/journal residue violate `DATA-03` and `PRD Section 17.1`.

Simultaneously, two concrete engineering constraints shape ADR-005:
1. **SQLCipher Version & SDK Alignment:** The prior scaffold (`38001da`) referenced `net.zetetic:android-database-sqlcipher:4.5.4`, which is EOL and lacks 16KB memory page-size alignment. Meanwhile, Zetetic's current stable 4.x release **`net.zetetic:sqlcipher-android:4.20.0`** pairs with `androidx.sqlite:sqlite:2.7.0` and was compiled against **`compileSdk 37`** (starting in `4.18.0`), whereas our proposed SDK baseline is `minSdk = 26`, `compileSdk = 36`, `targetSdk = 36`.
2. **Locked-Screen Ingestion Requirement:** Financial push notifications arrive when the phone screen is **off and locked** (after the user has unlocked the device once following boot — the **After First Unlock / AFU** state). Android Keystore flags like `setUnlockedDeviceRequired(true)` or `setUserAuthenticationRequired(true)` would block background decryption while the screen is off/locked.

---

## 4. Decision

### A. Evaluation of SQLCipher for Android `4.20.0` (vs. `4.17.0` & Prohibited Releases)

#### Release Evaluation Summary

| Artifact / Version | Status | SQLite Baseline & Crypto | Room / `androidx.sqlite` / SDK Compatibility | Verdict |
| :--- | :--- | :--- | :--- | :--- |
| **`net.zetetic:sqlcipher-android:4.20.0`** | **Latest Stable 4.x** | SQLite `3.54.1`; LibTomCrypt `1.18.2` community backend; SQLCipher 4 format (AES-256-CBC + HMAC-SHA512); full 16KB alignment; `arm64-v8a` | Supports `minSdk >= 23`, Room 2 (`2.8.x`) & Room 3, pairs with `androidx.sqlite:sqlite:2.7.0`. **Note:** `4.18.0`–`4.20.0` bumped library `compileSdk` to **37**. Fixes `PRAGMA cipher_migrate` deferred errors & `sqlcipher_export()` schema quoting. | **Recommended** (provided `compileSdk` alignment below is approved) |
| **`net.zetetic:sqlcipher-android:4.17.0`** | **Stable 4.x** (July 2026) | SQLite `3.53.3` (fixes CVE-2026-11822 / CVE-2026-11824); full 16KB alignment; `arm64-v8a` | Supports Room `2.8.4`/`2.8.5` and `androidx.sqlite:sqlite:2.6.2` cleanly under **`compileSdk = 36`**. | **Fallback Stable 4.x** if strict `compileSdk = 36` rejects `4.20.0` AAR metadata |
| **`net.zetetic:sqlcipher-android:5.0.0-beta*`** | **Beta / Pre-release** | Breaking SQLCipher 5 format (AES-256-GCM + 8KB default page size; non-backward-compatible with v4) | Unstable codec/VFS churn between beta drops. | **PROHIBITED** for production / PoC |
| **`net.zetetic:android-database-sqlcipher:4.5.4`** *(in `38001da`)* | **EOL / Deprecated** | Legacy 4KB-only native `.so` alignment | Breaks on 16KB page-size devices; incompatible with modern `androidx.sqlite` (`2.6+`/`2.7`). | **PROHIBITED** |

#### Resolving `sqlcipher-android:4.20.0` vs. `compileSdk = 36` (`[OWNER CONFIRMATION REQUIRED]`)
- **Option 1 (Recommended — Decouple `compileSdk = 37` [GA June 2026], keep `targetSdk = 36`, `minSdk = 26`):** `compileSdk` controls only compile-time AAR stubs; keeping `targetSdk = 36` ensures **zero Android 17 runtime changes or beta targeting**, aligns execution with the Android 16 (`iQOO Z10R`) device, and lets `:app` adopt **`net.zetetic:sqlcipher-android:4.20.0`** + `androidx.sqlite:sqlite:2.7.0` + `Room 2.8.5` cleanly without AAR metadata suppression.
- **Option 2 (Strict `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`):** Verify `sqlcipher-android:4.20.0` AAR `minCompileSdk` at M0 Gradle sync; if AGP rejects `4.20.0` under `compileSdk = 36`, use **`net.zetetic:sqlcipher-android:4.17.0`** + `androidx.sqlite:sqlite:2.6.2` + `Room 2.8.4`/`2.8.5` + `core-ktx:1.18.0` (never use `android.suppressUnsupportedCompileSdk`).

*Integration pattern (identical for `4.17.0` and `4.20.0`):* Load `System.loadLibrary("sqlcipher")` early, wrap a 32-byte random DEK via `net.zetetic.database.sqlcipher.SupportOpenHelperFactory(dekBytes)` in `Room.databaseBuilder(...)`, and rely on the single SQLCipher layer for both transactions and `payload_json` (`PRD Section 9.2`).

---

### B. Locked-Screen Ingestion & Android Keystore Key Policy

#### Device Lock State Matrix

| Device State | Screen / Credential State | Expected Ingestion Behavior | Keystore KEK Availability |
| :--- | :--- | :--- | :--- |
| **BFU (Before First Unlock)** | Device freshly rebooted; user has **not** entered PIN/biometric yet | Bank apps and normal user services are locked under File-Based Encryption (FBE); notifications are not yet flowing for credentialed apps. | Unavailable (OS FBE credential-encrypted storage `/data/user/0` is locked — **expected**). |
| **AFU (After First Unlock — Screen Off / Locked)** | User unlocked once post-boot; screen later turned off & locked | **REQUIRED:** `NotificationListenerService` MUST receive allowlisted notifications and persist them to encrypted Room immediately. | **MUST remain available** for background service decryption. |
| **Interactive Unlocked** | User actively using app | Full Room read/write and UI flows. | Available. |

#### Proposed Keystore Policy (`wtrack_db_kek_v1`)
1. **Wrapping Key (KEK):** 256-bit `AES/GCM/NoPadding` key in `AndroidKeyStore`:
   - `setRandomizedEncryptionRequired(true)`
   - Prefer `setIsStrongBoxBacked(true)` on API 28+ with graceful TEE fallback on `StrongBoxUnavailableException`.
   - **`setUserAuthenticationRequired(false)`** — **Required** so background `NotificationListenerService` can unwrap the DEK during AFU without interactive biometric/PIN prompts.
   - **`setUnlockedDeviceRequired(false)`** — **Explicitly Required (DO NOT set `true`).** Enabling `setUnlockedDeviceRequired(true)` causes Android Keystore to refuse key unwrap operations whenever the device screen is locked—meaning a process restart, listener rebind, or background worker wakeup while the screen is off/locked (`AFU`) fails decryption, drops incoming financial notifications, or falsely triggers `LOCKED_KEY_MISSING_OR_INVALID`.
2. **Database Encryption Key (DEK):** 32-byte `SecureRandom` key wrapped by the KEK with fresh 12-byte GCM IV and persisted via `AtomicFile` / Proto DataStore in app-private storage (`filesDir/keystore/db_key.pb`, explicitly avoiding deprecated `EncryptedSharedPreferences`). Zeroize raw DEK byte arrays after passing to `SupportOpenHelperFactory`.

#### Security Trade-Offs & Compensating Controls
- **Trade-Off:** Because `setUnlockedDeviceRequired(false)` and `setUserAuthenticationRequired(false)` allow background decryption during **AFU** while the screen is locked, an attacker with physical possession **and** a live root/ADB-debugging exploit on an already-unlocked (AFU) phone could access Keystore/RAM secrets without a second per-query hardware lock gate.
- **Why Acceptable for Ingestion PoC/MVP:**
  - Before first unlock (**BFU**), Android File-Based Encryption (FBE) locks app-private files and Credential-Encrypted storage completely.
  - Under standard Android security boundaries, non-privileged apps cannot read W-Track's `filesDir/keystore/`, `databases/`, or process memory.
  - Setting `setUnlockedDeviceRequired(true)` directly breaks the core automatic-capture product requirement (`ING-01`) on locked screens, especially combined with `iQOO Z10R` Doze (`wait_for_unlock=true`).
- **Compensating Controls:** `android:allowBackup="false"` (`DATA-08`), zero plaintext payload logging, bounded retention purge (`7d` verified / immediate ignore purge, `SEC-002`), single-DB encryption from v1, and optional post-MVP UI biometric lock + `FLAG_SECURE` tracked in PRD 1.5.

---

### C. Startup Key-Failure Policy & Mandatory Verification Tests

1. **Startup State Machine (`FRESH_INSTALL`, `READY`, `CORRUPT_INITIAL_STATE`, `LOCKED_KEY_MISSING_OR_INVALID`):**
   - If `dbFile.exists() && dbFile.length() > 0` and wrapped DEK is missing/corrupt, Keystore throws `KeyPermanentlyInvalidatedException` / `UnrecoverableKeyException` / `AEADBadTagException`, or SQLCipher fails to open: **NEVER overwrite or recreate an empty DB**. Enter explicit `DATABASE_LOCKED` screen pointing to backup restore (`DATA-09`) or explicit confirmed wipe (`DATA-06`).
2. **Mandatory Verification Tests (`SEC-001`, `TEST-004`):**
   - **T-KEY-01 (Locked-Screen AFU Capture):** Cold-launch / rebind `NotificationListenerService` while `iQOO Z10R` screen is off and locked (AFU) → verify KEK unwraps DEK, SQLCipher opens, and synthetic/test notification persists without errors.
   - **T-KEY-02 (Reboot + Post-Unlock Recovery):** Reboot physical device → wait for first unlock → verify DB reopens cleanly with intact historical rows and listener reconnects.
   - **T-KEY-03 (Key Availability & Key-Loss Locked State):** Simulate missing wrapped DEK, corrupted ciphertext/IV, and deleted Keystore alias while `.db` exists → verify `LOCKED_KEY_MISSING_OR_INVALID` blocks overwrite and preserves DB bytes verbatim.
   - **T-KEY-04 (`arm64-v8a` / 16KB Native Alignment & Disk Header Check):** Verify `System.loadLibrary("sqlcipher")` succeeds on `iQOO Z10R` (`arm64-v8a`, API 36) and `.db` file header lacks `"SQLite format 3"`.

---

## 5. Alternatives Considered

| Alternative | Description | Reason Rejected |
| :--- | :--- | :--- |
| **`setUnlockedDeviceRequired(true)` on DB KEK** | Enforce OS screen-unlock gating inside Android Keystore. | **Rejected.** Breaks locked-screen AFU notification persistence whenever the app cold-starts, restarts after OS kill, or rebinds while screen is off. |
| **SQLCipher 5 beta (`5.0.0-beta*`)** | Adopt pre-release AES-GCM SQLCipher 5. | **Rejected.** Beta instability and non-backward-compatible disk format changes prohibited for production/PoC. |
| **`net.zetetic:android-database-sqlcipher:4.5.4`** | Legacy package from `38001da`. | **Rejected.** EOL, lacks 16KB alignment, breaks with modern `androidx.sqlite`. |
| **`EncryptedSharedPreferences`** | Wrap DEK in `androidx.security:security-crypto`. | **Rejected.** Deprecated due to main-thread sync I/O and OEM corruption issues. |
| **Google Tink `AndroidKeysetManager`** | Alternative crypto wrapper around Keystore KEK + DataStore. | **Viable alternative** if owner wants audited Tink keyset rotation; slightly larger dependency footprint than direct Keystore AES-GCM + `AtomicFile`. |

---

## 6. Technical Justification

- **Verified 4.x Stability (`4.20.0` / `4.17.0`):** Avoiding SQLCipher 5 beta and EOL `4.5.4` eliminates both beta format churn and 16KB page-size crashes on API 36 hardware.
- **Explicit AFU Key Policy:** Evaluating BFU vs AFU upfront prevents subtle production failures where locked-screen notifications silently fail to persist after background process termination.

---

## 7. Consequences and Trade-Offs

- **Positive:**
  - Verified compatibility path with Room `2.8.x`, `arm64-v8a`, and API 36 without beta risks.
  - Deterministic locked-screen background persistence balanced with explicit hardware-backed at-rest protection.
- **Negative / Risks:**
  - Choosing Option 2 (`compileSdk = 36` strict) may require pinning `sqlcipher-android:4.17.0` instead of `4.20.0` if `4.20.0`'s AAR metadata enforces `compileSdk = 37`.
  - Lost Keystore keys remain unrecoverable by design, making encrypted full backups (`ADR-006`) mandatory for device migration.
