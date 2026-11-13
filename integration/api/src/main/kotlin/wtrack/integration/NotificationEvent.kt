package wtrack.integration

/**
 * A notification event as seen by the parser boundary.
 *
 * This is the pure-Kotlin representation that a provider parser receives. The `app` module
 * maps its persisted [android NotificationEvent][wtrack.data.event.NotificationEvent] to this
 * shape before calling a parser, so that parsers stay independent of the Room entities and of
 * the Android notification framework.
 *
 * The parser reads [text] and the metadata needed to resolve source, account and time. The raw
 * payload (the notification content the user sees) is intentionally kept out of this contract;
 * parsers never see the payload, only the fields the listener extracted.
 */
data class NotificationEvent(
    /** Provider/source package name that produced the event, e.g. `com.example.bank`. */
    val sourceId: String,
    /** The provider app's package name as reported by Android. */
    val packageName: String,
    /** The notification text that carries the transaction information. */
    val text: String,
    /** The notification's post time (epoch millis, UTC). Used as the time fallback (PAR-12). */
    val postedAtEpochMillis: Long,
    /** Android notification key, used for idempotency evidence (section 8.1). */
    val notificationKey: String?,
    /** Android notification ID, used for idempotency evidence (section 8.1). */
    val notificationId: Int,
    /** Android notification tag, used for idempotency evidence (section 8.1). */
    val notificationTag: String?,
    /** Arbitrary opaque source-provided id when the provider exposes one. */
    val sourceEventReference: String?,
)