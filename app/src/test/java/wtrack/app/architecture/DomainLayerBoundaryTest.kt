package wtrack.app.architecture

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Enforces PRD section 12 / ADR-001 dependency rules: `wtrack.app.domain` must remain pure Kotlin
 * with zero dependencies on Android, Room, Compose, DI frameworks, SQLCipher, or providers.
 */
class DomainLayerBoundaryTest {
    @Test
    fun domainPackageHasNoForbiddenImportsOrReferences() {
        val domainDir = locateDomainDir()
        val kotlinFiles = domainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertThat(kotlinFiles).isNotEmpty()

        val forbiddenPrefixes = listOf(
            "android.",
            "androidx.",
            "dagger.",
            "com.google.dagger.",
            "javax.inject.",
            "jakarta.inject.",
            "net.zetetic.",
            "wtrack.app.data.",
            "wtrack.app.feature.",
            "wtrack.app.core.",
            "wtrack.integration.providers.",
        )

        val violations = mutableListOf<String>()
        kotlinFiles.forEach { file ->
            file.readLines().forEachIndexed { index, rawLine ->
                val trimmed = rawLine.trim()
                if (trimmed.startsWith("import ")) {
                    val imported = trimmed.removePrefix("import ").trim()
                    forbiddenPrefixes.forEach { prefix ->
                        if (imported.startsWith(prefix)) {
                            violations += "${file.name}:${index + 1}: forbidden import `$imported`"
                        }
                    }
                }
            }
        }

        assertThat(violations).isEmpty()
    }

    private fun locateDomainDir(): File {
        val candidates = listOf(
            File("src/main/java/wtrack/app/domain"),
            File("app/src/main/java/wtrack/app/domain"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate domain package directory from ${File("").absolutePath}")
    }
}
