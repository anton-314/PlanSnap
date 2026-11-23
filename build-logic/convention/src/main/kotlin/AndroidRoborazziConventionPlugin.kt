import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Adds Robolectric (see [AndroidRobolectricConventionPlugin]) and the Roborazzi screenshot setup that needs no
 * Compose, for example `captureRoboImage` on a `Bitmap` or `View`. Apply after an Android plugin.
 *
 * Baselines live in `src/test/screenshots` of the module (tests pass a plain file name to `captureRoboImage`), so
 * they are versioned with the source. Roborazzi `verifyRoborazziDebug` compares against them,
 * `recordRoborazziDebug` rewrites them. Modules without screenshot tests have no baselines and verify nothing.
 */
class AndroidRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")
            pluginManager.apply(AndroidRobolectricConventionPlugin::class.java)

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
                add("testImplementation", libs.findLibrary("roborazzi").get())
            }
        }
}
