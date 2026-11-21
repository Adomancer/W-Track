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
    /** Candidate Google Play package IDs awaiting ADB verification on physical iQOO Z10R. */
    val candidatePackageIds: List<String>,
    val verifiedPackageId: String?,
    val verificationStatus: VerificationStatus,
    val notes: String,
)

/**
 * M3a provider discovery metadata catalog (docs/PROVIDERS.md).
 * Package IDs and notification schemas remain unverified until physical iQOO Z10R inspection.
 */
object ProviderCatalog {
    val initialPoCTargets: List<ProviderCandidate> = listOf(
        ProviderCandidate(
            priority = 1,
            id = "bank_jago",
            displayName = "Bank Jago",
            category = "Digital Bank",
            candidatePackageIds = listOf("com.jago.digitalBanking"),
            verifiedPackageId = null,
            verificationStatus = VerificationStatus.PENDING_DEVICE_VERIFICATION,
            notes = "Priority 1 PoC target; verify Jago Kantong/Pocket hints and format on iQOO Z10R before coding parser.",
        ),
        ProviderCandidate(
            priority = 2,
            id = "dana",
            displayName = "DANA",
            category = "E-Wallet",
            candidatePackageIds = listOf("id.dana"),
            verifiedPackageId = null,
            verificationStatus = VerificationStatus.PENDING_DEVICE_VERIFICATION,
            notes = "Priority 2 PoC target; inspect promo filtering and bank-funded QRIS dual-notifier behavior.",
        ),
        ProviderCandidate(
            priority = 3,
            id = "bca",
            displayName = "BCA (BCA mobile / myBCA)",
            category = "Bank",
            candidatePackageIds = listOf("com.bca", "com.bca.mybca.omni.android"),
            verifiedPackageId = null,
            verificationStatus = VerificationStatus.PENDING_DEVICE_VERIFICATION,
            notes = "Priority 3 PoC target; verify which BCA package(s) are installed and emit transaction push alerts.",
        ),
    )
}
