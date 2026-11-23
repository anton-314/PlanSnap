import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android test module (`com.android.test`) that targets `:app`, used by `:benchmark`. */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.test")

        extensions.configure<TestExtension> {
            configureAndroid(this, minSdkVersion = 28)
            defaultConfig.targetSdk = TARGET_SDK
            targetProjectPath = ":app"
        }
    }
}
