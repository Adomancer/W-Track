# Physical-Device Validation Checklist (`iQOO Z10R` & Gate G1)

**Primary Physical Test Device:** `vivo` / `iQOO` `iQOO Z10R` (`I2505`), Android `16` (`API 36`), Build `PD2512LF_EX_A_16.1.20.0.W20`, Security patch `2026-10-01`, CPU ABI `arm64-v8a`  
**Host Environment:** Fedora Linux + ADB  
**Other Devices / OEMs:** Explicitly **UNVERIFIED**  
**Status:** Pending Physical Device Connection

---

## Mandatory Baseline Rule
- **Pre-Device Automated Coverage Note:** M0–M5 software components (`:integration:api`, `:integration:providers` `PAR-12` utilities & catalog, M1 Room Schema v1 + `DatabaseKeyManager`, M2 ingestion/dedup/recovery/heartbeat, M3 aggregate/balance/transfer/rule/Gate G1 logic, M4 Material 3 Compose UI + ViewModels, and M5 `WTBK` v1 backup/restore, `RetentionPurgeWorker`, `CsvExporter`, and wipe) are implemented and unit/Robolectric-tested; all items below require real `iQOO Z10R` hardware execution before MVP sign-off.
- **Do NOT automatically whitelist W-Track or disable battery optimization before running Sections 3–4 under default OEM settings.**
- **JVM / Robolectric tests do NOT prove real hardware, SQLCipher native binary, Android Keystore, Doze, or provider notification behavior.**

---

## 1. APK Installation, ABI & Launch Verification
- [ ] Install debug/release APK cleanly via ADB onto `iQOO Z10R (I2505)`.
- [ ] Verify `arm64-v8a` native library loading (`System.loadLibrary("sqlcipher")` succeeds without `UnsatisfiedLinkError` or 16KB ELF alignment faults).
- [ ] Verify app launches cleanly in System, Light, and Dark modes without crashes.

## 2. SQLCipher (`4.20.0`) & Android Keystore (`ADR-005`) Verification
- [ ] Run `SqlCipherInstrumentedTest` via `./gradlew connectedDebugAndroidTest` on `iQOO Z10R`.
- [ ] Pull or inspect app database header via debuggable `run-as` check — confirm `.db` file header does **not** begin with `"SQLite format 3"`.
- [ ] Verify StrongBox vs TEE Keystore behavior for alias `wtrack_db_kek_v1`.
- [ ] Verify `LOCKED_KEY_MISSING_OR_INVALID` state: remove/corrupt wrapped DEK while `.db` exists → confirm `DatabaseLockedScreen` renders, existing `.db` is NOT overwritten/deleted, and Restore / Explicit Wipe controls work.
- [ ] Verify `BFU` vs `AFU` + Reboot recovery: unlock once post-boot, verify encrypted Room reopens seamlessly with zero data loss.

## 3. `NotificationListenerService` Lifecycle & Background Recovery (`ING-01`–`ING-14`)
- [ ] Verify Android 13–16 **Sideloaded Restricted Settings** onboarding guidance (`ING-10`) unblocks Notification Access toggle cleanly.
- [ ] Verify **Screen-Off & Locked-Screen Ingestion (AFU)**: unlock once post-boot, lock screen, send test notification → confirm `WtrackNotificationListenerService` persists event to encrypted SQLCipher DB without unlocking screen or opening UI.
- [ ] Verify **Listener Persistence** across screen-off cycles and swiping W-Track from recents.
- [ ] Verify **Process Termination Recovery (`ING-12`)**: terminate process (`adb shell am kill wtrack.app`), confirm `requestRebind()`, `activeNotifications` catch-up scan (`ING-09`), and recovery of `RECEIVED`/`PROCESSING`/`FAILED` rows (escalating after 3 failures to `PIPELINE_ERROR`).
- [ ] Verify **Reboot & Post-Unlock Recovery**: reboot phone, unlock once, confirm OS automatically rebinds `NotificationListenerService` and scans active shade notifications idempotently.
- [ ] Verify **Extended Idle, Light Doze & Deep Doze (`wait_for_unlock=true`)** under **default battery settings** (before whitelisting): observe actual delivery delays and `ING-14` `HEARTBEAT_STALE` gap recording; inspect whether `vivo` / `iQOO` kills listener silently without `onListenerDisconnected()`.
- [ ] Verify **Battery Optimization Baseline vs `ING-10` Exempt Behavior**: record baseline failure/delay rate first, then validate user-guided `ING-10` battery/autostart settings.
- [ ] Verify **Permission Revocation Gap (`ING-11`)**: disable & re-enable Notification Access → confirm `ATTENTION` → `ACTIVE` status transition and `CaptureGap(ACCESS_REVOKED)` row.

## 4. Android 16 (`API 36`) Notification Behaviors (`ING-06`)
- [ ] Verify **OTP / Sensitive Content Redaction**: confirm redacted/hidden notifications route safely to `NEEDS_REVIEW` without crashing, guessing amounts, or requesting ADB bypasses.
- [ ] Verify **Grouped / Summary Notifications** and **Live Updates (`Notification.ProgressStyle` / Promoted Ongoing)** do not trigger ANRs or duplicate transactions.
- [ ] Verify **Notification Cooldown** under rapid bursts.

## 5. Provider Discovery & Real Notification Parsing (`PROVIDERS.md`)
- [ ] Verify exact installed package IDs via `adb shell pm list packages | grep -iE 'jago|dana|bca'`:
  - **1. Bank Jago** (Play ref candidate: `com.jago.digitalBanking`)
  - **2. DANA** (Play ref candidate: `id.dana`)
  - **3. BCA** (`BCA mobile` `com.bca` vs `myBCA` `com.bca.mybca.omni.android`)
- [ ] Inspect real notification structures (`title`, `text`, `bigText`, `subText`, `channelId`, `key`, `id`, `tag`, `posted_at`), directions, number/date locales, multi-pocket/account hints, and dual-notifier overlap (e.g., DANA funded by Bank Jago/BCA).
- [ ] Confirm **zero OTPs, PINs, passwords, or full account numbers** are stored in fixtures, logs, or transactions.
- [ ] Implement & verify incremental parsers in `:integration:providers` (`Jago` → `DANA` → `BCA`) using captured anonymized fixtures.
- [ ] Verify duplicate updates/re-posts/catch-up scans produce **zero duplicate transactions**.
- [ ] Verify reconciliation, balance correction (`ACC-08`), transfer pairing + derived fee (`TRF-04`), and voiding (`TX-08`).

## 6. Backup, Restore, Retention & Data Safety (`M5`)
- [ ] Verify `RetentionPurgeWorker` (`SEC-002`) purges `IGNORED` immediately, `PROCESSED` after verification window, and `NEEDS_REVIEW` after 30d.
- [ ] Verify `.wtrackbackup` export + verification (`DATA-13`) + wrong-passphrase rejection + atomic fresh-install restore (`DATA-09`), confirming zero duplicate creation on subsequent catch-up scan and `ATTENTION` prompt to re-grant Notification Access.
- [ ] Verify CSV export warning + unencrypted output (`DATA-11`) and `DATA-06` complete data wipe.

## 7. Gate G1 Sign-Off (`ADR-003` & `PROVIDERS.md`)
- [ ] Lock Gate G1 thresholds in `ADR-003` **before** measured capture starts.
- [ ] Run ≥ 7–14 consecutive days, ≥ 30 statement transactions per evaluated provider.
- [ ] Record Appendix C ground-truth metrics in `docs/PROVIDERS.md` (`Coverage >= 90%`, `Completeness >= 95%`, `0` unexplained wrong amounts/directions).
