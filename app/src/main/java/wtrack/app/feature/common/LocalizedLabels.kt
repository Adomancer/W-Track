package wtrack.app.feature.common

import androidx.annotation.StringRes
import wtrack.app.R
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.CustomNumberLocale
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.LockedReason
import wtrack.app.domain.model.ThemeMode
import wtrack.integration.Direction

@StringRes
fun Direction.labelRes(): Int = when (this) {
    Direction.OUTFLOW -> R.string.direction_outflow
    Direction.INFLOW -> R.string.direction_inflow
}

@StringRes
fun DomainTransactionType.labelRes(): Int = when (this) {
    DomainTransactionType.PURCHASE -> R.string.tx_type_purchase
    DomainTransactionType.TRANSFER -> R.string.tx_type_transfer
    DomainTransactionType.FEE -> R.string.tx_type_fee
    DomainTransactionType.REFUND -> R.string.tx_type_refund
    DomainTransactionType.CASH -> R.string.tx_type_cash
    DomainTransactionType.ADJUSTMENT -> R.string.tx_type_adjustment
    DomainTransactionType.OTHER -> R.string.tx_type_other
}

@StringRes
fun AccountType.labelRes(): Int = when (this) {
    AccountType.BANK -> R.string.account_type_bank
    AccountType.EWALLET -> R.string.account_type_ewallet
    AccountType.CREDIT_CARD -> R.string.account_type_credit_card
    AccountType.CASH -> R.string.account_type_cash
    AccountType.OTHER -> R.string.account_type_other
}

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
fun CaptureStatus.labelRes(): Int = when (this) {
    CaptureStatus.ACTIVE -> R.string.capture_status_active
    CaptureStatus.DISCONNECTED -> R.string.capture_status_disconnected
    CaptureStatus.ATTENTION -> R.string.capture_status_attention
    CaptureStatus.UNSUPPORTED -> R.string.capture_status_unsupported
}

@StringRes
fun LockedReason.titleRes(): Int = when (this) {
    LockedReason.KEY_MISSING -> R.string.locked_reason_key_missing
    LockedReason.KEY_INVALIDATED_OR_CORRUPT -> R.string.locked_reason_key_invalid
    LockedReason.DATABASE_OPEN_FAILED -> R.string.locked_reason_db_open_failed
}

@StringRes
fun CustomNumberLocale.shortLabelRes(): Int = when (this) {
    CustomNumberLocale.INDONESIAN -> R.string.number_locale_id_short
    CustomNumberLocale.ENGLISH -> R.string.number_locale_en_short
}

@StringRes
fun CustomNumberLocale.exampleLabelRes(): Int = when (this) {
    CustomNumberLocale.INDONESIAN -> R.string.number_locale_id_example
    CustomNumberLocale.ENGLISH -> R.string.number_locale_en_example
}
