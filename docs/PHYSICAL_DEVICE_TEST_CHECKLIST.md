# Physical-Device Validation Checklist (`iQOO Z10R` & Gate G1)

**Primary Physical Test Device:** `vivo` / `iQOO` `iQOO Z10R` (`I2505`), Android `16` (`API 36`), Build `PD2512LF_EX_A_16.1.20.0.W20`, Security patch `2026-10-01`, CPU ABI `arm64-v8a`  
**Host Environment:** Fedora Linux + ADB  
**Other Devices / OEMs:** Explicitly **UNVERIFIED**  
**Status:** Hardware Instrumented Suite & R8 Release Build Verified (`0.2.0-rc1`); Daily-Driver Gate G1 & Manual OS Scenarios Active

---

## Mandatory Baseline Rule
- **Pre-Device Automated Coverage Note:** All PRD v1.4 MVP + `SHOULD`/`COULD` components (`:integration:api`, `:integration:providers` `PAR-12` + `JagoTransactionParser` `v2` / `DanaTransactionParser` `v1` / `BcaTransactionParser` `v1`, M1 Room Schema v1 + `DatabaseKeyManager`, M2 ingestion/dedup/recovery/heartbeat/`ING-08`/`ING-10` self-check, M3 aggregate/merchant trends `DASH-08`/balance/transfer/rule/`PAR-11` re-parse, M4 Material 3 Compose UI/`TX-06` tags/`DATA-12` thresholds, and M5 `WTBK` v1 backup/restore/SAF folder/reminders, `RetentionPurgeWorker`, `CsvExporter`, wipe, and R8 release build `0.2.0-rc1`) are implemented and unit/Robolectric-tested; physical daily-driver Gate G1 and interactive OS scenarios remain tracked below.
- **Daily-Driver Warning (`iQOO Z10R`):** Never run `./gradlew connectedDebugAndroidTest` on a daily-driver phone carrying live `wtrack.db` data — AGP uninstalls `wtrack.app` at task teardown. Use `adb install -r` + `adb shell am instrument -w -e debug false wtrack.app.test/androidx.test.runner.AndroidJUnitRunner` + `adb uninstall wtrack.app.test`.
- **Do NOT automatically whitelist W-Track or disable battery optimization before running Sections 3–4 under default OEM settings.**
- **JVM / Robolectric tests do NOT prove real hardware, SQLCipher native binary, Android Keystore, Doze, or provider notification behavior.**

---

## 1. APK Installation, ABI & Launch Verification
- [x] Install debug/release APK cleanly via ADB onto `iQOO Z10R (I2505)`. *(Debug APK `0.2.0-rc1` [`versionCode=2`, `33,021,954` bytes] installed cleanly via wireless `adb install -r`; R8-shrunk unsigned release APK built cleanly [`11,021,272` bytes, `allowBackup="false"`, non-debuggable]; signed release install pending local `keystore.properties`.)*
- [x] Verify `arm64-v8a` native library loading (`System.loadLibrary("sqlcipher")` succeeds without `UnsatisfiedLinkError` or 16KB ELF alignment faults). *(Verified via `SqlCipherInstrumentedTest`, `dumpsys package wtrack.app` showing `primaryCpuAbi=arm64-v8a`, & live `wtrack.db`.)*
- [ ] Verify app launches cleanly in System, Light, and Dark modes without crashes. *(`WtrackTheme` System, Light, and Dark rendering verified on `iQOO Z10R` via `ComposeThemeAndLockedUiInstrumentedTest`; manual full-app toggle audit pending.)*

## 2. SQLCipher (`4.20.0`) & Android Keystore (`ADR-005`) Verification
- [x] Run physical-device SQLCipher & hardware suites on `iQOO Z10R`. *(Executed full 7-test suite via `am instrument`: `OK (7 tests)` in `51,055 ms`, covering `SqlCipherInstrumentedTest`, `BackupAndRetentionInstrumentedTest`, `IngestionAndLedgerInstrumentedTest`, and `ComposeThemeAndLockedUiInstrumentedTest`.)*
- [x] Pull or inspect app database header via debuggable `run-as` check — confirm `.db` file header does **not** begin with `"SQLite format 3"`. *(Verified in `SqlCipherInstrumentedTest` on `iQOO Z10R` that SQLCipher DB bytes lack `"SQLite format 3"`.)*
- [x] Verify StrongBox vs TEE Keystore behavior for alias `wtrack_db_kek_v1`. *(Verified on `iQOO Z10R` via `SqlCipherInstrumentedTest`: `WTrackCryptoAudit` confirmed graceful TEE fallback `securityLevel=TEE userAuth=false`; live `files/keystore/db_key.bin` `88` bytes present.)*
- [x] Verify `LOCKED_KEY_MISSING_OR_INVALID` state: remove/corrupt wrapped DEK while `.db` exists → confirm `DatabaseLockedScreen` renders, existing `.db` is NOT overwritten/deleted, and Restore / Explicit Wipe controls work. *(Automated on `iQOO Z10R`: `SqlCipherInstrumentedTest` verified corrupted key, missing key, and deleted Keystore alias preserve raw `.db` bytes + `wipeAllDataAndReinitialize()`; `ComposeThemeAndLockedUiInstrumentedTest` rendered `DatabaseLockedScreen` recovery controls on device.)*
- [ ] Verify `BFU` vs `AFU` + Reboot recovery: unlock once post-boot, verify encrypted Room reopens seamlessly with zero data loss. *(SQLCipher reopen verified in `SqlCipherInstrumentedTest`; physical phone reboot check still pending.)*

## 3. `NotificationListenerService` Lifecycle & Background Recovery (`ING-01`–`ING-14`)
- [ ] Verify Android 13–16 **Sideloaded Restricted Settings** onboarding guidance (`ING-10`) unblocks Notification Access toggle cleanly.
- [ ] Verify **Screen-Off & Locked-Screen Ingestion (AFU)**: unlock once post-boot, lock screen, send test notification → confirm `WtrackNotificationListenerService` persists event to encrypted SQLCipher DB without unlocking screen or opening UI.
- [ ] Verify **Listener Persistence** across screen-off cycles and swiping W-Track from recents.
- [ ] Verify **Process Termination Recovery (`ING-12`)**: terminate process (`adb shell am kill wtrack.app`), confirm `requestRebind()`, `activeNotifications` catch-up scan (`ING-09`), and recovery of `RECEIVED`/`PROCESSING`/`FAILED` rows (escalating after 3 failures to `PIPELINE_ERROR`). *(Recovery & 3-failure `PIPELINE_ERROR` escalation logic verified under SQLCipher in `IngestionAndLedgerInstrumentedTest`; live OS `am kill` NLS test pending.)*
- [ ] Verify **Reboot & Post-Unlock Recovery**: reboot phone, unlock once, confirm OS automatically rebinds `NotificationListenerService` and scans active shade notifications idempotently.
- [ ] Verify **Extended Idle, Light Doze & Deep Doze (`wait_for_unlock=true`)** under **default battery settings** (before whitelisting): observe actual delivery delays and `ING-14` `HEARTBEAT_STALE` gap recording; inspect whether `vivo` / `iQOO` kills listener silently without `onListenerDisconnected()`. *(`ING-14` stale heartbeat gap recording verified in `IngestionAndLedgerInstrumentedTest` and `dumpsys deviceidle` confirmed `wait_for_unlock=true`; real overnight Doze observation pending.)*
- [ ] Verify **Battery Optimization Baseline vs `ING-10` Exempt Behavior**: record baseline failure/delay rate first, then validate user-guided `ING-10` battery/autostart settings.
- [ ] Verify **Permission Revocation Gap (`ING-11`)**: disable & re-enable Notification Access → confirm `ATTENTION` → `ACTIVE` status transition and `CaptureGap(ACCESS_REVOKED)` row.

## 4. Android 16 (`API 36`) Notification Behaviors (`ING-06`)
- [x] Verify **OTP / Sensitive Content Redaction**: confirm redacted/hidden notifications route safely to `NEEDS_REVIEW` without crashing, guessing amounts, or requesting ADB bypasses. *(Verified on `iQOO Z10R` in `IngestionAndLedgerInstrumentedTest` using `isContentRedacted=true`; live bank OTP shade observation ongoing in Gate G1.)*
- [x] Verify **Grouped / Summary Notifications** and **Live Updates (`Notification.ProgressStyle` / Promoted Ongoing)** do not trigger ANRs or duplicate transactions. *(Verified Bank Jago, DANA & myBCA Android 16 `g:Aggregate_AlertingSection` group summary handling in `IngestionAndLedgerInstrumentedTest`; `ProgressStyle`/Promoted Ongoing still pending.)*
- [ ] Verify **Notification Cooldown** under rapid bursts. *(20-event burst anti-collapse verified under SQLCipher in `IngestionAndLedgerInstrumentedTest`; OS SystemUI Notification Cooldown throttling still pending.)*

## 5. Provider Discovery & Real Notification Parsing (`PROVIDERS.md`)
- [x] Verify exact installed package IDs via `adb shell pm list packages | grep -iE 'jago|dana|bca'`:
  - **1. Bank Jago:** `com.jago.digitalBanking` (`v8.99.1`, `versionCode=11100`, `POST_NOTIFICATIONS: granted=true` enabled by owner)
  - **2. DANA:** `id.dana` (`v2.152.0`, `versionCode=21520016`, `POST_NOTIFICATIONS: granted=true`)
  - **3. BCA:** both **BCA mobile** (`com.bca`, `v4.9.5`, `versionCode=1297`) and **myBCA** (`com.bca.mybca.omni.android`, `v2.23.0`, `versionCode=299`) verified installed (`POST_NOTIFICATIONS: granted=true` on both); confirmed `myBCA` emits Financial Diary pushes; also installed: `com.bcadigital.blu`.
- [x] Inspect real notification structures (`title`, `text`, `bigText`, `subText`, `channelId`, `key`, `id`, `tag`, `posted_at`), directions, number/date locales, multi-pocket/account hints, and dual-notifier overlap (e.g., DANA funded by Bank Jago/BCA). *(Verified initial M3a notification structures across Bank Jago, DANA, and myBCA; monitor outgoing/QRIS & dual-notifier patterns during Gate G1 window.)*
- [ ] Confirm **zero OTPs, PINs, passwords, or full account numbers** are stored in fixtures, logs, or transactions. *(`SensitiveContentGuard` & backup exclusion of raw `payloadJson` verified in `BackupAndRetentionInstrumentedTest`; final pre-release audit ongoing after Gate G1 capture.)*
- [x] Implement & verify incremental parsers in `:integration:providers` (`Jago` → `DANA` → `BCA`) using captured anonymized fixtures. *(`JagoTransactionParser` v2, `DanaTransactionParser` v1, and `BcaTransactionParser` v1 for `myBCA` verified in unit tests and `IngestionAndLedgerInstrumentedTest`; unseen transaction variants route safely to `NEEDS_REVIEW`.)*
- [x] Verify duplicate updates/re-posts/catch-up scans produce **zero duplicate transactions**. *(Verified across replay, Android 16 group summaries, and post-restore catch-up scan in `IngestionAndLedgerInstrumentedTest` & `BackupAndRetentionInstrumentedTest`.)*
- [x] Verify reconciliation, balance correction (`ACC-08`), transfer pairing + derived fee (`TRF-04`), and voiding (`TX-08`). *(Verified under real SQLCipher on `iQOO Z10R` in `IngestionAndLedgerInstrumentedTest`: `CorrectBalanceUseCase`, `LinkTransferUseCase` derived `feeMinor`, `VoidRestoreTransactionUseCase`, `TagRepositoryImpl`, and immutable `originalJson`.)*

## 6. Backup, Restore, Retention & Data Safety (`M5`)
- [x] Verify `RetentionPurgeWorker` (`SEC-002`) purges `IGNORED` immediately, `PROCESSED` after verification window, and `NEEDS_REVIEW` after 30d. *(`RetentionPurgeRunner` expiration schedule verified under SQLCipher in `BackupAndRetentionInstrumentedTest`, and `dumpsys jobscheduler` confirmed `WorkManager` `SystemJobService` scheduled for `wtrack.app`.)*
- [x] Verify `.wtrackbackup` export + verification (`DATA-13`) + wrong-passphrase rejection + atomic fresh-install restore (`DATA-09`), confirming zero duplicate creation on subsequent catch-up scan and `ATTENTION` prompt to re-grant Notification Access. *(Hardware-benchmarked on `iQOO Z10R`: `Argon2id_65536KiB_3iter encryptMs=9879 decryptMs=8943 cipherBytes=332`; full 13-table export, tamper/wrong-passphrase/version rejection, atomic restore, excluded `payloadJson`/`NEEDS_REVIEW`, and zero-duplicate catch-up scan verified in `BackupAndRetentionInstrumentedTest`. Interactive SAF document/folder picker flow still pending manual UI tap-through.)*
- [ ] Verify CSV export warning + unencrypted output (`DATA-11`) and `DATA-06` complete data wipe. *(`CsvExporter` formula neutralization verified in `BackupAndRetentionInstrumentedTest` and wipe verified in `SqlCipherInstrumentedTest`; Settings UI warning dialog tap-through pending.)*

## 7. Gate G1 Sign-Off (`ADR-003` & `PROVIDERS.md`)
- [x] Lock Gate G1 thresholds in `ADR-003` **before** measured capture starts. *(Locked `Coverage >= 90%`, `Completeness >= 95%`, `0` wrong amounts/directions across `com.jago.digitalBanking`, `id.dana`, and `com.bca.mybca.omni.android` / `com.bca`.)*
- [ ] Run ≥ 7–14 consecutive days, ≥ 30 statement transactions per evaluated provider.
- [ ] Record Appendix C ground-truth metrics in `docs/PROVIDERS.md` (`Coverage >= 90%`, `Completeness >= 95%`, `0` unexplained wrong amounts/directions).
