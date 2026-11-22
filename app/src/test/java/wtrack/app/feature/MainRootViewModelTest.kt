package wtrack.app.feature

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wtrack.app.domain.exception.DatabaseLockedException
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountSource
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.CaptureHealthSummary
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.model.LockedReason
import wtrack.app.domain.model.NeedsReviewItem
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.CaptureStatusRepository
import wtrack.app.domain.repository.CreateAccountRequest
import wtrack.app.domain.repository.DatabaseHealthRepository
import wtrack.app.domain.repository.NeedsReviewRepository
import wtrack.app.domain.repository.SaveReviewItemRequest
import wtrack.app.domain.repository.UpdateAccountRequest
import wtrack.app.domain.time.Clock
import wtrack.app.feature.root.MainRootViewModel
import wtrack.app.feature.root.RootUiState

class MainRootViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun emitsLockedStateWithoutSubscribingToDatabaseBackedFlowsWhenDatabaseIsLocked() = runTest {
        val healthFlow = MutableStateFlow<DatabaseHealthState>(
            DatabaseHealthState.Locked(LockedReason.KEY_MISSING, "Wrapped key missing"),
        )
        val healthRepo = object : DatabaseHealthRepository {
            override val state: Flow<DatabaseHealthState> = healthFlow
            override suspend fun wipeAllDataAndReset() {
                healthFlow.value = DatabaseHealthState.Ready
            }
        }
        val throwingAccountRepo = object : AccountRepository {
            override val accounts: Flow<List<Account>> = flow { throw DatabaseLockedException("Locked") }
            override suspend fun account(id: Long): Account? = null
            override suspend fun getAllOnce(): List<Account> = emptyList()
            override suspend fun create(request: CreateAccountRequest): Long = 0L
            override suspend fun update(request: UpdateAccountRequest) {}
            override suspend fun archive(id: Long) {}
            override suspend fun restore(id: Long) {}
            override suspend fun updateLastReconciledAt(accountId: Long, atEpochMillis: Long) {}
            override val accountSources: Flow<List<AccountSource>> = flow { throw DatabaseLockedException("Locked") }
            override suspend fun getAccountSourcesForSource(sourceId: String): List<AccountSource> = emptyList()
            override suspend fun linkAccountSource(accountId: Long, sourceId: String, accountHint: String?): Long = 0L
            override suspend fun unlinkAccountSource(id: Long) {}
        }
        val throwingReviewRepo = object : NeedsReviewRepository {
            override val items: Flow<List<NeedsReviewItem>> = flow { throw DatabaseLockedException("Locked") }
            override val pendingCount: Flow<Int> = flow { throw DatabaseLockedException("Locked") }
            override suspend fun item(id: Long): NeedsReviewItem? = null
            override suspend fun saveResolved(itemId: Long, resolved: SaveReviewItemRequest): Long = 0L
            override suspend fun ignore(itemId: Long) {}
            override suspend fun retry(itemId: Long) {}
        }
        val throwingCaptureRepo = object : CaptureStatusRepository {
            override val healthSummary: Flow<CaptureHealthSummary> = flow { throw DatabaseLockedException("Locked") }
            override val captureGaps = flow<List<wtrack.app.domain.model.CaptureGap>> { throw DatabaseLockedException("Locked") }
            override suspend fun refreshStatus() {}
            override suspend fun recordGap(startedAt: Long, endedAt: Long?, reason: wtrack.app.domain.model.CaptureGapReason) {}
            override suspend fun closeOpenGaps(reason: wtrack.app.domain.model.CaptureGapReason, endedAt: Long) {}
        }

        val vm = MainRootViewModel(
            databaseHealthRepository = healthRepo,
            settingsRepository = FakeSettingsRepository(AppSettings(onboardingCompleted = false)),
            captureStatusRepository = throwingCaptureRepo,
            needsReviewRepository = throwingReviewRepo,
            accountRepository = throwingAccountRepo,
            backupPort = FakeBackupAndDiagnosticsPort(),
            clock = FakeClock(),
            dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.dispatcher),
        )

        vm.uiState.test {
            val item = awaitItem()
            val locked = if (item is RootUiState.Loading) awaitItem() else item
            assertThat(locked).isInstanceOf(RootUiState.Locked::class.java)
            assertThat((locked as RootUiState.Locked).reason).isEqualTo(LockedReason.KEY_MISSING)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun postRestoreSetsPermissionPromptWhenNotificationAccessNotGranted() = runTest {
        val healthRepo = object : DatabaseHealthRepository {
            override val state: Flow<DatabaseHealthState> = MutableStateFlow(DatabaseHealthState.Ready)
            override suspend fun wipeAllDataAndReset() {}
        }
        val captureRepo = FakeCaptureStatusRepository().apply {
            summaryFlow.value = CaptureHealthSummary(CaptureStatus.ATTENTION, false, false, null, 0L)
        }
        val vm = MainRootViewModel(
            databaseHealthRepository = healthRepo,
            settingsRepository = FakeSettingsRepository(AppSettings(onboardingCompleted = false)),
            captureStatusRepository = captureRepo,
            needsReviewRepository = FakeNeedsReviewRepository(),
            accountRepository = FakeAccountRepository(),
            backupPort = FakeBackupAndDiagnosticsPort(),
            clock = FakeClock(),
            dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.dispatcher),
        )

        vm.uiState.test {
            awaitItem()
            vm.restoreFromBackup("strong-passphrase") { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
            var state = awaitItem()
            while (state !is RootUiState.Ready) {
                state = awaitItem()
            }
            assertThat(state.showPostRestorePermissionPrompt).isTrue()
            vm.dismissPostRestorePrompt()
            assertThat((awaitItem() as RootUiState.Ready).showPostRestorePermissionPrompt).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun runsOnboardingCaptureSelfCheckWithoutModifyingLedger() = runTest {
        val healthRepo = object : DatabaseHealthRepository {
            override val state: Flow<DatabaseHealthState> = MutableStateFlow(DatabaseHealthState.Ready)
            override suspend fun wipeAllDataAndReset() {}
        }
        val vm = MainRootViewModel(
            databaseHealthRepository = healthRepo,
            settingsRepository = FakeSettingsRepository(AppSettings(onboardingCompleted = false)),
            captureStatusRepository = FakeCaptureStatusRepository(),
            needsReviewRepository = FakeNeedsReviewRepository(),
            accountRepository = FakeAccountRepository(),
            backupPort = FakeBackupAndDiagnosticsPort(),
            clock = FakeClock(),
            dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.dispatcher),
        )

        vm.uiState.test {
            var current = awaitItem()
            while (current !is RootUiState.Onboarding) {
                current = awaitItem()
            }
            assertThat(current.selfCheckResult).isNull()

            vm.runCaptureSelfCheck()
            var checked = awaitItem()
            while (checked !is RootUiState.Onboarding || checked.selfCheckResult == null) {
                checked = awaitItem()
            }
            assertThat(checked.selfCheckResult?.dryRunParserPassed).isTrue()
            assertThat(checked.selfCheckResult?.registeredParserCount).isEqualTo(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun appLockEnforcesColdStartAndTimeoutReLockInMainRootViewModel() = runTest {
        val healthRepo = object : DatabaseHealthRepository {
            override val state: Flow<DatabaseHealthState> = MutableStateFlow(DatabaseHealthState.Ready)
            override suspend fun wipeAllDataAndReset() {}
        }
        val mutableClock = object : Clock {
            var now = 10_000L
            override fun nowEpochMillis(): Long = now
        }
        val vm = MainRootViewModel(
            databaseHealthRepository = healthRepo,
            settingsRepository = FakeSettingsRepository(
                AppSettings(
                    onboardingCompleted = true,
                    appLockEnabled = true,
                    appLockTimeoutSeconds = 60,
                ),
            ),
            captureStatusRepository = FakeCaptureStatusRepository(),
            needsReviewRepository = FakeNeedsReviewRepository(),
            accountRepository = FakeAccountRepository(),
            backupPort = FakeBackupAndDiagnosticsPort(),
            clock = mutableClock,
            dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.dispatcher),
        )

        vm.uiState.test {
            var current = awaitItem()
            while (current !is RootUiState.Ready) {
                current = awaitItem()
            }
            assertThat(current.isAppLocked).isTrue()

            vm.unlockApp()
            var unlocked = awaitItem() as RootUiState.Ready
            while (unlocked.isAppLocked) {
                unlocked = awaitItem() as RootUiState.Ready
            }
            assertThat(unlocked.isAppLocked).isFalse()

            // Return before 60s -> stays unlocked
            vm.onAppBackgrounded()
            mutableClock.now = 40_000L
            vm.onAppForegrounded()
            expectNoEvents()

            // Background and return after >= 60s -> locks again
            vm.onAppBackgrounded()
            mutableClock.now = 110_000L
            vm.onAppForegrounded()
            val relocked = awaitItem() as RootUiState.Ready
            assertThat(relocked.isAppLocked).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
