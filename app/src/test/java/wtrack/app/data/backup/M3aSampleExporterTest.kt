package wtrack.app.data.backup

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import org.junit.Test
import wtrack.app.data.ingestion.FakeClock
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.mapper.LocalJson
import wtrack.app.data.local.mapper.NotificationPayloadJson
import wtrack.app.domain.usecase.IdentityHashGenerator

class M3aSampleExporterTest {

    @Test
    fun exportsAnonymizedSamplesSkipsSecurityNoticesAndHashesNotificationKeys() {
        val clock = FakeClock(now = 1_700_100_000_000L)
        val exporter = M3aSampleExporter(clock)

        val normalEvent = NotificationEventEntity(
            id = 1L,
            sourceId = "com.jago.digitalBanking",
            packageName = "com.jago.digitalBanking",
            stableEventKey = "stable-1",
            notificationKey = "0|com.jago.digitalBanking|101|secret-tag-88991122|10234",
            notificationId = 101,
            notificationTag = "acct-123456789012",
            postedAt = 1_700_050_000_000L,
            payloadJson = LocalJson.encodePayload(
                NotificationPayloadJson(
                    title = "Transfer keluar untuk Budi Santoso",
                    text = "Kirim Rp 150.000 ke 123456789012 / 081234567890 berhasil",
                    bigText = "Email konfirmasi: budi@example.com dari Kantong Utama",
                    subText = "Kantong Utama",
                    channelId = "trx_alert",
                    isGroupSummary = false,
                    isContentRedacted = false,
                ),
            ),
            purgeAfter = 1_700_999_000_000L,
            processingStatus = "IGNORED",
            attemptCount = 1,
            failureReason = "NO_PARSER",
            reviewDraftJson = null,
            receivedAt = 1_700_050_001_234L,
            processedAt = 1_700_050_001_500L,
            updatedAt = 1_700_050_001_500L,
        )

        val leakedSecurityEvent = NotificationEventEntity(
            id = 2L,
            sourceId = "id.dana",
            packageName = "id.dana",
            stableEventKey = "stable-2",
            notificationKey = "0|id.dana|202|otp|10235",
            notificationId = 202,
            notificationTag = null,
            postedAt = 1_700_060_000_000L,
            payloadJson = LocalJson.encodePayload(
                NotificationPayloadJson(
                    title = "Kode Verifikasi",
                    text = "Kode OTP Anda 774411. Jangan bagikan kepada siapa pun.",
                    channelId = "security",
                ),
            ),
            purgeAfter = 1_700_999_000_000L,
            processingStatus = "IGNORED",
            attemptCount = 1,
            failureReason = "NO_PARSER",
            reviewDraftJson = null,
            receivedAt = 1_700_060_001_000L,
            processedAt = 1_700_060_001_100L,
            updatedAt = 1_700_060_001_100L,
        )

        val baos = ByteArrayOutputStream()
        val result = exporter.export(
            events = listOf(normalEvent, leakedSecurityEvent),
            outputStream = baos,
            redactTerms = listOf("Budi Santoso"),
        )

        assertThat(result.exportedCount).isEqualTo(1)
        assertThat(result.skippedSecurityCount).isEqualTo(1)

        val jsonText = String(baos.toByteArray(), StandardCharsets.UTF_8)
        assertThat(jsonText).doesNotContain("Budi Santoso")
        assertThat(jsonText).doesNotContain("081234567890")
        assertThat(jsonText).doesNotContain("budi@example.com")
        assertThat(jsonText).doesNotContain("12345678")
        assertThat(jsonText).doesNotContain("774411")
        assertThat(jsonText).doesNotContain("secret-tag-88991122")

        val decoded = Json.decodeFromString<AnonymizedSampleExportBundle>(jsonText)
        assertThat(decoded.schemaVersion).isEqualTo(1)
        assertThat(decoded.exportedAtEpochMillis).isEqualTo(1_700_100_000_000L)
        assertThat(decoded.sampleCount).isEqualTo(1)
        assertThat(decoded.skippedSecurityCount).isEqualTo(1)

        val entry = decoded.samples.single()
        assertThat(entry.sourceId).isEqualTo("com.jago.digitalBanking")
        assertThat(entry.postedAtEpochMillis).isEqualTo(1_700_050_000_000L)
        assertThat(entry.notificationId).isEqualTo(101)
        assertThat(entry.channelId).isEqualTo("trx_alert")
        assertThat(entry.isGroupSummary).isFalse()
        assertThat(entry.notificationKeyHash).isEqualTo(
            IdentityHashGenerator.sha256Hex("0|com.jago.digitalBanking|101|secret-tag-88991122|10234").take(12),
        )
        assertThat(entry.notificationTagAnonymized).isEqualTo("acct-****9012")
        assertThat(entry.text).contains("Rp 150.000")
        assertThat(entry.text).contains("****9012")
    }
}
