package wtrack.integration

/**
 * Movement of money on the account. Does not determine income vs. expense reporting (PRD section 4.6).
 */
enum class Direction {
    INFLOW,
    OUTFLOW,
}

/**
 * Parser-producible transaction types (PRD section 6.3).
 * ADJUSTMENT belongs exclusively to the domain model (ACC-08); no parser can produce one.
 * TRANSFER: money sent to or received from another account, person, or provider (internal only once paired).
 * CASH: ATM or teller cash withdrawal or deposit.
 * REFUND: always INFLOW in the MVP.
 */
enum class TransactionType {
    PURCHASE,
    TRANSFER,
    FEE,
    REFUND,
    CASH,
    OTHER,
}
