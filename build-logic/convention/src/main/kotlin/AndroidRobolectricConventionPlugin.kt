import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Makes Robolectric available for the JVM unit tests of an Android module: the Robolectric dependency and merged
 * Android resources plus manifest in the unit test classpath. Apply after an Android plugin.
 */
class AndroidRobolectricConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            extensions
                .getByType<CommonExtension>()
                .testOptions.unitTests.isIncludeAndroidResources = true

            dependencies {
                add("testImplementation", libs.findLibrary("robolectric").get())
            }
        }
}
