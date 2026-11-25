import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Adds Hilt dependency injection with KSP code generation to an Android module. Apply after an Android plugin.
 *
 * Also wires the Hilt test artifacts: `hilt-android-testing` with its KSP processor for JVM unit tests and
 * instrumented tests, so a module can write `@HiltAndroidTest` graph tests without further setup.
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            dependencies {
                add("implementation", libs.findLibrary("hilt-android").get())
                add("ksp", libs.findLibrary("hilt-compiler").get())
                add("testImplementation", libs.findLibrary("hilt-android-testing").get())
                add("kspTest", libs.findLibrary("hilt-compiler").get())
                add("androidTestImplementation", libs.findLibrary("hilt-android-testing").get())
                add("kspAndroidTest", libs.findLibrary("hilt-compiler").get())
            }
        }
}
