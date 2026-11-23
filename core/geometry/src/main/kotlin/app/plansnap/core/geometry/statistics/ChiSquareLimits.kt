package app.plansnap.core.geometry.statistics

import app.plansnap.core.geometry.primitives.GeometryTolerances

/** Test level of the chi-square limit on the normalized corrections `q²` (DOMAIN section 5.5, ADR 0018). */
enum class ChiSquareLevel(
    /** Probability `p` of the quantile `χ²(r; p)`. */
    val probability: Double,
    internal val normalQuantile: Double,
) {
    /** Above this limit a large correction needs an explicit confirmation. */
    EXPLICIT_CONFIRMATION(
        GeometryTolerances.EXPLICIT_CONFIRMATION_CONFIDENCE,
        GeometryTolerances.NORMAL_QUANTILE_95,
    ),

    /** Above this limit a large correction is a conflict. */
    CONFLICT(GeometryTolerances.CONFLICT_CONFIDENCE, GeometryTolerances.NORMAL_QUANTILE_99),
}

/**
 * Deterministic chi-square limits of the overdetermination rule (DOMAIN section 5.5, ADR 0018).
 *
 * Redundancies up to [GeometryTolerances.CHI_SQUARE_TABLE_MAX_REDUNDANCY] use fixed tables. Larger ones use the
 * Wilson-Hilferty approximation, and the control threshold a closed normal approximation of the noncentral
 * distribution. All computations use [StrictMath], so that results are bit-identical on every device.
 */
object ChiSquareLimits {
    /** `χ²(r; 0.95)` for `r = 1 … 30`. */
    @Suppress("MagicNumber") // Reference data with one literal per redundancy.
    private val QUANTILES_95: DoubleArray =
        doubleArrayOf(
            3.841459,
            5.991465,
            7.814728,
            9.487729,
            11.070498,
            12.591587,
            14.067140,
            15.507313,
            16.918978,
            18.307038,
            19.675138,
            21.026070,
            22.362032,
            23.684791,
            24.995790,
            26.296228,
            27.587112,
            28.869299,
            30.143527,
            31.410433,
            32.670573,
            33.924438,
            35.172462,
            36.415029,
            37.652484,
            38.885139,
            40.113272,
            41.337138,
            42.556968,
            43.772972,
        )

    /** `χ²(r; 0.99)` for `r = 1 … 30`. */
    @Suppress("MagicNumber") // Reference data with one literal per redundancy.
    private val QUANTILES_99: DoubleArray =
        doubleArrayOf(
            6.634897,
            9.210340,
            11.344867,
            13.276704,
            15.086272,
            16.811894,
            18.475307,
            20.090235,
            21.665994,
            23.209251,
            24.724970,
            26.216967,
            27.688250,
            29.141238,
            30.577914,
            31.999927,
            33.408664,
            34.805306,
            36.190869,
            37.566235,
            38.932173,
            40.289360,
            41.638398,
            42.979820,
            44.314105,
            45.641683,
            46.962942,
            48.278236,
            49.587884,
            50.892181,
        )

    /**
     * `λ₀(r)` for `r = 1 … 30`: the smallest value with six decimals at which the test against the table limit
     * `χ²(r; 0.99)` reaches a power of at least 0.95.
     */
    @Suppress("MagicNumber") // Reference data with one literal per redundancy.
    private val CONTROL_THRESHOLDS: DoubleArray =
        doubleArrayOf(
            17.814166,
            20.649919,
            22.674257,
            24.329097,
            25.762062,
            27.043175,
            28.212040,
            29.293604,
            30.304773,
            31.257626,
            32.161143,
            33.022219,
            33.846282,
            34.637694,
            35.400024,
            36.136240,
            36.848833,
            37.539925,
            38.211339,
            38.864655,
            39.501250,
            40.122338,
            40.728993,
            41.322170,
            41.902724,
            42.471421,
            43.028955,
            43.575957,
            44.112995,
            44.640594,
        )

    private const val WILSON_HILFERTY_DIVISOR = 9.0
    private const val TWO = 2.0
    private const val FOUR = 4.0

    /**
     * Returns the quantile `χ²(r; p)` of [level].
     *
     * @param redundancy degrees of freedom `r`, at least 1.
     * @throws IllegalArgumentException if [redundancy] is below 1.
     */
    fun quantile(
        level: ChiSquareLevel,
        redundancy: Int,
    ): Double {
        require(redundancy >= 1) { "Redundancy must be positive: $redundancy" }
        if (redundancy > GeometryTolerances.CHI_SQUARE_TABLE_MAX_REDUNDANCY) {
            return wilsonHilferty(level.normalQuantile, redundancy.toDouble())
        }
        val table =
            when (level) {
                ChiSquareLevel.EXPLICIT_CONFIRMATION -> QUANTILES_95
                ChiSquareLevel.CONFLICT -> QUANTILES_99
            }
        return table[redundancy - 1]
    }

    /**
     * Returns `λ₀(r)`, the noncentrality at which the conflict test detects an error with power
     * [GeometryTolerances.CONTROL_POWER]. A measurement with `λ(3T) < λ₀(r)` is not controllable.
     *
     * @param redundancy degrees of freedom `r`, at least 1.
     * @throws IllegalArgumentException if [redundancy] is below 1.
     */
    fun controlThreshold(redundancy: Int): Double {
        require(redundancy >= 1) { "Redundancy must be positive: $redundancy" }
        if (redundancy <= GeometryTolerances.CHI_SQUARE_TABLE_MAX_REDUNDANCY) {
            return CONTROL_THRESHOLDS[redundancy - 1]
        }
        val r = redundancy.toDouble()
        val limit = wilsonHilferty(ChiSquareLevel.CONFLICT.normalQuantile, r)
        val z2 = GeometryTolerances.NORMAL_QUANTILE_95 * GeometryTolerances.NORMAL_QUANTILE_95
        return limit - r + TWO * z2 + StrictMath.sqrt(FOUR * z2 * z2 + z2 * (FOUR * limit - TWO * r))
    }

    private fun wilsonHilferty(
        z: Double,
        r: Double,
    ): Double {
        val v = TWO / (WILSON_HILFERTY_DIVISOR * r)
        val base = 1.0 - v + z * StrictMath.sqrt(v)
        return r * base * base * base
    }
}
