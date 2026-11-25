package app.plansnap.core.export.json

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.document.JsonDocumentCodec
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportRequest
import app.plansnap.core.export.svg.ExportFixtures
import app.plansnap.core.export.svg.Golden
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JsonExporterTest {
    private val codec = JsonDocumentCodec()
    private val exporter = JsonExporter(codec, ExportFixtures.unconfined)

    private fun request(format: ExportFormat = ExportFormat.JSON): ExportRequest =
        ExportFixtures.request(
            ExportFixtures.project(
                ExportFixtures.confirmed,
                ExportFixtures.unconfirmed,
                ExportFixtures.conflict,
                ExportFixtures.open,
            ),
            format,
        )

    private fun run(
        request: ExportRequest,
        output: OutputStream = ByteArrayOutputStream(),
    ) = runBlocking { exporter.export(request, output) }

    @Test
    fun `given a project when exporting then the document decodes to an equal project with all rooms`() {
        val request = request()
        val bytes = ByteArrayOutputStream()
        val result = run(request, bytes) as DomainResult.Success
        val decoded = codec.decodeProject(bytes.toByteArray().inputStream()) as DomainResult.Success
        assertEquals(request.project, decoded.value)
        assertEquals(4, decoded.value.rooms.size)
        assertEquals(ExportFormat.JSON, result.value.format)
        assertNull(result.value.plan)
        assertTrue(result.value.omittedRooms.isEmpty())
    }

    @Test
    fun `given the same input when exporting twice then the bytes are equal`() {
        val first = ByteArrayOutputStream()
        val second = ByteArrayOutputStream()
        run(request(), first)
        run(request(), second)
        assertTrue(first.toByteArray().contentEquals(second.toByteArray()))
    }

    @Test
    fun `given a stream when exporting then it stays open`() {
        var closed = false
        val stream =
            object : ByteArrayOutputStream() {
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        run(request(), stream)
        assertFalse(closed)
    }

    @Test
    fun `given a failing stream when exporting then the result is an export error`() {
        val failing =
            object : OutputStream() {
                override fun write(b: Int) = throw IOException("disk full")

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) = throw IOException("disk full")
            }
        assertEquals(
            DomainResult.Failure(DomainError.Export(ExportProblem.WRITE_FAILED)),
            run(request(), failing),
        )
    }

    @Test
    fun `given a cancellation while writing when exporting then it propagates`() {
        val cancelling =
            object : OutputStream() {
                override fun write(b: Int) = throw CancellationException("cancelled")

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) = throw CancellationException("cancelled")
            }
        assertThrows(CancellationException::class.java) { run(request(), cancelling) }
    }

    @Test
    fun `given another format when exporting then it is a programming error`() {
        assertThrows(IllegalArgumentException::class.java) { run(request(ExportFormat.SVG)) }
    }

    @Test
    fun `given a project when exporting then the golden document is written`() {
        val bytes = ByteArrayOutputStream()
        run(
            ExportFixtures.request(ExportFixtures.project(ExportFixtures.confirmed), ExportFormat.JSON),
            bytes,
        )
        Golden.assertMatches("json/confirmed-project.json", bytes.toString(Charsets.UTF_8))
    }
}
