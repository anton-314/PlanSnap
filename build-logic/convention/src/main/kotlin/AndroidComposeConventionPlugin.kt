import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Adds Jetpack Compose (compiler plugin and BOM) and the Compose screenshot test setup (Compose UI test on top of
 * Robolectric and Roborazzi) to an Android module. Apply after an Android plugin.
 *
 * The Robolectric and Roborazzi parts (baselines in `src/test/screenshots`) come from
 * [AndroidRoborazziConventionPlugin], which Compose-free modules can use on its own.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply(AndroidRoborazziConventionPlugin::class.java)

            dependencies {
                val bom = platform(libs.findLibrary("androidx-compose-bom").get())
                add("implementation", bom)
                add("implementation", libs.findLibrary("androidx-compose-ui").get())
                add("implementation", libs.findLibrary("androidx-compose-material3").get())

                add("testImplementation", bom)
                add("testImplementation", libs.findLibrary("roborazzi-compose").get())
                add("testImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
                add("debugImplementation", bom)
                add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
            }
        }
}
