package app.plansnap.core.geometry.adjustment

import app.plansnap.core.geometry.cancellation.CancellationSignal

/**
 * Cholesky factor `L` of the symmetric positive definite matrix `A Q Aᵀ` of the adjustment (DOMAIN section 6).
 *
 * Dense, without random elements and with a fixed loop order, so equal input gives a bit-identical factor. Roots go
 * through [StrictMath].
 */
internal class NormalSystem private constructor(
    private val lower: Array<DoubleArray>,
) {
    /** Solves `A Q Aᵀ · y = [rhs]` by forward and backward substitution. */
    fun solve(rhs: DoubleArray): DoubleArray {
        val size = lower.size
        val y = DoubleArray(size)
        for (i in 0 until size) {
            var sum = rhs[i]
            for (k in 0 until i) sum -= lower[i][k] * y[k]
            y[i] = sum / lower[i][i]
        }
        for (i in size - 1 downTo 0) {
            var sum = y[i]
            for (k in i + 1 until size) sum -= lower[k][i] * y[k]
            y[i] = sum / lower[i][i]
        }
        return y
    }

    /** Factorization. */
    companion object {
        /**
         * Builds and factors `A Q Aᵀ` for the condition rows [jacobian] and the diagonal [variances] `Q`.
         *
         * @return the factor, or `null` if a pivot is not positive and finite, that is, the matrix is singular.
         */
        fun factor(
            jacobian: Array<DoubleArray>,
            variances: DoubleArray,
            cancellation: CancellationSignal,
        ): NormalSystem? {
            val size = jacobian.size
            val lower = Array(size) { DoubleArray(size) }
            for (j in 0 until size) {
                cancellation.checkpoint()
                val pivot = reduced(jacobian, variances, lower, j, j)
                if (!(pivot > 0.0 && pivot.isFinite())) return null
                lower[j][j] = StrictMath.sqrt(pivot)
                for (i in j + 1 until size) lower[i][j] = reduced(jacobian, variances, lower, i, j) / lower[j][j]
            }
            return NormalSystem(lower)
        }

        /** Entry `(i, j)` of `A Q Aᵀ` minus the part already covered by the first `j` columns of the factor. */
        private fun reduced(
            jacobian: Array<DoubleArray>,
            variances: DoubleArray,
            lower: Array<DoubleArray>,
            i: Int,
            j: Int,
        ): Double {
            var sum = weightedProduct(jacobian[i], jacobian[j], variances)
            for (k in 0 until j) sum -= lower[i][k] * lower[j][k]
            return sum
        }

        /** `Σ_k a_k · q_k · b_k`, one entry of `A Q Aᵀ`. */
        private fun weightedProduct(
            a: DoubleArray,
            b: DoubleArray,
            variances: DoubleArray,
        ): Double {
            var sum = 0.0
            for (k in variances.indices) sum += a[k] * variances[k] * b[k]
            return sum
        }
    }
}
