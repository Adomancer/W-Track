# Frontend Contract

This document is the contract between the presentation layer (Compose UI / ViewModels) and the domain and data layers, per the PRD version 1.4 and section 12 of the PRD. The frontend agent implements against these types and interfaces; it must not invent methods or data structures. The domain package (wtrack.app.domain) must not import UI, Room, the Android framework, or provider-specific code.

The pure-Kotlin parser contract lives in the integration:api module and is referenced here. The presentation layer never talks to parsers directly; it talks to the repositories and use cases below.

## 1. Domain models (wtrack.app.domain.model)

All money values are Long minor units. Direction and TransactionType come from the integration API module (wtrack.integration).

```kotlin
enum class Direction { INFLOW, OUTFLOW }                       // from wtrack.integration
enum class TransactionType { PURCHASE, TRANSFER, FEE, REFUND, CASH, OTHER }  // from wtrack.integration

enum class TransactionStatus { CONFIRMED, VOIDED }

enum class AccountType { BANK, EWALLET, CASH, CREDIT_CARD, OTHER }

enum class CaptureStatus { ACTIVE, ATTENTION, DISCONNECTED, UNSUPPORTED }

enum class CategorySource { USER, RULE }

enum class TransferOrigin { MANUAL, AUTO }
enum class TransferStatus { SUGGESTED, CONFIRMED, REJECTED }

data class Account(
    val id: Long,
    val name: String,
    val provider: String,
    val accountType: AccountType,
    val currency: String,                       // ISO 4217
    val initialBalanceMinor: Long?,
    val initialBalanceAt: Long?,                // epoch millis UTC; required if initialBalanceMinor set
    val lastReconciledAt: Long?,
    val isActive: Boolean,
    val captureStatus: CaptureStatus,
)

data class Transaction(
    val id: Long,
    val accountId: Long,
    val sourceId: String?,
    val eventId: String?,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val transactionType: TransactionType,
    val merchantId: Long?,
    val description: String?,
    val occurredAt: Long,                       // epoch millis UTC
    val categoryId: Long?,
    val categorySource: CategorySource?,
    val appliedRuleId: Long?,
    val status: TransactionStatus,
    val fingerprint: String?,
    val parserVersion: Int?,
    val originalJson: String?,                  // TX-03: values it was created with
    val providerReference: String?,
    val relatedTransactionId: Long?,
    val reportedBalanceMinor: Long?,            // used only for reconciliation ACC-07
    val createdAt: Long,
    val updatedAt: Long,
)

enum class ReviewReason {
    MISSING_AMOUNT, MISSING_DIRECTION, INVALID_VALUE, AMBIGUOUS_FIELD, UNKNOWN_FORMAT,
    ACCOUNT_UNMAPPED, ACCOUNT_AMBIGUOUS, VALIDATION_FAILED, PIPELINE_ERROR,
}

data class NeedsReviewItem(
    val id: Long,                       // notification event id
    val sourceId: String,
    val packageName: String,
    val postedAt: Long,                 // notification post time, epoch millis UTC
    val reviewReason: ReviewReason,
    val draftAmountMinor: Long?,
    val draftCurrency: String?,
    val draftDirection: Direction?,
    val draftType: TransactionType?,
    val draftOccurredAt: Long?,
    val draftDescription: String?,
    val draftAccountHint: String?,
    val draftAccountId: Long?,
)

enum class NotificationEventStatus {
    RECEIVED, PROCESSING, PROCESSED, NEEDS_REVIEW, IGNORED, FAILED,
}

data class CaptureGap(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val reason: CaptureGapReason,
)

enum class CaptureGapReason { ACCESS_REVOKED, LISTENER_DISCONNECTED, HEARTBEAT_STALE }

data class TransferPair(
    val id: Long,
    val outgoingTransactionId: Long,
    val incomingTransactionId: Long,
    val origin: TransferOrigin,
    val status: TransferStatus,
    val feeMinor: Long,                 // outgoing - incoming, >= 0, TRF-04
    val createdAt: Long,
)
```

## 2. Repository interfaces (wtrack.app.domain.repository)

```kotlin
interface AccountRepository {
    val accounts: Flow<List<Account>>
    suspend fun account(id: Long): Account?
    suspend fun create(request: CreateAccountRequest): Long
    suspend fun update(request: UpdateAccountRequest)
    suspend fun archive(id: Long)
    suspend fun correctBalance(accountId: Long, actualBalanceMinor: Long, at: Long)
}

interface TransactionRepository {
    val transactions: Flow<List<Transaction>>
    suspend fun transaction(id: Long): Transaction?
    suspend fun createManual(request: CreateManualTransactionRequest): Long
    suspend fun update(request: UpdateTransactionRequest)      // TX-03, keeps originalJson
    suspend fun void(id: Long)
    suspend fun restore(id: Long)
    suspend fun deleteManual(id: Long)
}

interface NeedsReviewRepository {
    val items: Flow<List<NeedsReviewItem>>
    suspend fun item(id: Long): NeedsReviewItem?
    suspend fun save(itemId: Long, resolved: SaveReviewItemRequest)
    suspend fun ignore(itemId: Long)
    suspend fun retry(itemId: Long)
}

interface TransferRepository {
    val pairs: Flow<List<TransferPair>>
    suspend fun link(request: LinkTransferRequest)
    suspend fun unlink(pairId: Long)
}

interface CaptureStatusRepository {
    val captureStatus: Flow<CaptureStatus>
    val captureGaps: Flow<List<CaptureGap>>
}
```

## 3. Use case interfaces (wtrack.app.domain.usecase)

```kotlin
interface ObserveDashboardUseCase {
    fun observe(period: DashboardPeriod): Flow<DashboardUiState>
}
interface CorrectBalanceUseCase {
    suspend operator fun invoke(accountId: Long, actualBalanceMinor: Long)
}
interface LinkTransferUseCase {
    suspend operator fun invoke(outgoingId: Long, incomingId: Long)
}
```

## 4. Request models (wtrack.app.domain.repository)

```kotlin
data class CreateAccountRequest(
    val name: String,
    val provider: String,
    val accountType: AccountType,
    val currency: String,
    val initialBalanceMinor: Long?,
    val initialBalanceAt: Long?,
)

data class UpdateAccountRequest(
    val id: Long,
    val name: String,
    val provider: String,
    val accountType: AccountType,
    val currency: String,
)

data class CreateManualTransactionRequest(
    val accountId: Long,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val transactionType: TransactionType,
    val occurredAt: Long,
    val description: String?,
)

data class UpdateTransactionRequest(
    val id: Long,
    val accountId: Long,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val transactionType: TransactionType,
    val occurredAt: Long,
    val description: String?,
)

data class SaveReviewItemRequest(
    val accountId: Long?,
    val amountMinor: Long?,
    val currency: String?,
    val direction: Direction?,
    val transactionType: TransactionType?,
    val occurredAt: Long?,
    val description: String?,
)

data class LinkTransferRequest(
    val outgoingTransactionId: Long,
    val incomingTransactionId: Long,
)

enum class DashboardPeriod { MONTH, THIS_MONTH, LAST_MONTH }
```

## 5. Observable UI states

```kotlin
data class DashboardUiState(
    val incomeMinor: Long,
    val expensesMinor: Long,
    val netCashFlowMinor: Long,
    val unreconciledDifferenceMinor: Long,
    val accounts: List<Account>,
    val recentTransactions: List<Transaction>,
    val pendingReviewCount: Int,
    val isLoading: Boolean,
)

enum class ErrorKind { NO_NETWORK, NOT_FOUND, VALIDATION_FAILED, DATABASE_LOCKED, UNKNOWN }

sealed interface UiError {
    data class Message(val kind: ErrorKind, val message: String) : UiError
}
```

## 6. Error handling

- Repositories suspend and throw domain exceptions on failures; the presentation maps them to UiError.
- A lost or invalid database key surfaces as DATABASE_LOCKED; the UI must show a clear locked state and point to restore from a full backup, never create an empty database (ADR-005).
- Needs Review items are events, not transactions; saving a review item creates a Transaction in the same database transaction that marks the event PROCESSED (PRD section 4.4).

## 7. Notes for the frontend agent

- The parser contract (integration API) defines ParsedTransaction, PartialTransaction, ParseResult, Direction, TransactionType, ReviewReason, NotTransactionReason. Import from wtrack.integration, not from a local copy.
- The UI does not talk to Room directly; it uses these repositories and use cases.
- The four bottom-navigation destinations are Home, Activity, Accounts, Insights, plus a prominent Needs Review entry (PRD section 10.2). Building those screens is a later milestone; this contract is the seam they implement against.

## Date

2026-11-14
