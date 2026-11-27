package app.plansnap.feature.editor.rooms

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.common.units.Length
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.Project
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.shape.RecordingHost
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.testing.fixtures.fakes.FakeSettingsRepository
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The dock sheet and the hint bar as the user operates them, with the real [RoomsHandler] behind them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w800dp-h1280dp-port-notnight-mdpi")
class RoomsHostTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var host: RecordingHost
    private lateinit var handler: RoomsHandler

    private val draft: DockDraft? get() =
        host.state.value
            .section(RoomsState::class)
            ?.dock

    private fun onIntent(intent: EditorIntent) {
        handler.handle(intent, host)
    }

    private fun open(state: EditorUiState = roomsState()) {
        host = RecordingHost(state)
        handler =
            RoomsHandler(
                SequentialIdGenerator("gen"),
                FakeSettingsRepository(AppSettings()),
                ApplicationScope(CoroutineScope(Job())),
            )
        compose.setContent {
            val current by host.state.collectAsState()
            PlanSnapTheme(darkTheme = false) {
                RoomsHost(current, ::onIntent)
            }
        }
        compose.runOnUiThread { onIntent(RoomsIntent.OpenDock) }
        compose.waitForIdle()
    }

    private fun tap(description: String) {
        compose.onNodeWithContentDescription(description).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun key(label: String) = compose.onNode(hasText(label) and hasClickAction())

    private fun type(vararg labels: String) {
        labels.forEach { key(it).performScrollTo().performClick() }
        compose.waitForIdle()
    }

    private fun chooseWalls() {
        tap("Host wall 1, 400.0 cm")
        tap("Own wall 1, 200.0 cm")
    }

    @Test
    fun `given the sheet when opened then the offset is zero and it cannot be confirmed yet`() {
        open()

        compose.onNodeWithText("Room to dock: guest").assertExists()
        compose.onNodeWithText("+0").assertExists()
        compose.onNodeWithContentDescription("Offset 0 centimeters, Along the direction").assertExists()
        compose.onNodeWithText("Choose the host wall and your own wall to continue.").assertExists()
        key("OK").assertIsNotEnabled()
    }

    @Test
    fun `given a chosen host wall then its measuring direction is shown in words and as an arrow`() {
        open()

        chooseWalls()

        compose.onNodeWithText("1 → 2").assertExists()
        compose.onNodeWithContentDescription("Direction of the host wall: from corner 1 to corner 2").assertExists()
        compose.onNodeWithText("Host wall 1: it runs from corner 1 to corner 2.", substring = true).assertExists()
        compose.onNodeWithContentDescription("Host wall 1, 400.0 cm").assertIsSelected()
    }

    @Test
    fun `given the last wall of a closed host then its direction ends at corner 1`() {
        open()

        tap("Host wall 4, 300.0 cm")

        compose.onNodeWithText("4 → 1").assertExists()
    }

    @Test
    fun `given the last wall of an open host then its direction ends at the next corner`() {
        val project = twoRoomProject()
        val openHost =
            project.rooms[0].copy(
                walls =
                    project.rooms[0]
                        .walls
                        .take(2)
                        .toImmutableList(),
                corners =
                    project.rooms[0]
                        .corners
                        .take(2)
                        .toImmutableList(),
                openings = persistentListOf(),
                isClosed = false,
            )
        open(roomsState(project.copy(rooms = persistentListOf(openHost, project.rooms[1]))))

        tap("Host wall 2, 300.0 cm")

        compose.onNodeWithText("2 → 3").assertExists()
    }

    @Test
    fun `given both walls when confirmed then the room is docked flush`() {
        open()
        chooseWalls()

        key("OK").performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()

        val link = DockLink(HOST_ID, WallId("host0"), WallId("guest0"), Length.ZERO, null)
        assertEquals(listOf<ProjectEdit>(ProjectEdit.DockRoom(GUEST_ID, link)), host.submitted)
        assertNull(draft)
        compose.onNodeWithText("Room to dock: guest").assertDoesNotExist()
    }

    @Test
    fun `given the keypad when digits are typed then they replace the zero and can run backwards`() {
        open()
        chooseWalls()

        type("5", "0")
        compose.onNodeWithText("+50").assertExists()
        compose.onNodeWithText("Against the direction").performScrollTo().performClick()
        compose.onNodeWithText("−50").assertExists()
        key("OK").performScrollTo().performClick()
        compose.waitForIdle()

        val edit = host.submitted.single() as ProjectEdit.DockRoom
        assertEquals(Length(-500), edit.link.hostOffset)
    }

    @Test
    fun `given an opening when chosen then the sheet says where the offset counts from`() {
        open()

        compose.onNodeWithText("Door on wall 1, at 50.0 cm").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("The offset counts from the start of the opening.", substring = true).assertExists()
        assertEquals(DOOR_ID, draft?.hostOpeningId)
    }

    @Test
    fun `given docks in a circle then the sheet explains it and cannot be confirmed`() {
        val project = twoRoomProject()
        val guest = project.rooms[1]
        val link = DockLink(GUEST_ID, WallId("guest0"), WallId("host0"), Length.ZERO, null)
        val hostRoom = project.rooms[0].copy(placement = project.rooms[0].placement.copy(dock = link))
        open(roomsState(project.copy(rooms = persistentListOf(hostRoom, guest))))

        chooseWalls()

        compose.onNodeWithText("Docking in a circle is not possible.", substring = true).assertExists()
        key("OK").assertIsNotEnabled()
    }

    @Test
    fun `given the selection on the plan then only a hint is shown and cancel brings the sheet back`() {
        open()

        compose.onNodeWithText("Choose on plan").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tap a wall or an opening of another room on the plan.").assertExists()
        compose.onNodeWithText("Room to dock: guest").assertDoesNotExist()

        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Room to dock: guest").assertExists()
    }

    @Test
    fun `given the room to dock when it disappears then the sheet closes itself`() {
        open()

        compose.runOnUiThread {
            host.update { it.copy(project = withoutGuest(checkNotNull(it.project))) }
        }
        compose.waitForIdle()

        assertTrue(draft == null)
    }

    private fun withoutGuest(project: Project): Project =
        project.copy(rooms = project.rooms.filter { it.id != GUEST_ID }.toImmutableList())
}
