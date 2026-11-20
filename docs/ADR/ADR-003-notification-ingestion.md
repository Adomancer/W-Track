# ADR-003: Notification Ingestion, Android SDK Targets, Test Devices, and Gate G1 Thresholds

**Date:** 2026-11-21

---

## 1. Status

**Proposed — Partially Confirmed Baseline with Owner Decisions Pending**

- **Confirmed Baseline (PRD v1.4):** Use Android `NotificationListenerService`, strict package allowlist, lightweight callback handoff, persistent inbox before processing, rebind/catch-up scan, unfinished-event recovery (`ING-12`), unsupported-source ignoring/counting (`ING-13`), and Android 15+ redacted-content safe handling (`ING-06`).
- **Pending Owner Confirmation (Blocking M0 / Measured Capture):**
  1. Final `minSdk`, `targetSdk`, and `compileSdk` versions (must be resolved **before M0**).
  2. Physical test device roster — model, OEM skin, Android version (must be resolved **before M0**).
  3. The first two real financial apps to integrate (must be resolved **before M3a**).
  4. Final **Gate G1 measurement thresholds** (MUST be locked in this ADR **before any Gate G1 measured capture begins**, per PRD Section 17).
  5. Operational parameters for `ING-12` retry cap / grace period and `ING-14` heartbeat interval / stale threshold.

---

## 2. Relevant PRD Requirement References

- **PRD Section 4.2:** `ING-01` to `ING-14` (listener, permission, allowlist, hidden/grouped content, status, test capture, rebind/catch-up, battery/autostart/restricted-settings guidance, capture gaps, event recovery, unsupported sources, heartbeat).
- **PRD Section 4.1 & 4.3:** `ACC-06`, `PAR-01`, `PAR-06`, `PAR-12`.
- **PRD Section 5:** Android version decisions, Android 15 OTP redaction behavior, sideloaded restricted-settings behavior.
- **PRD Section 6.2 & 8.1:** 16-step ingestion pipeline, recording `posted_at`, `notification_key`, `notification_id`, `notification_tag`.
- **PRD Section 13 & 14.3:** Reliability handling and device/lifecycle test requirements.
- **PRD Section 17, 17.1, Appendix C:** Gate G1 go/no-go procedure, metrics, thresholds, and evidence log.
- **Backlog:** `PIPE-001` to `PIPE-008`, `PIPE-011`, `PIPE-015`, `TEST-003`, `TEST-004`, `TEST-007`.

---

## 3. Context and Problem

Many personal banks and e-wallets do not provide consumer-accessible transaction APIs. To aggregate spending automatically without requiring credentials or invasive screen scraping (explicit non-goals in PRD Section 1.4), the MVP relies on notifications posted to Android's notification shade.

However, Android notification ingestion introduces major platform and OEM risks:
1. `NotificationListenerService` callbacks (`onNotificationPosted`) execute on an IPC Binder thread; heavy database or regex work inside the callback risks ANRs, dropped IPC events, or OS listener unbinding.
2. OEM battery optimizations (Xiaomi HyperOS/MIUI, Samsung One UI, Oppo ColorOS, Vivo FuntouchOS, etc.) frequently kill background listener processes without delivering `onListenerDisconnected()`.
3. Android 13+ (API 33+) blocks Notification Access for sideloaded APKs under "Restricted Settings" until manually allowed in App Info.
4. Android 15+ (API 35+) automatically redacts notification content for non-privileged listeners when the system detects potential OTP/2FA codes (`RECEIVE_SENSITIVE_NOTIFICATIONS` is restricted to `role|signature` system/companion apps), replacing text with a redaction notice such as *"Sensitive notification content hidden"*.
5. Notification updates, grouped summary notifications, and re-posts vary by provider and must be measured empirically before committing to parser heuristics.

---

## 4. Decision

### A. Confirmed Ingestion Architecture (PRD v1.4 Baseline)

1. **Primary Mechanism (`ING-01`, `ING-02`):** Implement a subclass of `android.service.notification.NotificationListenerService` (`WtrackNotificationListenerService`) declared in `AndroidManifest.xml` with `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`. No `AccessibilityService`, no credential entry, and no screen scraping.
2. **Synchronous Allowlist Filter & Lightweight Callback (`ING-03`, `ING-04`, Section 6.2):**
   - Maintain an in-memory `StateFlow<Set<String>>` / `AtomicReference<Set<String>>` snapshot of allowlisted `package_name`s (backed by encrypted Room `transaction_sources` table).
   - Inside `onNotificationPosted(sbn)`: synchronously check `sbn?.packageName in allowlist`. Non-allowlisted packages return immediately in microseconds without allocating payloads or hitting disk.
   - Extract only minimal fields (`packageName`, `key`, `id`, `tag`, `postTime`, `title`, `text`, `bigText`, `subText`, `channelId`, `isGroupSummary`, `isContentRedacted` heuristic/metadata) into a lightweight envelope, then hand off asynchronously to a serialized/bounded coroutine ingestion queue (`SupervisorJob` + `Dispatchers.IO`). Do **not** rely on `WorkManager` for real-time capture (`PRD Section 4.2`).
3. **Persist-Before-Parse & Evidence Capture (`ING-05`, `ING-12`, Section 8.1):**
   - Every accepted event is persisted into the encrypted `notification_events` table with `processing_status = RECEIVED`, `posted_at = sbn.postTime`, `notification_key = sbn.key`, `notification_id = sbn.id`, and `notification_tag` (or SHA-256 hash if a provider's tag is observed to carry PII) **before** parser execution.
   - This ensures process death mid-pipeline does not lose accepted events and records `key`/`id`/`tag` lifecycle evidence from the first PoC build.
4. **Rebind, Catch-Up Scan & Unfinished Recovery (`ING-09`, `ING-12`):**
   - On `onListenerDisconnected()`, invoke `requestRebind(ComponentName)` where appropriate and record an open `CaptureGap(LISTENER_DISCONNECTED)`.
   - On `onListenerConnected()` (and app startup when listener is connected), close any open gap, call `getActiveNotifications()` (guarded against `SecurityException`/binder errors), and pass active allowlisted notifications through the same idempotent pipeline.
   - On app start and listener connect, query `notification_events` in `RECEIVED`, stale `PROCESSING`, or `FAILED` (`ING-12`) and re-run them.
5. **Unsupported Source Fast-Path (`ING-13`):**
   - If an allowlisted `packageName` has no registered `TransactionParser`, immediately mark `processing_status = IGNORED` with `failure_reason = NO_PARSER`, purge `payload_json` immediately (`payload_json = null`), and count the event for the 30-day unparsed notification badge (unless explicit M3a **Sample Collection** mode is toggled on by the owner for that source, capped at 14 days).
6. **Android 15+ Hidden Content & Group Handling (`ING-06`):**
   - Group summary notifications (`FLAG_GROUP_SUMMARY`) that contain no standalone transaction content are ignored or inspected safely per provider evidence; child notifications are processed individually.
   - Notifications whose content is hidden/redacted by Android 15+ (`Notification.FLAG_REDACTED` / redacted text pattern / empty body where transaction is expected) must **never** attempt to bypass `RECEIVE_SENSITIVE_NOTIFICATIONS` and must **never** guess amounts/directions. They route safely to `NEEDS_REVIEW` (or wait for provider-specific M3a classification evidence).
7. **Sideload Restricted Settings & OEM Battery Guidance (`ING-10`):**
   - Onboarding explicitly guides users through Notification Access, disabling battery optimization / enabling OEM autostart, and allowing Restricted Settings when sideloaded on Android 13+.

---

### B. Proposed Parameters & Owner-Open Decisions (`[OWNER DECISION REQUIRED]`)

> **CRITICAL RULE (PRD Section 17):** Owner-open items 1–4 below MUST be resolved and explicitly recorded in this section **before M0 implementation** (for SDK/devices) and **before Gate G1 measured capture begins** (for initial providers and G1 thresholds). Thresholds chosen *after* inspecting capture data invalidate Gate G1.

| Item | Proposed Value | Status | Owner Confirmation Requirement |
| :--- | :--- | :--- | :--- |
| **`minSdk`** | **API 26 (Android 8.0)** | **Proposed** (was in `38001da`) | Confirm lowest Android version among owner's primary and secondary test devices. Lowering below 26 is not recommended (loses `NotificationChannel`, standard `requestRebind` stability, `java.time`). |
| **`targetSdk` / `compileSdk`** | **`compileSdk = 35` or `36`**, **`targetSdk = 35` or `36`** *(Note: `38001da` used `compileSdk = 37`, `targetSdk = 36` with AGP 9.5.1)* | **Proposed** | Confirm target Android SDK matching owner's physical devices and installed Android SDK toolchain. Targeting API 35+ triggers mandatory 16KB native page-size compatibility (see ADR-005) and Android 15 OTP redaction behavior. |
| **Test Devices Matrix** | *None confirmed yet* | **OPEN (Blocking M0)** | Owner must populate the table below with exact physical device(s), OEM skin, and Android version before M0/PoC runs. |
| **First Two Target Financial Apps** | *None selected yet* | **OPEN (Blocking M3a)** | Owner must name the 2 real bank/e-wallet apps installed on the test device(s) based on daily usage. Do not hardcode speculative apps. |
| **Gate G1 Measurement Window** | **7–14 consecutive days**, **≥ 30 transactions per provider** | **Proposed (PRD 17)** | Owner confirms duration and minimum sample floor before starting measurement. |
| **Gate G1 Thresholds** | **Coverage (`D/A`) ≥ 90%**<br>**Completeness (`C/B`) ≥ 95%**<br>**Correctness = 0 unexplained wrong amounts/directions** | **Proposed (PRD 17)** | Owner locks final numeric thresholds **here** before collecting measured data. |
| **`ING-12` Retry & Grace Limits** | `MAX_ATTEMPTS = 3` before `PIPELINE_ERROR`; stale `PROCESSING` grace = `60s` | **Proposed (PRD 4.2)** | Owner confirms or adjusts retry count and grace period. |
| **`ING-14` Heartbeat Parameters** | Beat interval = `15 min` (plus on every processed event); stale gap threshold = `3 hours` | **Proposed (PRD 4.2)** | Owner confirms or tunes after M2/M3a OEM lifecycle testing. |

#### Physical Test Device Roster *(To Be Completed by Owner Before M0)*

| Role | Device Model | OEM / OS Skin | Android Version (API) | Install Method (Sideload / Play Internal) |
| :--- | :--- | :--- | :--- | :--- |
| Primary daily driver | `[TBD by Owner]` | `[TBD by Owner]` | `[TBD by Owner]` | `[TBD by Owner]` |
| Secondary / Android 15+ test device (optional) | `[TBD by Owner]` | `[TBD by Owner]` | `[TBD by Owner]` | `[TBD by Owner]` |

#### Locked Gate G1 Thresholds Sign-Off *(To Be Signed Before Measurement Starts)*

- **Provider 1:** `[TBD by Owner]` — Coverage ≥ `[90%]`, Completeness ≥ `[95%]`, Max unexplained wrong amount/direction = `[0]`, Window = `[7–14 days, ≥30 tx]`.
- **Provider 2:** `[TBD by Owner]` — Coverage ≥ `[90%]`, Completeness ≥ `[95%]`, Max unexplained wrong amount/direction = `[0]`, Window = `[7–14 days, ≥30 tx]`.

---

## 5. Alternatives Considered

| Alternative | Description | Reason Rejected / Deferred |
| :--- | :--- | :--- |
| **Accessibility Service screen inspection** | Read bank app UI trees directly to guarantee balance and history extraction. | **Rejected (PRD 1.4 Non-goal).** Severe privacy/security risk, fragile UI coupling, and strict Google Play policy bans for non-accessibility apps. |
| **Credential-based bank scraping / unofficial APIs** | Store user login/PIN/OTP to poll bank web/mobile APIs. | **Rejected (PRD 1.4 Non-goal).** Violates zero-credential security model and exposes user to account compromise/ToS violations. |
| **SMS ingestion as MVP primary source** | Request `READ_SMS` permission to parse bank SMS alerts. | **Deferred to PRD 1.5 consideration (non-Play builds).** Google Play strictly restricts `READ_SMS`, and modern Indonesian/regional banks/e-wallets primarily send push notifications rather than SMS. |
| **WorkManager for real-time ingestion** | Enqueue a `OneTimeWorkRequest` for every incoming notification instead of an in-process coroutine queue. | **Rejected for real-time path (PRD 4.2).** `WorkManager` batches/defers execution under Doze/App Standby, delaying capture and creating an unnecessary serialization hop before persistence. `WorkManager` IS used for periodic retention purge, fallback heartbeat, and backup reminders. |
| **Bypassing Android 15 redaction via ADB / CompanionDeviceManager** | Require users to run `adb shell appops set ... RECEIVE_SENSITIVE_NOTIFICATIONS allow` or abuse Companion roles. | **Rejected.** Unacceptable consumer UX and violates platform security boundaries; Android 15+ redaction is handled transparently via `NeedsReview` (`ING-06`). |

---

## 6. Technical Justification

- **Two-Stage Persistence (`RECEIVED` → `PROCESSED`)** decouples fast Binder callback reception from heavier parsing/SQLite write contention, eliminating lost notifications during rapid bursts while guaranteeing crash recovery (`ING-12`).
- **Evidence-First Notification Identity:** Recording `notification_key`, `notification_id`, `notification_tag`, and `posted_at` from build 1 prevents discovering weeks later that a provider mutates/re-posts notifications with shifting IDs or timestamps.
- **Pre-Committed Gate G1 Thresholds:** Writing quantitative thresholds before seeing real logs eliminates confirmation bias ("judging by feel") before investing ~30–50 working days in M3b–M5.

---

## 7. Consequences and Trade-Offs

- **Positive:**
  - Respects Android OS privacy boundaries and requires zero banking credentials.
  - Handles process death, listener disconnects, and rapid bursts without dropping accepted events.
  - Provides objective Go / Re-scope / No-go decision criteria at Gate G1.
- **Negative / Risks:**
  - Ingestion is inherently best-effort when OS kills the process or user revokes permission; missed notifications while disconnected and cleared from the drawer cannot be retroactively recovered (`PRD Section 20`).
  - Android 15+ OTP redaction may suppress transaction details if a provider embeds an OTP-like token or reference pattern that triggers Android's redaction classifier — this must be explicitly tested during M3a on real hardware.
  - `compileSdk = 37` in deleted commit `38001da` may rely on preview/bleeding-edge SDK dependencies (`core-ktx:1.19.1`, `agp:9.5.1`) that should be checked against the owner's local Android SDK and stable release compatibility in M0.
