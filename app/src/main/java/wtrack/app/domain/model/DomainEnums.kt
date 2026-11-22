package wtrack.app.domain.model

import wtrack.integration.TransactionType

/**
 * Domain transaction type (PRD sections 4.6 and 6.3).
 * Adds [ADJUSTMENT] for balance corrections (ACC-08); no parser can emit [ADJUSTMENT].
 */
enum class DomainTransactionType {
    PURCHASE,
    TRANSFER,
    FEE,
    REFUND,
    CASH,
    ADJUSTMENT,
    OTHER,
}

fun TransactionType.toDomain(): DomainTransactionType = when (this) {
    TransactionType.PURCHASE -> DomainTransactionType.PURCHASE
    TransactionType.TRANSFER -> DomainTransactionType.TRANSFER
    TransactionType.FEE -> DomainTransactionType.FEE
    TransactionType.REFUND -> DomainTransactionType.REFUND
    TransactionType.CASH -> DomainTransactionType.CASH
    TransactionType.OTHER -> DomainTransactionType.OTHER
}

enum class TransactionStatus {
    CONFIRMED,
    VOIDED,
}

enum class AccountType {
    BANK,
    EWALLET,
    CASH,
    CREDIT_CARD,
    OTHER,
}

enum class CaptureStatus {
    ACTIVE,
    ATTENTION,
    DISCONNECTED,
    UNSUPPORTED,
}

enum class CategorySource {
    USER,
    RULE,
}

enum class TransferOrigin {
    MANUAL,
    AUTO,
}

enum class TransferStatus {
    SUGGESTED,
    CONFIRMED,
    REJECTED,
}

enum class NotificationEventStatus {
    RECEIVED,
    PROCESSING,
    PROCESSED,
    NEEDS_REVIEW,
    IGNORED,
    FAILED,
}

enum class CaptureGapReason {
    ACCESS_REVOKED,
    LISTENER_DISCONNECTED,
    HEARTBEAT_STALE,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class CustomNumberLocale {
    INDONESIAN,
    ENGLISH,
}
