package wtrack.app.feature.common

import wtrack.app.domain.model.AccountType

data class AccountProviderPreset(
    val displayName: String,
    val accountType: AccountType,
)

data class WalletPackagePreset(
    val displayName: String,
    val packageName: String,
)

object ProviderPresets {
    val quickAccountProviders: List<AccountProviderPreset> = listOf(
        AccountProviderPreset("Jago", AccountType.BANK),
        AccountProviderPreset("myBCA", AccountType.BANK),
        AccountProviderPreset("Mandiri", AccountType.BANK),
        AccountProviderPreset("DANA", AccountType.EWALLET),
        AccountProviderPreset("OVO", AccountType.EWALLET),
        AccountProviderPreset("GoPay", AccountType.EWALLET),
        AccountProviderPreset("Cash", AccountType.CASH),
    )

    val commonWalletPackages: List<WalletPackagePreset> = listOf(
        WalletPackagePreset("GoPay / Gojek", "com.gojek.app"),
        WalletPackagePreset("OVO", "ovo.id"),
        WalletPackagePreset("Shopee", "com.shopee.id"),
        WalletPackagePreset("SeaBank", "id.co.seabank.id"),
        WalletPackagePreset("blu", "com.bcadigital.blu"),
        WalletPackagePreset("Livin'", "id.bmri.livin"),
        WalletPackagePreset("BRImo", "id.co.bri.brimo"),
        WalletPackagePreset("wondr BNI", "id.co.bni.wondr"),
    )
}
