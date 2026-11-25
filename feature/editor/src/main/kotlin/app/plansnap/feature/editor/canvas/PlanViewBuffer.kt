package app.plansnap.feature.editor.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.plansnap.feature.editor.state.PlanView

/**
 * Gesture-side copy of the plan view. The state layer owns the view, but it reaches the canvas one frame after a
 * gesture event. Without a buffer, a second event in the same frame would start from the stale view and lose its
 * movement, and the late echo of the first event would make the plan jump back.
 *
 * The buffer therefore applies gestures to [current] immediately, remembers the views it has passed on, and takes a
 * view from the state layer over only if it is not the echo of one of them (a restored or externally set view).
 */
internal class PlanViewBuffer(
    initial: PlanView?,
) {
    private val emitted = ArrayDeque<PlanView>()

    /** The view to draw and to apply the next gesture event to, `null` while nobody has set a view. */
    var current: PlanView? by mutableStateOf(initial)
        private set

    /** Records a view that the canvas produced and passes on to the state layer. */
    fun emit(view: PlanView) {
        if (view == current) return
        current = view
        emitted.addLast(view)
        while (emitted.size > MAX_PENDING) emitted.removeFirst()
    }

    /**
     * Offers the view that the state layer currently holds. `null` while views are still on their way is the state
     * before the first one arrived and is ignored.
     */
    fun sync(fromState: PlanView?) {
        if (fromState == null && emitted.isNotEmpty()) return
        val echo = fromState != null && emitted.contains(fromState)
        if (echo) {
            do {
                val oldest = emitted.removeFirst()
            } while (oldest != fromState)
        } else {
            emitted.clear()
            current = fromState
        }
    }

    private companion object {
        /** More views than one frame can produce, so an echo is always found. */
        const val MAX_PENDING = 32
    }
}
