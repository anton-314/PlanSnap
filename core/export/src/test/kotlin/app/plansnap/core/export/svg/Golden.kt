package app.plansnap.core.export.svg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import java.io.File

/**
 * Golden file comparison. Files live in `src/test/resources`. Run the tests with the environment variable
 * `PLANSNAP_UPDATE_GOLDEN=1` to rewrite them after an intended change, then review the diff.
 */
internal object Golden {
    private const val UPDATE_VARIABLE = "PLANSNAP_UPDATE_GOLDEN"

    fun assertMatches(
        path: String,
        actual: String,
    ) {
        if (System.getenv(UPDATE_VARIABLE) == "1") {
            val file = File("src/test/resources/$path")
            file.parentFile.mkdirs()
            file.writeText(actual, Charsets.UTF_8)
        }
        val stream = Golden::class.java.classLoader!!.getResourceAsStream(path)
        assertNotNull("Missing golden file $path, create it with $UPDATE_VARIABLE=1", stream)
        val expected = stream!!.use { it.readBytes().toString(Charsets.UTF_8) }
        assertEquals("Golden file $path differs", expected, actual)
    }
}
