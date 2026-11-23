import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Android library module. Kotlin support is built into AGP 9, so no Kotlin Android plugin is applied. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            testOptions.targetSdk = TARGET_SDK
            lint.targetSdk = TARGET_SDK
        }
        configureTestLimits()

        dependencies {
            add("testImplementation", libs.findLibrary("junit4").get())
        }
    }
}
