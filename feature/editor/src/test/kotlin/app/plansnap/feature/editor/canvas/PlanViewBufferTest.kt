package app.plansnap.feature.editor.canvas

import app.plansnap.feature.editor.state.PlanView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanViewBufferTest {
    private val a = PlanView(1.0, 0.0, 0.1)
    private val b = PlanView(2.0, 0.0, 0.1)
    private val c = PlanView(3.0, 0.0, 0.1)

    @Test
    fun `given an initial view when created then it is current`() {
        assertEquals(a, PlanViewBuffer(a).current)
        assertNull(PlanViewBuffer(null).current)
    }

    @Test
    fun `given two events before the state answers when the old echo arrives then the newer view is kept`() {
        val buffer = PlanViewBuffer(null)
        buffer.emit(a)
        buffer.emit(b)

        buffer.sync(a)

        assertEquals(b, buffer.current)
        buffer.sync(b)
        assertEquals(b, buffer.current)
    }

    @Test
    fun `given the state is still empty when the first view was emitted then it is not reset`() {
        val buffer = PlanViewBuffer(null)
        buffer.emit(a)

        buffer.sync(null)

        assertEquals(a, buffer.current)
    }

    @Test
    fun `given a view from outside when synced then it replaces the current view`() {
        val buffer = PlanViewBuffer(a)
        buffer.emit(b)

        buffer.sync(c)

        assertEquals(c, buffer.current)
    }

    @Test
    fun `given the same view when emitted then it is not recorded as pending`() {
        val buffer = PlanViewBuffer(a)
        buffer.emit(a)

        buffer.sync(b)

        assertEquals(b, buffer.current)
    }

    @Test
    fun `given more events than the history holds when the newest echo arrives then it is recognized`() {
        val buffer = PlanViewBuffer(null)
        val views = (1..100).map { PlanView(it.toDouble(), 0.0, 0.1) }
        views.forEach(buffer::emit)

        buffer.sync(views.last())

        assertEquals(views.last(), buffer.current)
    }
}
