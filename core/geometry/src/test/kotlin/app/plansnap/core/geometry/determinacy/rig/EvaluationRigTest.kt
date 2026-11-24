package app.plansnap.core.geometry.determinacy.rig

import app.plansnap.core.geometry.primitives.GeometryTolerances
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Seeded port of the prototype rig (`docs/research/evaluation-prototype/stress.py`, seed 20261124, 2 400 cases per
 * class) on the production code with `SOLVER_GRADIENT = 1e-9`. The targets are those of the README of the prototype.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EvaluationRigTest {
    private lateinit var tally: RigTally

    @BeforeAll
    fun runRig() {
        tally = EvaluationRig.run(CASES_PER_CLASS, THREADS)
        println(EvaluationRig.report(tally))
    }

    @Test
    fun `given the production constants then the solver gradient is the contract value`() {
        assertEquals(1e-9, GeometryTolerances.SOLVER_GRADIENT, 0.0)
    }

    @Test
    fun `given every class then all cases ran without an exception`() {
        assertEquals(EvaluationRig.CLASSES.size * CASES_PER_CLASS, totalOf("K"))
        assertEquals(emptyMap<String, Int>(), tally.counts.filterKeys { ":crash:" in it }, samples("crash"))
    }

    @Test
    fun `given honest consistent rooms then there is no deviation`() {
        assertEquals(emptyMap<String, Int>(), deviations("K1a:"), samples("K1a:"))
    }

    @Test
    fun `given shape deviations then false conflicts stay within one percent and no outline is wrong`() {
        assertTrue(tally.sum("K1b:false-conflict") <= ONE_PERCENT, "false conflicts: ${deviations("K1b:")}")
        assertEquals(0, tally.sum("K1b:non-simple"), samples("K1b:non-simple"))
        assertEquals(0, tally.sum("K1b:wrong-confirmed"), samples("K1b:wrong-confirmed"))
        // Search limit of ADR 0018: confirmed shapes that meet every input but are not the true room, guide value 2.
        assertTrue(tally.sum("K1b:ambiguous-other-solution") <= FAR_CONFIRMED_GUIDE, samples("K1b:ambiguous"))
    }

    @Test
    fun `given wrong directions then no outline is not simple and no confirmation violates a direction`() {
        assertEquals(emptyMap<String, Int>(), deviations("K2:"), samples("K2:"))
    }

    @Test
    fun `given underdetermined rooms then nothing is confirmed and conflicts stay within one percent`() {
        assertEquals(emptyMap<String, Int>(), deviations("K3:"), samples("K3:"))
        assertEquals(0, tally.sum("K3:status:CONFIRMED"))
        assertTrue(tally.sum("K3:status:CONFLICT") <= ONE_PERCENT, "conflicts: ${tally.sum("K3:status:CONFLICT")}")
    }

    @Test
    fun `given boundary shapes and gap rectangles then there is no deviation`() {
        assertEquals(emptyMap<String, Int>(), deviations("K4-"), samples("K4-"))
        assertEquals(CASES_PER_CLASS / GAP_EVERY, totalOf("K4-gap"))
    }

    @Test
    fun `given the seed then the generated rooms equal those of the prototype`() {
        val generated =
            REFERENCE_INDICES.flatMap { i ->
                listOf(
                    RigCases.classOne(i, honest = true),
                    RigCases.classOne(i, honest = false),
                    RigCases.classTwo(i),
                    RigCases.classThree(i),
                    RigCases.classFour(i),
                ).map { RigCases.describe(it.input).trimEnd() }
            }
        assertEquals(PROTOTYPE_ROOMS.map { it.trimEnd() }, generated)
    }

    @Test
    fun `given a seed then the random source reproduces CPython`() {
        val random = PythonRandom(40_522_249L)
        assertEquals(0.7674383297942013, random.random(), 0.0)
        assertEquals(0.7034493785632361, random.random(), 0.0)
        assertEquals(5, random.randint(3, 12))
        assertEquals(4.18929459367477, random.uniform(-2.0, 6.0), 0.0)
        assertEquals(3, random.randrange(7))
        val items = (0 until 10).toMutableList()
        random.shuffle(items)
        assertEquals(listOf(1, 4, 8, 2, 5, 3, 7, 9, 0, 6), items)
        assertEquals(listOf(5, 1, 6), random.sample((1 until 9).toList(), 3))
        assertEquals("c", random.choice(listOf("a", "b", "c")))
        assertEquals(0.5043802970418443, PythonRandom((1L shl 40) + 5).random(), 0.0)
    }

    /** Counters of [prefix] that are findings: neither total, status nor `info-` statistics. */
    private fun deviations(prefix: String): Map<String, Int> =
        tally.counts.filterKeys {
            it.startsWith(prefix) && !it.endsWith(":total") && ":status:" !in it && ":info-" !in it
        }

    private fun totalOf(prefix: String): Int =
        tally.counts
            .filterKeys {
                it.startsWith(prefix) && it.endsWith(":total")
            }.values
            .sum()

    private fun samples(prefix: String): String =
        tally.samples
            .filterKeys { it.startsWith(prefix) || prefix in it }
            .entries
            .joinToString("\n")

    private companion object {
        const val CASES_PER_CLASS = 2_400
        const val THREADS = 4
        const val GAP_EVERY = 5
        const val ONE_PERCENT = CASES_PER_CLASS / 100
        const val FAR_CONFIRMED_GUIDE = 2
        val REFERENCE_INDICES = listOf(0, 1, 5, 7)

        /** Output of `stress.py` for the cases [REFERENCE_INDICES], in the order K1a, K1b, K2, K3, K4. */
        val PROTOTYPE_ROOMS =
            listOf(
                "2049 2047 2049 2047 | M90000 L L L | 0-2=2896 1-3=2896 0-1=2049",
                "2800 2954 2800 2954 | F M90000 L M90000 | 0-2=4070 1-3=4070 0-1=2800",
                "4654 6398 2909 1640 1271 4425 5733 1712 | F m113255 m114696 m152315 M164110 M149317 m114707 R | " +
                    "0-2=9280 5-7=8582",
                "4089 2723 1467 6430 1119 2836 | F R R R R R | ",
                "4051 3000 4000 3000 | F L L L | ",
                "4620 1374 1641 5808 2584 5673 | M60302 M159293 M167715 M97344 M74867 M160479 | ",
                "6689 7264 2613 8729 5419 | F M92787 M122528 M116541 M87203 | ",
                "4305 2890 4305 2890 | F R M90000 m90000 | 0-2=5185 1-3=5185 0-1=4305 0-1=4305",
                "6283 3534 2428 2752 7184 3804 | F L L R L M127763 | ",
                "8654 107 8654 107 | F L M90000 L | ",
                "1115 2445 3917 1515 1535 2114 1265 1811 1321 4321 8290 | F M153699 M149124 M159361 M167969 M162113 " +
                    "M157572 M152532 M151384 M144871 M117880 | 1-9=10918 4-10=7995",
                "2842 1457 980 2544 2803 3930 2232 1848 2188 3025 3292 5798 | F L L m67810 L R L m64045 M134539 R " +
                    "M136661 R | 3-5=5132",
                "2000 1000 2000 4000 1000 4000 1000 1000 | F R R L L L L R | ",
                "5212 1346 5240 2066 4749 1296 4200 2172 | F m151932 R m103206 R R R R | ",
                "4050 3000 4000 3000 | F L L L | ",
                "2930 5582 2791 3630 1298 2317 5698 | m108671 m97957 m150639 m122752 m105916 m161526 m152539 | " +
                    "1-6=7193 0-4=8885 2-4=5651",
                "1428 1573 2795 3170 1367 1597 | F m90000 R m90000 m90000 R | 0-2=2125 1-4=3219 1-5=2142 1-3=3207",
                "1626 1545 2418 628 3497 1946 1975 4838 2938 2129 3207 4018 | M44273 R L L m95102 L L R M172622 R " +
                    "M156512 L | 0-8=8727 7-11=9453 6-7=1975",
                "3881 1188 991 912 3267 2210 4201 2474 4052 | F R R R R R R R R | ",
                "5203 1410 4453 | M14256 M51030 M114714 | ",
            )
    }
}
