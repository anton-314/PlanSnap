package app.plansnap.l10n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Checks the localization of the whole project (REQ-UX-008): every string resource exists in the default and in German,
 * plurals are complete and placeholders agree per key. The project is found by walking up from the working directory
 * to `settings.gradle.kts`, and the test fails when no resource file is found instead of passing silently.
 */
class LocalizationParityTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `given the project resources when compared then every key exists in every language with equal placeholders`() {
        val root = projectRoot()
        val groups = ResourceParity.scan(root)

        assertTrue("No string resource files found below $root", groups.isNotEmpty())
        assertTrue("Expected the app resources in the scan", groups.any { it.id == "app/strings.xml" })
        assertTrue("Expected the render resources in the scan", groups.any { it.id == "core/render/strings.xml" })

        val problems = groups.flatMap { ResourceParity.check(it) }
        assertEquals("Localization problems:\n" + problems.joinToString("\n"), emptyList<String>(), problems)
    }

    @Test
    fun `given a key missing in German when checked then the missing key is reported`() {
        writeModule(
            values = """<string name="shared">Shared</string><string name="only_default">Only default</string>""",
            de = """<string name="shared">Gemeinsam</string>""",
        )

        val problems = ResourceParity.check(onlyGroup())

        assertEquals(listOf("feature/x/strings.xml [de]: missing string only_default"), problems)
    }

    @Test
    fun `given a German placeholder that differs from the default when checked then it is reported`() {
        writeModule(
            values = """<string name="name">Hello %1${'$'}s, you have %2${'$'}d rooms</string>""",
            de = """<string name="name">Hallo %1${'$'}s</string>""",
        )

        val problems = ResourceParity.check(onlyGroup())

        assertTrue(problems.any { it.contains("string name has placeholders") && it.contains("[de]") })
    }

    @Test
    fun `given a plural without the other quantity in German when checked then it is reported`() {
        writeModule(
            values = """<plurals name="rooms">
                <item quantity="one">%d room</item>
                <item quantity="other">%d rooms</item>
            </plurals>""",
            de = """<plurals name="rooms"><item quantity="one">%d Raum</item></plurals>""",
        )

        val problems = ResourceParity.check(onlyGroup())

        assertTrue(problems.any { it.contains("plurals rooms lacks") && it.contains("[de]") })
    }

    @Test
    fun `given a folder without a German variant when checked then it is reported`() {
        writeModule(values = """<string name="only">Only</string>""", de = null)

        val problems = ResourceParity.check(onlyGroup())

        assertEquals(listOf("feature/x/strings.xml: no values-de/ folder"), problems)
    }

    @Test
    fun `given a root without resource files when scanned then nothing is found`() {
        assertTrue(ResourceParity.scan(tmp.root).isEmpty())
    }

    private fun writeModule(
        values: String,
        de: String?,
    ) {
        val res = File(tmp.root, "feature/x/src/main/res").apply { mkdirs() }
        File(res, "values").apply { mkdirs() }.resolve("strings.xml").writeText(resources(values))
        if (de != null) {
            File(res, "values-de").apply { mkdirs() }.resolve("strings.xml").writeText(resources(de))
        }
    }

    private fun onlyGroup(): ResourceGroup {
        val groups = ResourceParity.scan(tmp.root)
        assertEquals(1, groups.size)
        return groups.single()
    }

    private fun resources(body: String): String =
        """<?xml version="1.0" encoding="utf-8"?><resources>$body</resources>"""

    private fun projectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("settings.gradle.kts not found above $start")
    }
}
