package com.shelfpulse.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class ManifestThemeTest {

    private fun findProjectFile(path: String): File {
        val candidates = listOf(
            File(path),
            File("app", path.removePrefix("app/")),
            File(path.removePrefix("app/"))
        )
        for (c in candidates) {
            if (c.exists()) return c
        }
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            val c1 = File(dir, path)
            if (c1.exists()) return c1
            val c2 = File(dir, path.removePrefix("app/"))
            if (c2.exists()) return c2
            dir = dir.parentFile
        }
        throw AssertionError("Could not locate file: $path from ${File(".").canonicalPath}")
    }

    @Test
    fun manifest_referencesShelfPulseTheme() {
        val manifestFile = findProjectFile("app/src/main/AndroidManifest.xml")
        val manifestContent = manifestFile.readText()

        assertThat(manifestContent).contains("android:theme=\"@style/Theme.ShelfPulseAI\"")
        assertThat(manifestContent).doesNotContain("android:theme=\"@android:style/Theme.Material.NoActionBar\"")
    }

    @Test
    fun themesXml_inheritsFromAppCompatAndDefinesDarkWindow() {
        val themesFile = findProjectFile("app/src/main/res/values/themes.xml")
        val themesContent = themesFile.readText()

        assertThat(themesContent).contains("<style name=\"Theme.ShelfPulseAI\"")
        assertThat(themesContent).contains("parent=\"Theme.AppCompat.DayNight.NoActionBar\"")
        assertThat(themesContent).contains("android:windowBackground")
        assertThat(themesContent).contains("#0F172A")
    }
}
