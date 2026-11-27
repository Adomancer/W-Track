package wtrack.app.data.backup

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wtrack.app.data.ingestion.IngestionPipeline
import wtrack.app.data.ingestion.IngestionTestHarness
import wtrack.app.data.repository.AccountRepositoryImpl
import wtrack.app.data.repository.CategoryRepositoryImpl
import wtrack.app.data.repository.DatabaseHealthRepositoryImpl
import wtrack.app.data.repository.TransactionRepositoryImpl
import wtrack.app.domain.parser.DefaultParserRegistry
import wtrack.integration.Direction
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionParser
import wtrack.integration.providers.ProviderParsers
import wtrack.integration.providers.bca.BcaTransactionParser
import wtrack.integration.providers.dana.DanaTransactionParser
import wtrack.integration.providers.jago.JagoTransactionParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaptureSelfCheckTest {

    private lateinit var harness: IngestionTestHarness
    private val fastArgonConfig = BackupCryptoConfig(
        kdfId = BackupCryptoContainer.KDF_ARGON2ID,
        memoryKiB = 4_096,
        iterations = 1,
        parallelism = 1,
    )

    @Before
    fun setUp() {
        harness = IngestionTestHarness()
    }

    @After
    fun tearDown() {
        harness.tearDown()
    }

    private fun buildPort(parsers: Set<TransactionParser>): WtrackBackupAndDiagnosticsPort {
        val registry = DefaultParserRegistry(parsers)
        val pipeline = IngestionPipeline(
            holder = harness.holder,
            allowlistCache = harness.allowlistCache,
            parserRegistry = registry,
            settingsStore = harness.settingsStore,
            heartbeatMonitor = harness.heartbeatMonitor,
            clock = harness.clock,
            logger = harness.logger,
        )
        return WtrackBackupAndDiagnosticsPort(
            holder = harness.holder,
            backupService = BackupService(
                holder = harness.holder,
                settingsStore = harness.settingsStore,
                allowlistCache = harness.allowlistCache,
                captureStatusTracker = harness.captureStatusTracker,
                clock = harness.clock,
                logger = harness.logger,
                cryptoConfig = fastArgonConfig,
            ),
            csvExporter = CsvExporter(),
            m3aSampleExporter = M3aSampleExporter(harness.clock),
            accountRepository = AccountRepositoryImpl(harness.holder, harness.clock),
            transactionRepository = TransactionRepositoryImpl(harness.holder, harness.settingsStore, harness.clock),
            categoryRepository = CategoryRepositoryImpl(harness.holder, harness.clock),
            databaseHealthRepository = DatabaseHealthRepositoryImpl(harness.holder, harness.settingsStore),
            logger = harness.logger,
            allowlistCache = harness.allowlistCache,
            parserRegistry = registry,
            ingestionPipeline = pipeline,
            clock = harness.clock,
            captureStatusTracker = harness.captureStatusTracker,
        )
    }

    @Test
    fun selfCheckPassesWithAllBuiltInParsersRegistered() = runTest {
        val result = buildPort(ProviderParsers.registered()).runCaptureSelfCheck()
        assertThat(result.databaseReady).isTrue()
        assertThat(result.registeredParserCount).isEqualTo(3)
        assertThat(result.dryRunParserPassed).isTrue()
    }

    @Test
    fun selfCheckPassesWhenOnlyDanaOrOnlyBcaParserIsRegistered() = runTest {
        val danaOnly = buildPort(setOf(DanaTransactionParser())).runCaptureSelfCheck()
        assertThat(danaOnly.registeredParserCount).isEqualTo(1)
        assertThat(danaOnly.dryRunParserPassed).isTrue()

        val bcaOnly = buildPort(setOf(BcaTransactionParser())).runCaptureSelfCheck()
        assertThat(bcaOnly.registeredParserCount).isEqualTo(1)
        assertThat(bcaOnly.dryRunParserPassed).isTrue()
    }

    @Test
    fun selfCheckFailsWhenNoParsersRegisteredOrAnyActiveBuiltInParserFails() = runTest {
        val empty = buildPort(emptySet()).runCaptureSelfCheck()
        assertThat(empty.registeredParserCount).isEqualTo(0)
        assertThat(empty.dryRunParserPassed).isFalse()

        val brokenBca = object : TransactionParser {
            override val sourceId: String = BcaTransactionParser.MYBCA_SOURCE_ID
            override val version: Int = 99
            override fun supports(event: NotificationEvent): Boolean = true
            override suspend fun parse(event: NotificationEvent): ParseResult =
                ParseResult.NeedsReview(ReviewReason.UNKNOWN_FORMAT, null)
        }
        val mixed = buildPort(
            setOf(JagoTransactionParser(), DanaTransactionParser(), brokenBca),
        ).runCaptureSelfCheck()
        assertThat(mixed.registeredParserCount).isEqualTo(3)
        assertThat(mixed.dryRunParserPassed).isFalse()
    }

    @Test
    fun pureProbeRunnerValidatesExpectedDirectionAndAmount() = runTest {
        val wrongDirectionJago = object : TransactionParser {
            override val sourceId: String = JagoTransactionParser.SOURCE_ID
            override val version: Int = 99
            override fun supports(event: NotificationEvent): Boolean = true
            override suspend fun parse(event: NotificationEvent): ParseResult = ParseResult.Success(
                wtrack.integration.ParsedTransaction(
                    amountMinor = 5_000_000L,
                    currency = "IDR",
                    direction = Direction.OUTFLOW,
                    type = wtrack.integration.TransactionType.TRANSFER,
                    occurredAtEpochMillis = null,
                    merchantRaw = "SelfCheck",
                    description = "Wrong direction",
                    accountHint = null,
                    providerReference = null,
                    reportedBalanceMinor = null,
                ),
            )
        }
        assertThat(
            runBuiltInParserSelfCheck(DefaultParserRegistry(setOf(wrongDirectionJago)), 1_795_000_000_000L),
        ).isFalse()
    }
}
