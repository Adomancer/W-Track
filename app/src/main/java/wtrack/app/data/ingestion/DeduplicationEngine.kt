package wtrack.app.data.ingestion

import wtrack.app.data.local.WtrackDatabase
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.domain.usecase.IdentityHashGenerator
import wtrack.integration.Direction

enum class DuplicateMatchTier {
    PROVIDER_REFERENCE,
    EVENT_IDENTITY,
    COMPOSITE_FINGERPRINT,
}

data class DuplicateMatch(
    val existingTransactionId: Long,
    val tier: DuplicateMatchTier,
)

/**
 * 3-tier transaction deduplicator (PRD section 8.1):
 * 1. Provider reference (`provider_reference`) scoped by source.
 * 2. Event identity (`event_id`).
 * 3. Composite fingerprint within a bounded +/- 5-minute window (NEVER amount + timestamp alone).
 */
class DeduplicationEngine {
    suspend fun findDuplicate(
        db: WtrackDatabase,
        sourceId: String,
        eventId: Long,
        accountId: Long,
        amountMinor: Long,
        currency: String,
        direction: Direction,
        occurredAtEpochMillis: Long,
        merchantKeyOrDesc: String,
        providerReference: String?,
        notificationKeyOrId: String,
    ): Pair<DuplicateMatch?, String> {
        val txDao = db.transactionDao()

        if (!providerReference.isNullOrBlank()) {
            val byRef: TransactionEntity? = txDao.findByProviderReferenceAndDirection(
                sourceId = sourceId,
                providerReference = providerReference.trim(),
                direction = direction.name,
            )
            if (byRef != null) {
                val fp = IdentityHashGenerator.transactionFingerprint(
                    accountId, amountMinor, currency, direction, occurredAtEpochMillis,
                    merchantKeyOrDesc, providerReference, notificationKeyOrId,
                )
                return DuplicateMatch(byRef.id, DuplicateMatchTier.PROVIDER_REFERENCE) to fp
            }
        }

        val byEvent = txDao.findByEventId(eventId)
        if (byEvent != null) {
            val fp = IdentityHashGenerator.transactionFingerprint(
                accountId, amountMinor, currency, direction, occurredAtEpochMillis,
                merchantKeyOrDesc, providerReference, notificationKeyOrId,
            )
            return DuplicateMatch(byEvent.id, DuplicateMatchTier.EVENT_IDENTITY) to fp
        }

        val fingerprint = IdentityHashGenerator.transactionFingerprint(
            accountId = accountId,
            amountMinor = amountMinor,
            currency = currency,
            direction = direction,
            occurredAtEpochMillis = occurredAtEpochMillis,
            merchantKeyOrDesc = merchantKeyOrDesc,
            providerReference = providerReference,
            notificationKeyOrId = notificationKeyOrId,
        )
        val byFingerprint = txDao.findByFingerprintInWindow(
            fingerprint = fingerprint,
            windowStartMillis = occurredAtEpochMillis - FINGERPRINT_WINDOW_MILLIS,
            windowEndMillis = occurredAtEpochMillis + FINGERPRINT_WINDOW_MILLIS,
        )
        if (byFingerprint != null) {
            return DuplicateMatch(byFingerprint.id, DuplicateMatchTier.COMPOSITE_FINGERPRINT) to fingerprint
        }

        return null to fingerprint
    }

    private companion object {
        const val FINGERPRINT_WINDOW_MILLIS = 300_000L
    }
}
