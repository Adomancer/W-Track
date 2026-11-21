package wtrack.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.local.entity.TransferMatchEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WtrackDatabaseConstraintTest {
    private lateinit var db: WtrackDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WtrackDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(WtrackDatabase.createCallback())
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedsRequiredSystemCategoriesOnCreate() = runTest {
        val uncat = db.categoryDao().findBySystemKey(WtrackDatabase.SYSTEM_KEY_UNCATEGORIZED)
        val fees = db.categoryDao().findBySystemKey(WtrackDatabase.SYSTEM_KEY_FEES)
        val cash = db.categoryDao().findBySystemKey(WtrackDatabase.SYSTEM_KEY_CASH_WITHDRAWAL)

        assertThat(uncat).isNotNull()
        assertThat(uncat!!.isSystem).isTrue()
        assertThat(fees).isNotNull()
        assertThat(fees!!.isSystem).isTrue()
        assertThat(cash).isNotNull()
    }

    @Test
    fun enforcesAtMostOneNullHintPerSourceWhileAllowingDistinctNonNullHints() = runTest {
        val now = 1_700_000_000_000L
        db.transactionSourceDao().upsert(
            TransactionSourceEntity("src.bank", "src.bank", "Bank", true, 1, null, now, now),
        )
        val acc1 = db.accountDao().insert(
            AccountEntity(name = "Main", provider = "Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        val acc2 = db.accountDao().insert(
            AccountEntity(name = "Pocket", provider = "Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )

        db.accountSourceDao().insert(AccountSourceEntity(accountId = acc1, sourceId = "src.bank", accountHint = null, createdAt = now))
        val secondNullFailed = runCatching {
            db.accountSourceDao().insert(AccountSourceEntity(accountId = acc2, sourceId = "src.bank", accountHint = null, createdAt = now))
        }.isFailure
        assertThat(secondNullFailed).isTrue()

        db.accountSourceDao().insert(AccountSourceEntity(accountId = acc2, sourceId = "src.bank", accountHint = "9901", createdAt = now))
        assertThat(db.accountSourceDao().getForSource("src.bank")).hasSize(2)
    }

    @Test
    fun enforcesNonNegativeFeeAndSingleConfirmedPairPerTransaction() = runTest {
        val now = 1_700_000_000_000L
        val acc1 = db.accountDao().insert(
            AccountEntity(name = "Bank", provider = "Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        val acc2 = db.accountDao().insert(
            AccountEntity(name = "Wallet", provider = "Wallet", accountType = "EWALLET", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        val acc3 = db.accountDao().insert(
            AccountEntity(name = "Other", provider = "Other", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )

        val outTx = db.transactionDao().insert(
            TransactionEntity(accountId = acc1, sourceId = null, eventId = null, amountMinor = 5_650_000L, currency = "IDR", direction = "OUTFLOW", transactionType = "TRANSFER", merchantId = null, description = "Topup", note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        val inTx = db.transactionDao().insert(
            TransactionEntity(accountId = acc2, sourceId = null, eventId = null, amountMinor = 5_000_000L, currency = "IDR", direction = "INFLOW", transactionType = "TRANSFER", merchantId = null, description = "Topup", note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        val inTx2 = db.transactionDao().insert(
            TransactionEntity(accountId = acc3, sourceId = null, eventId = null, amountMinor = 5_000_000L, currency = "IDR", direction = "INFLOW", transactionType = "TRANSFER", merchantId = null, description = "Dup", note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )

        val negFeeFailed = runCatching {
            db.transferMatchDao().insert(TransferMatchEntity(outgoingTransactionId = outTx, incomingTransactionId = inTx, origin = "MANUAL", confidence = null, status = "CONFIRMED", feeMinor = -100L, createdAt = now))
        }.exceptionOrNull()
        assertThat(negFeeFailed).isInstanceOf(SQLiteException::class.java)

        db.transferMatchDao().insert(TransferMatchEntity(outgoingTransactionId = outTx, incomingTransactionId = inTx, origin = "MANUAL", confidence = null, status = "CONFIRMED", feeMinor = 650_000L, createdAt = now))

        val doublePairFailed = runCatching {
            db.transferMatchDao().insert(TransferMatchEntity(outgoingTransactionId = outTx, incomingTransactionId = inTx2, origin = "MANUAL", confidence = null, status = "CONFIRMED", feeMinor = 0L, createdAt = now))
        }.exceptionOrNull()
        assertThat(doublePairFailed).isInstanceOf(SQLiteConstraintException::class.java)
    }

    @Test
    fun enforcesAtMostOneTransactionPerNotificationEvent() = runTest {
        val now = 1_700_000_000_000L
        db.transactionSourceDao().upsert(TransactionSourceEntity("src", "src", "Src", true, 1, null, now, now))
        val accId = db.accountDao().insert(
            AccountEntity(name = "A", provider = "P", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        val eventId = db.notificationEventDao().insertIgnore(
            NotificationEventEntity(sourceId = "src", packageName = "src", stableEventKey = "key1", notificationKey = "k", notificationId = 1, notificationTag = null, postedAt = now, payloadJson = null, purgeAfter = null, processingStatus = "PROCESSED", attemptCount = 1, failureReason = null, reviewDraftJson = null, receivedAt = now, processedAt = now, updatedAt = now),
        )

        val tx = TransactionEntity(accountId = accId, sourceId = "src", eventId = eventId, amountMinor = 100_000L, currency = "IDR", direction = "OUTFLOW", transactionType = "PURCHASE", merchantId = null, description = null, note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now)
        db.transactionDao().insert(tx)

        val secondTxForSameEvent = runCatching { db.transactionDao().insert(tx) }.exceptionOrNull()
        assertThat(secondTxForSameEvent).isInstanceOf(SQLiteConstraintException::class.java)
    }

    @Test
    fun rejectsInitialBalanceWithoutInitialBalanceAt() = runTest {
        val now = 1_700_000_000_000L
        val failed = runCatching {
            db.accountDao().insert(
                AccountEntity(name = "Bad", provider = "P", accountType = "BANK", currency = "IDR", initialBalanceMinor = 10_000L, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
            )
        }.exceptionOrNull()
        assertThat(failed).isInstanceOf(SQLiteException::class.java)
    }
}
