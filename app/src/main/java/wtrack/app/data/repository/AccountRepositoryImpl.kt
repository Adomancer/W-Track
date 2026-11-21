package wtrack.app.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.CaptureGapEntity
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.local.mapper.toDomain
import wtrack.app.domain.exception.ValidationException
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountSource
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.parser.ParserRegistry
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.CreateAccountRequest
import wtrack.app.domain.repository.UpdateAccountRequest
import wtrack.app.domain.time.Clock
import wtrack.app.domain.usecase.BalanceCheckpointEvaluator
import wtrack.integration.Direction

@OptIn(ExperimentalCoroutinesApi::class)
class AccountRepositoryImpl(
    private val holder: WtrackDatabaseHolder,
    private val clock: Clock,
    private val listenerConnectedFlow: StateFlow<Boolean> = MutableStateFlow(false),
    private val parserRegistry: ParserRegistry? = null,
    private val checkpointEvaluator: BalanceCheckpointEvaluator = BalanceCheckpointEvaluator(),
    private val permissionGrantedProvider: () -> Boolean = { false },
) : AccountRepository {

    private data class LedgerSourceSnapshot(
        val accountRows: List<AccountEntity>,
        val txRows: List<TransactionEntity>,
        val accountSourceRows: List<AccountSourceEntity>,
        val sourceRows: List<TransactionSourceEntity>,
    )

    private data class HealthEventSnapshot(
        val gapRows: List<CaptureGapEntity>,
        val eventRows: List<NotificationEventEntity>,
        val listenerConnected: Boolean,
    )

    override val accounts: Flow<List<Account>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) {
            flowOf(emptyList())
        } else {
            val ledgerSources = combine(
                db.accountDao().observeAll(),
                db.transactionDao().observeAll(),
                db.accountSourceDao().observeAll(),
                db.transactionSourceDao().observeAll(),
            ) { accountRows, txRows, accountSourceRows, sourceRows ->
                LedgerSourceSnapshot(accountRows, txRows, accountSourceRows, sourceRows)
            }
            val healthSignals = combine(
                db.captureGapDao().observeAll(),
                db.notificationEventDao().observeAll(),
                listenerConnectedFlow,
            ) { gapRows, eventRows, listenerConnected ->
                HealthEventSnapshot(gapRows, eventRows, listenerConnected)
            }
            combine(ledgerSources, healthSignals) { ledger, health ->
                val sourcesById = ledger.sourceRows.associateBy { it.id }
                val granted = permissionGrantedProvider()
                val since30d = clock.nowEpochMillis() - NO_PARSER_WINDOW_MILLIS
                val unparsedBySource = health.eventRows
                    .asSequence()
                    .filter { it.failureReason == "NO_PARSER" && it.receivedAt >= since30d }
                    .groupingBy { it.sourceId }
                    .eachCount()
                val txsByAccount = ledger.txRows.groupBy { it.accountId }

                ledger.accountRows.map { entity ->
                    val domain = entity.toDomain()
                    val t0 = entity.initialBalanceAt ?: Long.MIN_VALUE
                    val accountTxRows = txsByAccount[entity.id].orEmpty()
                    val deltaSum = accountTxRows
                        .asSequence()
                        .filter {
                            it.status == TransactionStatus.CONFIRMED.name &&
                                it.occurredAt >= t0
                        }
                        .sumOf { tx ->
                            if (tx.direction == Direction.INFLOW.name) tx.amountMinor else -tx.amountMinor
                        }
                    val estimated = (entity.initialBalanceMinor ?: 0L) + deltaSum
                    val linkedSources = ledger.accountSourceRows.filter { it.accountId == entity.id }
                    val allowlistedLinkedSources = linkedSources.mapNotNull { link ->
                        sourcesById[link.sourceId]?.takeIf { it.isAllowlisted }
                    }
                    val derivedStatus = when {
                        linkedSources.isEmpty() || allowlistedLinkedSources.isEmpty() -> CaptureStatus.ATTENTION
                        allowlistedLinkedSources.any { src ->
                            src.parserVersion == null && parserRegistry?.parserVersionForSource(src.id) == null
                        } -> CaptureStatus.UNSUPPORTED
                        !granted -> CaptureStatus.ATTENTION
                        !health.listenerConnected -> CaptureStatus.DISCONNECTED
                        else -> CaptureStatus.ACTIVE
                    }
                    val unparsedCount = linkedSources
                        .asSequence()
                        .map { it.sourceId }
                        .distinct()
                        .sumOf { unparsedBySource[it] ?: 0 }
                    val reconciledBoundary = entity.lastReconciledAt ?: 0L
                    val hasCaptureGap = health.gapRows.any { gap ->
                        val gapEnd = gap.endedAt ?: Long.MAX_VALUE
                        gapEnd >= reconciledBoundary
                    }
                    val checkpoint = checkpointEvaluator.evaluateLatest(
                        account = domain,
                        transactions = accountTxRows.map { it.toDomain() },
                    )
                    val hasCheckpointDrift = checkpoint != null &&
                        checkpoint.driftMinor != 0L &&
                        checkpoint.checkpointOccurredAt >= reconciledBoundary
                    domain.copy(
                        estimatedBalanceMinor = estimated,
                        captureStatus = derivedStatus,
                        hasPossibleGap = hasCaptureGap || hasCheckpointDrift,
                        unparsedLast30DaysCount = unparsedCount,
                    )
                }
            }
        }
    }

    override suspend fun account(id: Long): Account? =
        holder.requireDatabase().accountDao().getById(id)?.toDomain()

    override suspend fun getAllOnce(): List<Account> =
        holder.requireDatabase().accountDao().getAllOnce().map { it.toDomain() }

    override suspend fun create(request: CreateAccountRequest): Long {
        if (request.name.isBlank()) throw ValidationException("Account name is required")
        if (request.currency.trim().length != 3) throw ValidationException("Currency must be a 3-letter ISO code")
        if ((request.initialBalanceMinor == null) != (request.initialBalanceAt == null)) {
            throw ValidationException("Opening balance and opening balance date must both be provided or both empty")
        }
        val now = clock.nowEpochMillis()
        return holder.requireDatabase().accountDao().insert(
            AccountEntity(
                name = request.name.trim(),
                provider = request.provider.trim().ifEmpty { request.accountType.name },
                accountType = request.accountType.name,
                currency = request.currency.trim().uppercase(),
                initialBalanceMinor = request.initialBalanceMinor,
                initialBalanceAt = request.initialBalanceAt,
                lastReconciledAt = request.initialBalanceAt,
                isActive = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun update(request: UpdateAccountRequest) {
        val dao = holder.requireDatabase().accountDao()
        val existing = dao.getById(request.id) ?: return
        if (request.name.isBlank()) throw ValidationException("Account name is required")
        if (request.currency.trim().length != 3) throw ValidationException("Currency must be a 3-letter ISO code")
        dao.update(
            existing.copy(
                name = request.name.trim(),
                provider = request.provider.trim().ifEmpty { existing.provider },
                accountType = request.accountType.name,
                currency = request.currency.trim().uppercase(),
                updatedAt = clock.nowEpochMillis(),
            ),
        )
    }

    override suspend fun archive(id: Long) {
        holder.requireDatabase().accountDao().setActive(id, isActive = false, updatedAt = clock.nowEpochMillis())
    }

    override suspend fun restore(id: Long) {
        holder.requireDatabase().accountDao().setActive(id, isActive = true, updatedAt = clock.nowEpochMillis())
    }

    override suspend fun updateLastReconciledAt(accountId: Long, atEpochMillis: Long) {
        holder.requireDatabase().accountDao().updateLastReconciledAt(
            id = accountId,
            atEpochMillis = atEpochMillis,
            updatedAt = clock.nowEpochMillis(),
        )
    }

    override val accountSources: Flow<List<AccountSource>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) {
            flowOf(emptyList())
        } else {
            db.accountSourceDao().observeAll().map { list -> list.map { it.toDomain() } }
        }
    }

    override suspend fun getAccountSourcesForSource(sourceId: String): List<AccountSource> =
        holder.requireDatabase().accountSourceDao().getForSource(sourceId).map { it.toDomain() }

    override suspend fun linkAccountSource(accountId: Long, sourceId: String, accountHint: String?): Long {
        val cleanedHint = accountHint?.trim()?.takeIf { it.isNotEmpty() }
        val db = holder.requireDatabase()
        return db.withTransaction {
            db.accountSourceDao().insert(
                AccountSourceEntity(
                    accountId = accountId,
                    sourceId = sourceId,
                    accountHint = cleanedHint,
                    createdAt = clock.nowEpochMillis(),
                ),
            )
        }
    }

    override suspend fun unlinkAccountSource(id: Long) {
        holder.requireDatabase().accountSourceDao().deleteById(id)
    }

    private companion object {
        const val NO_PARSER_WINDOW_MILLIS = 30L * 86_400_000L
    }
}
