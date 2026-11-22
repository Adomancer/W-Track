package wtrack.app.data

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wtrack.app.data.ingestion.WtrackNotificationListenerService
import wtrack.app.data.notification.AndroidStatusAlertNotifier
import wtrack.app.data.repository.AccountRepositoryImpl
import wtrack.app.data.repository.TransactionRepositoryImpl
import wtrack.app.data.repository.TransferRepositoryImpl
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.TransferStatus
import wtrack.app.domain.parser.DefaultParserRegistry
import wtrack.app.domain.repository.CreateAccountRequest
import wtrack.app.domain.repository.CreateManualTransactionRequest
import wtrack.app.domain.usecase.CreateManualTransactionUseCase
import wtrack.app.domain.usecase.LinkTransferUseCase
import wtrack.app.domain.usecase.TransferSuggestionEngine
import wtrack.app.feature.root.WTRACK_BIOMETRIC_AUTHENTICATORS
import wtrack.integration.Direction
import wtrack.integration.providers.ProviderParsers

/**
 * Isolated physical-device checks for Phase 2–3 features (`BiometricManager`,
 * `TransferSuggestionEngine` + SQLCipher dismiss/confirm persistence, and
 * `AndroidStatusAlertNotifier` + self-notification loop guard) using
 * [InstrumentedTestHarness] so live user data is never touched.
 */
@RunWith(AndroidJUnit4::class)
class DailyDriverFeaturesInstrumentedTest {

    private lateinit var harness: InstrumentedTestHarness

    @Before
    fun setUp() {
        harness = InstrumentedTestHarness()
    }

    @After
    fun tearDown() {
        harness.tearDown()
    }

    @Test
    fun biometricManagerReportsKnownStatusOnDevice() {
        val code = BiometricManager.from(harness.context).canAuthenticate(WTRACK_BIOMETRIC_AUTHENTICATORS)
        Log.i("WTrackHardwareQA", "biometricCanAuthenticate=$code")
        assertThat(code).isIn(
            listOf(
                BiometricManager.BIOMETRIC_SUCCESS,
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            ),
        )
    }

    @Test
    fun transferSuggestionsAndDismissPersistUnderRealSqlCipher() = runBlocking {
        withTimeout(30_000L) {
            harness.db()
            val now = harness.clock.now
            val accountRepo = AccountRepositoryImpl(
                harness.holder,
                harness.clock,
                harness.captureStatusTracker.isListenerConnected,
                DefaultParserRegistry(ProviderParsers.registered()),
            ) { true }
            val txRepo = TransactionRepositoryImpl(harness.holder, harness.settingsStore, harness.clock)
            val transferRepo = TransferRepositoryImpl(harness.holder, harness.clock)
            val createTx = CreateManualTransactionUseCase(txRepo)
            val linkTransfer = LinkTransferUseCase(txRepo, transferRepo)
            val engine = TransferSuggestionEngine()

            val bankId = accountRepo.create(CreateAccountRequest("Bank Utama", "Bank", AccountType.BANK, "IDR"))
            val walletId = accountRepo.create(CreateAccountRequest("E-Wallet", "Wallet", AccountType.EWALLET, "IDR"))

            // Pair A: exact transfer (Rp100,000)
            val outA = createTx(
                CreateManualTransactionRequest(bankId, 10_000_000L, "IDR", Direction.OUTFLOW, DomainTransactionType.TRANSFER, now, description = "Topup A out"),
            )
            val inA = createTx(
                CreateManualTransactionRequest(walletId, 10_000_000L, "IDR", Direction.INFLOW, DomainTransactionType.TRANSFER, now + 45_000L, description = "Topup A in"),
            )

            // Pair B: fee-adjusted transfer (Rp52,500 out -> Rp50,000 in, fee Rp2,500)
            val outB = createTx(
                CreateManualTransactionRequest(bankId, 5_250_000L, "IDR", Direction.OUTFLOW, DomainTransactionType.TRANSFER, now + 3_600_000L, description = "Topup B out"),
            )
            val inB = createTx(
                CreateManualTransactionRequest(walletId, 5_000_000L, "IDR", Direction.INFLOW, DomainTransactionType.TRANSFER, now + 3_720_000L, description = "Topup B in"),
            )

            val allTxs = txRepo.transactions.first()
            val initialSuggestions = engine.findSuggestions(allTxs, transferRepo.getAllOnce())
            assertThat(initialSuggestions).hasSize(2)
            assertThat(initialSuggestions.first { it.outgoing.id == outB }.estimatedFeeMinor).isEqualTo(250_000L)

            // Dismiss Pair A -> persists as REJECTED and disappears from suggestions
            transferRepo.dismissSuggestedPair(outA, inA)
            val pairsAfterDismiss = transferRepo.getAllOnce()
            assertThat(pairsAfterDismiss.any { it.outgoingTransactionId == outA && it.incomingTransactionId == inA && it.status == TransferStatus.REJECTED }).isTrue()

            val afterDismiss = engine.findSuggestions(allTxs, pairsAfterDismiss)
            assertThat(afterDismiss).hasSize(1)
            assertThat(afterDismiss.single().outgoing.id).isEqualTo(outB)

            // Confirm Pair B -> persists as CONFIRMED with derived fee and leaves 0 suggestions
            val pairBId = linkTransfer(outB, inB)
            val confirmedB = transferRepo.getAllOnce().first { it.id == pairBId }
            assertThat(confirmedB.status).isEqualTo(TransferStatus.CONFIRMED)
            assertThat(confirmedB.feeMinor).isEqualTo(250_000L)
            assertThat(engine.findSuggestions(allTxs, transferRepo.getAllOnce())).isEmpty()
        }
    }

    @Test
    fun localStatusAlertsRespectOptInAndSelfNotificationGuardOnAndroid16() = runBlocking {
        withTimeout(20_000L) {
            harness.db()
            // Anti-loop guard must ALWAYS drop W-Track's own package even if allowlisted.
            assertThat(
                WtrackNotificationListenerService.shouldAcceptPackage("wtrack.app", "wtrack.app") { true },
            ).isFalse()
            assertThat(
                WtrackNotificationListenerService.shouldAcceptPackage("com.jago.digitalBanking", "wtrack.app") {
                    it == "com.jago.digitalBanking"
                },
            ).isTrue()

            val notifier = AndroidStatusAlertNotifier(harness.context, harness.settingsStore)
            val manager = NotificationManagerCompat.from(harness.context)
            try {
                harness.settingsStore.setLocalAlertsEnabled(false)
                notifier.notifyNeedsReview("com.jago.digitalBanking", 2)
                notifier.notifyCaptureDisconnected()
                notifier.notifyBackupReminderDue()
                notifier.cancelCaptureDisconnected()

                harness.settingsStore.setLocalAlertsEnabled(true)
                notifier.notifyNeedsReview("com.jago.digitalBanking", 2)
                notifier.notifyCaptureDisconnected()
                notifier.notifyBackupReminderDue()
                notifier.cancelCaptureDisconnected()
            } finally {
                listOf(4101, 4102, 4103).forEach { id -> manager.cancel(id) }
            }
        }
    }
}
