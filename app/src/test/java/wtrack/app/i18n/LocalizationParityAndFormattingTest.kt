package wtrack.app.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import wtrack.app.R
import wtrack.app.core.i18n.AppLocaleHelper
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.UiText

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalizationParityAndFormattingTest {

    private val placeholderRegex = Regex("""%(?!%)(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]""")

    @Test
    fun xmlResourcesHaveCompleteKeyAndFormatSpecifierParityBetweenEnglishAndIndonesian() {
        val resDir = locateDir("src/main/res", "app/src/main/res")
        val enFile = File(resDir, "values/strings.xml")
        val idFile = File(resDir, "values-in/strings.xml")
        val legacyBPlusInDir = File(resDir, "values-b+in")

        assertThat(enFile.isFile).isTrue()
        assertThat(idFile.isFile).isTrue()
        assertThat(legacyBPlusInDir.exists()).isFalse()

        val enStrings = parseStringsXml(enFile)
        val idStrings = parseStringsXml(idFile)
        assertThat(enStrings.strings).isNotEmpty()
        assertThat(idStrings.strings.keys).containsExactlyElementsIn(enStrings.strings.keys)

        enStrings.strings.forEach { (key, enValue) ->
            val idValue = idStrings.strings.getValue(key)
            assertThat(enValue.trim()).isNotEmpty()
            assertThat(idValue.trim()).isNotEmpty()

            val enPlaceholders = extractPlaceholders(enValue)
            val idPlaceholders = extractPlaceholders(idValue)
            assertWithMessage("Format placeholders for string '$key'")
                .that(idPlaceholders)
                .containsExactlyElementsIn(enPlaceholders)
        }

        assertThat(enStrings.plurals).isNotEmpty()
        assertThat(idStrings.plurals.keys).containsExactlyElementsIn(enStrings.plurals.keys)

        enStrings.plurals.forEach { (key, enItems) ->
            val idItems = idStrings.plurals.getValue(key)
            assertThat(enItems).containsKey("other")
            assertThat(idItems).containsKey("other")

            val expectedPlaceholders = extractPlaceholders(enItems.getValue("other"))
            (enItems.entries + idItems.entries).forEach { (quantity, value) ->
                assertWithMessage("Plural '$key'[$quantity]").that(value.trim()).isNotEmpty()
                assertWithMessage("Format placeholders for plural '$key'[$quantity]")
                    .that(extractPlaceholders(value))
                    .containsExactlyElementsIn(expectedPlaceholders)
            }
        }
    }

    @Test
    fun uiTextAndAppLocaleHelperResolveResourcesInEnglishAndIndonesian() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val enContext = contextForLocale(appContext, Locale.ENGLISH)
        val idContext = contextForLocale(appContext, Locale.forLanguageTag("id-ID"))

        assertThat(AppLocaleHelper.activeLocale(enContext).language).isEqualTo("en")
        assertThat(AppLocaleHelper.isIndonesian(AppLocaleHelper.activeLocale(enContext))).isFalse()

        val idLocale = AppLocaleHelper.activeLocale(idContext)
        assertThat(AppLocaleHelper.isIndonesian(idLocale)).isTrue()
        assertThat(AppLocaleHelper.isIndonesian(Locale.forLanguageTag("in"))).isTrue()
        assertThat(AppLocaleHelper.isIndonesian(Locale.forLanguageTag("id"))).isTrue()

        val previousDefault = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("id-ID"))
            assertThat(AppLocaleHelper.isIndonesian()).isTrue()
            Locale.setDefault(Locale.ENGLISH)
            assertThat(AppLocaleHelper.isIndonesian()).isFalse()
        } finally {
            Locale.setDefault(previousDefault)
        }

        // Dynamic, Resource (with nested UiText), PluralResource, and fromError
        assertThat(UiText.Dynamic("Custom text").asString(enContext)).isEqualTo("Custom text")

        val nested = UiText.Resource(R.string.nav_home)
        val withNestedArg = UiText.Resource(R.string.msg_account_added_named, nested)
        assertThat(withNestedArg.asString(enContext)).contains("Home")
        assertThat(withNestedArg.asString(idContext)).contains("Beranda")

        val oneAutoParsed = UiText.PluralResource(
            R.plurals.msg_review_saved_with_pattern_and_autoparsed,
            1,
            1,
        )
        val twoAutoParsed = UiText.PluralResource(
            R.plurals.msg_review_saved_with_pattern_and_autoparsed,
            2,
            2,
        )
        assertThat(oneAutoParsed.asString(enContext)).contains("Auto-parsed 1 more")
        assertThat(twoAutoParsed.asString(enContext)).contains("Auto-parsed 2 more")
        assertThat(twoAutoParsed.asString(idContext)).contains("2")
        assertThat(twoAutoParsed.asString(idContext)).isNotEqualTo(twoAutoParsed.asString(enContext))

        // Value equality & hashCode for ViewModel/StateFlow assertions
        assertThat(
            UiText.Resource(R.string.msg_enter_positive_amount, "IDR")
        ).isEqualTo(
            UiText.Resource(R.string.msg_enter_positive_amount, "IDR")
        )
        assertThat(
            UiText.Resource(R.string.msg_enter_positive_amount, "IDR").hashCode()
        ).isEqualTo(
            UiText.Resource(R.string.msg_enter_positive_amount, "IDR").hashCode()
        )
        assertThat(twoAutoParsed).isEqualTo(
            UiText.PluralResource(R.plurals.msg_review_saved_with_pattern_and_autoparsed, 2, 2)
        )

        // Error fallback vs concrete error message
        assertThat(
            UiText.fromError(IllegalStateException("Disk full"), R.string.msg_backup_restore_failed)
        ).isEqualTo(UiText.Dynamic("Disk full"))
        assertThat(
            UiText.fromError(IllegalStateException("   "), R.string.msg_backup_restore_failed)
        ).isEqualTo(UiText.Resource(R.string.msg_backup_restore_failed))

        // Key screens & notification strings resolve distinctly in EN vs ID
        assertThat(enContext.getString(R.string.settings_title)).isEqualTo("Settings")
        assertThat(idContext.getString(R.string.settings_title)).isEqualTo("Pengaturan")
        assertThat(enContext.getString(R.string.db_locked_title)).isEqualTo("Encrypted Database Locked")
        assertThat(idContext.getString(R.string.db_locked_title)).isNotEqualTo("Encrypted Database Locked")
        assertThat(enContext.getString(R.string.app_lock_title)).isEqualTo("W-Track is Locked")
        assertThat(idContext.getString(R.string.app_lock_title)).isEqualTo("W-Track Terkunci")
        assertThat(enContext.getString(R.string.notif_needs_review_title)).isNotEmpty()
        assertThat(idContext.getString(R.string.notif_needs_review_title))
            .isNotEqualTo(enContext.getString(R.string.notif_needs_review_title))
        assertThat(enContext.resources.getQuantityString(R.plurals.notif_needs_review_body, 3, 3))
            .contains("3")
        assertThat(idContext.resources.getQuantityString(R.plurals.notif_needs_review_body, 3, 3))
            .contains("3")
    }

    @Test
    fun uiDateHelperRespectsActiveLocaleAndPreservesEditableDateTimeRoundTrip() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val enContext = contextForLocale(appContext, Locale.ENGLISH)
        val idContext = contextForLocale(appContext, Locale.forLanguageTag("id-ID"))

        val zone = ZoneId.of("Asia/Jakarta")
        val now = ZonedDateTime.of(2026, 8, 15, 14, 30, 0, 0, zone).toInstant().toEpochMilli()
        val yesterday = ZonedDateTime.of(2026, 8, 14, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val fiveDaysAgo = ZonedDateTime.of(2026, 8, 10, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertThat(UiDateHelper.formatDayGroupHeader(enContext, now, now, zone)).isEqualTo("Today")
        assertThat(UiDateHelper.formatDayGroupHeader(idContext, now, now, zone)).isEqualTo("Hari ini")

        assertThat(UiDateHelper.formatDayGroupHeader(enContext, yesterday, now, zone)).isEqualTo("Yesterday")
        assertThat(UiDateHelper.formatDayGroupHeader(idContext, yesterday, now, zone)).isEqualTo("Kemarin")

        val enOlderHeader = UiDateHelper.formatDayGroupHeader(enContext, fiveDaysAgo, now, zone)
        val idOlderHeader = UiDateHelper.formatDayGroupHeader(idContext, fiveDaysAgo, now, zone)
        assertThat(enOlderHeader).contains("Aug")
        assertThat(idOlderHeader).contains("Agu")

        assertThat(UiDateHelper.formatDaysAgo(enContext, now, now)).isEqualTo("Today")
        assertThat(UiDateHelper.formatDaysAgo(idContext, now, now)).isEqualTo("Hari ini")
        assertThat(UiDateHelper.formatDaysAgo(idContext, fiveDaysAgo, now))
            .isNotEqualTo(UiDateHelper.formatDaysAgo(enContext, fiveDaysAgo, now))

        val enMonth = UiDateHelper.monthWindow(now, monthOffset = 0, zoneId = zone, locale = Locale.ENGLISH)
        val idMonth = UiDateHelper.monthWindow(now, monthOffset = 0, zoneId = zone, locale = Locale.forLanguageTag("id-ID"))
        assertThat(enMonth.label).isEqualTo("August 2026")
        assertThat(idMonth.label).isEqualTo("Agustus 2026")

        val sampleEpoch = Instant.ofEpochMilli(now).toEpochMilli()
        val formatted = UiDateHelper.formatEditableDateTime(sampleEpoch, zone)
        val parsed = UiDateHelper.parseEditableDateTime(formatted, zone)
        assertThat(parsed).isEqualTo(sampleEpoch)
        assertThat(UiDateHelper.parseEditableDateTime("not-a-date", zone)).isNull()
    }

    @Test
    fun featureScreensViewModelsAndNotifierHaveNoHardcodedProseRegressions() {
        val featureDir = locateDir("src/main/java/wtrack/app/feature", "app/src/main/java/wtrack/app/feature")
        val notifierFile = locateFile(
            "src/main/java/wtrack/app/data/notification/AndroidStatusAlertNotifier.kt",
            "app/src/main/java/wtrack/app/data/notification/AndroidStatusAlertNotifier.kt",
        )
        val uiFormattersFile = File(featureDir, "common/UiFormatters.kt")
        assertThat(uiFormattersFile.readText()).doesNotContain("Locale.ENGLISH")

        val notifierText = stripComments(notifierFile.readText())
        assertThat(notifierText).doesNotContain(".setContentTitle(\"")
        assertThat(notifierText).doesNotContain(".setContentText(\"")

        val textCallStringRegex = Regex("""\bText\s*\(\s*"([^"\n]*)"""")
        val interpolationRegex = Regex("""\$\{[^}]+\}|\$[A-Za-z_][A-Za-z0-9_]*""")
        val literalProseRegex = Regex("""[A-Za-z]{2,}""")
        val forbiddenPatterns = listOf(
            Regex("""actionMessage\.value\s*=\s*"[^"]+""""),
            Regex("""\bWtrackTopBar\s*\([^)]*title\s*=\s*"[^"\n]+""""),
            Regex("""\bSectionHeader\s*\([^)]*title\s*=\s*"[^"\n]+""""),
            Regex("""\bV2SettingRow\s*\([^)]*title\s*=\s*"[^"\n]+""""),
        )

        val violations = mutableListOf<String>()
        featureDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val cleaned = stripComments(file.readText())
                cleaned.lines().forEachIndexed { idx, line ->
                    textCallStringRegex.findAll(line).forEach { match ->
                        val literalWithoutInterpolations = interpolationRegex.replace(match.groupValues[1], "")
                        if (literalProseRegex.containsMatchIn(literalWithoutInterpolations)) {
                            violations += "${file.name}:${idx + 1}: ${line.trim()}"
                        }
                    }
                    forbiddenPatterns.forEach { regex ->
                        if (regex.containsMatchIn(line)) {
                            violations += "${file.name}:${idx + 1}: ${line.trim()}"
                        }
                    }
                }
            }

        assertThat(violations).isEmpty()
    }

    private data class ParsedResources(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
    )

    private fun parseStringsXml(file: File): ParsedResources {
        val factory = DocumentBuilderFactory.newInstance()
        val document = factory.newDocumentBuilder().parse(file)
        val root = document.documentElement

        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Map<String, String>>()

        val childNodes = root.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            if (name.isBlank()) continue
            when (node.tagName) {
                "string" -> strings[name] = node.textContent
                "plurals" -> {
                    val items = linkedMapOf<String, String>()
                    val pluralChildren = node.childNodes
                    for (j in 0 until pluralChildren.length) {
                        val itemNode = pluralChildren.item(j) as? Element ?: continue
                        if (itemNode.tagName == "item") {
                            val quantity = itemNode.getAttribute("quantity")
                            if (quantity.isNotBlank()) {
                                items[quantity] = itemNode.textContent
                            }
                        }
                    }
                    plurals[name] = items
                }
            }
        }
        return ParsedResources(strings = strings, plurals = plurals)
    }

    private fun extractPlaceholders(value: String): List<String> =
        placeholderRegex.findAll(value).map { it.value }.sorted().toList()

    private fun contextForLocale(base: Context, locale: Locale): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    private fun stripComments(source: String): String {
        val noBlockComments = source.replace(Regex("""/\*[\s\S]*?\*/"""), "")
        return noBlockComments.replace(Regex("""(?m)//.*$"""), "")
    }

    private fun locateDir(vararg candidates: String): File =
        candidates.map(::File).firstOrNull { it.isDirectory }
            ?: error("Could not locate directory from ${File("").absolutePath}: ${candidates.toList()}")

    private fun locateFile(vararg candidates: String): File =
        candidates.map(::File).firstOrNull { it.isFile }
            ?: error("Could not locate file from ${File("").absolutePath}: ${candidates.toList()}")
}
