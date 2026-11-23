package app.plansnap.testing.fixtures.runtime

import app.plansnap.core.common.runtime.IdGenerator
import java.util.concurrent.atomic.AtomicLong

/**
 * [IdGenerator] that returns `<prefix>-1`, `<prefix>-2`, and so on. The sequence is the same in every run.
 *
 * @param prefix non-blank prefix, so that every generated id is non-blank.
 */
class SequentialIdGenerator(
    private val prefix: String = "id",
) : IdGenerator {
    private val counter = AtomicLong(0L)

    init {
        require(prefix.isNotBlank()) { "Prefix must not be blank" }
    }

    override fun newId(): String = "$prefix-${counter.incrementAndGet()}"
}
