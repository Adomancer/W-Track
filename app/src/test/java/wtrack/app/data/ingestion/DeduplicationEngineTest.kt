package wtrack.app.data.ingestion

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.app.domain.model.TransactionStatus
import wtrack.integration.Direction
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.TransactionType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeduplicationEngineTest {

    private lateinit var harness: IngestionTestHarness
    private val sourceId = "pkg.dedup.bank"

    @Before
    fun setUp() = runTest {
        harness = IngestionTestHarness()
        val db = harness.db()
        val now = harness.clock.now
        db.transactionSourceDao().upsert(TransactionSourceEntity(sourceId, sourceId, "Dedup Bank", true, 1, null, now, now))
        val accountId = db.accountDao().insert(
            AccountEntity(name = "Primary", provider = "Dedup Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        db.accountSourceDao().insert(AccountSourceEntity(accountId = accountId, sourceId = sourceId, accountHint = null, createdAt = now))
        harness.allowlistCache.refreshSnapshotNow()
    }

    @After
    fun tearDown() {
        harness.tearDown()
    }

    @Test
    fun tier1DeduplicatesSameDirectionAndPreventsVoidedResurrection() = runTest {
        val db = harness.db()
        val parser = StubTransactionParser(sourceId) { env ->
            ParseResult.Success(
                ParsedTransaction(
                    amountMinor = 75_000_00L,
                    currency = "IDR",
                    direction = Direction.OUTFLOW,
                    type = TransactionType.PURCHASE,
                    occurredAtEpochMillis = 1_699_990_000_000L,
                    merchantRaw = "Alfamart",
                    description = null,
                    accountHint = null,
                    providerReference = "TXN-12345",
                    reportedBalanceMinor = null,
                ),
            )
        }
        val pipeline = harness.pipeline(parser)

        val e1 = pipeline.ingest(sampleEnvelope(sourceId = sourceId, notificationId = 1, notificationKey = "k1"))!!
        val tx1 = db.transactionDao().findByEventId(e1)!!
        assertThat(db.transactionDao().getAllOnce()).hasSize(1)

        // Void the transaction (TX-08) and replay a notification carrying the same provider_reference.
        db.transactionDao().updateStatus(tx1.id, TransactionStatus.VOIDED.name, harness.clock.now)

        val e2 = pipeline.ingest(sampleEnvelope(sourceId = sourceId, notificationId = 2, notificationKey = "k2"))!!
        val event2 = db.notificationEventDao().getById(e2)!!

        assertThat(event2.processingStatus).isEqualTo(NotificationEventStatus.IGNORED.name)
        assertThat(event2.failureReason).isEqualTo("DUPLICATE_PROVIDER_REFERENCE")
        assertThat(db.transactionDao().getAllOnce()).hasSize(1)
        assertThat(db.transactionDao().getById(tx1.id)?.status).isEqualTo(TransactionStatus.VOIDED.name)
    }

    @Test
    fun tier2IgnoresIdenticalCatchUpDeliveryByStableEventKey() = runTest {
        val db = harness.db()
        val parser = StubTransactionParser(sourceId) {
            ParseResult.Success(
                ParsedTransaction(20_000_00L, "IDR", Direction.OUTFLOW, TransactionType.PURCHASE, 1_699_990_000_000L, "Cafe", null, null, null, null),
            )
        }
        val pipeline = harness.pipeline(parser)
        val env = sampleEnvelope(sourceId = sourceId, notificationId = 42, notificationKey = "key-42", postedAtEpochMillis = 1_699_990_000_000L)

        val firstEventId = pipeline.ingest(env)
        val catchUpEventId = pipeline.ingest(env)

        assertThat(firstEventId).isNotNull()
        assertThat(catchUpEventId).isNull()
        assertThat(db.notificationEventDao().getAllOnce()).hasSize(1)
        assertThat(db.transactionDao().getAllOnce()).hasSize(1)
    }

    @Test
    fun tier3DeduplicatesWithinFiveMinuteWindowAndPreservesDistinctMerchantsAtSameAmountAndTimestamp() = runTest {
        val db = harness.db()
        val parser = StubTransactionParser(sourceId) { env ->
            val merchant = if (env.text.contains("Kopi")) "Kopi Kenangan" else "Indomaret"
            val ts = if (env.text.contains("later")) 1_699_990_180_000L else 1_699_990_000_000L
            ParseResult.Success(
                ParsedTransaction(
                    amountMinor = 30_000_00L,
                    currency = "IDR",
                    direction = Direction.OUTFLOW,
                    type = TransactionType.PURCHASE,
                    occurredAtEpochMillis = ts,
                    merchantRaw = merchant,
                    description = null,
                    accountHint = null,
                    providerReference = null,
                    reportedBalanceMinor = null,
                ),
            )
        }
        val pipeline = harness.pipeline(parser)

        // First Kopi transaction
        pipeline.ingest(sampleEnvelope(sourceId = sourceId, text = "Kopi 30000", notificationId = 1, notificationKey = "k1"))!!
        // Same merchant, same 5-minute bucket (3 minutes later in same 5-min bucket 1699990000000/300000), different notificationKey -> Tier 3 dedup
        val dupEventId = pipeline.ingest(sampleEnvelope(sourceId = sourceId, text = "Kopi later 30000", notificationId = 2, notificationKey = "k2"))!!
        // Different merchant ("Indomaret") at exact same amount and exact same timestamp -> must NOT collapse!
        val distinctEventId = pipeline.ingest(sampleEnvelope(sourceId = sourceId, text = "Indomaret 30000", notificationId = 3, notificationKey = "k3"))!!

        val dupEvent = db.notificationEventDao().getById(dupEventId)!!
        assertThat(dupEvent.processingStatus).isEqualTo(NotificationEventStatus.IGNORED.name)
        assertThat(dupEvent.failureReason).isEqualTo("DUPLICATE_COMPOSITE_FINGERPRINT")

        val distinctTx = db.transactionDao().findByEventId(distinctEventId)
        assertThat(distinctTx).isNotNull()
        assertThat(db.transactionDao().getAllOnce()).hasSize(2)
    }
}
