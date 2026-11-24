import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Pure JVM library: Kotlin/JVM without any Android plugin, JUnit 6 on the JUnit Platform. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(JDK_VERSION)
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                    allWarningsAsErrors.set(true)
                }
            }
            extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
                sourceCompatibility = JAVA_TARGET
                targetCompatibility = JAVA_TARGET
            }

            pluginManager.apply(ArchitectureConventionPlugin::class.java)
            pluginManager.apply(QualityConventionPlugin::class.java)

            tasks.withType<Test>().configureEach { useJUnitPlatform() }
            configureTestLimits()

            dependencies {
                add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
                add("testImplementation", libs.findLibrary("junit-jupiter").get())
                add("testImplementation", libs.findLibrary("kotest-property").get())
                add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
            }
        }
}
