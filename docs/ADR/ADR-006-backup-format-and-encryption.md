# ADR-006: Backup Format, Key Derivation, and Encryption

**Date:** 2026-11-21

---

## 1. Status

**Proposed / Deferred Until Before `EXP-001` (M5)**

- **Confirmed Direction (PRD v1.4):** User-initiated, versioned, passphrase-encrypted full backup (`DATA-05`, `DATA-10`), atomic restore on a fresh install (`DATA-09`), exclusion of raw notification payloads and `NeedsReview` items, automatic OS backup disabled (`DATA-08`).
- **Pending Owner Confirmation Before M5 (`SEC-004` / `EXP-001`):** Exact KDF choice & work factors (Argon2id via Bouncy Castle vs PBKDF2-HMAC-SHA256), container binary layout, and CSV export (`DATA-11`, `SHOULD`) priority confirmation.

---

## 2. Relevant PRD Requirement References

- **PRD Section 4.7:** `DATA-05` (full backup contents), `DATA-06` (delete data), `DATA-08` (OS auto-backup off), `DATA-09` (atomic versioned restore), `DATA-10` (passphrase encryption, no plaintext backup), `DATA-11` (CSV export, `SHOULD`), `DATA-12` (backup reminders, `SHOULD`), `DATA-13` (post-write backup verification, `SHOULD`), `DATA-14` (scheduled SAF folder backup, `COULD`).
- **PRD Section 9.4:** Format explanation, SAF document picker, KDF/AEAD design, exclusion of payloads & Needs Review items.
- **PRD Section 16:** MVP Acceptance Criterion 15.
- **Backlog:** `SEC-004`, `EXP-001` to `EXP-006`, `TEST-006`.

---

## 3. Context and Problem

Because W-Track is strictly local-first (ADR-002) and database encryption keys are bound to the originating device's Android Keystore (ADR-005), a raw copy of the encrypted Room `.db` file cannot be restored on a new or factory-reset phone. At the same time, user backups travel outside app-private storage (into user-chosen folders, USB drives, or personal cloud folders via SAF), so they must remain strongly protected against offline brute-force cracking with zero reliance on device Keystore keys.

Furthermore, restoring a backup on a fresh device must:
1. Preserve idempotency (`notification_events` identity metadata) so notifications still sitting in the OS shade do not re-create duplicate transactions (`DATA-05`, PRD Section 9.4).
2. Never leave partial, half-migrated data if the passphrase is wrong, the file is truncated/corrupt, or the backup comes from a newer app version (`DATA-09`).
3. Never leak retained raw notification payloads (`payload_json`) or unresolved `NeedsReview` drafts outside the device's database (`DATA-05`, Section 9.4).

---

## 4. Decision

### A. Proposed Backup Container Layout (`.wtrackbackup` — Confirm in `SEC-004` Before `EXP-001`)

Define a self-describing, authenticated binary container consisting of a **plaintext versioned header** followed by **AEAD ciphertext**:

```text
+-----------------------------------------------------------------------+
| Magic Bytes (4B): "WTBK" (0x57 0x54 0x42 0x4B)                        |
| Format Version (uint16, big-endian): 1                                |
| KDF ID (uint8): 1 = Argon2id, 2 = PBKDF2-HMAC-SHA256                  |
| KDF Memory KiB (uint32): e.g., 65536 (or 0 if PBKDF2)                 |
| KDF Iterations (uint32): e.g., 3 (Argon2id) or 600000 (PBKDF2)        |
| KDF Parallelism (uint8): e.g., 1 (or 0 if PBKDF2)                     |
| Salt Length (uint8): 32                                               |
| Salt (32 bytes, CSPRNG)                                               |
| Cipher ID (uint8): 1 = AES-256-GCM                                    |
| IV / Nonce Length (uint8): 12                                         |
| IV / Nonce (12 bytes, CSPRNG)                                         |
| Compression ID (uint8): 1 = GZIP                                      |
| Reserved / Flags (uint16): 0                                          |
+-----------------------------------------------------------------------+
| Ciphertext: AES-256-GCM(key, iv, GZIP(JSON_PAYLOAD), AAD = FULL_HEADER)|
| GCM Authentication Tag (16 bytes, appended per JCA AES/GCM/NoPadding) |
+-----------------------------------------------------------------------+
```

**Header Integrity Guarantee:** Passing the **entire serialized header bytes** as **Additional Authenticated Data (AAD)** into `AES-256-GCM` guarantees that any tampering with `format_version`, KDF parameters, compression flags, or salt causes immediate decryption failure (`AEADBadTagException`) before parsing a single byte of JSON.

### B. Proposed Cryptography (Confirm Before `EXP-001`)

| Component | Recommended Choice | Fallback / Alternative | Justification |
| :--- | :--- | :--- | :--- |
| **KDF** | **Argon2id** (`org.bouncycastle:bcprov-jdk18on` `Argon2BytesGenerator`) | `PBKDF2WithHmacSHA256` (Android built-in JCA, `600,000` iterations) | Pure-Java Bouncy Castle Argon2id provides OWASP-recommended memory-hard GPU/ASIC resistance **without** native `.so` binaries that complicate Android 15+ 16KB page-size alignment. |
| **Proposed Argon2id Params** | `memory = 65,536 KiB` (64 MiB; tunable down to `32,768 KiB` on low-RAM test devices), `iterations = 3`, `parallelism = 1`, `salt = 32B`, `outputKey = 32B` | `PBKDF2` 600k iterations, `salt = 32B`, `outputKey = 32B` | Fast (~300–800ms on modern ARM64 phones) while making offline GPU cracking expensive. Exact parameters are stored in the container header so future devices remain backward-compatible. |
| **Symmetric Cipher** | **AES-256-GCM** (`AES/GCM/NoPadding`, 12-byte random IV, 128-bit tag) | ChaCha20-Poly1305 | Hardware-accelerated AES + authenticated encryption built into Android JCA on all API 26+ devices. |

### C. Canonical JSON Payload Schema (Backup `format_version = 1`)

Inside the compressed, encrypted JSON payload (`kotlinx.serialization`):
- `metadata`: `backupSchemaVersion`, `roomSchemaVersion`, `appVersionName`, `appVersionCode`, `createdAtEpochMillisUtc`, `baseCurrency`, `appSettings` (theme, retention days, backup reminder thresholds, heartbeat settings).
- `transactionSources` (allowlist packages, parser IDs, enabled flags).
- `accounts` (including `initialBalanceMinor`, `initialBalanceAt`, `lastReconciledAt`, `isActive`).
- `accountSources` (account-to-source links and masked `accountHint`).
- `categories` (default + custom + archived state).
- `merchants` (normalized `key` + `displayName`).
- `transactionRules` (`conditionJson`, `actionJson`, `enabled`, timestamps).
- `transactions` (all `CONFIRMED` and `VOIDED` transactions, including adjustments and immutable `originalJson`, `providerReference`, `relatedTransactionId`, `reportedBalanceMinor`).
- `tags` & `transactionTags`.
- `transferMatches` (`CONFIRMED`, `SUGGESTED`, `REJECTED`, `feeMinor`).
- `captureGaps` (`startedAt`, `endedAt`, `reason`).
- `budgets` (if implemented).
- `eventIdentities` (`sourceId`, `packageName`, `stableEventKey`, `notificationKey`, `notificationId`, `notificationTag`, `postedAt`, `processingStatus`, `processedAt`, `receivedAt` — **explicitly excluding `payloadJson`, excluding `reviewDraftJson`, and excluding `NEEDS_REVIEW` rows**).

### D. Backup, Verification & Atomic Restore Flows

1. **Pre-Export Disclosure & Needs Review Check:**
   - Display pending `NEEDS_REVIEW` count and explain that unresolved review items and raw notification payloads are excluded from backups (`DATA-05`, PRD Section 9.4).
   - Display explicit warning that a lost passphrase **cannot** be recovered (`DATA-10`), and enforce minimum passphrase strength guidance (e.g., ≥ 10–12 characters or strong passphrase estimator, while allowing user confirmation).
2. **Storage Access Framework (SAF) Output (`DATA-05`):**
   - Write strictly via `ActivityResultContracts.CreateDocument` to a user-selected destination URI; zero `WRITE_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` permissions.
   - Write to an in-app temporary encrypted stream or directly after verifying payload build, then run **Post-Write Verification (`DATA-13`, SHOULD)**: re-open the written SAF URI, verify header, re-derive key, authenticate & decrypt AES-GCM, deserialize JSON, verify record counts match source DB, and report status.
3. **Atomic Versioned Restore (`DATA-09`):**
   - Read header → verify `"WTBK"` magic → check `format_version <= SUPPORTED_BACKUP_VERSION` (if newer, **abort immediately** with explicit version-mismatch error).
   - Derive key with header KDF params → decrypt & verify AES-GCM tag (if wrong passphrase or corrupted bytes, **abort immediately** with zero DB writes).
   - Decompress GZIP → decode JSON → run older-format migrators if `format_version < SUPPORTED_BACKUP_VERSION` → validate foreign keys & invariants (`fee_minor >= 0`, `initial_balance_at` rules, unique constraints).
   - Only after full validation passes: execute a **single Room `withTransaction`** replacing all tables in topological dependency order, followed by committing restored settings into DataStore. If Room transaction fails, SQLite rolls back atomically leaving previous DB state intact.
   - **Post-Restore Onboarding Handoff:** Set capture status to `ATTENTION` across all allowlisted sources (since Android never restores Notification Access grants across installs) and navigate directly to the Notification Access prompt (`ING-02`).
4. **CSV Export (`DATA-11`, `SHOULD`):**
   - Separate export action for `accounts`, `transactions`, and `categories` only. Requires explicit modal warning that the exported `.csv` file is **unencrypted plaintext** before launching the SAF document picker.

---

## 5. Alternatives Considered

| Alternative | Description | Reason Rejected |
| :--- | :--- | :--- |
| **Plaintext JSON backup** | Export readable JSON via SAF. | **Rejected (`DATA-10`).** Explicitly prohibited; leaks full financial history if cloud-synced or shared. |
| **Encrypted SQLCipher `.db` copy + `PRAGMA rekey`** | Copy SQLite file and rekey with user passphrase. | **Rejected.** Ties backup format strictly to exact Room internal schema version/SQLite page details, complicates excluding `payload_json` / `NEEDS_REVIEW` items without vacuuming/scrubbing sensitive deleted pages, and skips clean JSON version migration. |
| **JNI native Argon2 (`argon2kt` / `argon2-jvm`)** | Use C bindings for Argon2id. | **Not preferred.** Requires maintaining native `.so` binaries per ABI with 16KB ELF page alignment; pure-Java Bouncy Castle `Argon2BytesGenerator` avoids native packaging fragility while delivering identical Argon2id cryptographic parameters. *(Remains reversible before `EXP-001` if profiling shows unacceptable latency on owner's device.)* |
| **PBKDF2-HMAC-SHA256 only** | Built-in JCA KDF with zero external dependency. | **Kept as explicit fallback.** Lower GPU/ASIC cracking resistance than Argon2id; acceptable only if owner prioritizes zero extra dependencies (`bcprov`) over memory-hard KDF. |
| **Standard encrypted ZIP (Zip4j)** | AES-encrypted ZIP containing JSON files. | **Rejected.** Weak/non-standard header authentication, easy to misconfigure legacy ZipCrypto, and less clean AAD binding than a single purpose-built authenticated container. |

---

## 6. Technical Justification

- **Authenticated Header (AAD) + AES-GCM:** Prevents silent corruption, version-downgrade tampering, or wrong-passphrase partial reads (`TEST-006`).
- **Logical JSON Inside Container vs. Raw DB Snapshot:** Guarantees clean exclusion of raw payloads and `NEEDS_REVIEW` items while ensuring `eventIdentities` survive device migration so catch-up scans after restore never create duplicates.
- **Transactional Staging Before Wipe:** Separating decryption/parsing/validation from the atomic Room `withTransaction` guarantees `DATA-09` ("a failed or wrong-passphrase restore leaves no partial data").

---

## 7. Consequences and Trade-Offs

- **Positive:**
  - Portable, hardware-independent recovery across devices and Android versions.
  - Strong offline brute-force resistance with zero cloud dependency.
  - Safe forward/backward format compatibility checks.
- **Negative / Risks:**
  - Pure-Java Bouncy Castle (`bcprov-jdk18on`) adds ~5–6 MB to APK size unless R8 shrinking/ProGuard strips unused providers cleanly (or PBKDF2 fallback is chosen by owner), and allocates 32–64 MiB temporary heap arrays during backup/restore KDF execution (must run on `Dispatchers.Default`/`IO` with explicit progress indicator and byte-array clearing).
  - Unrecoverable passphrase: if user forgets passphrase, backup is mathematically unrecoverable (`DATA-10`).
  - Pending `NeedsReview` items at backup time are intentionally excluded and must be resolved before backup or re-entered manually if lost.
