import com.android.build.api.dsl.CommonExtension
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/**
 * Adds Jetpack Compose (compiler plugin and BOM) and the screenshot test setup (Robolectric, Compose UI test and
 * Roborazzi) to an Android module. Apply after an Android plugin.
 *
 * Baselines live in `src/test/screenshots` of the module (tests pass a plain file name to `captureRoboImage`), so
 * they are versioned with the source. Roborazzi `verifyRoborazziDebug` compares against them,
 * `recordRoborazziDebug` rewrites them. Modules without
 * screenshot tests have no baselines and verify nothing.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("io.github.takahirom.roborazzi")

            // Robolectric needs merged resources and the manifest in the unit test classpath.
            extensions
                .getByType<CommonExtension>()
                .testOptions.unitTests.isIncludeAndroidResources = true

            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
            }
            // Resolve `captureRoboImage("name.png")` against the baseline directory above, not the working dir.
            tasks.withType<Test>().configureEach {
                systemProperty(
                    "roborazzi.record.filePathStrategy",
                    "relativePathFromRoborazziContextOutputDirectory",
                )
            }

            dependencies {
                val bom = platform(libs.findLibrary("androidx-compose-bom").get())
                add("implementation", bom)
                add("implementation", libs.findLibrary("androidx-compose-ui").get())
                add("implementation", libs.findLibrary("androidx-compose-material3").get())

                add("testImplementation", bom)
                add("testImplementation", libs.findLibrary("robolectric").get())
                add("testImplementation", libs.findLibrary("roborazzi").get())
                add("testImplementation", libs.findLibrary("roborazzi-compose").get())
                add("testImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
                add("debugImplementation", bom)
                add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
            }
        }
}
