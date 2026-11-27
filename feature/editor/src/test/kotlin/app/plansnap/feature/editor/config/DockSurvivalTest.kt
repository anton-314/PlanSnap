package app.plansnap.feature.editor.config

import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import app.plansnap.core.common.units.Length
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.rooms.DockDraft
import app.plansnap.feature.editor.rooms.GUEST_ID
import app.plansnap.feature.editor.rooms.HOST_ID
import app.plansnap.feature.editor.rooms.RoomsState
import app.plansnap.feature.editor.rooms.twoRoomProject
import app.plansnap.feature.editor.rooms.worldCorners
import app.plansnap.feature.editor.state.CoreIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Docking, with the sheet and with the choice of the host on the plan, across window changes (REQ-UX-009). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DockSurvivalTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun openDockSheet(): ConfigEditor {
        val editor = ConfigEditor(compose, twoRoomProject()).launch()
        editor.viewModel.onIntent(CoreIntent.SelectRoom(GUEST_ID))
        editor.settle()
        editor.chooseFromMenu("Dock room")
        return editor
    }

    private val ConfigEditor.draft: DockDraft? get() = state.section(RoomsState::class)?.dock

    private fun ConfigEditor.storedLink() =
        rig.stored.rooms
            .first { it.id == GUEST_ID }
            .placement.dock

    @Test
    fun `given a filled dock sheet when the window changes then every choice stays and the room docks`() {
        val editor = openDockSheet()
        editor.tapDescribed("Host wall 2, 300.0 cm")
        editor.tapDescribed("Own wall 4, 200.0 cm")
        editor.tapSheet("Against the direction")
        editor.sheetType("2", "5")
        val filled = editor.draft

        editor.tour {
            assertEquals(filled, editor.draft)
            compose.onNodeWithText("Host room").assertExists()
        }
        assertNull(editor.storedLink())

        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        editor.sheetType("OK")
        val link = checkNotNull(editor.storedLink())
        assertEquals(WallId("host1"), link.hostWallId)
        assertEquals(WallId("guest3"), link.ownWallId)
        assertEquals(Length(-250), link.hostOffset)
    }

    @Test
    fun `given the choice on the plan when the window changes then it stays active and the plan can be tapped`() {
        val editor = openDockSheet()
        editor.tapDescribed("Own wall 4, 200.0 cm")
        editor.tapSheet("Choose on plan")
        val picking = editor.draft
        assertEquals(true, picking?.picking)

        editor.tour {
            assertEquals(picking, editor.draft)
            compose.onNodeWithText("Tap a wall or an opening of another room on the plan.").assertExists()
            compose.onNodeWithText("Host room").assertDoesNotExist()
        }

        tapHostWall(editor, WALL_INDEX)
        val picked = checkNotNull(editor.draft)
        assertEquals(false, picked.picking)
        assertEquals(HOST_ID, picked.hostRoomId)
        assertEquals(WallId("host$WALL_INDEX"), picked.hostWallId)
        assertEquals(WallId("guest3"), picked.ownWallId)
        compose.onNodeWithText("Host room").assertExists()
    }

    /** Taps the middle of the wall [index] of the host, where the current view shows it. */
    private fun tapHostWall(
        editor: ConfigEditor,
        index: Int,
    ) {
        val state = editor.state
        val host = checkNotNull(state.project).rooms.first { it.id == HOST_ID }
        val corners = worldCorners(host, state.evaluations.getValue(HOST_ID))
        val from = corners[index]
        val to = corners[(index + 1) % corners.size]
        val middle = Vec2((from.x + to.x) / 2, (from.y + to.y) / 2)
        val point = editor.onPlan(middle)
        editor.plan().performTouchInput { click(point) }
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_TIMEOUT_MS)
        editor.settle()
    }

    private companion object {
        /** The right wall of the host. */
        const val WALL_INDEX = 1

        /** The canvas waits this long for a second tap before it reports a single tap. */
        const val DOUBLE_TAP_TIMEOUT_MS = 500L
    }
}
