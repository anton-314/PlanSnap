import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Root project: provides the lifecycle tasks (`check`, `build`) and applies the same Spotless and detekt rules
 * to the build logic itself. The unit tests of build-logic run as part of the root `check`.
 */
class RootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            applyRoot()
        }
    }

    private fun Project.applyRoot() {
        pluginManager.apply("base")
        pluginManager.apply("com.diffplug.spotless")
        pluginManager.apply("io.gitlab.arturbosch.detekt")

        val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion
        val editorConfig = file("config/spotless/.editorconfig")
        extensions.configure<SpotlessExtension> {
            kotlin {
                target("build-logic/*/src/**/*.kt")
                ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
            }
            kotlinGradle {
                target("*.gradle.kts", "build-logic/*.gradle.kts", "build-logic/*/*.gradle.kts")
                ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
            }
        }

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            parallel = true
            config.setFrom(file("config/detekt/detekt.yml"))
            source.setFrom(
                "build-logic/convention/src/main/kotlin",
                "build-logic/convention/src/test/kotlin",
            )
        }
        tasks.withType<Detekt>().configureEach { jvmTarget = "17" }

        tasks.named("check") {
            dependsOn(gradle.includedBuild("build-logic").task(":convention:test"))
        }
    }
}
