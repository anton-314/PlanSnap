package app.plansnap.core.document

import app.plansnap.core.common.result.DocumentProblem
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomPlacement
import app.plansnap.testing.fixtures.random.SeededRandom
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class JsonDocumentCodecTest {
    private val codec = JsonDocumentCodec()
    private val project = TestProjects.representative()

    private fun decode(text: String) = codec.decodeProject(ByteArrayInputStream(text.toByteArray()))

    private fun assertDocumentError(
        problem: DocumentProblem,
        result: DomainResult<*>,
    ) {
        assertEquals(DomainResult.Failure(DomainError.Document(problem)), result, result.toString().take(80))
    }

    private fun golden(): String =
        javaClass.getResourceAsStream("/golden/project-v1.json")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `given representative project when roundtripped then it is identical`() {
        assertEquals(DomainResult.Success(project), decode(codec.encodeProject(project)))
    }

    @Test
    fun `given golden file when decoded then it equals the project and encoding reproduces it byte for byte`() {
        val text = golden()
        assertEquals(DomainResult.Success(project), decode(text))
        assertEquals(text, codec.encodeProject(project))
    }

    @Test
    fun `given same project when encoded twice then output is byte identical`() {
        assertArrayEquals(
            codec.encodeProject(project).toByteArray(),
            JsonDocumentCodec().encodeProject(project).toByteArray(),
        )
    }

    @Test
    fun `given room when roundtripped then it is identical`() {
        val room = project.rooms.first()
        assertEquals(DomainResult.Success(room), codec.decodeRoom(codec.encodeRoom(room)))
    }

    @Test
    fun `given edge case doubles when roundtripped then bits are preserved`() {
        val random = SeededRandom(DOUBLE_SEED)
        val specials =
            listOf(
                -0.0,
                0.0,
                Double.MIN_VALUE,
                Double.MAX_VALUE,
                0.1 + 0.2,
                1.0 / 3.0,
                1e-300,
                1e21,
                123456789.12345678,
            )
        val randoms = List(RANDOM_DOUBLES) { Double.fromBits(random.nextLong()) }.filter { it.isFinite() }
        val values = specials + randoms
        val lengths = values.map { Math.abs(it) }.map { if (it == 0.0) 1.0 else it }
        val room = project.rooms[0]
        val adjusted =
            room.copy(
                walls = room.walls,
                adjustment =
                    ClosureAdjustment(
                        1L,
                        1,
                        lengths.take(4).toImmutableList(),
                        values.take(4).toImmutableList(),
                    ),
            )
        // The room has four walls, so check all values over several four-element windows and the placement.
        values.windowed(4, 4).forEachIndexed { index, window ->
            val ls = window.map { Math.abs(it).let { v -> if (v == 0.0) 1.0 else v } }
            val candidate =
                adjusted.copy(
                    adjustment = ClosureAdjustment(index.toLong(), 1, ls.toImmutableList(), window.toImmutableList()),
                    placement = RoomPlacement(window[0], window[1], window[2], null),
                )
            val back = (codec.decodeRoom(codec.encodeRoom(candidate)) as DomainResult.Success<Room>).value
            assertEquals(window.map { it.toRawBits() }, back.adjustment!!.adjustedTurnsRad.map { it.toRawBits() })
            assertEquals(ls.map { it.toRawBits() }, back.adjustment!!.adjustedLengthsMm.map { it.toRawBits() })
            assertEquals(
                listOf(window[0].toRawBits(), window[1].toRawBits(), window[2].toRawBits()),
                listOf(
                    back.placement.originX.toRawBits(),
                    back.placement.originY.toRawBits(),
                    back.placement.rotationRad.toRawBits(),
                ),
            )
        }
    }

    @Test
    fun `given newer major version when decoded then it is unsupported`() {
        val text = codec.encodeProject(project).replace("\"major\":1", "\"major\":2")
        assertDocumentError(DocumentProblem.UNSUPPORTED_VERSION, decode(text))
        assertDocumentError(
            DocumentProblem.UNSUPPORTED_VERSION,
            codec.decodeRoom(codec.encodeRoom(project.rooms[0]).replace("\"major\":1", "\"major\":7")),
        )
    }

    @Test
    fun `given newer major version with unknown shape when decoded then it is unsupported not malformed`() {
        assertDocumentError(
            DocumentProblem.UNSUPPORTED_VERSION,
            decode("""{"formatVersion":{"major":2,"minor":0},"x":[]}"""),
        )
    }

    @Test
    fun `given newer minor version with unknown fields when decoded then they are ignored`() {
        val text =
            codec
                .encodeProject(
                    project,
                ).replace(
                    "\"minor\":0",
                    "\"minor\":9,\"extra\":{\"a\":1}",
                ).replace("\"name\":\"Musterprojekt\"", "\"name\":\"Musterprojekt\",\"future\":true")
        assertEquals(DomainResult.Success(project), decode(text))
    }

    @Test
    fun `given invalid version header when decoded then it is malformed`() {
        listOf(
            """{"project":{}}""",
            """{"formatVersion":1}""",
            """{"formatVersion":{"major":0,"minor":0}}""",
            """{"formatVersion":{"major":1,"minor":-1}}""",
            """{"formatVersion":{"major":"1","minor":0}}""",
            """{"formatVersion":{"major":1.5,"minor":0}}""",
            """{"formatVersion":{"major":1}}""",
        ).forEach { assertDocumentError(DocumentProblem.MALFORMED, decode(it)) }
    }

    @Test
    fun `given current version without body when decoded then it is malformed`() {
        assertDocumentError(DocumentProblem.MALFORMED, decode("""{"formatVersion":{"major":1,"minor":0}}"""))
    }

    @Test
    fun `given duplicate migrations when created then they are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { JsonDocumentCodec(listOf(Noop(1), Noop(1))) }
    }

    @Test
    fun `given empty migration chain when current document is read then no migration runs`() {
        val failing = JsonDocumentCodec(listOf(Noop(1)))
        assertEquals(
            DomainResult.Success(project),
            failing.decodeProject(ByteArrayInputStream(codec.encodeProject(project).toByteArray())),
        )
    }

    @Test
    fun `given missing required fields or wrong types when decoded then it is malformed`() {
        val text = codec.encodeProject(project)
        listOf(
            text.replace("\"defaultHeightMm\":2500", "\"defaultHeightMm\":\"2500\""),
            text.replace("\"defaultHeightMm\":2500,", ""),
            text.replace("\"isClosed\":true", "\"isClosed\":1"),
            text.replace("\"rooms\":[", "\"rooms\":{\"a\":["),
            text.replace("\"lengthMm\":5000", "\"lengthMm\":5000.5"),
            text
                .replace(
                    "\"type\":\"DOOR\"",
                    "\"type\":\"GATE\"",
                ).replace("GATE", "DOOR")
                .replace("\"name\":\"Flur\"", "\"name\":null"),
            text.replace("\"rotationRad\":3.141592653589793", "\"rotationRad\":1e999"),
            text.replace("\"areaFactorPercent\":50", "\"areaFactorPercent\":\"50\""),
            text.replace("\"originY\":0.0", "\"originY\":\"0.0\""),
            "[]",
            "null",
            "",
            "   ",
            "{",
            text.dropLast(5),
            text.take(text.length / 2),
        ).forEachIndexed { index, case ->
            assertEquals(
                DomainResult.Failure(DomainError.Document(DocumentProblem.MALFORMED)),
                decode(case),
                "case $index",
            )
        }
    }

    @Test
    fun `given values outside their ranges when decoded then they are invalid values`() {
        val text = codec.encodeProject(project)
        listOf(
            text.replace("\"defaultHeightMm\":2500", "\"defaultHeightMm\":0"),
            text.replace("\"lengthMm\":5000,", "\"lengthMm\":-5,"),
            text.replace("\"areaFactorPercent\":50", "\"areaFactorPercent\":101"),
            text.replace("\"angleMilliDegrees\":89999", "\"angleMilliDegrees\":180001"),
            text.replace("\"type\":\"DOOR\"", "\"type\":\"GATE\""),
            text.replace("\"direction\":\"RIGHT\"", "\"direction\":\"UP\""),
            text.replace("\"kind\":\"MEASURED\"", "\"kind\":\"ODD\""),
            text.replace("\"id\":\"room-b\"", "\"id\":\"room-a\""),
            text.replace("\"id\":\"room-b\"", "\"id\":\" \""),
            text.replace("\"solverVersion\":3", "\"solverVersion\":0"),
            text.replace("\"swing\":\"HINGE_RIGHT_OUTWARD\"", "\"swing\":\"SIDEWAYS\""),
        ).forEachIndexed { index, case ->
            assertEquals(
                DomainResult.Failure(DomainError.Document(DocumentProblem.INVALID_VALUES)),
                decode(case),
                "case $index",
            )
        }
    }

    @Test
    fun `given inconsistent corner payloads when decoded then they are invalid values`() {
        val text = codec.encodeProject(project)
        listOf(
            text.replace(
                "\"kind\":\"UNSPECIFIED\",\"direction\":null",
                "\"kind\":\"UNSPECIFIED\",\"direction\":\"LEFT\"",
            ),
            text.replace(
                "\"kind\":\"DECLARED_RIGHT\",\"direction\":\"LEFT\",\"angleMilliDegrees\":null",
                "\"kind\":\"DECLARED_RIGHT\",\"direction\":\"LEFT\",\"angleMilliDegrees\":90000",
            ),
            text.replace(
                "\"kind\":\"MEASURED\",\"direction\":\"RIGHT\",\"angleMilliDegrees\":89999",
                "\"kind\":\"MEASURED\",\"direction\":\"RIGHT\",\"angleMilliDegrees\":null",
            ),
            text.replace("\"kind\":\"MEASURED\",\"direction\":\"RIGHT\"", "\"kind\":\"MEASURED\",\"direction\":null"),
            text.replace(
                "\"kind\":\"DECLARED_RIGHT\",\"direction\":\"LEFT\"",
                "\"kind\":\"DECLARED_RIGHT\",\"direction\":null",
            ),
        ).forEach { assertDocumentError(DocumentProblem.INVALID_VALUES, decode(it)) }
    }

    @Test
    fun `given invalid utf8 when decoded then it is malformed`() {
        val bytes = byteArrayOf('{'.code.toByte(), 0xC3.toByte(), 0x28, '}'.code.toByte())
        assertDocumentError(DocumentProblem.MALFORMED, codec.decodeProject(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `given deeply nested input when decoded then it is rejected without exception`() {
        val deep =
            "{\"formatVersion\":{\"major\":1,\"minor\":0},\"project\":" + "[".repeat(DEPTH) + "]".repeat(DEPTH) + "}"
        assertDocumentError(DocumentProblem.MALFORMED, decode(deep))
    }

    @Test
    fun `given document of exactly the limit when decoded then it is accepted`() {
        val body = codec.encodeProject(project)
        val bytes = body.toByteArray()
        val padded = bytes + ByteArray((DocumentLimits.MAX_DOCUMENT_BYTES - bytes.size).toInt()) { ' '.code.toByte() }
        assertEquals(DocumentLimits.MAX_DOCUMENT_BYTES, padded.size.toLong())
        assertEquals(DomainResult.Success(project), codec.decodeProject(ByteArrayInputStream(padded)))
    }

    @Test
    fun `given endless stream when decoded then reading stops one byte beyond the limit`() {
        val stream = EndlessStream()
        assertDocumentError(DocumentProblem.TOO_LARGE, codec.decodeProject(stream))
        assertEquals(DocumentLimits.MAX_DOCUMENT_BYTES + 1L, stream.delivered)
        assertTrue(!stream.closed)
    }

    @Test
    fun `given oversized room text when decoded then it is too large`() {
        assertDocumentError(
            DocumentProblem.TOO_LARGE,
            codec.decodeRoom(
                " ".repeat(DocumentLimits.MAX_DOCUMENT_BYTES.toInt() + 1),
            ),
        )
    }

    @Test
    fun `given failing stream when decoded then it is a storage failure`() {
        val failing =
            object : InputStream() {
                override fun read(): Int = throw IOException("boom")
            }
        assertEquals(
            DomainResult.Failure(DomainError.Storage(StorageProblem.READ_FAILED)),
            codec.decodeProject(failing),
        )
    }

    private class Noop(
        override val fromMajor: Int,
    ) : DocumentMigration {
        override fun migrate(json: String): DomainResult<String> = DomainResult.Success(json)
    }

    private class EndlessStream : InputStream() {
        var delivered = 0L
        var closed = false

        override fun read(): Int {
            delivered++
            return ' '.code
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            delivered += len
            b.fill(' '.code.toByte(), off, off + len)
            return len
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val DOUBLE_SEED = 20_260_101L
        const val RANDOM_DOUBLES = 4000
        const val DEPTH = 200_000
    }
}
