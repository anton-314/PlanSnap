import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when the module breaks the architecture rules (docs/ARCHITECTURE.md section 2).
 * Inputs are plain values so the task is compatible with the configuration cache.
 */
abstract class CheckArchitectureTask : DefaultTask() {
    /** Gradle path of the module, e.g. `:core:geometry`. */
    @get:Input
    abstract val modulePath: Property<String>

    /** True when the module is a pure JVM module and must not touch Android APIs. */
    @get:Input
    abstract val jvmModule: Property<Boolean>

    /** Project dependencies encoded as `configuration|path`. */
    @get:Input
    abstract val projectDependencies: ListProperty<String>

    /** External dependencies encoded as `configuration|group|name`. */
    @get:Input
    abstract val externalDependencies: ListProperty<String>

    /** Kotlin and Java sources of the module. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Marker file that makes the task up to date when nothing changed. */
    @get:OutputFile
    abstract val marker: RegularFileProperty

    /** Runs all rules and throws a [GradleException] listing every violation. */
    @TaskAction
    fun check() {
        val path = modulePath.get()
        val projectDeps =
            projectDependencies.get().map {
                val (configuration, target) = it.split('|', limit = 2)
                DeclaredProjectDependency(configuration, target)
            }
        val externalDeps =
            externalDependencies.get().map {
                val (configuration, group, name) = it.split('|', limit = 3)
                DeclaredExternalDependency(configuration, group, name)
            }
        val violations =
            ArchitectureRules.checkDependencies(path, jvmModule.get(), projectDeps, externalDeps) +
                if (jvmModule.get()) {
                    ArchitectureRules.checkSources(
                        path,
                        sources.files.filter { it.isFile }.associate { file ->
                            file.invariantSeparatorsPath.substringAfter("/src/", file.name) to file.readText()
                        },
                    )
                } else {
                    emptyList()
                }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Architecture violations in $path:\n" + violations.joinToString("\n") { " - $it" },
            )
        }
        marker.get().asFile.apply {
            parentFile.mkdirs()
            writeText("ok\n")
        }
    }
}
