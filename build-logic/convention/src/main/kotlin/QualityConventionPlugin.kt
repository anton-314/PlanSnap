import com.diffplug.gradle.spotless.SpotlessExtension
import info.solidsoft.gradle.pitest.PitestPluginExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Static analysis and coverage for one module: Spotless (ktlint), detekt, Kover and, for `:core:geometry`,
 * Pitest. Applied by the other convention plugins. Rule deviations live in `config/` and are justified there.
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            configureSpotless()
            configureDetekt()
            configureKover()
            if (path == GEOMETRY) configurePitest()
        }

    private fun Project.configureSpotless() {
        pluginManager.apply("com.diffplug.spotless")
        val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion
        val editorConfig = rootProject.file(EDITORCONFIG)
        extensions.configure<SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
            }
            kotlinGradle {
                target("*.gradle.kts")
                ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
            }
        }
    }

    private fun Project.configureDetekt() {
        pluginManager.apply("io.gitlab.arturbosch.detekt")
        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            parallel = true
            config.setFrom(rootProject.file(DETEKT_CONFIG))
        }
        tasks.withType<Detekt>().configureEach { jvmTarget = "17" }
    }

    private fun Project.configureKover() {
        pluginManager.apply("org.jetbrains.kotlinx.kover")
        val quota = coverageQuota(path) ?: return
        // An empty module has no instructions to cover, so the rule only exists once matching sources do.
        val hasSources = fileTree("src/main") { include(quota.sourcePattern) }.files.isNotEmpty()
        if (!hasSources) return
        extensions.configure<KoverProjectExtension> {
            reports {
                filters {
                    excludes {
                        // Generated code is not authored, so measuring it would only distort the quota.
                        classes(
                            "*BuildConfig",
                            "*.R",
                            "*.R\$*",
                            "*_Impl",
                            "*_Impl\$*",
                            "*_Factory",
                            "*Hilt_*",
                            "*_HiltModules*",
                        )
                    }
                    if (quota.classPatterns.isNotEmpty()) {
                        includes { classes(quota.classPatterns) }
                    }
                }
                verify {
                    rule("line coverage of $path") {
                        bound {
                            minValue.set(quota.lineMin)
                            coverageUnits.set(CoverageUnit.LINE)
                            aggregationForGroup.set(AggregationType.COVERED_PERCENTAGE)
                        }
                    }
                    quota.branchMin?.let { branchMin ->
                        rule("branch coverage of $path") {
                            bound {
                                minValue.set(branchMin)
                                coverageUnits.set(CoverageUnit.BRANCH)
                                aggregationForGroup.set(AggregationType.COVERED_PERCENTAGE)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Project.configurePitest() {
        pluginManager.apply("info.solidsoft.pitest")
        extensions.configure<PitestPluginExtension> {
            pitestVersion.set(libs.findVersion("pitest").get().requiredVersion)
            junit5PluginVersion.set(libs.findVersion("pitestJunit5").get().requiredVersion)
            targetClasses.set(listOf("$NAMESPACE_BASE.core.geometry.*"))
            // docs/TESTING.md section 3: at least 70 percent killed mutants.
            mutationThreshold.set(PITEST_MUTATION_THRESHOLD)
            // The module may still be empty; once it has mutable code the threshold above applies.
            failWhenNoMutations.set(false)
            threads.set(2)
            timestampedReports.set(false)
            outputFormats.set(listOf("HTML", "XML"))
        }
    }

    private companion object {
        const val GEOMETRY = ":core:geometry"
        const val EDITORCONFIG = "config/spotless/.editorconfig"
        const val DETEKT_CONFIG = "config/detekt/detekt.yml"
        const val PITEST_MUTATION_THRESHOLD = 70
    }
}

/** Coverage quota of a module according to docs/TESTING.md section 4. */
internal data class CoverageQuota(
    val lineMin: Int,
    val branchMin: Int? = null,
    val sourcePattern: String = "**/*.kt",
    val classPatterns: List<String> = emptyList(),
)

private val FEATURE_MODULES = setOf(":feature:projects", ":feature:editor", ":feature:export", ":feature:settings")
private val LINE_ONLY_CORE_MODULES =
    setOf(
        ":core:common",
        ":core:model",
        ":core:domain",
        ":core:document",
        ":core:drawing",
        ":core:data",
    )

/** Returns the quota of [path], or null for modules without one (Composables, rendering, export, app). */
internal fun coverageQuota(path: String): CoverageQuota? =
    when (path) {
        ":core:geometry" -> {
            CoverageQuota(lineMin = 90, branchMin = 85)
        }

        in LINE_ONLY_CORE_MODULES -> {
            CoverageQuota(lineMin = 80)
        }

        in FEATURE_MODULES -> {
            CoverageQuota(
                lineMin = 80,
                sourcePattern = "**/*ViewModel.kt",
                classPatterns = listOf("*ViewModel", "*ViewModel\$*"),
            )
        }

        else -> {
            null
        }
    }
