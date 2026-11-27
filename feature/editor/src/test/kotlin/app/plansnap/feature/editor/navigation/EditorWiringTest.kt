package app.plansnap.feature.editor.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.opening.DefaultOpeningRules
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.canvas.PlanTap
import app.plansnap.feature.editor.details.DetailsHandler
import app.plansnap.feature.editor.opening.OpeningHandler
import app.plansnap.feature.editor.opening.OpeningIntent
import app.plansnap.feature.editor.rooms.RoomsHandler
import app.plansnap.feature.editor.rooms.RoomsIntent
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.feature.editor.state.ROOM_ID
import app.plansnap.feature.editor.state.closedProject
import app.plansnap.feature.editor.state.emptyProject
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The wiring of the sub-functions into the editor: every entry of the overflow menu reaches its handler and opens its
 * surface, and a tap on the plan is routed to the right function.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h900dp")
class EditorWiringTest {
    @get:Rule
    val compose = createComposeRule()

    private val scope = TestScope()
    private var exports = 0

    private fun show(project: Project): EditorViewModel {
        val rig = EditorRig(scope, project, AppSettings())
        val ids = SequentialIdGenerator("gen")
        rig.handlers += OpeningHandler(ids, DefaultOpeningRules())
        rig.handlers += RoomsHandler(ids, rig.settingsRepository, ApplicationScope(scope.backgroundScope))
        rig.handlers += DetailsHandler()
        val viewModel = rig.viewModel()
        compose.setContent {
            PlanSnapTheme {
                EditorRoute(
                    state = viewModel.state,
                    effects = viewModel.effects,
                    onIntent = viewModel::onIntent,
                    actions = EditorNavigationActions(onBack = {}, onOpenExport = { exports++ }),
                )
            }
        }
        settle()
        return viewModel
    }

    private fun settle() {
        scope.advanceUntilIdle()
        compose.waitForIdle()
        scope.runCurrent()
        compose.waitForIdle()
    }

    private fun openMenu() {
        compose.onNodeWithContentDescription("More actions").performClick()
        settle()
    }

    private fun choose(label: String) {
        openMenu()
        compose.onNodeWithText(label).performScrollTo().performClick()
        settle()
    }

    @Test
    fun `given a closed room when add door is chosen then the opening form opens`() {
        show(closedProject(3000))
        choose("Add door")
        compose.onNodeWithText("New opening").assertIsDisplayed()
    }

    @Test
    fun `given a room when its details are chosen then the details sheet opens`() {
        show(closedProject(3000))
        openMenu()
        compose.onNodeWithTag("menu-room-details").performScrollTo().performClick()
        settle()
        compose.onNodeWithText("Room details").assertIsDisplayed()
    }

    @Test
    fun `given a project when the area overview is chosen then it opens`() {
        show(closedProject(3000))
        choose("Rooms and areas")
        compose.onNodeWithText("Totals").assertExists()
    }

    @Test
    fun `given two measured rooms when dock room is chosen then the dock sheet opens`() {
        show(twoRoomsProject())
        choose("Dock room")
        compose.onNodeWithText("Host room").assertExists()
    }

    @Test
    fun `given a project when add room is chosen then a further room is selected`() {
        val viewModel = show(closedProject(3000))
        choose("Add room")
        assertEquals(
            2,
            viewModel.state.value.project
                ?.rooms
                ?.size,
        )
        assertNotEquals(ROOM_ID, viewModel.state.value.selectedRoomId)
    }

    @Test
    fun `given the grid switch when it is chosen then the menu shows the new value`() {
        val viewModel = show(closedProject(3000))
        val before = viewModel.state.value.settings.gridEnabled
        choose("Grid")
        assertEquals(!before, viewModel.state.value.settings.gridEnabled)
        openMenu()
        val item = compose.onNodeWithText("Grid").performScrollTo()
        if (before) item.assertIsOff() else item.assertIsOn()
    }

    @Test
    fun `given a project without rooms then openings and room details are disabled`() {
        show(emptyProject().copy(rooms = persistentListOf()))
        openMenu()
        compose.onNodeWithText("Add door").assertIsNotEnabled()
        compose.onNodeWithTag("menu-room-details").assertIsNotEnabled()
        compose.onNodeWithText("Add room").assertIsEnabled()
    }

    @Test
    fun `given the menu when export is chosen then the app callback runs`() {
        show(emptyProject())
        choose("Export")
        assertEquals(1, exports)
    }

    @Test
    fun `given a tap on a wall then its room is selected before its openings open`() {
        val viewModel = show(closedProject(3000))
        val tap = PlanTap(Vec2(0.0, 0.0), SceneTarget.Wall(ROOM_ID, WallId("w0")))
        assertEquals(
            listOf(CoreIntent.SelectRoom(ROOM_ID), OpeningIntent.OpenWall(ROOM_ID, WallId("w0"))),
            planTapIntents(tap, viewModel.state.value),
        )
        assertEquals(emptyList<Any>(), planTapIntents(PlanTap(Vec2(0.0, 0.0), null), viewModel.state.value))
    }

    @Test
    fun `given the host of a dock is chosen on the plan then a tap belongs to the dock alone`() {
        val viewModel = show(twoRoomsProject())
        viewModel.onIntent(RoomsIntent.OpenDock)
        viewModel.onIntent(RoomsIntent.StartPicking)
        settle()
        compose.onNodeWithText("Tap a wall or an opening of another room on the plan.").assertIsDisplayed()
        val host = OTHER_ROOM_ID.takeIf { viewModel.state.value.selectedRoomId == ROOM_ID } ?: ROOM_ID
        val wall =
            viewModel.state.value.project!!
                .rooms
                .single { it.id == host }
                .walls
                .first()
                .id
        val intents = planTapIntents(PlanTap(Vec2(0.0, 0.0), SceneTarget.Wall(host, wall)), viewModel.state.value)
        assertEquals(listOf(RoomsIntent.SelectHost(host, wall, null)), intents)
    }

    /** The closed room twice, the copy with ids of its own. */
    private fun twoRoomsProject(): Project {
        val project = closedProject(3000)
        val room = project.rooms.single()
        val walls = room.walls.map { it.copy(id = WallId("b-${it.id.value}")) }.toImmutableList()
        return project.copy(rooms = persistentListOf(room, room.copy(id = OTHER_ROOM_ID, walls = walls)))
    }

    private companion object {
        val OTHER_ROOM_ID = RoomId("b")
    }
}
