package app.plansnap.core.geometry.determinacy.rig

import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Seeded port of `docs/research/evaluation-prototype/stress.py` on the production code. Each case has its own random
 * source derived from seed and case number, so the result does not depend on the thread count or order.
 */
internal object EvaluationRig {
    /** The class generators of the prototype in its order. */
    val CLASSES: List<Pair<String, (Int) -> RigCase>> =
        listOf(
            "K1a" to { i -> RigCases.classOne(i, honest = true) },
            "K1b" to { i -> RigCases.classOne(i, honest = false) },
            "K2" to RigCases::classTwo,
            "K3" to RigCases::classThree,
            "K4" to RigCases::classFour,
        )

    /** Runs [casesPerClass] cases of every class on [threads] threads and merges the findings. */
    fun run(
        casesPerClass: Int,
        threads: Int,
    ): RigTally {
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val jobs =
                CLASSES.flatMap { (_, generator) ->
                    (0 until casesPerClass).map { index ->
                        Callable { RigChecks.run(generator(index), RigEvaluator()) }
                    }
                }
            val total = RigTally()
            pool.invokeAll(jobs).forEach { total.addAll(it.get()) }
            return total
        } finally {
            pool.shutdown()
        }
    }

    /** Text report in the layout of `stress.py`: per tag the status counts, then every other counter. */
    fun report(tally: RigTally): String {
        val tags =
            tally.counts.keys
                .map { it.substringBefore(':') }
                .toSortedSet()
        return buildString {
            for (tag in tags) {
                val entries = tally.counts.filterKeys { it.startsWith("$tag:") }
                val statuses =
                    entries
                        .filterKeys {
                            it.startsWith(
                                "$tag:status:",
                            )
                        }.mapKeys { it.key.substringAfterLast(':') }
                appendLine("$tag n=${entries["$tag:total"]} $statuses")
                entries
                    .filterKeys { !it.startsWith("$tag:status:") && it != "$tag:total" }
                    .forEach { (key, value) ->
                        appendLine("    $value  ${key.removePrefix("$tag:")}  ${tally.samples[key].orEmpty()}")
                    }
            }
        }
    }
}
