package wtrack.app.data.ingestion

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.local.mapper.toDomain
import wtrack.app.domain.model.CustomParserRule
import wtrack.app.domain.model.DatabaseHealthState

/**
 * Lock-free in-memory mirror of enabled user-defined [CustomParserRule]s indexed by `sourceId`.
 * Updated automatically when the Room database changes and refreshed synchronously on rule edits
 * or backup restores.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomParserRuleCache(
    private val holder: WtrackDatabaseHolder,
    private val scope: CoroutineScope,
) {
    private val rulesBySource = AtomicReference<Map<String, List<CustomParserRule>>>(emptyMap())
    private val observing = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)

    fun startObserving() {
        if (!observing.compareAndSet(false, true)) return
        holder.databaseFlow
            .flatMapLatest { db ->
                if (db == null) {
                    rulesBySource.set(emptyMap())
                    initialized.set(false)
                    emptyFlow()
                } else {
                    db.customParserRuleDao().observeAll()
                }
            }
            .onEach { entities ->
                val grouped = entities
                    .asSequence()
                    .filter { it.enabled }
                    .map { it.toDomain() }
                    .groupBy { it.sourceId }
                rulesBySource.set(grouped)
                initialized.set(true)
            }
            .launchIn(scope)
    }

    fun refreshSnapshotNow() {
        if (holder.healthState.value !is DatabaseHealthState.Ready) {
            rulesBySource.set(emptyMap())
            initialized.set(false)
            return
        }
        runCatching {
            val db = holder.requireDatabase()
            val cursor = db.openHelper.readableDatabase.query(
                """
                SELECT id, source_id, name, title_contains, template_pattern, direction,
                       transaction_type, currency, number_locale, account_hint, enabled,
                       created_at, updated_at
                FROM custom_parser_rules
                WHERE enabled = 1
                ORDER BY updated_at DESC, id DESC
                """.trimIndent(),
            )
            cursor.use { c ->
                val map = mutableMapOf<String, MutableList<CustomParserRule>>()
                while (c.moveToNext()) {
                    val rule = wtrack.app.data.local.entity.CustomParserRuleEntity(
                        id = c.getLong(0),
                        sourceId = c.getString(1),
                        name = c.getString(2),
                        titleContains = if (c.isNull(3)) null else c.getString(3),
                        templatePattern = c.getString(4),
                        direction = c.getString(5),
                        transactionType = c.getString(6),
                        currency = c.getString(7),
                        numberLocale = c.getString(8),
                        accountHint = if (c.isNull(9)) null else c.getString(9),
                        enabled = c.getInt(10) != 0,
                        createdAt = c.getLong(11),
                        updatedAt = c.getLong(12),
                    ).toDomain()
                    map.getOrPut(rule.sourceId) { mutableListOf() }.add(rule)
                }
                rulesBySource.set(map)
                initialized.set(true)
            }
        }
    }

    fun rulesForSource(sourceId: String): List<CustomParserRule> {
        if (!initialized.get() && holder.healthState.value is DatabaseHealthState.Ready) {
            refreshSnapshotNow()
        }
        return rulesBySource.get()[sourceId].orEmpty()
    }

    fun enabledSourceIds(): Set<String> {
        if (!initialized.get() && holder.healthState.value is DatabaseHealthState.Ready) {
            refreshSnapshotNow()
        }
        return rulesBySource.get().keys
    }

    fun setRulesForTest(rules: List<CustomParserRule>) {
        rulesBySource.set(rules.filter { it.enabled }.groupBy { it.sourceId })
        initialized.set(true)
    }
}
