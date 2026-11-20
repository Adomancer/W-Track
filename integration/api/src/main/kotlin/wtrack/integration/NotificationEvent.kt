package wtrack.integration

/**
 * A notification event as seen across the pure-Kotlin parser boundary (TECHNICAL_SPEC section 1.2).
 *
 * The `app` module maps Android notification metadata and extracted fields into this pure-Kotlin
 * envelope before calling a [TransactionParser], keeping parsers strictly isolated from Room
 * entities and the Android notification framework.
 */
data class NotificationEvent(
    /** Internal source identifier matching the registered parser's [TransactionParser.sourceId]. */
    val sourceId: String,
    /** Provider app package name as reported by Android. */
    val packageName: String,
    /** Notification title (`Notification.EXTRA_TITLE`). */
    val title: String?,
    /** Primary notification text (`Notification.EXTRA_TEXT`, empty if absent). */
    val text: String,
    /** Expanded notification text (`Notification.EXTRA_BIG_TEXT`). */
    val bigText: String?,
    /** Secondary notification sub-text (`Notification.EXTRA_SUB_TEXT`). */
    val subText: String?,
    /** Android notification channel ID (API 26+). */
    val channelId: String?,
    /** True when Android flags this notification as a group summary. */
    val isGroupSummary: Boolean,
    /** True when OS redaction or hidden lock-screen state stripped content (ING-06). */
    val isContentRedacted: Boolean,
    /** Android notification post time (UTC epoch millis). Used as timestamp fallback (PAR-12). */
    val postedAtEpochMillis: Long,
    /** Android notification key (`sbn.key`), recorded for idempotency evidence (PRD section 8.1). */
    val notificationKey: String?,
    /** Android notification ID (`sbn.id`), recorded for idempotency evidence (PRD section 8.1). */
    val notificationId: Int,
    /** Android notification tag (`sbn.tag`, or hashed if PII is observed). */
    val notificationTag: String?,
    /** Arbitrary opaque source-provided event/message ID when exposed. */
    val sourceEventReference: String?,
)
