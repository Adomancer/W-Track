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
    /** Candidate Google Play package IDs monitored for this provider. */
    val candidatePackageIds: List<String>,
    val verifiedPackageId: String?,
    val verificationStatus: VerificationStatus,
    val notes: String,
    val verifiedPackageIds: List<String> = listOfNotNull(verifiedPackageId),
)

/**
 * Built-in provider discovery metadata catalog (docs/PROVIDERS.md).
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
            notes = "Supports incoming/outgoing transfers and Main Pocket movements (internal sub-pocket moves ignored; unrecognized formats route to Review).",
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
            notes = "Supports incoming transfers with promotional notification filtering (unrecognized formats route to Review).",
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
            notes = "Supports myBCA Financial Diary income and expense notifications (unrecognized formats route to Review).",
            verifiedPackageIds = listOf("com.bca", "com.bca.mybca.omni.android"),
        ),
    )
}
