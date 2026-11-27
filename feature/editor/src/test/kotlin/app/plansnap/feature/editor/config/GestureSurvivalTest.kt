package app.plansnap.feature.editor.config

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performTouchInput
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.feature.editor.rooms.GUEST_ID
import app.plansnap.feature.editor.rooms.RoomDrag
import app.plansnap.feature.editor.rooms.RoomsState
import app.plansnap.feature.editor.rooms.twoRoomProject
import app.plansnap.feature.editor.rooms.worldCorners
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A room that is being dragged while the window changes (REQ-UX-009). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GestureSurvivalTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val ConfigEditor.drag: RoomDrag? get() = state.section(RoomsState::class)?.drag

    /** Presses long on the middle of the guest room and moves the finger, without lifting it. */
    private fun ConfigEditor.startDrag() {
        val room = checkNotNull(state.project).rooms.first { it.id == GUEST_ID }
        val corners = worldCorners(room, state.evaluations.getValue(GUEST_ID))
        val middle = onPlan(Vec2(corners.map { it.x }.average(), corners.map { it.y }.average()))
        plan().performTouchInput { down(middle) }
        compose.mainClock.advanceTimeBy(LONG_PRESS_MS)
        settle()
        plan().performTouchInput { moveBy(Offset(MOVE_PX, MOVE_PX)) }
        settle()
    }

    @Test
    fun `given a room being dragged when the window changes then the drag is discarded and nothing is moved`() {
        val editor = ConfigEditor(compose, twoRoomProject()).launch()
        val before = editor.rig.stored
        editor.startDrag()
        assertNotNull(editor.drag)

        editor.resize(DeviceConfig.PHONE_LANDSCAPE)

        assertNull(editor.drag)
        assertEquals(before, editor.rig.stored)
        assertEquals(before, editor.state.project)
        assertEquals(GUEST_ID, editor.state.selectedRoomId)
    }

    private companion object {
        /** Longer than the long press timeout of the platform. */
        const val LONG_PRESS_MS = 600L
        const val MOVE_PX = 40f
    }
}
