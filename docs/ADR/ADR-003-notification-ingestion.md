# ADR-003: Notification Ingestion, Android SDK Targets, Test Devices, and Gate G1 Thresholds

**Date:** 2026-11-21

---

## 1. Status

**Proposed — Primary Device, M0 SDK Baseline, and Three Initial PoC Target Names Confirmed; On-Device Package Verification and Pre-Capture G1 Lock Pending**

- **Confirmed Baseline (PRD v1.4):** Use Android `NotificationListenerService`, strict package allowlist, lightweight callback handoff, persistent inbox before processing, rebind/catch-up scan, unfinished-event recovery (`ING-12`), unsupported-source ignoring/counting (`ING-13`), and Android 15+/16 redacted-content safe handling (`ING-06`).
- **Confirmed Primary Physical Test Device (via ADB):** `vivo` / `iQOO Z10R` (`I2505`), Android `16` (API `36`), Build `PD2512LF_EX_A_16.1.20.0.W20`, Security patch `2026-10-01`, CPU ABI `arm64-v8a`.
- **Confirmed M0 SDK Configuration:** `minSdk = 26`, `targetSdk = 36`, `compileSdk = 37` (Android 17 stable GA, required by selected stable AndroidX/Compose dependencies; Android 17 beta excluded).
- **Confirmed Initial PoC Provider Priority (`docs/PROVIDERS.md`):** `1. Bank Jago` → `2. DANA` → `3. BCA` (implemented incrementally after M0–M2; must **not** delay M0 or M1).
- **Provisional Gate G1 Baseline (Subject to Final Owner Lock Before Measurement):** Coverage $\ge 90\%$, Completeness $\ge 95\%$, $0$ unexplained wrong amounts/directions, minimum $7$ consecutive days (preferably $14$), minimum $30$ statement transactions per provider.
- **Explicitly Pending Before M3a Capture:**
  1. On-device ADB verification of exact package IDs (`com.jago.digitalBanking`, `id.dana`, and `BCA mobile` `com.bca` vs `myBCA` `com.bca.mybca.omni.android` on `iQOO Z10R`) before writing provider-specific parsers.
  2. Final owner approval locking the Gate G1 thresholds here **before** measured capture begins (`PRD Section 17`).

---

## 2. Relevant PRD Requirement References

- **PRD Section 4.2:** `ING-01` to `ING-14` (listener, permission, allowlist, hidden/grouped content, status, test capture, rebind/catch-up, battery/autostart/restricted-settings guidance, capture gaps, event recovery, unsupported sources, heartbeat).
- **PRD Section 4.1 & 4.3:** `ACC-06`, `PAR-01`, `PAR-06`, `PAR-12`.
- **PRD Section 5:** Android version decisions, redaction behavior on Android 15+, sideloaded restricted-settings behavior.
- **PRD Section 6.2 & 8.1:** 16-step ingestion pipeline, recording `posted_at`, `notification_key`, `notification_id`, `notification_tag`.
- **PRD Section 13 & 14.3:** Reliability handling and device/lifecycle test requirements.
- **PRD Section 17, 17.1, Appendix C:** Gate G1 go/no-go procedure, metrics, thresholds, and evidence log.
- **Backlog:** `PIPE-001` to `PIPE-008`, `PIPE-011`, `PIPE-015`, `TEST-003`, `TEST-004`, `TEST-007`.

---

## 3. Context and Problem

Many personal banks and e-wallets do not provide consumer-accessible transaction APIs. To aggregate spending automatically without requiring credentials or invasive screen scraping (explicit non-goals in PRD Section 1.4), the MVP relies on notifications posted to Android's notification shade.

However, Android notification ingestion introduces major platform and OEM risks:
1. `NotificationListenerService` callbacks (`onNotificationPosted`) execute on an IPC Binder thread; heavy database or regex work inside the callback risks ANRs, dropped IPC events, or OS listener unbinding.
2. OEM battery optimizations and Android Doze modes aggressively throttle or terminate background listener processes without always delivering `onListenerDisconnected()`. Confirmed ADB diagnostics on the primary `iQOO Z10R` (`I2505`, Android 16) test device show both **Light Doze** and **Deep Doze** enabled with historical transitions into both states and **`wait_for_unlock=true`**, meaning idle/maintenance exits may wait for physical unlock while the screen is locked.
3. Android 13+ (API 33+) blocks Notification Access for sideloaded APKs under "Restricted Settings" until manually allowed in App Info.
4. Android 15+ and Android 16 (API 35–36) automatically redact notification content containing OTP/2FA-like tokens for non-privileged listeners (`RECEIVE_SENSITIVE_NOTIFICATIONS` is restricted to `role|signature` system/companion apps), introduce lock-screen privacy masking, add **Live Updates (`Notification.ProgressStyle`) / Promoted Ongoing** notifications with frequent updates, and apply **Notification Cooldown** during notification bursts.
5. Notification updates, grouped summary notifications, and re-posts vary by provider and must be measured empirically before committing to parser heuristics.

---

## 4. Decision

### A. Confirmed Ingestion Architecture (PRD v1.4 Baseline)

1. **Primary Mechanism (`ING-01`, `ING-02`):** Implement a subclass of `android.service.notification.NotificationListenerService` (`WtrackNotificationListenerService`) declared in `AndroidManifest.xml` with `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`. No `AccessibilityService`, no credential entry, and no screen scraping.
2. **Synchronous Allowlist Filter & Lightweight Callback (`ING-03`, `ING-04`, Section 6.2):**
   - Maintain an in-memory `AtomicReference<Set<String>>` snapshot of allowlisted `package_name`s (backed by encrypted Room `transaction_sources` table).
   - Inside `onNotificationPosted(sbn)`: synchronously check `sbn?.packageName in allowlist`. Non-allowlisted packages return immediately in microseconds without allocating payloads or hitting disk.
   - Extract only minimal fields (`packageName`, `key`, `id`, `tag`, `postTime`, `title`, `text`, `bigText`, `subText`, `channelId`, `isGroupSummary`, `isContentRedacted` indicator) into a lightweight envelope, then hand off asynchronously to a serialized/bounded coroutine ingestion queue (`SupervisorJob` + `Dispatchers.IO`). Do **not** rely on `WorkManager` for real-time capture (`PRD Section 4.2`).
3. **Persist-Before-Parse & Evidence Capture (`ING-05`, `ING-12`, Section 8.1):**
   - Every accepted event is persisted into the encrypted `notification_events` table with `processing_status = RECEIVED`, `posted_at = sbn.postTime`, `notification_key = sbn.key`, `notification_id = sbn.id`, and `notification_tag` (or SHA-256 hash if a provider's tag is observed to carry PII) **before** parser execution.
4. **Rebind, Catch-Up Scan & Unfinished Recovery (`ING-09`, `ING-12`):**
   - On `onListenerDisconnected()`, invoke `requestRebind(ComponentName)` where appropriate and record an open `CaptureGap(LISTENER_DISCONNECTED)`.
   - On `onListenerConnected()` (and app startup when listener is connected), close any open gap, call `getActiveNotifications()` (guarded against `SecurityException`/binder errors), and pass active allowlisted notifications through the same idempotent pipeline.
   - On app start and listener connect, re-run `RECEIVED`, stale `PROCESSING`, and `FAILED` events (`ING-12`).
5. **Unsupported Source Fast-Path (`ING-13`):**
   - If an allowlisted `packageName` has no registered `TransactionParser`, immediately mark `IGNORED` with `failure_reason = NO_PARSER`, purge `payload_json` immediately (unless opt-in M3a Sample Collection is enabled, max 14d), and count for the 30-day unparsed badge.
6. **Android 15+/16 Hidden Content, Live Updates & Group Handling (`ING-06`):**
   - Never attempt to bypass `RECEIVE_SENSITIVE_NOTIFICATIONS`. Redacted or empty financial notifications route safely to `NEEDS_REVIEW` without guessing fields.
   - Handle Android 16 `Notification.ProgressStyle` / Live Updates and Promoted Ongoing updates safely in the listener without blocking Binder threads or creating duplicate transactions on frequent progress ticks.
7. **Baseline Battery Testing Before Whitelisting (`ING-10`):**
   - During PoC baseline evaluation on the `iQOO Z10R`, **do NOT automatically whitelist W-Track or disable battery optimization as a workaround before baseline measurements complete**. Measure default behavior first, then validate `ING-10` onboarding guidance separately.

---

### B. M0 Verified Android SDK Baseline & Stable Toolchain Compatibility Matrix

Authorized M0 SDK baseline: **`minSdk = 26`**, **`targetSdk = 36`**, **`compileSdk = 37`** (Android 17 stable GA, `PreviewSdkInt=0`, verified locally at `platforms/android-37`). Android 17 beta is **explicitly excluded**. Keeping `targetSdk = 36` aligns runtime behavior directly with the confirmed `iQOO Z10R` (Android 16 / API 36) test device, while `compileSdk = 37` is required by our selected stable AndroidX / Compose 1.12 dependencies (`core-ktx:1.19.1`, `activity-compose:1.13.0`, `compose-bom:2026.09.00`) and aligns cleanly with future M1 `sqlcipher-android:4.20.0`.

**M0 Locked Stable Toolchain Matrix (Zero Preview / Zero Beta):**

| Stack Component | Locked Version (`M0`) | Compatibility & Rationale |
| :--- | :--- | :--- |
| **`minSdk` / `targetSdk` / `compileSdk`** | `26` / `36` / `37` | `targetSdk = 36` prevents Android 17 behavioral changes; `compileSdk = 37` (GA) satisfies AAR metadata of `core-ktx:1.19.1` and `activity-compose:1.13.0`. |
| **Gradle Wrapper** | `9.9.1` | Latest stable Gradle 9.x; supports host `OpenJDK 25.0.5` and AGP `9.5.1`. |
| **AGP** | `9.5.1` (`android.builtInKotlin=false`, `android.newDsl=false`) | Latest stable AGP 9.x; classic DSL opt-outs avoid KSP / Hilt extension incompatibilities while preserving pure-JVM `:integration:*` modules. |
| **Kotlin & KSP** | `2.2.21` / `2.2.21-2.0.4` | Stable Kotlin 2.2 + matching KSP2 compiler (`JVM_17` bytecode target). |
| **Hilt** | `2.60.1` (`hilt-navigation-compose:1.3.0`) | Latest stable AGP 9-compatible Hilt release using KSP (no `kapt`). |
| **Compose & AndroidX** | `compose-bom:2026.09.00`, `activity-compose:1.13.0`, `navigation-compose:2.10.2`, `core-ktx:1.19.1`, `lifecycle:2.10.0`, `datastore-preferences:1.3.0` | Stable Compose 1.12 & AndroidX suite verified against `compileSdk = 37`. |
| **Kotlinx** | `kotlinx-serialization-json:1.9.0`, `kotlinx-coroutines:1.10.2` | Pure-Kotlin JSON & Coroutines primitives shared across `:integration:api` and `:app`. |
| **M1 Deferred Persistence (`ADR-005`)** | `Room 2.8.5` + `net.zetetic:sqlcipher-android:4.20.0` + `androidx.sqlite:sqlite:2.7.0` *(Proposed)* | Intentionally excluded from M0 Gradle dependencies until `ADR-005` encryption & key policy is accepted for M1. **SQLCipher 5 beta** and EOL **`4.5.4`** remain prohibited. |

---

### C. Confirmed Primary Physical Test Device & Idle Diagnostics

#### Confirmed Physical Device Profile (via ADB on Fedora Linux)

| Field | Confirmed Value |
| :--- | :--- |
| **Role** | Primary physical test device for M0–M3a Ingestion PoC |
| **Manufacturer** | `vivo` |
| **Brand** | `iQOO` |
| **Commercial Device** | `iQOO Z10R` |
| **Model Code** | `I2505` |
| **Android Version / API Level** | `Android 16` (`API 36`) |
| **Build ID** | `PD2512LF_EX_A_16.1.20.0.W20` |
| **Security Patch** | `2026-10-01` |
| **CPU ABI** | `arm64-v8a` |
| **Test Host Environment** | Physical device connected through Fedora Linux using ADB |

#### Confirmed Device Idle Diagnostics (`dumpsys deviceidle`)

| Diagnostic Observation | Value | Engineering Implication |
| :--- | :--- | :--- |
| **Light Doze** | Enabled (observed in history) | Screen-off short idle windows batch background jobs and network/CPU wakeups. |
| **Deep Doze** | Enabled (observed in history) | Extended stationary screen-off periods heavily restrict background execution outside maintenance windows. |
| **`wait_for_unlock`** | **`true`** | System withholds exiting idle/certain maintenance transitions until physical unlock — elevates risk of delayed listener/pipeline processing while screen stays locked. |
| **Force idle** | Disabled | Production idle transitions occur naturally rather than under forced state during normal use. |
| **State during inspection** | `ACTIVE` | **Must NOT be interpreted as proof of background reliability**; active bench connection masks Doze and OEM background killing. |

#### Explicitly Unverified Device Scope
- **Additional device and OS compatibility remains UNVERIFIED:** Behavior on other Android versions (API 26–35, Android 17), other OEM skins (Samsung One UI, Xiaomi MIUI/HyperOS, Oppo ColorOS, Pixel AOSP, etc.), and 32-bit/x86 ABIs has **not** been tested or verified.

---

### D. Mandatory Android 16 & Physical-Device Baseline Testing Scope

All 7 baseline scenarios below must be executed on `iQOO Z10R` (`I2505`, Android 16) **under default battery optimization settings first** (before testing any `ING-10` battery/autostart exemptions):
1. **Screen-off locked-screen notification delivery** (AFU state after first boot unlock).
2. **`NotificationListenerService` connection persistence** across screen-off periods and app-switcher swipes.
3. **Listener recovery after process termination** (manual/OS kill + `ING-12` startup recovery + `requestRebind()`).
4. **Device reboot & post-unlock recovery** (verify `onListenerConnected()`, `activeNotifications` catch-up, and encrypted DB reopen).
5. **Extended idle & Light/Deep Doze behavior** (`dumpsys deviceidle step` / natural stationary overnight tests accounting for `wait_for_unlock=true`).
6. **`vivo` / `iQOO` OEM-specific background restrictions** (observe whether OEM killer drops listener silently without `onListenerDisconnected()`, triggering `ING-14` `HEARTBEAT_STALE`).
7. **Default battery optimization baseline vs. `ING-10` exempt behavior** (measure real drop/delay rate under defaults before recommending whitelisting steps).
8. **Android 16 (API 36) notification behaviors:** OTP/sensitive content redaction (`ING-06`), lock-screen privacy masking, Live Updates (`Notification.ProgressStyle`) / Promoted Ongoing updates, and Notification Cooldown during bursts.

---

### E. Initial Provider Targets (`PROVIDERS.md`) & Recommended Gate G1 Baseline

| Item | Proposed / Recommended Value | Status | Required Action |
| :--- | :--- | :--- | :--- |
| **Initial PoC Provider Priority (`docs/PROVIDERS.md`)** | **1. Bank Jago** *(Play ref: `com.jago.digitalBanking`)*<br>**2. DANA** *(Play ref: `id.dana`)*<br>**3. BCA** *(Play refs: `com.bca` [BCA mobile] / `com.bca.mybca.omni.android` [myBCA])* | **Names Confirmed; Device Package IDs & Formats Pending ADB / M3a** | Verify installed package IDs via ADB on `iQOO Z10R` and inspect real notification structures in M3a before writing parsers. Must **not** delay M0 or M1. |
| **Gate G1 Measurement Window** | Minimum **7 consecutive days**, preferably **14 days**; minimum **30 statement transactions per provider** | **Recommended Baseline (PRD 17)** | Owner confirms before measurement starts. |
| **Gate G1 Thresholds** | **Coverage (`D/A`) $\ge 90\%$**, **Completeness (`C/B`) $\ge 95\%$**, **Correctness $= 0$ unexplained wrong amounts or directions** | **Recommended Baseline (PRD 17)** | Owner formally locks numbers here **before** any measured capture begins. |
| **`ING-12` Retry & Grace Limits** | `MAX_ATTEMPTS = 3` (`PIPELINE_ERROR`); stale `PROCESSING` grace $= 60\text{s}$ | **Proposed (PRD 4.2)** | Confirm or tune during M2/M3a. |
| **`ING-14` Heartbeat Parameters** | Beat interval $= 15\text{ min}$ (plus on every processed event); stale gap threshold $= 3\text{ hours}$ | **Proposed (PRD 4.2)** | Confirm or tune from `iQOO Z10R` Doze/unlock tests. |

#### Gate G1 Thresholds Sign-Off *(Lock Before Capture Starts)*
- **1. Bank Jago (`[Verified Pkg TBD]`):** Coverage $\ge$ `[90%]`, Completeness $\ge$ `[95%]`, Unexplained wrong amount/direction $= [0]$, Window $= [\ge 7\text{–}14\text{d}, \ge 30\text{ tx}]$.
- **2. DANA (`[Verified Pkg TBD]`):** Coverage $\ge$ `[90%]`, Completeness $\ge$ `[95%]`, Unexplained wrong amount/direction $= [0]$, Window $= [\ge 7\text{–}14\text{d}, \ge 30\text{ tx}]$.
- **3. BCA (`[Verified Pkg TBD]`):** Coverage $\ge$ `[90%]`, Completeness $\ge$ `[95%]`, Unexplained wrong amount/direction $= [0]$, Window $= [\ge 7\text{–}14\text{d}, \ge 30\text{ tx}]$.

---

## 5. Alternatives Considered

| Alternative | Description | Reason Rejected / Deferred |
| :--- | :--- | :--- |
| **Accessibility Service screen inspection** | Read bank UI trees directly. | **Rejected (PRD 1.4 Non-goal).** Severe privacy/Play policy risk. |
| **Credential-based bank scraping** | Store PINs/passwords/OTPs to poll providers. | **Rejected (PRD 1.4 Non-goal).** Violates zero-credential model. |
| **SMS ingestion as MVP primary source** | Request `READ_SMS` for bank SMS alerts. | **Deferred to PRD 1.5 consideration (non-Play builds).** |
| **Targeting Android 17 beta** | Target preview APIs before production stabilization. | **Rejected.** Owner mandates stable `targetSdk = 36` aligned to Android 16 hardware. |
| **Auto-whitelisting battery optimization before baseline tests** | Bypass Doze/OEM restrictions automatically or immediately in PoC. | **Rejected.** Masks real-world default Android 16 / `vivo` / `iQOO` background failure modes needed for honest PoC & `ING-10` design. |

---

## 6. Technical Justification

- **Two-Stage Persistence (`RECEIVED` → `PROCESSED`)** decouples Binder callbacks from parsing and SQLCipher I/O, preserving events if OEM restrictions kill the service mid-burst (`ING-12`).
- **Evidence-Based Idle & Android 16 Validation:** Knowing upfront that the confirmed `iQOO Z10R` enables Light/Deep Doze with `wait_for_unlock=true` shapes realistic testing for locked-screen delivery and `ING-14` heartbeat monitoring instead of assuming bench `ACTIVE` state equals field reliability.
- **Pre-Committed Gate G1 Thresholds:** Locking `90% / 95% / 0` before capture prevents post-hoc rationalization.

---

## 7. Consequences and Trade-Offs

- **Positive:**
  - Concrete, reproducible physical test baseline anchored to real hardware (`vivo / iQOO Z10R`, API 36, `arm64-v8a`).
  - Realistic separation between default OEM behavior and `ING-10` user-guided battery exemptions.
  - Clear resolution path for `compileSdk = 36` vs `sqlcipher-android:4.20.0` AAR metadata.
- **Negative / Risks:**
  - Passing all tests on `iQOO Z10R` (Android 16) does **not** prove reliability on Samsung, Xiaomi, Oppo, or Android 8–15 devices; multi-device compatibility remains unverified until additional hardware is tested.
  - `wait_for_unlock=true` may delay `WorkManager` maintenance or Doze exit until physical unlock, reinforcing why real-time ingestion cannot depend on `WorkManager` (`PRD Section 4.2`).
