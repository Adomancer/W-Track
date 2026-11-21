package wtrack.app.data.ingestion

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import wtrack.app.core.logging.WtrackLogger
import wtrack.app.data.crypto.DatabaseKeyManager
import wtrack.app.data.crypto.EncryptedOpenHelperFactoryProvider
import wtrack.app.data.crypto.KeyWrappingCipher
import wtrack.app.data.crypto.WrappedKeyStore
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.local.preferences.AppSettingsDataStore
import wtrack.app.domain.parser.DefaultParserRegistry
import wtrack.app.domain.time.Clock
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.TransactionParser

class FakeClock(var now: Long = 1_700_000_000_000L) : Clock {
    override fun nowEpochMillis(): Long = now
    fun advanceBy(millis: Long) {
        now += millis
    }
}

class InMemoryAesGcmCipher : KeyWrappingCipher {
    private val keys = mutableMapOf<String, SecretKey>()

    override fun wrap(alias: String, plaintext: ByteArray): wtrack.app.data.crypto.WrappedPayload {
        val key = keys.getOrPut(alias) {
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return wtrack.app.data.crypto.WrappedPayload(
            formatVersion = 1,
            alias = alias,
            iv = cipher.iv,
            ciphertext = cipher.doFinal(plaintext),
        )
    }

    override fun unwrap(payload: wtrack.app.data.crypto.WrappedPayload): ByteArray {
        val key = keys[payload.alias] ?: throw GeneralSecurityException("Missing KEK")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload.iv))
        return cipher.doFinal(payload.ciphertext)
    }

    override fun deleteKey(alias: String) {
        keys.remove(alias)
    }
}

class StubTransactionParser(
    override val sourceId: String,
    override val version: Int = 1,
    private val supportsCheck: (NotificationEvent) -> Boolean = { true },
    private val handler: suspend (NotificationEvent) -> ParseResult,
) : TransactionParser {
    override fun supports(event: NotificationEvent): Boolean = supportsCheck(event)
    override suspend fun parse(event: NotificationEvent): ParseResult = handler(event)
}

class IngestionTestHarness(
    val clock: FakeClock = FakeClock(),
    initialPermissionGranted: Boolean = true,
) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val tempDir: File = Files.createTempDirectory("wtrack_m2_test").toFile()
    private val dbName: String = "wtrack_test_${UUID.randomUUID()}.db"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var permissionGranted: Boolean = initialPermissionGranted

    private val keyManager = DatabaseKeyManager(
        dbFile = context.getDatabasePath(dbName),
        wrappedKeyStore = WrappedKeyStore(File(tempDir, "db_key.bin")),
        wrappingCipher = InMemoryAesGcmCipher(),
    )

    val holder = WtrackDatabaseHolder(
        context = context,
        keyManager = keyManager,
        helperFactoryProvider = EncryptedOpenHelperFactoryProvider { FrameworkSQLiteOpenHelperFactory() },
        databaseName = dbName,
    )

    val settingsStore = AppSettingsDataStore(
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tempDir, "settings_${UUID.randomUUID()}.preferences_pb") },
        ),
    )

    val logFile = File(tempDir, "wtrack.log")
    val logger = WtrackLogger(logFile, clock)
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
        listOf(
            context.getDatabasePath(dbName),
            File("${context.getDatabasePath(dbName).path}-wal"),
            File("${context.getDatabasePath(dbName).path}-shm"),
            File("${context.getDatabasePath(dbName).path}-journal"),
        ).forEach { it.delete() }
        tempDir.deleteRecursively()
    }
}

fun sampleEnvelope(
    sourceId: String = "pkg.test.bank",
    packageName: String = sourceId,
    title: String? = "Transaction Alert",
    text: String = "Paid Rp 50.000 at Kopi Kenangan",
    bigText: String? = null,
    subText: String? = null,
    channelId: String? = "tx_channel",
    isGroupSummary: Boolean = false,
    isContentRedacted: Boolean = false,
    postedAtEpochMillis: Long = 1_699_999_900_000L,
    notificationKey: String? = "0|$packageName|101|null|1001",
    notificationId: Int = 101,
    notificationTag: String? = null,
    sourceEventReference: String? = null,
): NotificationEvent = NotificationEvent(
    sourceId = sourceId,
    packageName = packageName,
    title = title,
    text = text,
    bigText = bigText,
    subText = subText,
    channelId = channelId,
    isGroupSummary = isGroupSummary,
    isContentRedacted = isContentRedacted,
    postedAtEpochMillis = postedAtEpochMillis,
    notificationKey = notificationKey,
    notificationId = notificationId,
    notificationTag = notificationTag,
    sourceEventReference = sourceEventReference,
)
