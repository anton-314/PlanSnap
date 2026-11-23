import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Runs `checkArchitecture` in a throw-away multi-module build that uses the real convention plugins. */
class ArchitectureCheckFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    private fun write(
        path: String,
        content: String,
    ) {
        File(projectDir, path).apply {
            parentFile.mkdirs()
            writeText(content)
        }
    }

    private fun setUp(
        geometryBuild: String = "",
        commonSource: String? = null,
    ) {
        val catalog = File(System.getProperty("plansnap.catalog")).absolutePath
        write(
            "settings.gradle.kts",
            """
            pluginManagement {
                repositories { gradlePluginPortal(); mavenCentral(); google() }
            }
            dependencyResolutionManagement {
                repositories { mavenCentral(); google() }
                versionCatalogs { create("libs") { from(files("$catalog")) } }
            }
            include(":core:common", ":core:geometry", ":core:model")
            """.trimIndent(),
        )
        write("gradle.properties", "org.gradle.jvmargs=-Xmx512m -XX:MaxMetaspaceSize=512m\n")
        write("core/common/build.gradle.kts", "plugins { id(\"plansnap.jvm.library\") }\n")
        write(
            "core/model/build.gradle.kts",
            "plugins { id(\"plansnap.jvm.library\") }\ndependencies { api(project(\":core:common\")) }\n",
        )
        write(
            "core/geometry/build.gradle.kts",
            "plugins { id(\"plansnap.jvm.library\") }\n" +
                "dependencies { api(project(\":core:common\")) }\n$geometryBuild\n",
        )
        if (commonSource != null) write("core/common/src/main/kotlin/Source.kt", commonSource)
    }

    private fun runner(vararg arguments: String) =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*arguments, "--stacktrace")

    @Test
    fun `given allowed dependencies and clean sources when checked then the build succeeds`() {
        setUp(commonSource = "package a\n\nfun ok() = 1\n")
        val result = runner(":core:geometry:checkArchitecture", ":core:common:checkArchitecture").build()
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `given geometry depends on model when checked then the build fails naming the violation`() {
        setUp(geometryBuild = "dependencies { implementation(project(\":core:model\")) }")
        val result = runner(":core:geometry:checkArchitecture").buildAndFail()
        assertTrue(result.output.contains(":core:geometry must not depend on :core:model"), result.output)
    }

    @Test
    fun `given an android import in a JVM module when checked then the build fails naming file and line`() {
        setUp(commonSource = "package a\n\nimport android.util.Log\n\nfun bad() = Log::class\n")
        val result = runner(":core:common:checkArchitecture").buildAndFail()
        assertTrue(result.output.contains("Source.kt:3: import android.util.Log"), result.output)
    }

    @Test
    fun `given a violation when check runs then check is not successful`() {
        setUp(geometryBuild = "dependencies { implementation(project(\":core:model\")) }")
        val result = runner(":core:geometry:check", "-x", "test", "-x", "spotlessCheck", "-x", "detekt").buildAndFail()
        assertEquals(true, result.output.contains("Architecture violations in :core:geometry"), result.output)
    }
}
