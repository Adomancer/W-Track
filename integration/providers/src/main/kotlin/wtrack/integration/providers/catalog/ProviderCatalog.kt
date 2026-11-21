package wtrack.integration.providers.catalog

enum class VerificationStatus {
    PENDING_DEVICE_VERIFICATION,
    VERIFIED_ACTIVE,
    DEPRECATED,
}

data class ProviderCandidate(
    val priority: Int,
    val id: String,
    val displayName: String,
    val category: String,
    /** Candidate Google Play package IDs checked on physical iQOO Z10R. */
    val candidatePackageIds: List<String>,
    val verifiedPackageId: String?,
    val verificationStatus: VerificationStatus,
    val notes: String,
    val verifiedPackageIds: List<String> = listOfNotNull(verifiedPackageId),
)

/**
 * M3a provider discovery metadata catalog (docs/PROVIDERS.md).
 * Package IDs verified via ADB on physical iQOO Z10R (`I2505`, Android 16); notification schemas
 * remain pending live M3a sample capture (`ProviderParsers.registered()` stays empty until then).
 */
object ProviderCatalog {
    val initialPoCTargets: List<ProviderCandidate> = listOf(
        ProviderCandidate(
            priority = 1,
            id = "bank_jago",
            displayName = "Bank Jago",
            category = "Digital Bank",
            candidatePackageIds = listOf("com.jago.digitalBanking"),
            verifiedPackageId = "com.jago.digitalBanking",
            verificationStatus = VerificationStatus.VERIFIED_ACTIVE,
            notes = "v8.99.1 verified on iQOO Z10R; JagoTransactionParser v2 live for transfers and Main Pocket-only pocket movements (sub-pocket-to-sub-pocket moves ignored; unseen formats route to Review).",
            verifiedPackageIds = listOf("com.jago.digitalBanking"),
        ),
        ProviderCandidate(
            priority = 2,
            id = "dana",
            displayName = "DANA",
            category = "E-Wallet",
            candidatePackageIds = listOf("id.dana"),
            verifiedPackageId = "id.dana",
            verificationStatus = VerificationStatus.VERIFIED_ACTIVE,
            notes = "v2.152.0 verified on iQOO Z10R; DanaTransactionParser v1 live for incoming transfers & quest/mission promo filtering (unseen outgoing formats route to Review).",
            verifiedPackageIds = listOf("id.dana"),
        ),
        ProviderCandidate(
            priority = 3,
            id = "bca",
            displayName = "BCA (BCA mobile / myBCA)",
            category = "Bank",
            candidatePackageIds = listOf("com.bca", "com.bca.mybca.omni.android"),
            verifiedPackageId = "com.bca.mybca.omni.android",
            verificationStatus = VerificationStatus.VERIFIED_ACTIVE,
            notes = "BcaTransactionParser v1 live for myBCA (com.bca.mybca.omni.android v2.23.0); BCA mobile (com.bca v4.9.5) also installed.",
            verifiedPackageIds = listOf("com.bca", "com.bca.mybca.omni.android"),
        ),
    )
}
