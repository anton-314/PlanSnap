package app.plansnap.core.domain.validation

import app.plansnap.core.common.result.DomainResult
import app.plansnap.testing.fixtures.generators.ProjectGenerator
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Property: projects of the fixture generator are valid (TESTING section 3, fixed seed). */
class ProjectValidatorPropertyTest {
    private val validator = DefaultProjectValidator()

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `given generated projects when validating then every one passes`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, PropTestConfig(seed = SEED), Arb.long()) { seed ->
                assertEquals(DomainResult.Success(Unit), validator.validate(ProjectGenerator(seed).project()))
            }
        }

    private companion object {
        const val ITERATIONS = 200
        const val SEED = 7_007L
    }
}
