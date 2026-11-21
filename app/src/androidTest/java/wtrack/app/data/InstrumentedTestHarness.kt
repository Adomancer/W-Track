package wtrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import wtrack.app.core.logging.WtrackLogger
import wtrack.app.data.crypto.AndroidKeyStoreWrappingCipher
import wtrack.app.data.crypto.DatabaseKeyManager
import wtrack.app.data.crypto.SqlCipherOpenHelperFactoryProvider
import wtrack.app.data.crypto.WrappedKeyStore
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.ingestion.AllowlistCache
import wtrack.app.data.ingestion.CaptureStatusTracker
import wtrack.app.data.ingestion.HeartbeatMonitor
import wtrack.app.data.ingestion.IngestionPipeline
import wtrack.app.data.local.preferences.AppSettingsDataStore
import wtrack.app.domain.parser.DefaultParserRegistry
import wtrack.app.domain.time.Clock
import wtrack.integration.TransactionParser

class MutableTestClock(var now: Long = 1_795_000_000_000L) : Clock {
    override fun nowEpochMillis(): Long = now
    fun advanceBy(millis: Long) {
        now += millis
    }
}

/**
 * Strictly isolated hardware test harness backed by real SQLCipher 4.20.0 + real AndroidKeyStore.
 * Never touches the live `wtrack.db`, `db_key.bin`, `wtrack_db_kek_v1`, or `wtrack_settings`.
 */
class InstrumentedTestHarness(
    val clock: MutableTestClock = MutableTestClock(),
    initialPermissionGranted: Boolean = true,
) {
    val context: Context = ApplicationProvider.getApplicationContext()
    private val id = UUID.randomUUID().toString().replace("-", "").take(12)
    val dbName: String = "wtrack_test_$id.db"
    val keyAlias: String = "wtrack_test_kek_$id"
    val tempDir: File = Files.createTempDirectory("wtrack_inst_$id").toFile()
    val keyFile: File = File(tempDir, "test_db_key.bin")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var permissionGranted: Boolean = initialPermissionGranted

    val wrappingCipher = AndroidKeyStoreWrappingCipher()
    val keyManager = DatabaseKeyManager(
        dbFile = context.getDatabasePath(dbName),
        wrappedKeyStore = WrappedKeyStore(keyFile),
        wrappingCipher = wrappingCipher,
        keyAlias = keyAlias,
    )
    val holder = WtrackDatabaseHolder(
        context = context,
        keyManager = keyManager,
        helperFactoryProvider = SqlCipherOpenHelperFactoryProvider,
        databaseName = dbName,
    )
    val settingsStore = AppSettingsDataStore(
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tempDir, "test_settings_$id.preferences_pb") },
        ),
    )
    val logger = WtrackLogger(File(tempDir, "test_wtrack.log"), clock)
    val allowlistCache = AllowlistCache(holder, scope)
    val captureStatusTracker = CaptureStatusTracker(
        context = context,
        holder = holder,
        clock = clock,
        logger = logger,
        permissionGrantedCheck = { permissionGranted },
    )
    val heartbeatMonitor = HeartbeatMonitor(holder, settingsStore, clock, logger)

    fun pipeline(vararg parsers: TransactionParser): IngestionPipeline = IngestionPipeline(
        holder = holder,
        allowlistCache = allowlistCache,
        parserRegistry = DefaultParserRegistry(parsers.toSet()),
        settingsStore = settingsStore,
        heartbeatMonitor = heartbeatMonitor,
        clock = clock,
        logger = logger,
    )

    fun db() = holder.requireDatabase()

    fun tearDown() {
        runCatching { holder.requireDatabase().close() }
        scope.cancel()
        val dbFile = context.getDatabasePath(dbName)
        listOf(
            dbFile,
            File("${dbFile.path}-wal"),
            File("${dbFile.path}-shm"),
            File("${dbFile.path}-journal"),
        ).forEach { if (it.exists()) it.delete() }
        runCatching { wrappingCipher.deleteKey(keyAlias) }
        tempDir.deleteRecursively()
    }
}
