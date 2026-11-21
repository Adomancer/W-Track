package wtrack.app.data.backup

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.mapper.LocalJson
import wtrack.app.domain.repository.SampleExportResult
import wtrack.app.domain.time.Clock
import wtrack.app.domain.usecase.IdentityHashGenerator
import wtrack.integration.NotTransactionReason
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.providers.common.FixtureAnonymizer
import wtrack.integration.providers.common.SensitiveContentGuard

@Serializable
data class AnonymizedSampleEntry(
    val sourceId: String,
    val packageName: String,
    val postedAtEpochMillis: Long,
    val receivedAtEpochMillis: Long,
    val notificationKeyHash: String?,
    val notificationId: Int,
    val notificationTagAnonymized: String?,
    val channelId: String?,
    val isGroupSummary: Boolean,
    val isContentRedacted: Boolean,
    val title: String?,
    val text: String,
    val bigText: String?,
    val subText: String?,
)

@Serializable
data class AnonymizedSampleExportBundle(
    val schemaVersion: Int = 1,
    val exportedAtEpochMillis: Long,
    val sampleCount: Int,
    val skippedSecurityCount: Int,
    val samples: List<AnonymizedSampleEntry>,
)

/**
 * Exports retained M3a raw notification samples as anonymized JSON so real Bank Jago / DANA / BCA
 * notification formats can be inspected without exposing OTPs, PINs, names, phones, emails, or
 * full account/card numbers (PRD section 11, SECURITY.md section 6.2).
 */
class M3aSampleExporter(
    private val clock: Clock,
) {
    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun export(
        events: List<NotificationEventEntity>,
        outputStream: OutputStream,
        redactTerms: List<String> = emptyList(),
    ): SampleExportResult {
        val entries = mutableListOf<AnonymizedSampleEntry>()
        var skippedSecurityCount = 0

        events.forEach { event ->
            val payload = LocalJson.decodePayload(event.payloadJson) ?: return@forEach
            val envelope = NotificationEvent(
                sourceId = event.sourceId,
                packageName = event.packageName,
                title = payload.title,
                text = payload.text,
                bigText = payload.bigText,
                subText = payload.subText,
                channelId = payload.channelId,
                isGroupSummary = payload.isGroupSummary,
                isContentRedacted = payload.isContentRedacted,
                postedAtEpochMillis = event.postedAt,
                notificationKey = event.notificationKey,
                notificationId = event.notificationId,
                notificationTag = event.notificationTag,
                sourceEventReference = null,
            )

            val precheck = SensitiveContentGuard.inspectPreconditions(envelope)
            if (precheck is ParseResult.NotTransaction &&
                precheck.reason == NotTransactionReason.SECURITY_NOTICE
            ) {
                skippedSecurityCount++
                return@forEach
            }

            entries += AnonymizedSampleEntry(
                sourceId = event.sourceId,
                packageName = event.packageName,
                postedAtEpochMillis = event.postedAt,
                receivedAtEpochMillis = event.receivedAt,
                notificationKeyHash = event.notificationKey?.let {
                    IdentityHashGenerator.sha256Hex(it).take(12)
                },
                notificationId = event.notificationId,
                notificationTagAnonymized = event.notificationTag?.let {
                    FixtureAnonymizer.anonymize(it, redactTerms)
                },
                channelId = payload.channelId,
                isGroupSummary = payload.isGroupSummary,
                isContentRedacted = payload.isContentRedacted,
                title = payload.title?.let { FixtureAnonymizer.anonymize(it, redactTerms) },
                text = FixtureAnonymizer.anonymize(payload.text, redactTerms),
                bigText = payload.bigText?.let { FixtureAnonymizer.anonymize(it, redactTerms) },
                subText = payload.subText?.let { FixtureAnonymizer.anonymize(it, redactTerms) },
            )
        }

        val bundle = AnonymizedSampleExportBundle(
            exportedAtEpochMillis = clock.nowEpochMillis(),
            sampleCount = entries.size,
            skippedSecurityCount = skippedSecurityCount,
            samples = entries,
        )
        val encoded = prettyJson.encodeToString(bundle)
        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(encoded)
            writer.flush()
        }
        return SampleExportResult(
            exportedCount = entries.size,
            skippedSecurityCount = skippedSecurityCount,
        )
    }
}
