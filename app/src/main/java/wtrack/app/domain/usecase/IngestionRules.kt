package wtrack.app.domain.usecase

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import wtrack.app.domain.model.AccountSource
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.toDomain
import wtrack.integration.Direction
import wtrack.integration.NotificationEvent
import wtrack.integration.ParsedTransaction
import wtrack.integration.PartialTransaction
import wtrack.integration.ReviewReason

sealed interface ValidationOutcome {
    data class Valid(
        val amountMinor: Long,
        val currency: String,
        val direction: Direction,
        val transactionType: DomainTransactionType,
        val occurredAtEpochMillis: Long,
    ) : ValidationOutcome

    data class Invalid(
        val reason: ReviewReason,
        val partial: PartialTransaction,
    ) : ValidationOutcome
}

/**
 * Validates parsed transaction fields and enforces PRD fallback & classification constraints:
 * - Positive amount and valid 3-letter ISO currency (PAR-05).
 * - Outgoing refunds (`OUTFLOW + REFUND`) are never saved automatically -> `NeedsReview` (PRD section 4.6).
 * - Missing transaction timestamp falls back strictly to `posted_at`, NEVER `received_at` (PAR-12).
 */
class TransactionValidator {
    fun validate(parsed: ParsedTransaction, postedAtEpochMillis: Long): ValidationOutcome {
        val normalizedCurrency = parsed.currency.trim().uppercase()
        val occurredAt = parsed.occurredAtEpochMillis ?: postedAtEpochMillis
        val domainType = parsed.type.toDomain()

        fun asPartial(): PartialTransaction = PartialTransaction(
            amountMinor = parsed.amountMinor,
            currency = normalizedCurrency.ifEmpty { null },
            direction = parsed.direction,
            type = parsed.type,
            occurredAtEpochMillis = occurredAt,
            merchantRaw = parsed.merchantRaw,
            description = parsed.description,
            accountHint = parsed.accountHint,
            providerReference = parsed.providerReference,
            reportedBalanceMinor = parsed.reportedBalanceMinor,
        )

        if (parsed.amountMinor <= 0L) {
            return ValidationOutcome.Invalid(ReviewReason.INVALID_VALUE, asPartial())
        }
        if (normalizedCurrency.length != 3 || !normalizedCurrency.all { it in 'A'..'Z' }) {
            return ValidationOutcome.Invalid(ReviewReason.INVALID_VALUE, asPartial())
        }
        if (occurredAt <= 0L) {
            return ValidationOutcome.Invalid(ReviewReason.INVALID_VALUE, asPartial())
        }
        if (domainType == DomainTransactionType.REFUND && parsed.direction == Direction.OUTFLOW) {
            return ValidationOutcome.Invalid(ReviewReason.VALIDATION_FAILED, asPartial())
        }

        return ValidationOutcome.Valid(
            amountMinor = parsed.amountMinor,
            currency = normalizedCurrency,
            direction = parsed.direction,
            transactionType = domainType,
            occurredAtEpochMillis = occurredAt,
        )
    }
}

sealed interface AccountResolutionResult {
    data class Resolved(val accountId: Long) : AccountResolutionResult
    data class NeedsReview(val reason: ReviewReason) : AccountResolutionResult
}

/**
 * Resolves a source event + optional masked `accountHint` to a configured account (ACC-05, PAR-02).
 */
class AccountResolver {
    fun resolve(links: List<AccountSource>, accountHint: String?): AccountResolutionResult {
        if (links.isEmpty()) {
            return AccountResolutionResult.NeedsReview(ReviewReason.ACCOUNT_UNMAPPED)
        }
        val cleanedHint = accountHint?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanedHint != null) {
            val exactMatches = links.filter {
                it.accountHint?.equals(cleanedHint, ignoreCase = true) == true
            }
            if (exactMatches.size == 1) {
                return AccountResolutionResult.Resolved(exactMatches.first().accountId)
            }
            if (exactMatches.size > 1) {
                return AccountResolutionResult.NeedsReview(ReviewReason.ACCOUNT_AMBIGUOUS)
            }
            // If only a single unhinted fallback exists for this source, resolve to it cleanly.
            val nullHintLinks = links.filter { it.accountHint == null }
            if (links.size == 1 && nullHintLinks.size == 1) {
                return AccountResolutionResult.Resolved(nullHintLinks.first().accountId)
            }
            return AccountResolutionResult.NeedsReview(ReviewReason.ACCOUNT_UNMAPPED)
        }

        // No hint extracted from notification: resolve only if unambiguous.
        if (links.size == 1) {
            return AccountResolutionResult.Resolved(links.first().accountId)
        }
        val defaultLink = links.singleOrNull { it.accountHint == null }
        return if (defaultLink != null && links.count { it.accountHint != null } == 0) {
            AccountResolutionResult.Resolved(defaultLink.accountId)
        } else {
            AccountResolutionResult.NeedsReview(ReviewReason.ACCOUNT_AMBIGUOUS)
        }
    }
}

/**
 * Generates deterministic SHA-256 identifiers for Tier 2 event identity (`stable_event_key`)
 * and Tier 3 composite transaction deduplication (`fingerprint`).
 *
 * Never deduplicates solely on `(amount, timestamp)` (PRD section 8.1).
 */
object IdentityHashGenerator {
    private const val FINGERPRINT_BUCKET_MILLIS = 300_000L // 5-minute bucket

    fun stableEventKey(event: NotificationEvent): String {
        val contentBlob = listOf(
            event.title.orEmpty(),
            event.text,
            event.bigText.orEmpty(),
            event.subText.orEmpty(),
            if (event.isContentRedacted) "REDACTED" else "VISIBLE",
        ).joinToString("|")
        val contentHash = sha256Hex(contentBlob)
        val identityToken = when {
            !event.sourceEventReference.isNullOrBlank() -> "ref:${event.sourceEventReference}"
            !event.notificationKey.isNullOrBlank() -> "key:${event.notificationKey}"
            else -> "id:${event.notificationId}:tag:${event.notificationTag.orEmpty()}"
        }
        return sha256Hex("${event.packageName}|$identityToken|${event.postedAtEpochMillis}|$contentHash")
    }

    fun transactionFingerprint(
        accountId: Long,
        amountMinor: Long,
        currency: String,
        direction: Direction,
        occurredAtEpochMillis: Long,
        merchantKeyOrDesc: String,
        providerReference: String?,
        notificationKeyOrId: String,
    ): String {
        val bucket = occurredAtEpochMillis / FINGERPRINT_BUCKET_MILLIS
        val discriminator = providerReference?.takeIf { it.isNotBlank() }
            ?: merchantKeyOrDesc.takeIf { it.isNotBlank() }
            ?: notificationKeyOrId
        return sha256Hex("$accountId|$amountMinor|${currency.uppercase()}|${direction.name}|$bucket|$discriminator")
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
