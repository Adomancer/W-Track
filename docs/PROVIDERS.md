# Provider Discovery & Integration Tracking — W-Track

**Document Status:** Provider Discovery Registry (PRD v1.4 — Section 11 & Gate G1 Input)  
**Primary Physical Test Device:** `vivo / iQOO Z10R` (`I2505`, Android 16 / API 36, Build `PD2512LF_EX_A_16.1.20.0.W20`, `arm64-v8a`)  
**Last Updated:** 2026-11-21

---

## 1. Initial PoC Provider Targets & Incremental Priority

> **M3a Tooling Readiness (Unit-Verified):** `LocaleNumberParser` (`PAR-12`), `ProviderDateTimeParser`, `SensitiveContentGuard`, `FixtureAnonymizer`, `ProviderCatalog` (`:integration:providers`), and `GateG1Evaluator` (`:app`) are implemented and tested against synthetic fixtures. `ProviderParsers.registered()` intentionally remains `emptySet()` with zero fabricated templates until on-device `iQOO Z10R` verification completes.

The owner has identified **three initial financial application targets** for PoC validation:

| Priority | Provider Target | Category | Implementation Sequence & Gate Rule |
| :-: | :--- | :--- | :--- |
| **1** | **Bank Jago** | Digital Bank | First parser built after ingestion foundation (**M0 → M1 → M2** complete) |
| **2** | **DANA** | E-Wallet | Second parser built incrementally after Priority 1 PoC capture/validation |
| **3** | **BCA** | Bank | Third parser built incrementally; must **not** block M0, M1, M2, or Bank Jago |

**Non-Blocking Phasing Rules:**
1. **Do NOT delay M0 or M1** for provider discovery or parser implementation.
2. Provider formats, package IDs, and notification schemas are **never assumed** from memory or third-party assumptions — every format must be captured and verified directly on the physical `iQOO Z10R` device (`PRD Section 11`).
3. **Ultimate PoC Objective:** Validate real notification ingestion, idempotency/deduplication, and canonical transaction normalization across all three applications (**Bank Jago**, **DANA**, and **BCA**) while preserving strict `TransactionParser` abstraction.

---

## 2. Package Discovery & On-Device Verification Registry

Verified directly against installed packages on the physical `vivo / iQOO Z10R` (`I2505`, Android 16 / API 36) via wireless ADB (`adb shell pm list packages | grep -iE 'jago|dana|bca|wtrack'`). *(Also present on device: `com.bcadigital.blu`, `com.bca.halobca.android`.)*

| Priority | Provider | Google Play Reference Candidate(s) | `iQOO Z10R` ADB Verification | Parser Subpackage (`:integration:providers`) |
| :-: | :--- | :--- | :--- | :--- |
| **1** | **Bank Jago** | `com.jago.digitalBanking` *(PT Bank Jago Tbk)* | **Verified:** `com.jago.digitalBanking` (`v8.99.1`, `versionCode=11100`, **`POST_NOTIFICATIONS: granted=false`** — enable in App Info) | `wtrack.integration.providers.jago` |
| **2** | **DANA** | `id.dana` *(PT Espay Debit Indonesia Koe)* | **Verified:** `id.dana` (`v2.152.0`, `versionCode=21520016`, `POST_NOTIFICATIONS: granted=true`) | `wtrack.integration.providers.dana` |
| **3** | **BCA** | **BCA mobile:** `com.bca`<br>**myBCA:** `com.bca.mybca.omni.android`<br>*(PT Bank Central Asia Tbk)* | **Both Verified Installed:** `com.bca` (`v4.9.5`, `versionCode=1297`, `POST_NOTIFICATIONS: granted=true`) & `com.bca.mybca.omni.android` (`v2.23.0`, `versionCode=299`, `POST_NOTIFICATIONS: granted=true`) | `wtrack.integration.providers.bca` |

> **Module Scaling Note (PRD Section 12 & ADR-001):** During M3a and Gate G1, all three provider parsers reside in isolated subpackages (`jago`, `dana`, `bca`) inside the single pure-Kotlin `:integration:providers` module. Splitting into separate per-provider Gradle modules is evaluated post-Gate G1 only when complexity or build times justify it.

---

## 3. Provider Abstraction & Security Boundaries

1. **Zero Hardcoded Provider Logic in Core Services:** `WtrackNotificationListenerService`, `AllowlistManager`, and `IngestionPipeline` remain 100% provider-agnostic. Parsers implement `TransactionParser` (`:integration:api`) and register via Hilt `@IntoSet` into `ParserRegistry`.
2. **Strict Non-Collection of Credentials / OTPs:**
   - Never collect, persist as transactions, or retain OTPs, PINs, passwords, CVVs, or full account/card numbers (`PRD Section 1.4`, `9.1`).
   - Explicit OTP/security/login alerts must classify as `NotTransaction(SECURITY_NOTICE)` and purge payload immediately (or enter `NEEDS_REVIEW` without field guessing if OS Android 15+/16 OTP redaction stripped the body — `ING-06`).
3. **Sample Collection & Anonymization (`PRD Section 11`, `SECURITY.md §6.2`):**
   - Raw M3a sample collection is **off by default**, toggled explicitly per single source (max 14 days inside SQLCipher DB, excluded from backups).
   - Real account hints, names, references, and personal histories must be replaced with synthetic placeholders before committing fixtures to `:integration:providers/src/test/resources/`.

---

## 4. Per-Provider Discovery & Evidence Templates *(Populated on `iQOO Z10R` During M3a)*

> **Zero-Assumption Policy:** All notification template tables below are intentionally blank until verified from actual captures on the `iQOO Z10R` (`I2505`).

### 4.1 Priority 1 — Bank Jago
- **Verified Package ID(s):** `com.jago.digitalBanking`
- **Installed App Version & Channels:** `v8.99.1` (`versionCode=11100`); `POST_NOTIFICATIONS: granted=true` (enabled by owner); observed alert channel: `foreground`
- **Notification Fields Observed (`title` / `text` / `bigText` / `subText` / `channelId` / `isGroupSummary`):**
  - Transfer alerts: `title = "Jago"`, `text` and `bigText` contain identical full sentence ending with `"Need help? Contact Tanya Jago at 1500 746."`, `subText = null`, `channelId = "foreground"`, `isGroupSummary = false`
  - Android 16 auto-group summary: `title = null`, `text = ""`, `bigText = null`, `channelId = "foreground"`, `isGroupSummary = true`
- **Android 16 Identity Evidence (`notification_key` / `notification_id` / `notification_tag` / `posted_at` mutation on update/re-post):**
  - Individual transfer notifications use distinct integer `notificationId` values (`1144404937`, `1302759633`), `notificationTag = null`, distinct `notificationKey`
  - OS posts an empty group summary ~258ms after multi-notification arrival (`notificationId = 0`, `notificationTag = "0|com.jago.digitalBanking|g:Aggregate_AlertingSection"`, `isGroupSummary = true`)

**Empirical Verification Checklist (Bank Jago):**
- [x] Do notifications distinguish Jago Pockets (*Kantong*) with an account hint (`ACC-05`, `PAR-02`)? *(Yes: Pocket-to-Pocket templates include explicit pocket names such as `"Main Pocket Pocket"` / `"<Pocket Name> Pocket"`. External transfer alerts do not carry pocket hints and fall back to the single Bank Jago account link.)*
- [x] Are internal Pocket-to-Pocket movements pushed as notifications? How should direction (`INFLOW`/`OUTFLOW`) and `TRANSFER` type be classified? *(Yes: observed `"Rp... has been moved from your Main Pocket Pocket to your <Dest> Pocket."`, `"You've moved Rp... from <Src> Pocket to your <Dest> Pocket."`, and `"You've moved Rp... out of your <Src> Pocket."` Returning from a sub-pocket to Main Pocket uses `"out of your <Src> Pocket"` without repeating destination. Policy (`v2`): monitor `Main Pocket` exclusively (`OUTFLOW` when leaving `Main Pocket`, `INFLOW` when entering or returning to `Main Pocket`), and ignore sub-pocket <-> sub-pocket movements via `NotTransaction(OTHER)`.)*
- [x] Which language(s) (`id-ID` vs `en-ID`) and number separators (`Rp50.000` vs `IDR 50,000.00`) appear? *(Observed English prose with Indonesian dot thousands formatting `Rp50.000` — no space after `Rp`.)*
- [x] Does notification text carry a reliable timestamp/timezone or require `posted_at` fallback (`PAR-12`)? *(No timestamp inside text; requires `posted_at` fallback.)*
- [ ] Are failed attempts, QRIS payments, and refunds/reversals pushed, and is a provider reference or balance reported? *(No `providerReference` or reported balance in transfer or pocket-move alerts; QRIS/failed/refund alerts still pending observation.)*

| Sample # (Anonymized) | Event Class | Direction (`INFLOW`/`OUTFLOW`) | Type | Language & Amount Format | Timestamp / TZ | Account Hint | Provider Ref | Balance Reported? | Ambiguities / Edge Cases |
| :-: | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `JAGO-01` | Transfer In (`"<SENDER> has sent Rp50.000 to you..."`) | `INFLOW` | `TRANSFER` | `en` text + `Rp50.000` (`id-ID` dot separator) | `posted_at` fallback | None | None | No | Strip `"Need help? Contact Tanya Jago at 1500 746."` footer |
| `JAGO-02` | Transfer Out (`"You've transferred Rp50.000 to <RECIPIENT>..."`) | `OUTFLOW` | `TRANSFER` | `en` text + `Rp50.000` (`id-ID` dot separator) | `posted_at` fallback | None | None | No | Strip `"Need help? Contact Tanya Jago at 1500 746."` footer |
| `JAGO-03` | Android 16 Auto-Group Summary (`g:Aggregate_AlertingSection`) | `—` (`NotTransaction(OTHER)`) | `—` | Empty (`""`) | `posted_at` | None | None | No | `isGroupSummary=true`; must filter before blank-body `NeedsReview` check |
| `JAGO-04` | Main Pocket -> Sub-Pocket (`"Rp250.000 has been moved from your Main Pocket Pocket to your <DEST> Pocket."`) | `OUTFLOW` | `TRANSFER` | `en` text + `Rp250.000` (`id-ID` dot separator) | `posted_at` fallback | `"Main Pocket"` | None | No | Double `"Pocket"` suffix (`"Main Pocket Pocket"`) -> strip 1 trailing `" Pocket"` |
| `JAGO-05` | Sub-Pocket -> Main Pocket (`"You've moved Rp250.000 out of your <SRC> Pocket..."`) | `INFLOW` | `TRANSFER` | `en` text + `Rp250.000` (`id-ID` dot separator) | `posted_at` fallback | `"Main Pocket"` | None | No | Destination omitted when returning to Main Pocket; if `<SRC>` is Main Pocket, treat as `OUTFLOW` |
| `JAGO-06` | Sub-Pocket <-> Sub-Pocket (`"You've moved Rp1.000 from <SRC> Pocket to your <DEST> Pocket..."`) | `—` (`NotTransaction(OTHER)`) | `—` | `en` text + `Rp1.000` (`id-ID` dot separator) | `posted_at` | None | None | No | Strip `"Click here to see how to add money to Jago."` + Tanya Jago footer; ignore completely |

---

### 4.2 Priority 2 — DANA
- **Verified Package ID(s):** `id.dana`
- **Installed App Version & Channels:** `v2.152.0` (`versionCode=21520016`); `POST_NOTIFICATIONS: granted=true`; observed channel: `fcm_default_channel_sound_2131951648` (shared by both transaction alerts and quest/mission pushes)
- **Notification Fields Observed (`title` / `text` / `bigText` / `subText` / `channelId` / `isGroupSummary`):**
  - Transfer In: `title = "DANA"`, `text` and `bigText` contain `"Rp2.000 telah diterima dari <SENDER> 💰"`, `subText = null`, `channelId = "fcm_default_channel_sound_2131951648"`, `isGroupSummary = false`
  - Quest / Mission Noise: `title = "DANA"`, `"Yay, kamu berhasil selesaikan quest!"` / `"Misi selesai🎉 Yuk, klaim hadiahmu sekarang!"` on the same channel
  - Android 16 auto-group summary: `title = null`, `text = ""`, `bigText = null`, `channelId = "fcm_default_channel_sound_2131951648"`, `isGroupSummary = true`
- **Android 16 Identity Evidence (`notification_key` / `notification_id` / `notification_tag` / `posted_at` mutation on update/re-post):**
  - Individual alerts use distinct integer `notificationId` values (`21004`–`21009`), `notificationTag = null`, distinct `notificationKey`
  - OS posts an empty group summary (`notificationId = 0`, `notificationTag = "0|id.dana|g:Aggregate_AlertingSection"`, `isGroupSummary = true`)

**Empirical Verification Checklist (DANA):**
- [x] How reliably can transaction alerts be separated from high-volume promo/voucher/loyalty push notifications (`NotTransaction(PROMOTION)`)? *(Observed 5 quest/mission pushes vs 1 transfer alert on the same FCM channel; reliably filtered via no-amount quest/mission rules in `DanaTransactionParser`.)*
- [ ] Do DANA QRIS/merchant payments funded directly from linked Bank Jago or BCA debit cards trigger notifications from **both** apps (*One payment, two notifiers*, `PRD Section 11`)?
- [ ] Do pending vs completed payment updates reuse the same `(notification_id, tag)` or create separate keys?
- [ ] Are refunds/cashback rewards distinguishable from `PURCHASE` / `TRANSFER` (`PAR-10`)?
- [ ] Does DANA expose `provider_reference` or updated wallet balance in notification extras? *(Not present in incoming transfer notification; monitor outgoing/QRIS pushes.)*

| Sample # (Anonymized) | Event Class | Direction (`INFLOW`/`OUTFLOW`) | Type | Language & Amount Format | Timestamp / TZ | Account Hint | Provider Ref | Balance Reported? | Ambiguities / Edge Cases |
| :-: | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `DANA-01` | Transfer In (`"Rp2.000 telah diterima dari <SENDER> 💰"`) | `INFLOW` | `TRANSFER` | `id-ID` + `Rp2.000` (dot thousands, no space) | `posted_at` fallback | None | None | No | Strip trailing `💰` emoji from `merchantRaw` |
| `DANA-02` | Quest Completed (`"Yay, kamu berhasil selesaikan quest!"`) | `—` (`NotTransaction(PROMOTION)`) | `—` | No amount | `posted_at` | None | None | No | Same channel as real tx; filter as `PROMOTION` |
| `DANA-03` | Mission Reward (`"Misi selesai🎉 Yuk, klaim hadiahmu sekarang!"`) | `—` (`NotTransaction(PROMOTION)`) | `—` | No amount | `posted_at` | None | None | No | Same channel as real tx; filter as `PROMOTION` |
| `DANA-04` | Android 16 Auto-Group Summary (`g:Aggregate_AlertingSection`) | `—` (`NotTransaction(OTHER)`) | `—` | Empty (`""`) | `posted_at` | None | None | No | `isGroupSummary=true`; must filter before blank-body check |

---

### 4.3 Priority 3 — BCA (`BCA mobile` / `myBCA`)
- **Verified Package ID(s):** `com.bca` (`BCA mobile`) and `com.bca.mybca.omni.android` (`myBCA`) — both installed on `iQOO Z10R`; **`myBCA` (`com.bca.mybca.omni.android`) actively emits transaction Financial Diary pushes**
- **Installed App Version(s) & Channels:** `com.bca` `v4.9.5` (`versionCode=1297`), `com.bca.mybca.omni.android` `v2.23.0` (`versionCode=299`), both `POST_NOTIFICATIONS: granted=true`; observed `myBCA` channel: `General Notification`
- **Notification Fields Observed (`title` / `text` / `bigText` / `subText` / `channelId` / `isGroupSummary`):**
  - Financial Diary alerts: `title = "Financial Diary"`, `text` and `bigText` carry identical English summary (`"You received IDR 50,000.00 from ... at ... category."` / `"You spent IDR 50,000.00 at ..."`), `subText = null`, `channelId = "General Notification"`, `isGroupSummary = false`
  - Android 16 auto-group summary: `title = null`, `text = ""`, `bigText = null`, `channelId = "General Notification"`, `isGroupSummary = true`
- **Android 16 Identity Evidence (`notification_key` / `notification_id` / `notification_tag` / `posted_at` mutation on update/re-post):**
  - Individual alerts use distinct integer `notificationId` values (`285226`, `235859`), `notificationTag = null`, distinct `notificationKey`
  - OS posts an empty group summary ~229ms after multi-notification arrival (`notificationId = 0`, `notificationTag = "0|com.bca.mybca.omni.android|g:Aggregate_AlertingSection"`, `isGroupSummary = true`)

**Empirical Verification Checklist (BCA):**
- [x] Which app (`BCA mobile`, `myBCA`, or both) is installed and actively sends transaction push notifications vs only security/approval/promo pushes? *(Confirmed `myBCA` `com.bca.mybca.omni.android` emits Financial Diary transaction pushes; `com.bca` emitted zero pushes during test transactions.)*
- [ ] If both apps are installed, does one payment emit duplicate alerts across `com.bca` and `com.bca.mybca.omni.android`? *(Only `myBCA` fired for the tested account transfer; monitor QRIS/VA payments.)*
- [ ] How do BCA notifications report account hints (last 4 digits vs masked account), multi-account sources (`ACC-05`), or credit card transactions (`ACC-10`)? *(Not present in tested Financial Diary transfer alerts; sender name is partially asterisk-masked by myBCA.)*
- [ ] Do any BCA security/OTP/m-BCA activation alerts look similar to transactions or trigger Android 16 OTP redaction (`ING-06`)?
- [ ] How are transfer admin fees (e.g., BI-FAST / LLG / RTOL) reported — bundled in amount, separate text line, or separate alert (`TRF-04`)?

| Sample # (Anonymized) | App (`BCA mobile` / `myBCA`) | Event Class | Direction (`INFLOW`/`OUTFLOW`) | Type | Language & Amount Format | Timestamp / TZ | Account Hint | Provider Ref | Ambiguities / Edge Cases |
| :-: | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `BCA-01` | `myBCA` | Financial Diary Received (`"You received IDR 50,000.00 from <MASKED_SENDER> at Account Transfer category."`) | `INFLOW` | `TRANSFER` | `en` + `IDR 50,000.00` (`en-US` comma thousands, `.00`) | `posted_at` fallback | None | None | Sender name is pre-masked with `*` by myBCA |
| `BCA-02` | `myBCA` | Financial Diary Spent (`"You spent IDR 50,000.00 at Account Transfer."`) | `OUTFLOW` | `TRANSFER` | `en` + `IDR 50,000.00` (`en-US` comma thousands, `.00`) | `posted_at` fallback | None | None | Type derived from category/merchant label (`Account Transfer`) |
| `BCA-03` | `myBCA` | Android 16 Auto-Group Summary (`g:Aggregate_AlertingSection`) | `—` (`NotTransaction(OTHER)`) | `—` | Empty (`""`) | `posted_at` | None | None | `isGroupSummary=true`; must filter before blank-body `NeedsReview` check |

---

## 5. Gate G1 Evidence Log (`PRD Appendix C` Format)

Target thresholds (recommended baseline in `ADR-003`, formally locked before measurement window starts):
- **Duration & Volume:** Minimum **7 consecutive days** (preferably **14 days**), **$\ge 30$ statement transactions per provider**.
- **Coverage (`D / A`):** $\ge 90\%$ | **Completeness (`C / B`):** $\ge 95\%$ | **Wrong Amount / Direction:** $0$ unexplained.

*(Populate exclusively from each provider's official app statement as ground truth for `A` — never from W-Track's DB.)*

| Provider | Verified Package ID | Days | A: Tx in Statement | B: Tx Notifications Captured | C: Usable (Amount, Direction, Time) | D: Statement Tx with Usable Notification | Coverage (`D/A`) | Completeness (`C/B`) | Wrong Amount / Direction | Duplicates / Updates / Dual-Notifiers Observed | Notes |
| :--- | :--- | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :--- | :--- |
| **1. Bank Jago** | `[TBD]` | — | — | — | — | — | — | — | — | — | — |
| **2. DANA** | `[TBD]` | — | — | — | — | — | — | — | — | — | — |
| **3. BCA** | `[TBD]` | — | — | — | — | — | — | — | — | — | — |
