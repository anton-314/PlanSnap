package app.plansnap.core.geometry.determinacy.rig

/**
 * Port of CPython's `random.Random` (MT19937) for the calls used by the prototype rig
 * `docs/research/evaluation-prototype/stress.py`. With the same seed it yields the same sequence as Python 3.14, so the
 * ported rig generates the same rooms as the prototype.
 */
internal class PythonRandom(
    seed: Long,
) {
    private val state = IntArray(STATE_SIZE)
    private var index = STATE_SIZE

    init {
        require(seed >= 0) { "Only non-negative seeds are supported" }
        val key =
            if (seed ushr WORD_BITS == 0L) {
                intArrayOf(seed.toInt())
            } else {
                intArrayOf(seed.toInt(), (seed ushr WORD_BITS).toInt())
            }
        initByArray(key)
    }

    /** `random()`: a value in `[0, 1)` with 53 bits. */
    fun random(): Double {
        val a = nextWord() ushr RANDOM_SHIFT_A
        val b = nextWord() ushr RANDOM_SHIFT_B
        return (a * TWO_POW_26 + b) / TWO_POW_53
    }

    /** `uniform(a, b)`. */
    fun uniform(
        a: Double,
        b: Double,
    ): Double = a + (b - a) * random()

    /** `randrange(n)`. */
    fun randrange(n: Int): Int = randbelow(n)

    /** `randint(a, b)`, both ends included. */
    fun randint(
        a: Int,
        b: Int,
    ): Int = a + randbelow(b - a + 1)

    /** `choice(items)`. */
    fun <T> choice(items: List<T>): T = items[randbelow(items.size)]

    /** `shuffle(items)` in place. */
    fun <T> shuffle(items: MutableList<T>) {
        for (i in items.size - 1 downTo 1) {
            val j = randbelow(i + 1)
            val value = items[i]
            items[i] = items[j]
            items[j] = value
        }
    }

    /** `sample(population, k)` for populations of at most 21 elements, the pool variant of CPython. */
    fun <T> sample(
        population: List<T>,
        k: Int,
    ): List<T> {
        require(population.size <= SMALL_SET_SIZE && k in 0..population.size) { "Unsupported sample" }
        val pool = population.toMutableList()
        val n = pool.size
        return List(k) { i ->
            val j = randbelow(n - i)
            val chosen = pool[j]
            pool[j] = pool[n - i - 1]
            chosen
        }
    }

    private fun randbelow(n: Int): Int {
        require(n > 0) { "Empty range" }
        val bits = Int.SIZE_BITS - Integer.numberOfLeadingZeros(n)
        var r = getrandbits(bits)
        while (r >= n) r = getrandbits(bits)
        return r
    }

    private fun getrandbits(bits: Int): Int = (nextWord() ushr (WORD_BITS - bits)).toInt()

    /** Next tempered 32-bit output as an unsigned value. */
    private fun nextWord(): Long {
        if (index >= STATE_SIZE) twist()
        var y = state[index++]
        y = y xor (y ushr TEMPER_SHIFT_U)
        y = y xor ((y shl TEMPER_SHIFT_S) and TEMPER_MASK_B)
        y = y xor ((y shl TEMPER_SHIFT_T) and TEMPER_MASK_C)
        y = y xor (y ushr TEMPER_SHIFT_L)
        return y.toLong() and WORD_MASK
    }

    private fun twist() {
        for (i in 0 until STATE_SIZE) {
            val y = (state[i] and UPPER_MASK) or (state[(i + 1) % STATE_SIZE] and LOWER_MASK)
            var next = state[(i + SHIFT_SIZE) % STATE_SIZE] xor (y ushr 1)
            if (y and 1 != 0) next = next xor MATRIX_A
            state[i] = next
        }
        index = 0
    }

    private fun initGenrand(seed: Int) {
        state[0] = seed
        for (i in 1 until STATE_SIZE) {
            val previous = state[i - 1]
            state[i] = INIT_MULTIPLIER * (previous xor (previous ushr INIT_SHIFT)) + i
        }
        index = STATE_SIZE
    }

    private fun initByArray(key: IntArray) {
        initGenrand(ARRAY_SEED)
        var i = 1
        var j = 0
        repeat(maxOf(STATE_SIZE, key.size)) {
            val previous = state[i - 1]
            state[i] = (state[i] xor ((previous xor (previous ushr INIT_SHIFT)) * ARRAY_MULTIPLIER_1)) + key[j] + j
            i++
            j++
            if (i >= STATE_SIZE) {
                state[0] = state[STATE_SIZE - 1]
                i = 1
            }
            if (j >= key.size) j = 0
        }
        repeat(STATE_SIZE - 1) {
            val previous = state[i - 1]
            state[i] = (state[i] xor ((previous xor (previous ushr INIT_SHIFT)) * ARRAY_MULTIPLIER_2)) - i
            i++
            if (i >= STATE_SIZE) {
                state[0] = state[STATE_SIZE - 1]
                i = 1
            }
        }
        state[0] = UPPER_MASK
    }

    private companion object {
        const val STATE_SIZE = 624
        const val SHIFT_SIZE = 397
        const val WORD_BITS = 32
        const val WORD_MASK = 0xFFFF_FFFFL
        const val MATRIX_A = 0x9908B0DF.toInt()
        const val UPPER_MASK = 0x80000000.toInt()
        const val LOWER_MASK = 0x7FFFFFFF
        const val TEMPER_SHIFT_U = 11
        const val TEMPER_SHIFT_S = 7
        const val TEMPER_SHIFT_T = 15
        const val TEMPER_SHIFT_L = 18
        const val TEMPER_MASK_B = 0x9D2C5680.toInt()
        const val TEMPER_MASK_C = 0xEFC60000.toInt()
        const val INIT_MULTIPLIER = 1_812_433_253
        const val INIT_SHIFT = 30
        const val ARRAY_SEED = 19_650_218
        const val ARRAY_MULTIPLIER_1 = 1_664_525
        const val ARRAY_MULTIPLIER_2 = 1_566_083_941
        const val RANDOM_SHIFT_A = 5
        const val RANDOM_SHIFT_B = 6
        const val TWO_POW_26 = 67_108_864L
        const val TWO_POW_53 = 9_007_199_254_740_992.0
        const val SMALL_SET_SIZE = 21
    }
}
