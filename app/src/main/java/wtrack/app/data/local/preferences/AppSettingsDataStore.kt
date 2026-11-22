package wtrack.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.ThemeMode

private val Context.wtrackPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "wtrack_settings")

class AppSettingsDataStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.wtrackPrefsDataStore)

    val settings: Flow<AppSettings> = dataStore.data.map(::mapToSettings)

    suspend fun current(): AppSettings = mapToSettings(dataStore.data.first())

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setBaseCurrency(currency: String) {
        dataStore.edit { it[KEY_BASE_CURRENCY] = currency.trim().uppercase() }
    }

    suspend fun setVerificationWindowDays(days: Int) {
        dataStore.edit { it[KEY_VERIFICATION_WINDOW_DAYS] = days.coerceIn(0, 30) }
    }

    suspend fun setHeartbeatStaleHours(hours: Int) {
        dataStore.edit { it[KEY_HEARTBEAT_STALE_HOURS] = hours.coerceIn(1, 24) }
    }

    suspend fun updateLastAliveAt(epochMillis: Long) {
        dataStore.edit { it[KEY_LAST_ALIVE_AT] = epochMillis }
    }

    suspend fun incrementTxCountSinceBackup() {
        dataStore.edit { prefs ->
            val current = prefs[KEY_TX_COUNT_SINCE_BACKUP] ?: 0
            prefs[KEY_TX_COUNT_SINCE_BACKUP] = current + 1
        }
    }

    suspend fun recordBackupCompleted(atEpochMillis: Long) {
        dataStore.edit {
            it[KEY_LAST_BACKUP_AT] = atEpochMillis
            it[KEY_TX_COUNT_SINCE_BACKUP] = 0
        }
    }

    suspend fun dismissBackupReminder(atEpochMillis: Long) {
        dataStore.edit { it[KEY_BACKUP_REMINDER_DISMISSED_AT] = atEpochMillis }
    }

    suspend fun setBackupReminderThresholds(days: Int, txCount: Int) {
        dataStore.edit {
            it[KEY_BACKUP_REMINDER_DAYS] = days.coerceIn(1, 90)
            it[KEY_BACKUP_REMINDER_TX_COUNT] = txCount.coerceIn(10, 5000)
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setScheduledBackupConfig(folderUri: String?, enabled: Boolean, intervalDays: Int) {
        dataStore.edit { prefs ->
            val cleaned = folderUri?.trim()?.takeIf { it.isNotEmpty() }
            if (cleaned == null) {
                prefs.remove(KEY_SCHEDULED_BACKUP_FOLDER_URI)
            } else {
                prefs[KEY_SCHEDULED_BACKUP_FOLDER_URI] = cleaned
            }
            prefs[KEY_SCHEDULED_BACKUP_ENABLED] = enabled && cleaned != null
            prefs[KEY_SCHEDULED_BACKUP_INTERVAL_DAYS] = intervalDays.coerceIn(1, 90)
        }
    }

    suspend fun setAppLock(enabled: Boolean, timeoutSeconds: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_APP_LOCK_ENABLED] = enabled
            prefs[KEY_APP_LOCK_TIMEOUT_SECONDS] = timeoutSeconds.coerceIn(0, 3600)
        }
    }

    suspend fun setLocalAlertsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_LOCAL_ALERTS_ENABLED] = enabled
        }
    }

    suspend fun replaceAll(restored: AppSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = restored.themeMode.name
            prefs[KEY_BASE_CURRENCY] = restored.baseCurrency
            prefs[KEY_VERIFICATION_WINDOW_DAYS] = restored.verificationWindowDays.coerceIn(0, 30)
            prefs[KEY_HEARTBEAT_STALE_HOURS] = restored.heartbeatStaleHours.coerceIn(1, 24)
            prefs[KEY_LAST_ALIVE_AT] = restored.lastAliveAt
            prefs[KEY_BACKUP_REMINDER_DAYS] = restored.backupReminderDays
            prefs[KEY_BACKUP_REMINDER_TX_COUNT] = restored.backupReminderTxCount
            prefs[KEY_LAST_BACKUP_AT] = restored.lastBackupAt
            prefs[KEY_TX_COUNT_SINCE_BACKUP] = restored.txCountSinceLastBackup
            prefs[KEY_BACKUP_REMINDER_DISMISSED_AT] = restored.backupReminderDismissedAt
            prefs[KEY_ONBOARDING_COMPLETED] = restored.onboardingCompleted
            val cleanedFolder = restored.scheduledBackupFolderUri?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanedFolder == null) {
                prefs.remove(KEY_SCHEDULED_BACKUP_FOLDER_URI)
            } else {
                prefs[KEY_SCHEDULED_BACKUP_FOLDER_URI] = cleanedFolder
            }
            prefs[KEY_SCHEDULED_BACKUP_ENABLED] = restored.scheduledBackupEnabled && cleanedFolder != null
            prefs[KEY_SCHEDULED_BACKUP_INTERVAL_DAYS] = restored.scheduledBackupIntervalDays.coerceIn(1, 90)
            prefs[KEY_APP_LOCK_ENABLED] = restored.appLockEnabled
            prefs[KEY_APP_LOCK_TIMEOUT_SECONDS] = restored.appLockTimeoutSeconds.coerceIn(0, 3600)
            prefs[KEY_LOCAL_ALERTS_ENABLED] = restored.localAlertsEnabled
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private fun mapToSettings(prefs: Preferences): AppSettings = AppSettings(
        themeMode = prefs[KEY_THEME_MODE]
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        baseCurrency = prefs[KEY_BASE_CURRENCY] ?: "IDR",
        verificationWindowDays = (prefs[KEY_VERIFICATION_WINDOW_DAYS] ?: 7).coerceIn(0, 30),
        heartbeatStaleHours = (prefs[KEY_HEARTBEAT_STALE_HOURS] ?: 3).coerceIn(1, 24),
        lastAliveAt = prefs[KEY_LAST_ALIVE_AT] ?: 0L,
        backupReminderDays = prefs[KEY_BACKUP_REMINDER_DAYS] ?: 14,
        backupReminderTxCount = prefs[KEY_BACKUP_REMINDER_TX_COUNT] ?: 100,
        lastBackupAt = prefs[KEY_LAST_BACKUP_AT] ?: 0L,
        txCountSinceLastBackup = prefs[KEY_TX_COUNT_SINCE_BACKUP] ?: 0,
        backupReminderDismissedAt = prefs[KEY_BACKUP_REMINDER_DISMISSED_AT] ?: 0L,
        onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
        scheduledBackupFolderUri = prefs[KEY_SCHEDULED_BACKUP_FOLDER_URI],
        scheduledBackupEnabled = (prefs[KEY_SCHEDULED_BACKUP_ENABLED] ?: false) &&
            !prefs[KEY_SCHEDULED_BACKUP_FOLDER_URI].isNullOrBlank(),
        scheduledBackupIntervalDays = (prefs[KEY_SCHEDULED_BACKUP_INTERVAL_DAYS] ?: 7).coerceIn(1, 90),
        appLockEnabled = prefs[KEY_APP_LOCK_ENABLED] ?: false,
        appLockTimeoutSeconds = (prefs[KEY_APP_LOCK_TIMEOUT_SECONDS] ?: 60).coerceIn(0, 3600),
        localAlertsEnabled = prefs[KEY_LOCAL_ALERTS_ENABLED] ?: false,
    )

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_BASE_CURRENCY = stringPreferencesKey("base_currency")
        val KEY_VERIFICATION_WINDOW_DAYS = intPreferencesKey("verification_window_days")
        val KEY_HEARTBEAT_STALE_HOURS = intPreferencesKey("heartbeat_stale_hours")
        val KEY_LAST_ALIVE_AT = longPreferencesKey("last_alive_at")
        val KEY_BACKUP_REMINDER_DAYS = intPreferencesKey("backup_reminder_days")
        val KEY_BACKUP_REMINDER_TX_COUNT = intPreferencesKey("backup_reminder_tx_count")
        val KEY_LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val KEY_TX_COUNT_SINCE_BACKUP = intPreferencesKey("tx_count_since_backup")
        val KEY_BACKUP_REMINDER_DISMISSED_AT = longPreferencesKey("backup_reminder_dismissed_at")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_SCHEDULED_BACKUP_FOLDER_URI = stringPreferencesKey("scheduled_backup_folder_uri")
        val KEY_SCHEDULED_BACKUP_ENABLED = booleanPreferencesKey("scheduled_backup_enabled")
        val KEY_SCHEDULED_BACKUP_INTERVAL_DAYS = intPreferencesKey("scheduled_backup_interval_days")
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_APP_LOCK_TIMEOUT_SECONDS = intPreferencesKey("app_lock_timeout_seconds")
        val KEY_LOCAL_ALERTS_ENABLED = booleanPreferencesKey("local_alerts_enabled")
    }
}
