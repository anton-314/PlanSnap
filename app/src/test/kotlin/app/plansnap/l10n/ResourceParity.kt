package app.plansnap.l10n

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * One language variant of a resource file: the default `values` folder or a `values-xx` folder.
 *
 * @property language [ResourceParity.DEFAULT] for the `values` folder, otherwise the qualifier, for example `de`.
 * @property strings plain `string` entries by name.
 * @property plurals `plurals` entries by name, each mapping a quantity (`one`, `other`, …) to its text.
 */
internal data class ResourceVariant(
    val language: String,
    val strings: Map<String, String>,
    val plurals: Map<String, Map<String, String>>,
)

/**
 * All variants of one resource file name within one module, identified by [id] such as `feature/editor/strings.xml`.
 * [variants] lists the default variant first.
 */
internal data class ResourceGroup(
    val id: String,
    val variants: List<ResourceVariant>,
)

/**
 * Compares the string resources of all modules with each other: every language has the keys of the default, the same
 * plural quantities and the same placeholders per key. The check reads the XML files directly, so it needs no Android
 * runtime, and it reports problems as text instead of failing on the first one.
 */
internal object ResourceParity {
    /** Language of the `values` folder, which is the default resource set of the project. */
    const val DEFAULT = "default"

    /** Second language of the project; every resource group must have it. */
    const val SECOND_LANGUAGE = "de"

    private val valuesFolder = Regex("values(-[a-z]{2}(-r[A-Z]{2})?)?")
    private val placeholder = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z]")
    private val requiredQuantities = setOf("one", "other")

    /** Reads every `src/main/res/values*` resource file below [root]. Build output is skipped. */
    fun scan(root: File): List<ResourceGroup> =
        root
            .walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            .filter { isMainResourceFile(root, it) }
            .groupBy { groupId(root, it) }
            .map { (id, files) ->
                val variants = files.map { parse(it) }.sortedBy { if (it.language == DEFAULT) 0 else 1 }
                ResourceGroup(id, variants)
            }.filter { group -> group.variants.any { it.strings.isNotEmpty() || it.plurals.isNotEmpty() } }
            .sortedBy { it.id }

    /** Returns one text per problem in [group]; an empty list means the group is complete and consistent. */
    fun check(group: ResourceGroup): List<String> {
        val problems = mutableListOf<String>()
        val default = group.variants.firstOrNull { it.language == DEFAULT }
        if (default == null) {
            return listOf("${group.id}: no default values/ folder")
        }
        if (group.variants.none { it.language == SECOND_LANGUAGE }) {
            problems += "${group.id}: no values-$SECOND_LANGUAGE/ folder"
        }
        for (variant in group.variants.filter { it.language != DEFAULT }) {
            (default.strings.keys - variant.strings.keys).forEach {
                problems +=
                    "${group.id} [${variant.language}]: missing string $it"
            }
            (default.plurals.keys - variant.plurals.keys).forEach {
                problems +=
                    "${group.id} [${variant.language}]: missing plurals $it"
            }
            (variant.strings.keys - default.strings.keys).forEach {
                problems +=
                    "${group.id} [${variant.language}]: string $it has no default"
            }
            (variant.plurals.keys - default.plurals.keys).forEach {
                problems +=
                    "${group.id} [${variant.language}]: plurals $it has no default"
            }
        }
        for (variant in group.variants) {
            checkStrings(group.id, variant, default, problems)
            checkPlurals(group.id, variant, default, problems)
        }
        return problems
    }

    private fun checkStrings(
        id: String,
        variant: ResourceVariant,
        default: ResourceVariant,
        problems: MutableList<String>,
    ) {
        for ((name, text) in variant.strings) {
            if (text.isBlank()) problems += "$id [${variant.language}]: string $name is empty"
            val expected = default.strings[name] ?: continue
            if (placeholders(text) != placeholders(expected)) {
                problems +=
                    "$id [${variant.language}]: string $name has placeholders ${placeholders(
                        text,
                    )}, default has ${placeholders(expected)}"
            }
        }
    }

    private fun checkPlurals(
        id: String,
        variant: ResourceVariant,
        default: ResourceVariant,
        problems: MutableList<String>,
    ) {
        for ((name, items) in variant.plurals) {
            val missingQuantities = requiredQuantities - items.keys
            if (missingQuantities.isNotEmpty()) {
                problems +=
                    "$id [${variant.language}]: plurals $name lacks $missingQuantities"
            }
            items.forEach { (quantity, text) ->
                if (text.isBlank()) problems += "$id [${variant.language}]: plurals $name/$quantity is empty"
            }
            val expected = default.plurals[name]?.get("other") ?: continue
            for ((quantity, text) in items) {
                if (placeholders(text) != placeholders(expected)) {
                    problems +=
                        "$id [${variant.language}]: plurals $name/$quantity has placeholders ${placeholders(
                            text,
                        )}, default has ${placeholders(expected)}"
                }
            }
        }
    }

    /** Sorted format specifiers of [text], for example `[%1$s, %2$d]`. Equal lists mean equal placeholders. */
    fun placeholders(text: String): List<String> =
        placeholder
            .findAll(text)
            .map { it.value }
            .sorted()
            .toList()

    private fun isMainResourceFile(
        root: File,
        file: File,
    ): Boolean {
        val folder = file.parentFile ?: return false
        val isXml = file.isFile && file.extension == "xml" && file.path.startsWith(root.path)
        return isXml &&
            valuesFolder.matches(folder.name) &&
            folder.parentFile?.name == "res" &&
            folder.parentFile?.parentFile?.name == "main" &&
            folder.parentFile
                ?.parentFile
                ?.parentFile
                ?.name == "src"
    }

    private fun groupId(
        root: File,
        file: File,
    ): String {
        val relative = file.relativeTo(root).invariantSeparatorsPath
        return relative.substringBefore("/src/main/res/") + "/" + file.name
    }

    private fun parse(file: File): ResourceVariant {
        val qualifier =
            file.parentFile.name
                .removePrefix("values")
                .removePrefix("-")
        val language = if (qualifier.isEmpty()) DEFAULT else qualifier
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Map<String, String>>()
        val children = document.documentElement.childNodes
        for (index in 0 until children.length) {
            val element = children.item(index) as? Element ?: continue
            val name = element.getAttribute("name")
            when (element.tagName) {
                "string" -> strings[name] = element.textContent
                "plurals" -> plurals[name] = quantities(element)
            }
        }
        return ResourceVariant(language, strings, plurals)
    }

    private fun quantities(plurals: Element): Map<String, String> {
        val items = plurals.getElementsByTagName("item")
        return (0 until items.length).associate { index ->
            val item = items.item(index) as Element
            item.getAttribute("quantity") to item.textContent
        }
    }
}
