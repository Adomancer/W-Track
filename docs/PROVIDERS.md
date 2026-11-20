# Provider Discovery & Integration Tracking — W-Track

**Document Status:** Provider Discovery Registry (PRD v1.4 — Section 11 & Gate G1 Input)  
**Primary Physical Test Device:** `vivo / iQOO Z10R` (`I2505`, Android 16 / API 36, Build `PD2512LF_EX_A_16.1.20.0.W20`, `arm64-v8a`)  
**Last Updated:** 2026-11-21

---

## 1. Initial PoC Provider Targets & Incremental Priority

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

The Google Play package IDs below are **Reference Candidates Only** and remain marked **Unverified** until confirmed directly against installed packages on the `iQOO Z10R` via ADB (`adb shell pm list packages | grep -iE 'jago|dana|bca'`).

| Priority | Provider | Google Play Reference Candidate(s) *(Unverified on Device)* | `iQOO Z10R` ADB Verification | Parser Subpackage (`:integration:providers`) |
| :-: | :--- | :--- | :--- | :--- |
| **1** | **Bank Jago** | `com.jago.digitalBanking` *(PT Bank Jago Tbk)* | `[PENDING ADB CHECK]` | `wtrack.integration.providers.jago` |
| **2** | **DANA** | `id.dana` *(PT Espay Debit Indonesia Koe)* | `[PENDING ADB CHECK]` | `wtrack.integration.providers.dana` |
| **3** | **BCA** | **BCA mobile:** `com.bca`<br>**myBCA:** `com.bca.mybca.omni.android`<br>*(PT Bank Central Asia Tbk — verify which app(s) are installed & emit tx alerts)* | `[PENDING ADB CHECK]` | `wtrack.integration.providers.bca` |

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
- **Verified Package ID(s):** `[TBD via ADB on iQOO Z10R]`
- **Installed App Version & Channels:** `[TBD]`
- **Notification Fields Observed (`title` / `text` / `bigText` / `subText` / `channelId` / `isGroupSummary`):** `[TBD]`
- **Android 16 Identity Evidence (`notification_key` / `notification_id` / `notification_tag` / `posted_at` mutation on update/re-post):** `[TBD]`

**Empirical Verification Checklist (Bank Jago):**
- [ ] Do notifications distinguish Jago Pockets (*Kantong*) with an account hint (`ACC-05`, `PAR-02`)?
- [ ] Are internal Pocket-to-Pocket movements pushed as notifications? How should direction (`INFLOW`/`OUTFLOW`) and `TRANSFER` type be classified?
- [ ] Which language(s) (`id-ID` vs `en-ID`) and number separators (`Rp50.000` vs `IDR 50,000.00`) appear?
- [ ] Does notification text carry a reliable timestamp/timezone or require `posted_at` fallback (`PAR-12`)?
- [ ] Are failed attempts, QRIS payments, and refunds/reversals pushed, and is a provider reference or balance reported?

| Sample # (Anonymized) | Event Class | Direction (`INFLOW`/`OUTFLOW`) | Type | Language & Amount Format | Timestamp / TZ | Account Hint | Provider Ref | Balance Reported? | Ambiguities / Edge Cases |
| :-: | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `JAGO-01` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` |

---

### 4.2 Priority 2 — DANA
- **Verified Package ID(s):** `[TBD via ADB on iQOO Z10R]`
- **Installed App Version & Channels:** `[TBD]`
- **Notification Fields Observed (`title` / `text` / `bigText` / `subText` / `channelId` / `isGroupSummary`):** `[TBD]`
- **Android 16 Identity Evidence (`notification_key` / `notification_id` / `notification_tag` / `posted_at` mutation on update/re-post):** `[TBD]`

**Empirical Verification Checklist (DANA):**
- [ ] How reliably can transaction alerts be separated from high-volume promo/voucher/loyalty push notifications (`NotTransaction(PROMOTION)`)?
- [ ] Do DANA QRIS/merchant payments funded directly from linked Bank Jago or BCA debit cards trigger notifications from **both** apps (*One payment, two notifiers*, `PRD Section 11`)?
- [ ] Do pending vs completed payment updates reuse the same `(notification_id, tag)` or create separate keys?
- [ ] Are refunds/cashback rewards distinguishable from `PURCHASE` / `TRANSFER` (`PAR-10`)?
- [ ] Does DANA expose `provider_reference` or updated wallet balance in notification extras?

| Sample # (Anonymized) | Event Class | Direction (`INFLOW`/`OUTFLOW`) | Type | Language & Amount Format | Timestamp / TZ | Account Hint | Provider Ref | Balance Reported? | Ambiguities / Edge Cases |
| :-: | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `DANA-01` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` |

---

### 4.3 Priority 3 — BCA (`BCA mobile` / `myBCA`)
- **Verified Package ID(s):** `[TBD via ADB on iQOO Z10R — verify com.bca vs com.bca.mybca.omni.android]`
- **Installed App Version(s) & Channels:** `[TBD]`
- **Notification Fields Observed (`title` / `text` / `bigText` / `subText` / `channelId` / `isGroupSummary`):** `[TBD]`
- **Android 16 Identity Evidence (`notification_key` / `notification_id` / `notification_tag` / `posted_at` mutation on update/re-post):** `[TBD]`

**Empirical Verification Checklist (BCA):**
- [ ] Which app (`BCA mobile`, `myBCA`, or both) is installed and actively sends transaction push notifications vs only security/approval/promo pushes?
- [ ] If both apps are installed, does one payment emit duplicate alerts across `com.bca` and `com.bca.mybca.omni.android`?
- [ ] How do BCA notifications report account hints (last 4 digits vs masked account), multi-account sources (`ACC-05`), or credit card transactions (`ACC-10`)?
- [ ] Do any BCA security/OTP/m-BCA activation alerts look similar to transactions or trigger Android 16 OTP redaction (`ING-06`)?
- [ ] How are transfer admin fees (e.g., BI-FAST / LLG / RTOL) reported — bundled in amount, separate text line, or separate alert (`TRF-04`)?

| Sample # (Anonymized) | App (`BCA mobile` / `myBCA`) | Event Class | Direction (`INFLOW`/`OUTFLOW`) | Type | Language & Amount Format | Timestamp / TZ | Account Hint | Provider Ref | Ambiguities / Edge Cases |
| :-: | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `BCA-01` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` | `[TBD]` |

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
