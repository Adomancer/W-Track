# ADR-001: Modular Architecture

**Date:** 2026-11-21

---

## 1. Status

**Accepted** — Confirmed baseline in PRD v1.4 (Sections 5, 12, and 19).  
Implementation adjustments noted below (specifically `DomainTransactionType` separation and `integration:api` envelope enrichment) are scheduled for **M0 (Foundation)** prior to M1/M2 implementation.

---

## 2. Relevant PRD Requirement References

- **PRD Section 1.3 (Product Goal 6):** Keep the architecture modular enough to add providers and capabilities later.
- **PRD Section 5:** Modular monolith with pragmatic Clean Architecture.
- **PRD Section 6.1:** Architectural boundaries (Presentation, Domain, Data, Integration, Core).
- **PRD Section 6.3:** Parser contract (`TransactionParser`, `ParseResult`, `NotificationEvent`, `ParsedTransaction`, `PartialTransaction`).
- **PRD Section 12:** Three-module Gradle layout (`app`, `integration:api`, `integration:providers`), package organization inside `app`, and strict dependency direction.
- **PRD Section 19 (Confirmed Decisions):** Parser boundary and local-first structure.
- **Requirements & Backlog:** `PAR-08` (isolated, versioned provider parsers), `CORE-002` (three-module structure and package boundaries).

---

## 3. Context and Problem

The application must ingest transaction notifications from multiple financial providers whose formats differ, evolve independently, and require isolated JVM fixture-based testing. At the same time, the product is in the proof-of-concept and MVP stage where premature multi-module fragmentation slows development, increases Gradle configuration overhead, and introduces artificial mapping boilerplate before notification quality is even validated at Gate G1.

We need an architecture that:
1. Cleanly isolates provider parsers from Android UI, Room, and Hilt internals so parsers remain pure Kotlin and testable on the JVM.
2. Enforces unidirectional dependencies into the business domain without prematurely extracting `:domain`, `:data`, or `:feature:*` Gradle modules.
3. Aligns with what already existed in the initial scaffold (`38001da`) while correcting structural edge cases observed in the prior contract draft (`docs/FRONTEND_CONTRACT.md` in `38001da`).

---

## 4. Decision

### Confirmed Baseline (PRD v1.4)

1. **Three Gradle Modules + `build-logic` Convention Plugins:**
   - `:app` (Android application module)
   - `:integration:api` (pure Kotlin JVM library defining the parser contract)
   - `:integration:providers` (pure Kotlin JVM library containing isolated, versioned provider parser implementations and anonymized test fixtures; remains a single module until **3 or more** providers exist, per PRD Section 12)
   - `build-logic` (composite build providing shared Kotlin/Android Gradle convention plugins, as established in `38001da`).
2. **Package-Based Layering Inside `:app`:**
   - `wtrack.app.feature` — Jetpack Compose screens, UI state holders, `ViewModel`s, and navigation.
   - `wtrack.app.domain` — Pure Kotlin business models, repository interfaces, use cases, aggregate/classification rules, and domain exceptions. Must have **zero imports** of Android framework (`android.*`), Jetpack Compose, Room (`androidx.room.*`), SQLCipher, DataStore, or `:integration:providers`.
   - `wtrack.app.data` — Room database, SQLCipher integration, Android Keystore key wrapper, DAOs, entity-to-domain mappers, repository implementations, `NotificationListenerService`, ingestion pipeline orchestrator, retention/heartbeat `WorkManager` workers, and backup/restore engine.
   - `wtrack.app.core` — Material 3 design system/theme, DI setup utilities, clock/time abstractions, and currency/locale helpers.
3. **Strict Module Dependency Direction:**
   - `:app` → `:integration:api` and `:integration:providers` (wires parser implementations into the `ParserRegistry` via Hilt `@IntoSet` or explicit provider list).
   - `:integration:providers` → `:integration:api` **only** (no Android dependencies, no `:app` dependencies).
   - `:integration:api` → Kotlin standard library / pure JVM only.

### Proposed Refinements for M0 Implementation

1. **Automated Architecture Boundary Check (`M0 / CORE-002`):** Add a lightweight JUnit unit test in `:app` (e.g., scanning `wtrack.app.domain` bytecode/imports or using an architecture assertion rule) to fail the build if `domain` imports `android.*`, `androidx.*` (excluding pure annotations if needed, preferably zero `androidx`), `net.zetetic.*`, or `wtrack.integration.providers.*`.
2. **Domain vs. Integration `TransactionType` Separation:** In the prior `38001da` `FRONTEND_CONTRACT.md`, `domain` reused `wtrack.integration.TransactionType` directly, which omits `ADJUSTMENT` (PRD Section 6.3 explicitly notes: *"The domain model adds ADJUSTMENT, which no parser can produce (ACC-08)"*). In M0, `:integration:api` will keep `TransactionType` (`PURCHASE`, `TRANSFER`, `FEE`, `REFUND`, `CASH`, `OTHER`), while `wtrack.app.domain.model` will define `DomainTransactionType` adding `ADJUSTMENT` (or `TransactionType` in domain mapped cleanly from `wtrack.integration.TransactionType`). This preserves compile-time enforcement that no parser can emit an `ADJUSTMENT`.
3. **Enriched `NotificationEvent` Parser Envelope:** In M0, expand `:integration:api`'s `NotificationEvent` beyond a single `text` string to pass safe, extracted notification fields (`title`, `text`, `bigText`, `subText`, `channelId`, `isGroupSummary`, `isContentRedacted`, etc.) while keeping Room entities and raw Android `StatusBarNotification` objects strictly inside `:app:data`.

---

## 5. Alternatives Considered

| Alternative | Description | Reason Rejected / Deferred |
| :--- | :--- | :--- |
| **Single-module monolith (`:app` only)** | Put parser interfaces and provider implementations inside packages in `:app`. | Rejected. Allows accidental Android/Context/Room leakage into parsers and forces Robolectric or Android instrumentation overhead for parser fixture tests instead of fast pure-JVM tests. |
| **Fine-grained Clean Architecture modules (`:domain`, `:data`, `:core`, `:feature:*`)** | Immediately split every layer and UI screen into standalone Gradle modules. | Rejected for MVP (PRD Section 12). Creates excessive Gradle configuration/sync overhead and boilerplate before Gate G1 validates notification quality. Layers can be extracted later if boundaries are repeatedly violated or build times degrade. |
| **Per-provider Gradle modules (`:integration:provider-a`, `:integration:provider-b`)** | One Gradle module per bank/e-wallet from day one. | Deferred per PRD Section 12. One `:integration:providers` module with strict per-provider subpackages (`wtrack.integration.providers.<provider_id>`) is sufficient until 3+ providers exist. |

---

## 6. Technical Justification

- **Fast JVM Test Feedback Loop:** Because `:integration:api` and `:integration:providers` have no Android Gradle Plugin (`com.android.library`) dependency — using pure `kotlin-jvm` — hundreds of anonymized notification fixture tests, locale number-parsing tests (`PAR-12`), and parser regression suites run in milliseconds on the JVM without an emulator or Robolectric.
- **Pragmatic Boundary Enforcement:** Keeping `domain` and `data` as packages inside `:app` avoids creating cross-module DTO clones while still letting `domain` remain pure Kotlin testable on the JVM (`./gradlew :app:testDebugUnitTest`).
- **Clean Provider Scaling Seam:** Wiring `Set<TransactionParser>` in `:app` against the `:integration:api` interface guarantees the ingestion pipeline has zero compile-time coupling to specific banks or wallets (`PAR-08`).

---

## 7. Consequences and Trade-Offs

- **Positive:**
  - Rapid iteration during M0–M3a PoC and clear separation between ingestion mechanics and provider parsing logic.
  - Compile-time safety preventing parsers from accessing Room DAOs, network APIs, Android `Context`, or emitting `ADJUSTMENT` transactions.
- **Negative / Risks:**
  - Package-level isolation inside `:app` (`feature` / `domain` / `data`) is not enforced by Gradle compilation alone; requires code review discipline and the M0 automated package-import test.
  - Once 3+ providers or 5+ feature teams/flows exist post-MVP, extracting `:app:domain` or per-provider Gradle modules will require a controlled refactoring pass.
- **Note on Repository History (`38001da`):**
  - The prior setup commit (`38001da`) adopted this three-module structure (`app`, `integration:api`, `integration:providers`, `build-logic`), though its files are currently deleted in the working tree. When M0 resumes, the codebase should carry forward the three-module layout while addressing the `DomainTransactionType.ADJUSTMENT` gap and `sqlcipher-android` dependency correction (see ADR-005).
