package app.plansnap.feature.export.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import app.plansnap.core.export.ExportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ShareAndFilesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val files = AndroidExportFiles(context)

    @Before
    fun resetProviderCache() {
        // FileProvider keeps its path strategy per authority in a static map. Every Robolectric test has its own
        // cache directory, so a strategy from an earlier test would point to a directory that no longer exists.
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }

    private fun cacheFile(
        name: String,
        content: String,
    ): ExportTarget =
        files.createCacheTarget(name).also { target -> target.stream.use { it.write(content.toByteArray()) } }

    @Test
    fun `given each format when the share intent is built then action, type, stream and grant are set`() {
        val expected =
            mapOf(
                ExportFormat.PDF to "application/pdf",
                ExportFormat.PNG to "image/png",
                ExportFormat.SVG to "image/svg+xml",
                ExportFormat.JSON to "application/json",
            )
        ExportFormat.entries.forEach { format ->
            val uri = cacheFile("plan.${format.fileExtension}", "x").uri
            val chooser = ShareIntents.chooser(ShareRequest(uri, format, "plan.${format.fileExtension}"), "Share")

            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            assertEquals("Share", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE))
            assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(uri, chooser.clipData?.getItemAt(0)?.uri)

            @Suppress("DEPRECATION")
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertNotNull(send)
            send!!
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals(expected.getValue(format), send.type)
            assertEquals(expected.getValue(format), format.mimeType)

            @Suppress("DEPRECATION")
            assertEquals(uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(uri, send.clipData?.getItemAt(0)?.uri)
            assertFalse(send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        }
    }

    @Test
    fun `given the manifest when resolved then the provider is private, grants per uri and matches the constant`() {
        val provider =
            context.packageManager.getProviderInfo(
                ComponentName(context.packageName, ExportFileProvider::class.java.name),
                PackageManager.GET_META_DATA,
            )
        assertEquals(EXPORT_FILE_AUTHORITY, provider.authority)
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
    }

    @Test
    fun `given a cache target when written then the content uri serves the same bytes`() {
        val target = cacheFile("Flat_1.pdf", "pdf-bytes")
        assertEquals("content", target.uri.scheme)
        assertEquals(EXPORT_FILE_AUTHORITY, target.uri.authority)
        assertTrue(target.uri.toString().endsWith("/Flat_1.pdf"))
        val read = context.contentResolver.openInputStream(target.uri)!!.use { it.readBytes() }
        assertEquals("pdf-bytes", String(read))
        assertTrue(File(context.cacheDir, "$EXPORT_CACHE_DIRECTORY/Flat_1.pdf").isFile)
    }

    @Test
    fun `given an older export when a new target is created then the old file is removed`() {
        cacheFile("old.png", "old")
        cacheFile("new.png", "new")
        val names = File(context.cacheDir, EXPORT_CACHE_DIRECTORY).list()!!.toList()
        assertEquals(listOf("new.png"), names)
    }

    @Test
    fun `given a failed export when the target is discarded then the file is gone`() {
        val target = files.createCacheTarget("broken.svg")
        target.stream.close()
        target.discard()
        assertFalse(File(context.cacheDir, "$EXPORT_CACHE_DIRECTORY/broken.svg").exists())
    }

    @Test
    fun `given a chosen document when opened then its content is replaced`() {
        val document = File(context.cacheDir, "chosen.json").apply { writeText("old content that is longer") }
        val target = files.openDocument(Uri.fromFile(document))
        target.stream.use { it.write("new".toByteArray()) }
        assertEquals("new", document.readText())
        assertEquals(Uri.fromFile(document), target.uri)
    }

    @Test
    fun `given a document that cannot be opened when opened then an IOException is raised`() {
        try {
            files.openDocument(Uri.parse("content://missing.authority/doc/1"))
            fail("Expected an IOException")
        } catch (_: IOException) {
            // expected
        }
    }

    @Test
    fun `given awkward project names when the file name is built then it is safe and carries the extension`() {
        assertEquals("Flat_1.pdf", ExportFileNames.forProject("Flat <1>", ExportFormat.PDF))
        assertEquals("a_b.svg", ExportFileNames.forProject("a/b", ExportFormat.SVG))
        assertEquals("plansnap.json", ExportFileNames.forProject("  ", ExportFormat.JSON))
        assertEquals("Küche-2_x.png", ExportFileNames.forProject("Küche-2 x", ExportFormat.PNG))
        assertEquals(80 + ".png".length, ExportFileNames.forProject("a".repeat(200), ExportFormat.PNG).length)
        assertEquals("plansnap.pdf", ExportFileNames.forProject("../..", ExportFormat.PDF))
    }
}
