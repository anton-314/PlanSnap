package app.plansnap.feature.editor.opening

import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.canvas.PlanTap
import app.plansnap.feature.editor.input.editorState
import app.plansnap.feature.editor.shape.SHAPE_ROOM
import app.plansnap.feature.editor.shape.roomOf
import app.plansnap.feature.editor.shape.stateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningEntryPointsTest {
    private val state = stateOf(openingRoom(door("o1", "w0", 0, 900)))

    private fun tap(target: SceneTarget?) = PlanTap(Vec2(0.0, 0.0), target)

    @Test
    fun `given a tap on an opening when translated then its form opens`() {
        val intent = openingTapIntent(tap(SceneTarget.Opening(SHAPE_ROOM, OpeningId("o1"))), state)
        assertEquals(OpeningIntent.Edit(SHAPE_ROOM, OpeningId("o1")), intent)
    }

    @Test
    fun `given a tap on a wall when translated then the list of its openings opens`() {
        val intent = openingTapIntent(tap(SceneTarget.Wall(SHAPE_ROOM, WallId("w2"))), state)
        assertEquals(OpeningIntent.OpenWall(SHAPE_ROOM, WallId("w2")), intent)
    }

    @Test
    fun `given taps on other things when translated then they are not handled`() {
        assertNull(openingTapIntent(tap(null), state))
        assertNull(openingTapIntent(tap(SceneTarget.Corner(SHAPE_ROOM, 1)), state))
        assertNull(openingTapIntent(tap(SceneTarget.RoomBody(SHAPE_ROOM)), state))
        assertNull(openingTapIntent(tap(SceneTarget.Diagonal(SHAPE_ROOM, DiagonalId("d"))), state))
    }

    @Test
    fun `given targets that no longer exist when translated then they are not handled`() {
        assertNull(openingTapIntent(tap(SceneTarget.Opening(SHAPE_ROOM, OpeningId("gone"))), state))
        assertNull(openingTapIntent(tap(SceneTarget.Wall(SHAPE_ROOM, WallId("gone"))), state))
        assertNull(openingTapIntent(tap(SceneTarget.Wall(RoomId("other"), WallId("w0"))), state))
        assertNull(openingTapIntent(tap(SceneTarget.Wall(SHAPE_ROOM, WallId("w0"))), editorState()))
    }

    @Test
    fun `given a selected room with walls when the menu is built then three entries add to its last wall`() {
        val entries = openingMenuEntries(stateOf(roomOf(4000, 3000, 4000)))
        assertEquals(
            listOf(R.string.opening_add_door, R.string.opening_add_window, R.string.opening_add_passage),
            entries.map { it.label },
        )
        assertTrue(entries.all { it.enabled })
        assertEquals(
            OpeningType.entries.map { OpeningIntent.AddToSelectedRoom(it) },
            entries.map { it.intent },
        )
    }

    @Test
    fun `given a room without walls when the menu is built then the entries are disabled`() {
        val entries = openingMenuEntries(stateOf(roomOf()))
        assertEquals(3, entries.size)
        assertFalse(entries.any { it.enabled })
        assertTrue(openingMenuEntries(editorState()).none { it.enabled })
    }
}
