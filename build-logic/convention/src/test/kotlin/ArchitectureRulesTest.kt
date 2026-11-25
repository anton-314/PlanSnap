import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArchitectureRulesTest {
    private fun deps(
        module: String,
        vararg declared: Pair<String, String>,
        jvm: Boolean = true,
    ) = ArchitectureRules.checkDependencies(
        modulePath = module,
        isJvmModule = jvm,
        projectDependencies =
            declared.map { (configuration, path) ->
                DeclaredProjectDependency(configuration, path)
            },
        externalDependencies = emptyList(),
    )

    @Test
    fun `given the dependencies of the architecture diagram when checked then there is no violation`() {
        val violations =
            ArchitectureRules.allowedDependencies.flatMap { (module, allowed) ->
                deps(module, *allowed.map { "api" to it }.toTypedArray())
            }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `given geometry depends on model when checked then the violation names both modules`() {
        val violations = deps(":core:geometry", "api" to ":core:model")
        assertEquals(1, violations.size)
        assertTrue(":core:geometry must not depend on :core:model" in violations.single())
    }

    @Test
    fun `given one feature depends on another feature when checked then it is a violation`() {
        val violations = deps(":feature:editor", "implementation" to ":feature:settings", jvm = false)
        assertEquals(1, violations.size)
    }

    @Test
    fun `given a core module depends upwards when checked then it is a violation`() {
        assertEquals(1, deps(":core:model", "api" to ":core:domain").size)
        assertEquals(1, deps(":core:domain", "api" to ":core:data").size)
        assertEquals(1, deps(":core:render", "api" to ":feature:editor", jvm = false).size)
    }

    @Test
    fun `given the app depends on the design system when checked then there is no violation`() {
        assertEquals(emptyList<String>(), deps(":app", "implementation" to ":core:designsystem", jvm = false))
    }

    @Test
    fun `given the app depends on a core module outside the diagram when checked then it is a violation`() {
        assertEquals(1, deps(":app", "implementation" to ":core:drawing", jvm = false).size)
        assertEquals(1, deps(":app", "implementation" to ":core:domain", jvm = false).size)
    }

    @Test
    fun `given a feature depends on core data when checked then it is a violation`() {
        assertEquals(1, deps(":feature:projects", "implementation" to ":core:data", jvm = false).size)
    }

    @Test
    fun `given fixtures as a production dependency when checked then it is a violation`() {
        val violations = deps(":core:model", "implementation" to ":testing:fixtures")
        assertEquals(1, violations.size)
        assertTrue("may only be a test dependency" in violations.single())
    }

    @Test
    fun `given fixtures as a test dependency when checked then there is no violation`() {
        assertEquals(emptyList<String>(), deps(":core:geometry", "testImplementation" to ":testing:fixtures"))
        assertEquals(
            emptyList<String>(),
            deps(":feature:editor", "androidTestImplementation" to ":testing:fixtures", jvm = false),
        )
    }

    @Test
    fun `given fixtures depend on an Android module when checked then it is a violation`() {
        assertEquals(1, deps(":testing:fixtures", "api" to ":core:render").size)
    }

    @Test
    fun `given an unregistered module when checked then it is a violation`() {
        val violations = deps(":core:newmodule")
        assertEquals(1, violations.size)
        assertTrue("not registered" in violations.single())
    }

    @Test
    fun `given an androidx artifact in a JVM module when checked then it is a violation`() {
        val external = listOf(DeclaredExternalDependency("implementation", "androidx.core", "core-ktx"))
        val violations = ArchitectureRules.checkDependencies(":core:common", true, emptyList(), external)
        assertEquals(1, violations.size)
        assertTrue(ArchitectureRules.checkDependencies(":core:data", false, emptyList(), external).isEmpty())
    }

    @Test
    fun `given an android import in a JVM source when checked then it is a violation with file and line`() {
        val source = "package a\n\nimport android.util.Log\n\nclass A\n"
        val violations = ArchitectureRules.checkSources(":core:common", mapOf("main/kotlin/A.kt" to source))
        assertEquals(1, violations.size)
        assertTrue("main/kotlin/A.kt:3" in violations.single())
    }

    @Test
    fun `given an androidx import or fully qualified use when checked then it is a violation`() {
        val imports =
            ArchitectureRules.checkSources(
                ":core:common",
                mapOf("A.kt" to "import androidx.annotation.Keep\n"),
            )
        val qualified =
            ArchitectureRules.checkSources(
                ":core:common",
                mapOf("B.kt" to "val x: android.graphics.Rect? = null\n"),
            )
        assertEquals(1, imports.size)
        assertEquals(1, qualified.size)
    }

    @Test
    fun `given android only in comments strings or similar package names when checked then there is no violation`() {
        val source =
            """
            // import android.util.Log
            /* import androidx.core.app.Foo
               android.view.View */
            /** See [android.graphics.Canvas]. */
            import kotlinx.coroutines.android.Foo
            val text = "android.permission.INTERNET"
            val other = com.example.android.util.Thing
            """.trimIndent()
        assertEquals(emptyList<String>(), ArchitectureRules.checkSources(":core:common", mapOf("A.kt" to source)))
    }

    @Test
    fun `given an import after a block comment when checked then the line number stays correct`() {
        val source = "/*\n one\n two\n*/\nimport android.os.Build\n"
        val violations = ArchitectureRules.checkSources(":core:common", mapOf("A.kt" to source))
        assertTrue("A.kt:5" in violations.single())
    }

    @Test
    fun `given the coverage targets of docs TESTING when quotas are looked up then they match`() {
        assertEquals(CoverageQuota(lineMin = 90, branchMin = 85), coverageQuota(":core:geometry"))
        listOf(":core:common", ":core:model", ":core:domain", ":core:document", ":core:drawing", ":core:data")
            .forEach { assertEquals(80, coverageQuota(it)?.lineMin, it) }
        listOf(":feature:projects", ":feature:editor", ":feature:export", ":feature:settings")
            .forEach { assertEquals(80, coverageQuota(it)?.lineMin, it) }
        listOf(":app", ":core:render", ":core:export", ":core:designsystem", ":testing:fixtures")
            .forEach { assertEquals(null, coverageQuota(it), it) }
    }
}
