package app.plansnap.core.geometry.statistics

import app.plansnap.core.geometry.primitives.GeometryTolerances
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Reference values come from an independent regularized incomplete gamma computation (T-ARC-006). */
class ChiSquareLimitsTest {
    @ParameterizedTest
    @CsvSource(
        "1, 3.841459, 6.634897, 17.814165",
        "2, 5.991465, 9.210340, 20.649920",
        "3, 7.814728, 11.344867, 22.674257",
        "6, 12.591587, 16.811894, 27.043176",
        "10, 18.307038, 23.209251, 31.257627",
        "30, 43.772972, 50.892181, 44.640595",
    )
    fun `given table redundancy when read then reference values match`(
        redundancy: Int,
        q95: Double,
        q99: Double,
        lambda0: Double,
    ) {
        assertEquals(q95, ChiSquareLimits.quantile(ChiSquareLevel.EXPLICIT_CONFIRMATION, redundancy), 1e-6)
        assertEquals(q99, ChiSquareLimits.quantile(ChiSquareLevel.CONFLICT, redundancy), 1e-6)
        assertEquals(lambda0, ChiSquareLimits.controlThreshold(redundancy), 2e-6)
    }

    @ParameterizedTest
    @CsvSource(
        "31, 44.979167, 52.213049, 47.278308",
        "50, 67.500552, 76.171856, 55.729797",
        "100, 124.339593, 135.820083, 72.183387",
        "1000, 1074.678919, 1106.973522, 193.613008",
    )
    fun `given redundancy above the table when computed then the closed forms apply`(
        redundancy: Int,
        q95: Double,
        q99: Double,
        lambda0: Double,
    ) {
        assertEquals(q95, ChiSquareLimits.quantile(ChiSquareLevel.EXPLICIT_CONFIRMATION, redundancy), 1e-5)
        assertEquals(q99, ChiSquareLimits.quantile(ChiSquareLevel.CONFLICT, redundancy), 1e-5)
        assertEquals(lambda0, ChiSquareLimits.controlThreshold(redundancy), 1e-5)
    }

    @ParameterizedTest
    @CsvSource(
        "31, 44.985343, 52.191395",
        "50, 67.504807, 76.153891",
        "100, 124.342113, 135.806723",
    )
    fun `given redundancy above the table when approximated then exact quantiles are met`(
        redundancy: Int,
        exact95: Double,
        exact99: Double,
    ) {
        val q95 = ChiSquareLimits.quantile(ChiSquareLevel.EXPLICIT_CONFIRMATION, redundancy)
        val q99 = ChiSquareLimits.quantile(ChiSquareLevel.CONFLICT, redundancy)
        assertEquals(exact95, q95, exact95 * 5e-4)
        assertEquals(exact99, q99, exact99 * 5e-4)
    }

    @Test
    fun `given all redundancies when compared then every limit grows across the table boundary`() {
        (1 until 400).forEach { r ->
            ChiSquareLevel.entries.forEach { level ->
                assertTrue(ChiSquareLimits.quantile(level, r + 1) > ChiSquareLimits.quantile(level, r), "$level $r")
            }
            assertTrue(ChiSquareLimits.controlThreshold(r + 1) > ChiSquareLimits.controlThreshold(r), "lambda $r")
            assertTrue(
                ChiSquareLimits.quantile(ChiSquareLevel.CONFLICT, r) >
                    ChiSquareLimits.quantile(ChiSquareLevel.EXPLICIT_CONFIRMATION, r),
                "order $r",
            )
        }
    }

    @Test
    fun `given levels when read then probabilities come from the central constants`() {
        assertEquals(0.95, ChiSquareLevel.EXPLICIT_CONFIRMATION.probability, 0.0)
        assertEquals(0.99, ChiSquareLevel.CONFLICT.probability, 0.0)
        assertEquals(GeometryTolerances.CONFLICT_CONFIDENCE, ChiSquareLevel.CONFLICT.probability, 0.0)
    }

    @Test
    fun `given redundancy below one when requested then it is rejected`() {
        listOf(0, -1).forEach { r ->
            assertThrows(IllegalArgumentException::class.java) {
                ChiSquareLimits.quantile(ChiSquareLevel.CONFLICT, r)
            }
            assertThrows(IllegalArgumentException::class.java) { ChiSquareLimits.controlThreshold(r) }
        }
    }
}
