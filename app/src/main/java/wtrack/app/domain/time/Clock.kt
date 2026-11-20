package wtrack.app.domain.time

/**
 * Pure-Kotlin time provider so domain logic stays testable and independent of Android clock APIs.
 */
interface Clock {
    fun nowEpochMillis(): Long
}
