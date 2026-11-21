package wtrack.app.data.ingestion

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.CategoryEntity
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.data.local.entity.TransactionRuleEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.local.entity.TransferMatchEntity
import wtrack.app.data.local.mapper.LocalJson
import wtrack.app.data.repository.AccountRepositoryImpl
import wtrack.app.data.repository.NeedsReviewRepositoryImpl
import wtrack.app.data.repository.TransactionRepositoryImpl
import wtrack.app.domain.exception.TransferPairEditBlockedException
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.CategorySource
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.app.domain.repository.SaveReviewItemRequest
import wtrack.app.domain.repository.UpdateTransactionRequest
import wtrack.integration.Direction
import wtrack.integration.NotTransactionReason
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.PartialTransaction
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IngestionPipelineTest {

    private lateinit var harness: IngestionTestHarness

    @Before
    fun setUp() {
        harness = IngestionTestHarness()
    }

    @After
    fun tearDown() {
        harness.tearDown()
    }

    @Test
    fun dropsNonAllowlistedPackagesWithoutPersistingEventOrSource() = runTest {
        harness.allowlistCache.setAllowlistedForTest(setOf("pkg.allowed"))
        val pipeline = harness.pipeline()

        val result = pipeline.ingest(
            sampleEnvelope(sourceId = "pkg.ignored", packageName = "pkg.ignored"),
            enforceAllowlist = true,
        )

        assertThat(result).isNull()
        assertThat(harness.db().notificationEventDao().getAllOnce()).isEmpty()
        assertThat(harness.db().transactionDao().getAllOnce()).isEmpty()
    }

    @Test
    fun noParserPurgesPayloadImmediatelyAndRespectsSampleCollectionCap() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        val sourceId = "pkg.unsupported.bank"
        db.transactionSourceDao().upsert(
            TransactionSourceEntity(
                id = sourceId,
                packageName = sourceId,
                displayName = "Unsupported Bank",
                isAllowlisted = true,
                parserVersion = null,
                sampleCollectionUntil = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val accountId = db.accountDao().insert(
            AccountEntity(
                name = "Main Bank",
                provider = "Unsupported Bank",
                accountType = "BANK",
                currency = "IDR",
                initialBalanceMinor = null,
                initialBalanceAt = null,
                lastReconciledAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        db.accountSourceDao().insert(
            AccountSourceEntity(accountId = accountId, sourceId = sourceId, accountHint = null, createdAt = now),
        )
        harness.allowlistCache.refreshSnapshotNow()
        val pipeline = harness.pipeline()

        val id1 = pipeline.ingest(sampleEnvelope(sourceId = sourceId, notificationId = 1, notificationKey = "k1"))!!
        val event1 = db.notificationEventDao().getById(id1)!!
        assertThat(event1.processingStatus).isEqualTo(NotificationEventStatus.IGNORED.name)
        assertThat(event1.failureReason).isEqualTo("NO_PARSER")
        assertThat(event1.payloadJson).isNull()
        assertThat(event1.purgeAfter).isNull()

        // Enable 30-day sample request -> pipeline enforces 14-day hard cap (ING-13 / SEC-02)
        val thirtyDaysLater = now + 30L * 86_400_000L
        db.transactionSourceDao().setSampleCollectionUntil(sourceId, thirtyDaysLater, now)

        val id2 = pipeline.ingest(sampleEnvelope(sourceId = sourceId, notificationId = 2, notificationKey = "k2"))!!
        val event2 = db.notificationEventDao().getById(id2)!!
        assertThat(event2.processingStatus).isEqualTo(NotificationEventStatus.IGNORED.name)
        assertThat(event2.failureReason).isEqualTo("NO_PARSER")
        assertThat(event2.payloadJson).isNotNull()
        assertThat(event2.purgeAfter).isEqualTo(now + 14L * 86_400_000L)

        val accountRepo = AccountRepositoryImpl(
            holder = harness.holder,
            clock = harness.clock,
            listenerConnectedFlow = harness.captureStatusTracker.isListenerConnected,
            permissionGrantedProvider = { harness.permissionGranted },
        )
        val account = accountRepo.accounts.first().single { it.id == accountId }
        assertThat(account.captureStatus).isEqualTo(CaptureStatus.UNSUPPORTED)
        assertThat(account.unparsedLast30DaysCount).isEqualTo(2)
    }

    @Test
    fun notTransactionPurgesPayloadImmediatelyWithoutCreatingTransaction() = runTest {
        val db = harness.db()
        val sourceId = "pkg.bank"
        seedSourceAndAccount(db, sourceId, harness.clock.now)
        harness.allowlistCache.refreshSnapshotNow()
        val parser = StubTransactionParser(sourceId) {
            ParseResult.NotTransaction(NotTransactionReason.SECURITY_NOTICE)
        }
        val pipeline = harness.pipeline(parser)

        val eventId = pipeline.ingest(sampleEnvelope(sourceId = sourceId, text = "OTP: 123456"))!!
        val event = db.notificationEventDao().getById(eventId)!!

        assertThat(event.processingStatus).isEqualTo(NotificationEventStatus.IGNORED.name)
        assertThat(event.failureReason).isEqualTo(NotTransactionReason.SECURITY_NOTICE.name)
        assertThat(event.payloadJson).isNull()
        assertThat(event.purgeAfter).isNull()
        assertThat(db.transactionDao().getAllOnce()).isEmpty()
    }

    @Test
    fun redactedLockScreenContentNeverSavesTransactionEvenIfParserReturnsSuccess() = runTest {
        val db = harness.db()
        val sourceId = "pkg.bank"
        seedSourceAndAccount(db, sourceId, harness.clock.now)
        harness.allowlistCache.refreshSnapshotNow()
        val parser = StubTransactionParser(sourceId) {
            ParseResult.Success(
                ParsedTransaction(
                    amountMinor = 50_000_00L,
                    currency = "IDR",
                    direction = Direction.OUTFLOW,
                    type = TransactionType.PURCHASE,
                    occurredAtEpochMillis = null,
                    merchantRaw = "Placeholder",
                    description = null,
                    accountHint = null,
                    providerReference = null,
                    reportedBalanceMinor = null,
                ),
            )
        }
        val pipeline = harness.pipeline(parser)

        val eventId = pipeline.ingest(
            sampleEnvelope(
                sourceId = sourceId,
                text = "",
                isContentRedacted = true,
            ),
        )!!
        val event = db.notificationEventDao().getById(eventId)!!

        assertThat(event.processingStatus).isEqualTo(NotificationEventStatus.NEEDS_REVIEW.name)
        assertThat(event.failureReason).isEqualTo(ReviewReason.UNKNOWN_FORMAT.name)
        assertThat(db.transactionDao().getAllOnce()).isEmpty()
    }

    @Test
    fun missingOccurredAtFallsBackToPostedAtNeverReceivedAt() = runTest {
        val db = harness.db()
        val sourceId = "pkg.bank"
        val accountId = seedSourceAndAccount(db, sourceId, harness.clock.now)
        harness.allowlistCache.refreshSnapshotNow()
        val postedAt = 1_699_000_000_000L // strictly distinct from clock.now (1_700_000_000_000L)

        val parser = StubTransactionParser(sourceId, version = 3) {
            ParseResult.Success(
                ParsedTransaction(
                    amountMinor = 25_000_00L,
                    currency = "idr",
                    direction = Direction.OUTFLOW,
                    type = TransactionType.PURCHASE,
                    occurredAtEpochMillis = null,
                    merchantRaw = "Kopi Kenangan #102 Jakarta",
                    description = "QRIS payment",
                    accountHint = null,
                    providerReference = "REF-POSTED-1",
                    reportedBalanceMinor = 500_000_00L,
                ),
            )
        }
        val pipeline = harness.pipeline(parser)
        val foodCatId = db.categoryDao().insert(
            CategoryEntity(name = "Coffee", systemKey = null, directionScope = "OUTFLOW", createdAt = harness.clock.now),
        )
        db.transactionRuleDao().insert(
            TransactionRuleEntity(
                name = "Kopi Rule",
                accountId = accountId,
                categoryId = foodCatId,
                conditionJson = LocalJson.encodeRuleCondition(
                    merchantKey = "kopi kenangan",
                    descriptionFragment = null,
                    accountId = accountId,
                    direction = Direction.OUTFLOW,
                ),
                actionJson = LocalJson.encodeRuleAction(foodCatId),
                enabled = true,
                createdAt = harness.clock.now,
                updatedAt = harness.clock.now,
            ),
        )

        val eventId = pipeline.ingest(sampleEnvelope(sourceId = sourceId, postedAtEpochMillis = postedAt))!!
        val tx = db.transactionDao().findByEventId(eventId)!!
        val event = db.notificationEventDao().getById(eventId)!!

        assertThat(tx.occurredAt).isEqualTo(postedAt)
        assertThat(tx.occurredAt).isNotEqualTo(harness.clock.now)
        assertThat(tx.currency).isEqualTo("IDR")
        assertThat(tx.amountMinor).isEqualTo(25_000_00L)
        assertThat(tx.categoryId).isEqualTo(foodCatId)
        assertThat(tx.categorySource).isEqualTo(CategorySource.RULE.name)
        assertThat(tx.originalJson).isNotNull()
        assertThat(tx.parserVersion).isEqualTo(3)
        assertThat(event.processingStatus).isEqualTo(NotificationEventStatus.PROCESSED.name)
        assertThat(event.payloadJson).isNotNull()
        assertThat(event.purgeAfter).isEqualTo(harness.clock.now + 7L * 86_400_000L)

        val settingsAfter = harness.settingsStore.current()
        assertThat(settingsAfter.txCountSinceLastBackup).isEqualTo(1)
        assertThat(settingsAfter.lastAliveAt).isEqualTo(harness.clock.now)
    }

    @Test
    fun verificationWindowZeroPurgesPayloadImmediatelyOnSuccess() = runTest {
        harness.settingsStore.setVerificationWindowDays(0)
        val db = harness.db()
        val sourceId = "pkg.bank"
        seedSourceAndAccount(db, sourceId, harness.clock.now)
        harness.allowlistCache.refreshSnapshotNow()
        val parser = StubTransactionParser(sourceId) {
            ParseResult.Success(
                ParsedTransaction(10_000_00L, "IDR", Direction.OUTFLOW, TransactionType.PURCHASE, null, "Shop", null, null, "REF-ZERO", null),
            )
        }
        val pipeline = harness.pipeline(parser)
        val eventId = pipeline.ingest(sampleEnvelope(sourceId = sourceId))!!
        val event = db.notificationEventDao().getById(eventId)!!

        assertThat(event.processingStatus).isEqualTo(NotificationEventStatus.PROCESSED.name)
        assertThat(event.payloadJson).isNull()
        assertThat(event.purgeAfter).isNull()
    }

    @Test
    fun resolvesMultipleAccountsByHintAndRoutesAmbiguousOrUnmappedToNeedsReview() = runTest {
        val db = harness.db()
        val sourceId = "pkg.jago.multi"
        val now = harness.clock.now
        db.transactionSourceDao().upsert(
            TransactionSourceEntity(sourceId, sourceId, "Multi Bank", true, 1, null, now, now),
        )
        val accA = db.accountDao().insert(
            AccountEntity(name = "Main Pocket", provider = "Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        val accB = db.accountDao().insert(
            AccountEntity(name = "Spend Pocket", provider = "Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        db.accountSourceDao().insert(AccountSourceEntity(accountId = accA, sourceId = sourceId, accountHint = "1111", createdAt = now))
        db.accountSourceDao().insert(AccountSourceEntity(accountId = accB, sourceId = sourceId, accountHint = "2222", createdAt = now))
        harness.allowlistCache.refreshSnapshotNow()

        val parser = StubTransactionParser(sourceId) { env ->
            val hint = when {
                env.text.contains("2222") -> "2222"
                else -> null
            }
            ParseResult.Success(
                ParsedTransaction(15_000_00L, "IDR", Direction.OUTFLOW, TransactionType.PURCHASE, env.postedAtEpochMillis, "Merchant", null, hint, env.sourceEventReference, null),
            )
        }
        val pipeline = harness.pipeline(parser)

        val hintedEventId = pipeline.ingest(sampleEnvelope(sourceId = sourceId, text = "Debit **2222 Rp 15.000", notificationId = 1, notificationKey = "k1", sourceEventReference = "r1"))!!
        val tx = db.transactionDao().findByEventId(hintedEventId)!!
        assertThat(tx.accountId).isEqualTo(accB)

        val ambiguousEventId = pipeline.ingest(sampleEnvelope(sourceId = sourceId, text = "Debit Rp 15.000", notificationId = 2, notificationKey = "k2", sourceEventReference = "r2"))!!
        val ambiguousEvent = db.notificationEventDao().getById(ambiguousEventId)!!
        assertThat(ambiguousEvent.processingStatus).isEqualTo(NotificationEventStatus.NEEDS_REVIEW.name)
        assertThat(ambiguousEvent.failureReason).isEqualTo(ReviewReason.ACCOUNT_AMBIGUOUS.name)

        // Unmapped source routes cleanly to ACCOUNT_UNMAPPED and preserves NeedsReview on event only.
        val unmappedSource = "pkg.unmapped"
        db.transactionSourceDao().upsert(TransactionSourceEntity(unmappedSource, unmappedSource, "Unmapped", true, 1, null, now, now))
        harness.allowlistCache.refreshSnapshotNow()
        val unmappedPipeline = harness.pipeline(StubTransactionParser(unmappedSource) {
            ParseResult.Success(ParsedTransaction(5_000_00L, "IDR", Direction.OUTFLOW, TransactionType.PURCHASE, now, "Merchant", null, null, null, null))
        })
        val unmappedEventId = unmappedPipeline.ingest(sampleEnvelope(sourceId = unmappedSource, notificationId = 3, notificationKey = "k3"))!!
        val unmappedEvent = db.notificationEventDao().getById(unmappedEventId)!!
        assertThat(unmappedEvent.processingStatus).isEqualTo(NotificationEventStatus.NEEDS_REVIEW.name)
        assertThat(unmappedEvent.failureReason).isEqualTo(ReviewReason.ACCOUNT_UNMAPPED.name)
    }

    @Test
    fun outflowRefundRoutesToNeedsReviewWhileInflowRefundLinksToOutflowByProviderReference() = runTest {
        val db = harness.db()
        val sourceId = "pkg.bank"
        seedSourceAndAccount(db, sourceId, harness.clock.now)
        harness.allowlistCache.refreshSnapshotNow()

        val parser = StubTransactionParser(sourceId) { env ->
            when {
                env.text.contains("PAY") -> ParseResult.Success(
                    ParsedTransaction(100_000_00L, "IDR", Direction.OUTFLOW, TransactionType.PURCHASE, 1_699_999_000_000L, "Tokopedia", null, null, "ORD-999", null),
                )
                env.text.contains("IN_REFUND") -> ParseResult.Success(
                    ParsedTransaction(100_000_00L, "IDR", Direction.INFLOW, TransactionType.REFUND, 1_699_999_500_000L, "Tokopedia", null, null, "ORD-999", null),
                )
                else -> ParseResult.Success(
                    ParsedTransaction(20_000_00L, "IDR", Direction.OUTFLOW, TransactionType.REFUND, 1_699_999_600_000L, "Tokopedia", null, null, "ORD-888", null),
                )
            }
        }
        val pipeline = harness.pipeline(parser)

        val payEventId = pipeline.ingest(sampleEnvelope(sourceId = sourceId, text = "PAY ORD-999", notificationId = 1, notificationKey = "k1"))!!
        val payTx = db.transactionDao().findByEventId(payEventId)!!

        val refundEventId = pipeline.ingest(sampleEnvelope(sourceId = sourceId, text = "IN_REFUND ORD-999", notificationId = 2, notificationKey = "k2"))!!
        val refundTx = db.transactionDao().findByEventId(refundEventId)!!
        assertThat(refundTx.relatedTransactionId).isEqualTo(payTx.id)

        // Duplicate delivery of the exact INFLOW refund with same provider_reference must deduplicate via Tier 1.
        val dupRefundEventId = pipeline.ingest(sampleEnvelope(sourceId = sourceId, text = "IN_REFUND ORD-999", notificationId = 3, notificationKey = "k3"))!!
        val dupRefundEvent = db.notificationEventDao().getById(dupRefundEventId)!!
        assertThat(dupRefundEvent.processingStatus).isEqualTo(NotificationEventStatus.IGNORED.name)
        assertThat(dupRefundEvent.failureReason).isEqualTo("DUPLICATE_PROVIDER_REFERENCE")

        // Outgoing refund is always queued for review (PRD section 4.6)
        val outRefundEventId = pipeline.ingest(sampleEnvelope(sourceId = sourceId, text = "OUT_REFUND", notificationId = 4, notificationKey = "k4"))!!
        val outRefundEvent = db.notificationEventDao().getById(outRefundEventId)!!
        assertThat(outRefundEvent.processingStatus).isEqualTo(NotificationEventStatus.NEEDS_REVIEW.name)
        assertThat(outRefundEvent.failureReason).isEqualTo(ReviewReason.VALIDATION_FAILED.name)
        assertThat(db.transactionDao().findByEventId(outRefundEventId)).isNull()
    }

    @Test
    fun accountRepositoryDerivesCaptureStatusesAndNeedsReviewRetryWorks() = runTest {
        val db = harness.db()
        val sourceId = "pkg.bank"
        val accountId = seedSourceAndAccount(db, sourceId, harness.clock.now)
        harness.allowlistCache.refreshSnapshotNow()

        val accountRepo = AccountRepositoryImpl(
            holder = harness.holder,
            clock = harness.clock,
            listenerConnectedFlow = harness.captureStatusTracker.isListenerConnected,
            permissionGrantedProvider = { harness.permissionGranted },
        )

        // Initially permission granted, parserVersion=1, listener disconnected -> DISCONNECTED
        assertThat(accountRepo.accounts.first().single { it.id == accountId }.captureStatus)
            .isEqualTo(CaptureStatus.DISCONNECTED)

        // Listener connects -> ACTIVE
        harness.captureStatusTracker.onListenerConnected()
        assertThat(accountRepo.accounts.first().single { it.id == accountId }.captureStatus)
            .isEqualTo(CaptureStatus.ACTIVE)

        // Permission revoked -> ATTENTION
        harness.permissionGranted = false
        harness.captureStatusTracker.onPermissionStateChecked()
        assertThat(accountRepo.accounts.first().single { it.id == accountId }.captureStatus)
            .isEqualTo(CaptureStatus.ATTENTION)

        // Parser first emits NeedsReview, then user resolves or retries with fixed parser
        var fixedParser = false
        val dynamicParser = StubTransactionParser(sourceId) {
            if (!fixedParser) {
                ParseResult.NeedsReview(ReviewReason.UNKNOWN_FORMAT, PartialTransaction(amountMinor = 40_000_00L, currency = "IDR"))
            } else {
                ParseResult.Success(ParsedTransaction(40_000_00L, "IDR", Direction.OUTFLOW, TransactionType.PURCHASE, null, "Shop", null, null, "REF-RETRY", null))
            }
        }
        val pipeline = harness.pipeline(dynamicParser)
        val reviewRepo = NeedsReviewRepositoryImpl(
            holder = harness.holder,
            settingsStore = harness.settingsStore,
            clock = harness.clock,
            onRetryRequested = { eventId -> pipeline.processEvent(eventId) },
        )

        val eventId = pipeline.ingest(sampleEnvelope(sourceId = sourceId))!!
        assertThat(reviewRepo.pendingCount.first()).isEqualTo(1)

        fixedParser = true
        reviewRepo.retry(eventId)
        assertThat(reviewRepo.pendingCount.first()).isEqualTo(0)
        assertThat(db.transactionDao().findByEventId(eventId)?.amountMinor).isEqualTo(40_000_00L)
    }

    @Test
    fun confirmedTransferLegBlocksStructuralEditsAndUserCanClearCategory() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        val acc1 = db.accountDao().insert(AccountEntity(name = "A", provider = "Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now))
        val acc2 = db.accountDao().insert(AccountEntity(name = "B", provider = "Wallet", accountType = "EWALLET", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now))

        val outTxId = db.transactionDao().insert(
            TransactionEntity(accountId = acc1, sourceId = null, eventId = null, amountMinor = 100_000_00L, currency = "IDR", direction = "OUTFLOW", transactionType = "TRANSFER", merchantId = null, description = "Topup", note = null, occurredAt = now, categoryId = 4L, categorySource = "RULE", appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = "{\"amountMinor\":10000000}", providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        val inTxId = db.transactionDao().insert(
            TransactionEntity(accountId = acc2, sourceId = null, eventId = null, amountMinor = 100_000_00L, currency = "IDR", direction = "INFLOW", transactionType = "TRANSFER", merchantId = null, description = "Topup", note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        db.transferMatchDao().insert(
            TransferMatchEntity(outgoingTransactionId = outTxId, incomingTransactionId = inTxId, origin = "MANUAL", confidence = null, status = "CONFIRMED", feeMinor = 0L, createdAt = now),
        )

        val txRepo = TransactionRepositoryImpl(harness.holder, harness.settingsStore, harness.clock)

        // Attempting to change amount on confirmed transfer leg fails with TransferPairEditBlockedException.
        val blockedError = runCatching {
            txRepo.update(
                UpdateTransactionRequest(
                    id = outTxId,
                    accountId = acc1,
                    amountMinor = 90_000_00L,
                    currency = "IDR",
                    direction = Direction.OUTFLOW,
                    transactionType = wtrack.app.domain.model.DomainTransactionType.TRANSFER,
                    occurredAt = now,
                ),
            )
        }.exceptionOrNull()
        assertThat(blockedError).isInstanceOf(TransferPairEditBlockedException::class.java)

        // Updating note and explicitly clearing category works without altering originalJson.
        txRepo.update(
            UpdateTransactionRequest(
                id = outTxId,
                accountId = acc1,
                amountMinor = 100_000_00L,
                currency = "IDR",
                direction = Direction.OUTFLOW,
                transactionType = wtrack.app.domain.model.DomainTransactionType.TRANSFER,
                occurredAt = now,
                categoryId = null,
                description = "Topup",
                note = "Monthly budget transfer",
                categorySetByUser = true,
            ),
        )
        val updated = db.transactionDao().getById(outTxId)!!
        assertThat(updated.note).isEqualTo("Monthly budget transfer")
        assertThat(updated.categoryId).isNull()
        assertThat(updated.categorySource).isNull()
        assertThat(updated.originalJson).isEqualTo("{\"amountMinor\":10000000}")
    }

    private suspend fun seedSourceAndAccount(
        db: wtrack.app.data.local.WtrackDatabase,
        sourceId: String,
        now: Long,
    ): Long {
        db.transactionSourceDao().upsert(
            TransactionSourceEntity(sourceId, sourceId, "Test Bank", true, 1, null, now, now),
        )
        val accountId = db.accountDao().insert(
            AccountEntity(name = "Main Account", provider = "Test Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        db.accountSourceDao().insert(
            AccountSourceEntity(accountId = accountId, sourceId = sourceId, accountHint = null, createdAt = now),
        )
        return accountId
    }
}
