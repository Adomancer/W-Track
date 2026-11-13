package wtrack.integration

/** Direction is the movement of money on the account. It does not say whether the movement is income or expense (section 4.6). */
enum class Direction { INFLOW, OUTFLOW }

/**
 * The transaction type (section 6.3). ADJUSTMENT is produced only by the domain model (ACC-08); no parser can produce one.
 * TRANSFER: money sent to or received from another account, person, or provider (internal only once the user confirms a pair).
 * CASH: ATM or teller cash withdrawal or deposit. REFUND: always INFLOW in the MVP.
 */
enum class TransactionType { PURCHASE, TRANSFER, FEE, REFUND, CASH, OTHER }