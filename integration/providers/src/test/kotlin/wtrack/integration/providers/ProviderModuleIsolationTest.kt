package wtrack.integration.providers

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class ProviderModuleIsolationTest {
    @Test
    fun noPrematureProviderParsersRegisteredInFoundation() {
        assertThat(ProviderParsers.registered()).isEmpty()
    }

    @Test
    fun providerSourcesContainNoAndroidFrameworkImports() {
        val mainDir = locateProvidersMainSourceDir()
        val kotlinFiles = mainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertThat(kotlinFiles).isNotEmpty()

        val forbiddenPrefixes = listOf(
            "import android.",
            "import androidx.",
            "import dagger.",
            "import wtrack.app.",
        )

        kotlinFiles.forEach { file ->
            file.readLines().forEach { line ->
                val trimmed = line.trim()
                forbiddenPrefixes.forEach { prefix ->
                    assertThat(trimmed.startsWith(prefix)).isFalse()
                }
            }
        }
    }

    private fun locateProvidersMainSourceDir(): File {
        val candidates = listOf(
            File("src/main/kotlin"),
            File("integration/providers/src/main/kotlin"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate integration/providers main kotlin directory from ${File("").absolutePath}")
    }
}
