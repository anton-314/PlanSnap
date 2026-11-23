import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.register

/**
 * Registers `checkArchitecture` for a module and hooks it into `check`. Applied by the other convention
 * plugins after the Kotlin or Android plugin, because it needs to know whether the module is pure JVM.
 */
class ArchitectureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            val moduleGradlePath = path
            val isJvm = pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")
            val check =
                tasks.register<CheckArchitectureTask>("checkArchitecture") {
                    group = "verification"
                    description = "Fails on forbidden module dependencies or android imports (ARCHITECTURE 2)."
                    modulePath.set(moduleGradlePath)
                    jvmModule.set(isJvm)
                    // Evaluated lazily: build scripts declare their dependencies after the plugin was applied.
                    projectDependencies.set(
                        provider {
                            configurations
                                .flatMap { configuration ->
                                    configuration.dependencies
                                        .withType(ProjectDependency::class.java)
                                        .map { "${configuration.name}|${it.path}" }
                                }.distinct()
                        },
                    )
                    externalDependencies.set(
                        provider {
                            configurations
                                .flatMap { configuration ->
                                    configuration.dependencies
                                        .withType(ExternalModuleDependency::class.java)
                                        .map { "${configuration.name}|${it.group.orEmpty()}|${it.name}" }
                                }.distinct()
                        },
                    )
                    sources.from(fileTree("src") { include("**/*.kt", "**/*.java") })
                    marker.set(layout.buildDirectory.file("architecture/checkArchitecture.ok"))
                }
            tasks.matching { it.name == "check" }.configureEach { dependsOn(check) }
        }
}
