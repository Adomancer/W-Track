package wtrack.app.data.ingestion

import android.app.Notification
import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.integration.NotificationEvent

/**
 * Lock-free in-memory package allowlist mirror checked synchronously inside
 * `NotificationListenerService.onNotificationPosted` so disallowed notifications are dropped
 * immediately without allocating payloads or touching disk (ING-04).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AllowlistCache(
    private val holder: WtrackDatabaseHolder,
    private val scope: CoroutineScope,
) {
    private val allowedPackages = AtomicReference<Set<String>>(emptySet())
    private val observing = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)

    fun startObserving() {
        if (!observing.compareAndSet(false, true)) return
        holder.databaseFlow
            .flatMapLatest { db ->
                if (db == null) {
                    allowedPackages.set(emptySet())
                    initialized.set(false)
                    emptyFlow()
                } else {
                    db.transactionSourceDao().observeAllowlistedPackages()
                }
            }
            .onEach { packages ->
                allowedPackages.set(packages.toSet())
                initialized.set(true)
            }
            .launchIn(scope)
    }

    fun refreshSnapshotNow() {
        if (holder.healthState.value !is DatabaseHealthState.Ready) {
            allowedPackages.set(emptySet())
            initialized.set(false)
            return
        }
        runCatching {
            val db = holder.requireDatabase()
            val current = db.openHelper.readableDatabase
                .query("SELECT package_name FROM transaction_sources WHERE is_allowlisted = 1")
            current.use { cursor ->
                val set = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    set.add(cursor.getString(0))
                }
                allowedPackages.set(set)
                initialized.set(true)
            }
        }
    }

    fun setAllowlistedForTest(packages: Set<String>) {
        allowedPackages.set(packages)
        initialized.set(true)
    }

    fun isAllowlisted(packageName: String?): Boolean {
        if (packageName == null) return false
        if (!initialized.get() && holder.healthState.value is DatabaseHealthState.Ready) {
            refreshSnapshotNow()
        }
        return allowedPackages.get().contains(packageName)
    }
}

/**
 * Extracts minimal structured notification fields into a pure-Kotlin [NotificationEvent] envelope.
 * Detects Android 15+/16 hidden/redacted notifications safely without bypassing OS permissions (ING-06).
 */
object NotificationEnvelopeExtractor {
    private val knownRedactionMarkers = listOf(
        "sensitive notification content hidden",
        "konten notifikasi sensitif disembunyikan",
        "contents hidden",
        "isi disembunyikan",
    )

    fun extract(sbn: StatusBarNotification): NotificationEvent {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        val channelId = notification.channelId
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

        val isRedacted = isNotificationRedacted(
            flags = notification.flags,
            title = title,
            text = text,
            bigText = bigText,
            isGroupSummary = isGroupSummary,
        )

        return NotificationEvent(
            sourceId = sbn.packageName,
            packageName = sbn.packageName,
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            channelId = channelId,
            isGroupSummary = isGroupSummary,
            isContentRedacted = isRedacted,
            postedAtEpochMillis = sbn.postTime,
            notificationKey = sbn.key,
            notificationId = sbn.id,
            notificationTag = sbn.tag,
            sourceEventReference = null,
        )
    }

    fun isNotificationRedacted(
        flags: Int,
        title: String?,
        text: String,
        bigText: String?,
        isGroupSummary: Boolean,
    ): Boolean {
        // Android 15+ may set redaction markers in text or strip body text when OTP-like tokens appear.
        val combinedLower = "${title.orEmpty()} $text ${bigText.orEmpty()}".lowercase()
        if (knownRedactionMarkers.any { marker -> combinedLower.contains(marker) }) {
            return true
        }
        // If non-summary notification has completely blank text and bigText despite having a title or channel:
        if (!isGroupSummary && text.isBlank() && bigText.isNullOrBlank()) {
            return true
        }
        // Check platform redaction flag constant bit (0x00020000 on newer SDKs if set) safely via reflection.
        val redactedFlag = redactedFlagBitOrNull()
        return redactedFlag != null && (flags and redactedFlag) != 0
    }

    private fun redactedFlagBitOrNull(): Int? = runCatching {
        Notification::class.java.getField("FLAG_REDACTED").getInt(null)
    }.getOrNull()
}
