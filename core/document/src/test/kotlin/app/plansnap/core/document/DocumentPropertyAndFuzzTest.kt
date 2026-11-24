package app.plansnap.core.document

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.testing.fixtures.generators.ProjectGenerator
import app.plansnap.testing.fixtures.random.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class DocumentPropertyAndFuzzTest {
    private val codec = JsonDocumentCodec()

    @Test
    fun `given generated projects when roundtripped then they are identical and encoding is stable`() {
        for (seed in 1L..PROPERTY_CASES) {
            val project = ProjectGenerator(seed).project()
            val text = codec.encodeProject(project)
            val result = codec.decodeProject(ByteArrayInputStream(text.toByteArray()))
            assertEquals(DomainResult.Success(project), result, "seed $seed")
            assertEquals(text, codec.encodeProject((result as DomainResult.Success).value), "seed $seed")
            project.rooms.forEach { room ->
                assertEquals(DomainResult.Success(room), codec.decodeRoom(codec.encodeRoom(room)), "seed $seed")
            }
        }
    }

    @Test
    fun `given mutated golden file when decoded then only domain errors or valid projects result`() {
        val golden = javaClass.getResourceAsStream("/golden/project-v1.json")!!.readBytes()
        val random = SeededRandom(FUZZ_SEED)
        repeat(FUZZ_CASES) { case ->
            val mutated = mutate(golden, random)
            val result = codec.decodeProject(ByteArrayInputStream(mutated))
            if (result is DomainResult.Failure) {
                assertTrue(result.error is DomainError.Document, "case $case: ${result.error}")
            } else {
                codec.encodeProject((result as DomainResult.Success).value)
            }
            val asText = codec.decodeRoom(mutated.toString(Charsets.UTF_8))
            assertTrue(asText is DomainResult.Success || (asText as DomainResult.Failure).error is DomainError.Document)
        }
    }

    private fun mutate(
        source: ByteArray,
        random: SeededRandom,
    ): ByteArray {
        var bytes = source.copyOf()
        repeat(1 + random.nextInt(MAX_MUTATIONS)) {
            if (bytes.isEmpty()) return bytes
            val at = random.nextInt(bytes.size)
            bytes =
                when (random.nextInt(MUTATION_KINDS)) {
                    0 -> {
                        bytes.also { it[at] = random.nextLong().toByte() }
                    }

                    1 -> {
                        bytes.copyOfRange(0, at)
                    }

                    2 -> {
                        bytes.copyOfRange(0, at) +
                            bytes.copyOfRange(minOf(bytes.size, at + 1 + random.nextInt(SKIP)), bytes.size)
                    }

                    3 -> {
                        bytes.copyOfRange(0, at) + random.nextLong().toByte() + bytes.copyOfRange(at, bytes.size)
                    }

                    else -> {
                        bytes.also { it[at] = TOKENS[random.nextInt(TOKENS.size)].code.toByte() }
                    }
                }
        }
        return bytes
    }

    private companion object {
        const val PROPERTY_CASES = 150L
        const val FUZZ_SEED = 0x5EEDL
        const val FUZZ_CASES = 3000
        const val MAX_MUTATIONS = 4
        const val MUTATION_KINDS = 5
        const val SKIP = 40
        val TOKENS = listOf('{', '}', '[', ']', '"', ',', ':', '-', '0', '9', 'e', '.', 'n', 't')
    }
}
