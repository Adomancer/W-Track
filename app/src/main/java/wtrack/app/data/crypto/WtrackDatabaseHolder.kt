package wtrack.app.data.crypto

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.io.File
import java.util.Arrays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import wtrack.app.data.local.WtrackDatabase
import wtrack.app.domain.exception.DatabaseLockedException
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.model.LockedReason

fun interface EncryptedOpenHelperFactoryProvider {
    fun create(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory
}

object SqlCipherOpenHelperFactoryProvider : EncryptedOpenHelperFactoryProvider {
    @Volatile
    private var nativeLoaded = false

    override fun create(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory {
        if (!nativeLoaded) {
            synchronized(this) {
                if (!nativeLoaded) {
                    System.loadLibrary("sqlcipher")
                    nativeLoaded = true
                }
            }
        }
        return SupportOpenHelperFactory(passphrase)
    }
}

/**
 * Controls encrypted [WtrackDatabase] opening, startup key-failure detection, and safe wipe/reopen
 * for backup restore (ADR-005 / DATA-06 / DATA-09).
 */
class WtrackDatabaseHolder(
    private val context: Context,
    private val keyManager: DatabaseKeyManager,
    private val helperFactoryProvider: EncryptedOpenHelperFactoryProvider = SqlCipherOpenHelperFactoryProvider,
    private val databaseName: String = WtrackDatabase.DATABASE_NAME,
) {
    private val lock = Any()

    @Volatile
    private var database: WtrackDatabase? = null

    private val _databaseFlow = MutableStateFlow<WtrackDatabase?>(null)
    val databaseFlow: StateFlow<WtrackDatabase?> = _databaseFlow.asStateFlow()

    private val _healthState = MutableStateFlow<DatabaseHealthState>(DatabaseHealthState.Ready)
    val healthState: StateFlow<DatabaseHealthState> = _healthState.asStateFlow()

    init {
        initializeIfPossible()
    }

    fun initializeIfPossible() {
        synchronized(lock) {
            if (database != null) return
            when (val access = keyManager.obtainDatabaseKey()) {
                is DekAccessResult.Locked -> {
                    _databaseFlow.value = null
                    _healthState.value = DatabaseHealthState.Locked(access.reason, access.detail)
                }
                is DekAccessResult.Unlocked -> {
                    var builtDb: WtrackDatabase? = null
                    try {
                        builtDb = Room.databaseBuilder(
                            context.applicationContext,
                            WtrackDatabase::class.java,
                            databaseName,
                        )
                            .openHelperFactory(helperFactoryProvider.create(access.dek.copyOf()))
                            .addCallback(WtrackDatabase.createCallback())
                            .build()
                        // Force SQLCipher decryption & schema verification before exposing instance.
                        builtDb.openHelper.writableDatabase.query("SELECT count(*) FROM sqlite_master").use { cursor ->
                            cursor.moveToFirst()
                        }
                        database = builtDb
                        _databaseFlow.value = builtDb
                        _healthState.value = DatabaseHealthState.Ready
                    } catch (t: Throwable) {
                        runCatching { builtDb?.close() }
                        database = null
                        _databaseFlow.value = null
                        _healthState.value = DatabaseHealthState.Locked(
                            reason = LockedReason.DATABASE_OPEN_FAILED,
                            detail = t.message ?: "Failed to decrypt local SQLite database.",
                        )
                    } finally {
                        Arrays.fill(access.dek, 0.toByte())
                    }
                }
            }
        }
    }

    fun requireDatabase(): WtrackDatabase {
        database?.let { return it }
        initializeIfPossible()
        return database ?: run {
            val locked = _healthState.value as? DatabaseHealthState.Locked
            throw DatabaseLockedException(locked?.detail ?: "Database is locked.")
        }
    }

    fun wipeAllDataAndReinitialize() {
        synchronized(lock) {
            val previous = database
            database = null
            _databaseFlow.value = null
            runCatching { previous?.close() }
            deleteDatabaseFiles()
            keyManager.wipeAllKeyMaterial()
            initializeIfPossible()
        }
    }

    private fun deleteDatabaseFiles() {
        val dbFile = context.getDatabasePath(databaseName)
        listOf(
            dbFile,
            File("${dbFile.path}-wal"),
            File("${dbFile.path}-shm"),
            File("${dbFile.path}-journal"),
        ).forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
