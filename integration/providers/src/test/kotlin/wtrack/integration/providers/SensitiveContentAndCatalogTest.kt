package wtrack.integration.providers

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wtrack.integration.NotTransactionReason
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ReviewReason
import wtrack.integration.providers.catalog.ProviderCatalog
import wtrack.integration.providers.catalog.VerificationStatus
import wtrack.integration.providers.common.FixtureAnonymizer
import wtrack.integration.providers.common.SensitiveContentGuard

class SensitiveContentAndCatalogTest {

    @Test
    fun securityNoticesAndRedactedEnvelopesAreBlockedSafely() {
        val otpEvent = testEvent(title = "Kode OTP", text = "Kode OTP Anda 482910. Jangan berikan kode ini kepada siapa pun.")
        assertThat(SensitiveContentGuard.inspectPreconditions(otpEvent))
            .isEqualTo(ParseResult.NotTransaction(NotTransactionReason.SECURITY_NOTICE))

        val pinAlert = testEvent(title = "Security Alert", text = "New device login detected on your account")
        assertThat(SensitiveContentGuard.inspectPreconditions(pinAlert))
            .isEqualTo(ParseResult.NotTransaction(NotTransactionReason.SECURITY_NOTICE))

        val redacted = testEvent(title = null, text = "Sensitive notification content hidden", isContentRedacted = true)
        assertThat(SensitiveContentGuard.inspectPreconditions(redacted))
            .isEqualTo(ParseResult.NeedsReview(ReviewReason.UNKNOWN_FORMAT, null))

        // Merchant containing "Pin" inside a word ("Pinang") must not false-positive as PIN security notice
        val normalPurchase = testEvent(title = "Payment", text = "Paid Rp 25.000 at Warung Pinang")
        assertThat(SensitiveContentGuard.inspectPreconditions(normalPurchase)).isNull()

        val failedAttempt = testEvent(title = "Transaksi Gagal", text = "Pembayaran Rp 50.000 gagal karena saldo tidak cukup")
        assertThat(SensitiveContentGuard.inspectNonTransactionNotice(failedAttempt))
            .isEqualTo(ParseResult.NotTransaction(NotTransactionReason.FAILED_ATTEMPT))

        val promo = testEvent(title = "Promo Spesial", text = "Dapatkan cashback hingga 50% pakai voucher hari ini")
        assertThat(SensitiveContentGuard.inspectNonTransactionNotice(promo))
            .isEqualTo(ParseResult.NotTransaction(NotTransactionReason.PROMOTION))
    }

    @Test
    fun fixtureAnonymizerMasksPiiAndRetainsStructure() {
        val raw = "Halo Budi Santoso, transfer Rp 250.000 dari 123456789012 ke budi@example.com / 081234567890 sukses. OTP 889911"
        val clean = FixtureAnonymizer.anonymize(raw, personNamesOrAliases = listOf("Budi Santoso", "Budi"))

        assertThat(clean).doesNotContain("Budi")
        assertThat(clean).doesNotContain("budi@example.com")
        assertThat(clean).doesNotContain("081234567890")
        assertThat(clean).doesNotContain("12345678")
        assertThat(clean).doesNotContain("889911")
        assertThat(clean).contains("Rp 250.000")
        assertThat(clean).contains("****9012")
    }

    @Test
    fun providerCatalogListsInitialThreeVerifiedPoCTargetsWithNoPrematureParsers() {
        val targets = ProviderCatalog.initialPoCTargets
        assertThat(targets.map { it.displayName }).containsExactly(
            "Bank Jago",
            "DANA",
            "BCA (BCA mobile / myBCA)",
        ).inOrder()
        assertThat(targets.all { it.verificationStatus == VerificationStatus.VERIFIED_ACTIVE }).isTrue()
        assertThat(targets[0].verifiedPackageId).isEqualTo("com.jago.digitalBanking")
        assertThat(targets[1].verifiedPackageId).isEqualTo("id.dana")
        assertThat(targets[2].verifiedPackageId).isEqualTo("com.bca.mybca.omni.android")
        assertThat(targets[2].verifiedPackageIds).containsExactly("com.bca", "com.bca.mybca.omni.android").inOrder()
        assertThat(ProviderParsers.registered().map { it.sourceId })
            .containsExactly("com.jago.digitalBanking", "id.dana", "com.bca.mybca.omni.android")
    }

    private fun testEvent(
        title: String?,
        text: String,
        isContentRedacted: Boolean = false,
    ): NotificationEvent = NotificationEvent(
        sourceId = "synthetic.test",
        packageName = "synthetic.test",
        title = title,
        text = text,
        bigText = null,
        subText = null,
        channelId = "default",
        isGroupSummary = false,
        isContentRedacted = isContentRedacted,
        postedAtEpochMillis = 1_700_000_000_000L,
        notificationKey = "k",
        notificationId = 1,
        notificationTag = null,
        sourceEventReference = null,
    )
}
