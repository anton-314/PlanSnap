import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Fails when the merged manifest contains a `uses-permission` entry that is not in [allowedPermissions].
 * PlanSnap is offline: by default no permission is allowed, in particular not `INTERNET`.
 */
@CacheableTask
abstract class CheckManifestPermissionsTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val allowedPermissions: SetProperty<String>

    init {
        allowedPermissions.convention(emptySet())
        group = "verification"
        description = "Fails if the merged manifest declares a permission that is not allowed."
    }

    @TaskAction
    fun check() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(mergedManifest.get().asFile)
        val androidNs = "http://schemas.android.com/apk/res/android"
        val declared = mutableSetOf<String>()
        for (tag in listOf("uses-permission", "uses-permission-sdk-23")) {
            val nodes = document.getElementsByTagName(tag)
            for (index in 0 until nodes.length) {
                declared += (nodes.item(index) as org.w3c.dom.Element).getAttributeNS(androidNs, "name")
            }
        }
        val forbidden = declared - allowedPermissions.get()
        if (forbidden.isNotEmpty()) {
            throw GradleException("Merged manifest declares forbidden permissions: ${forbidden.sorted()}")
        }
    }
}
