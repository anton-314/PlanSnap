/** A project dependency declared in a Gradle configuration of the module under check. */
internal data class DeclaredProjectDependency(
    val configuration: String,
    val path: String,
)

/** An external dependency declared in a Gradle configuration of the module under check. */
internal data class DeclaredExternalDependency(
    val configuration: String,
    val group: String,
    val name: String,
)

/**
 * Pure rule set for the module architecture (docs/ARCHITECTURE.md section 2). It has no Gradle dependency so
 * it can be unit-tested; [CheckArchitectureTask] feeds it with the real build data.
 */
internal object ArchitectureRules {
    const val FIXTURES = ":testing:fixtures"

    private const val COMMON = ":core:common"
    private const val GEOMETRY = ":core:geometry"
    private const val MODEL = ":core:model"
    private const val DOMAIN = ":core:domain"
    private const val DOCUMENT = ":core:document"
    private const val DRAWING = ":core:drawing"
    private const val RENDER = ":core:render"
    private const val DATA = ":core:data"
    private const val EXPORT = ":core:export"
    private const val DESIGNSYSTEM = ":core:designsystem"

    /**
     * Allowed direct production dependencies per module, one entry per edge of the diagram in
     * ARCHITECTURE section 2. Rule 1 (arrows only, features never depend on features) and rule 3
     * (`:core:geometry` depends only on `:core:common`) follow from this table. A module that is not
     * listed is itself a violation, so new modules must be registered here deliberately.
     */
    val allowedDependencies: Map<String, Set<String>> =
        mapOf(
            ":app" to
                setOf(
                    ":feature:projects",
                    ":feature:editor",
                    ":feature:export",
                    ":feature:settings",
                    DATA,
                    EXPORT,
                ),
            ":feature:projects" to setOf(DOMAIN, DESIGNSYSTEM),
            ":feature:editor" to setOf(DOMAIN, RENDER, DRAWING, DESIGNSYSTEM),
            ":feature:export" to setOf(DOMAIN, EXPORT, DESIGNSYSTEM),
            ":feature:settings" to setOf(DOMAIN, DESIGNSYSTEM),
            DATA to setOf(DOMAIN, DOCUMENT),
            EXPORT to setOf(DOMAIN, DRAWING, RENDER, DOCUMENT),
            RENDER to setOf(DRAWING),
            DRAWING to setOf(DOMAIN),
            DOCUMENT to setOf(MODEL),
            DOMAIN to setOf(MODEL, GEOMETRY),
            MODEL to setOf(COMMON),
            GEOMETRY to setOf(COMMON),
            COMMON to emptySet(),
            DESIGNSYSTEM to emptySet(),
            // Fixtures may use the pure JVM core to build reference data, never Android or feature modules.
            FIXTURES to setOf(COMMON, GEOMETRY, MODEL, DOMAIN, DOCUMENT, DRAWING),
            // AGP adds `:app` to `:benchmark` (compileOnly, testedApks) because of `targetProjectPath`.
            ":benchmark" to setOf(":app"),
        )

    /** Group prefixes of artifacts that must not appear in a pure JVM module (rule 2). */
    private val androidGroupPrefixes = listOf("androidx", "com.android")

    private val androidImport = Regex("""^\s*import\s+(android|androidx)\.""")
    private val androidQualified = Regex("""(?<![\w.])(android|androidx)\.[a-z]\w*\.""")
    private val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    private val lineComment = Regex("""//[^\n]*""")
    private val stringLiteral = Regex(""""(?:\\.|[^"\\\n])*"""")

    /** True for configurations that only feed test source sets (`testImplementation`, `androidTestImplementation`). */
    fun isTestConfiguration(name: String): Boolean = name.contains("test", ignoreCase = true)

    /** Checks rules 1, 3 and 4 plus the Android-artifact ban of rule 2 and returns one message per violation. */
    fun checkDependencies(
        modulePath: String,
        isJvmModule: Boolean,
        projectDependencies: List<DeclaredProjectDependency>,
        externalDependencies: List<DeclaredExternalDependency>,
    ): List<String> {
        val allowed = allowedDependencies[modulePath]
        val unregistered =
            if (allowed == null) {
                listOf("$modulePath is not registered in ArchitectureRules.allowedDependencies")
            } else {
                emptyList()
            }
        val projectViolations = projectDependencies.mapNotNull { checkProjectDependency(modulePath, allowed, it) }
        val androidViolations =
            if (isJvmModule) {
                externalDependencies.mapNotNull {
                    checkExternalDependency(
                        modulePath,
                        it,
                    )
                }
            } else {
                emptyList()
            }
        return unregistered + projectViolations + androidViolations
    }

    private fun checkProjectDependency(
        modulePath: String,
        allowed: Set<String>?,
        dependency: DeclaredProjectDependency,
    ): String? {
        val target = dependency.path
        val isTest = isTestConfiguration(dependency.configuration)
        return when {
            target == modulePath || (target == FIXTURES && isTest) -> {
                null
            }

            target == FIXTURES -> {
                "$FIXTURES may only be a test dependency, but $modulePath uses it in '${dependency.configuration}'"
            }

            allowed != null && target !in allowed -> {
                "$modulePath must not depend on $target (declared in '${dependency.configuration}'); " +
                    "allowed: ${allowed.sorted().ifEmpty { listOf("nothing") }}"
            }

            else -> {
                null
            }
        }
    }

    private fun checkExternalDependency(
        modulePath: String,
        dependency: DeclaredExternalDependency,
    ): String? {
        val isAndroid = androidGroupPrefixes.any { dependency.group == it || dependency.group.startsWith("$it.") }
        return if (isAndroid) {
            "JVM module $modulePath must not depend on Android artifact " +
                "${dependency.group}:${dependency.name} (declared in '${dependency.configuration}')"
        } else {
            null
        }
    }

    /**
     * Checks rule 2 for sources: no `android.*` or `androidx.*` import and no fully qualified use in code.
     * [sources] maps a display name to the file content; comments and string literals are ignored.
     */
    fun checkSources(
        modulePath: String,
        sources: Map<String, String>,
    ): List<String> {
        val violations = mutableListOf<String>()
        for ((name, content) in sources) {
            val code =
                content
                    .replace(blockComment) { match -> "\n".repeat(match.value.count { it == '\n' }) }
                    .replace(lineComment, "")
                    .replace(stringLiteral, "\"\"")
            code.lineSequence().forEachIndexed { index, line ->
                if (androidImport.containsMatchIn(line) || androidQualified.containsMatchIn(line)) {
                    violations += "JVM module $modulePath must not use android/androidx APIs: " +
                        "$name:${index + 1}: ${line.trim()}"
                }
            }
        }
        return violations
    }
}
