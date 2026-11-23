import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

internal const val NAMESPACE_BASE = "app.plansnap"
internal const val COMPILE_SDK = 36
internal const val TARGET_SDK = 36
internal const val MIN_SDK = 26
internal const val JDK_VERSION = 21

internal val JAVA_TARGET = JavaVersion.VERSION_17

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Derives the Android namespace from the Gradle path, e.g. `:core:render` becomes `app.plansnap.core.render`. */
internal fun Project.namespaceFor(): String =
    if (path == ":app") NAMESPACE_BASE else NAMESPACE_BASE + path.replace(':', '.')

/** Applies the shared Android settings (SDK levels, Java target). */
internal fun Project.configureAndroid(extension: CommonExtension, minSdkVersion: Int = MIN_SDK) {
    extension.apply {
        namespace = namespaceFor()
        compileSdk = COMPILE_SDK
        defaultConfig.minSdk = minSdkVersion
        compileOptions.sourceCompatibility = JAVA_TARGET
        compileOptions.targetCompatibility = JAVA_TARGET
    }
}

/** Applies the test JVM limits from docs/WORKFLOWS.md section 2. */
internal fun Project.configureTestLimits() {
    tasks.withType<Test>().configureEach {
        maxHeapSize = "1g"
        maxParallelForks = 2
    }
}
