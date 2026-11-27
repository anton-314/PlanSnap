package app.plansnap.e2e

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.IntentCompat
import app.plansnap.MainActivity
import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.runtime.EpochClock
import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.repository.EditorDraftRepository
import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.di.RuntimeModule
import app.plansnap.testing.fixtures.runtime.FakeEpochClock
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Number of confirmed edits of the full session, one undo step each. */
private const val SESSION_EDITS = 16

/** Gap that the measurements of the first room leave. */
private const val GAP_MM = 10.0

/** Allowed deviation of a placed point, covering the adjustment of the 10 mm gap. */
private const val PLACEMENT_TOLERANCE_MM = 15.0

/**
 * Whole editing sessions in the real app (T-E2E-003, UX section 3): [MainActivity] with the production navigation,
 * view models, editor handlers, domain services, Room database, settings store and exporters. A session starts by
 * creating a project in the project list; nothing is seeded. Only dispatchers, clock and ids are replaced, so that all
 * application code is confined to the main looper and ids are repeatable.
 *
 * The screen is 320 dp wide on purpose: on a 360 x 640 dp Robolectric screen an `AlertDialog` with an
 * `OutlinedTextField`, as the project name dialog is one, never lets Compose become idle, even without any application
 * code.
 */
@HiltAndroidTest
@UninstallModules(RuntimeModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h891dp-mdpi", application = HiltTestApplication::class)
class EditorSessionE2ETest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var projects: ProjectRepository

    @Inject lateinit var drafts: EditorDraftRepository

    @Inject lateinit var settings: SettingsRepository

    private lateinit var app: E2eRobot

    @Before
    fun setUp() {
        hilt.inject()
        app = E2eRobot(compose, projects, drafts, settings)
    }

    @Test
    fun `given a new project when a whole session is edited then every step is stored and survives reopening`() {
        createProject()
        closeFirstHint()
        measureAndAdjustFirstRoom()
        addDoor()
        measureSecondRoom()
        dockSecondRoom()
        editDetails()
        checkOverviewAndChangeProjectHeight()
        val final = undoAndRedoEverything()
        exportAsSvg()
        leaveAndReopen(final)
    }

    @Test
    fun `given unconfirmed input when the screen is recreated and left then measuring continues where it was`() {
        createProject()
        app.measureWall("400", roomIndex = 0)
        app.type("12")
        app.awaitDraft("the typed input as draft") { it?.inputText == "12" }

        compose.activityRule.scenario.recreate()
        app.awaitNode(hasContentDescription("Wall 2, 12 centimeters"))

        app.tapDescription("Back")
        app.awaitText("New project")
        app.tapText(PROJECT_NAME)
        app.awaitNode(hasContentDescription("Wall 2, 12 centimeters"))
        assertEquals(
            listOf(4000L),
            app
                .stored()
                .rooms
                .single()
                .rawLengths(),
        )

        repeat(2) { app.tapDescription("Delete last character") }
        listOf("300", "400", "300").forEach { app.measureWall(it, roomIndex = 0) }
        app.tapDescription("Close room")
        app.awaitText("Shape confirmed")
        app.awaitText("12.00 m²")
        val room = app.awaitStored("the closed room") { it.rooms.single().isClosed }.rooms.single()
        assertEquals(listOf(4000L, 3000L, 4000L, 3000L), room.rawLengths())
        assertNull(room.adjustment)
        app.awaitDraft("no leftover input") { it?.inputText.isNullOrEmpty() }
    }

    private fun createProject() {
        app.createProject(PROJECT_NAME)
        app.awaitText("Enter the first wall")

        val created = app.stored()
        assertEquals(PROJECT_NAME, created.name)
        assertEquals(listOf(emptyList<Long>()), created.rooms.map { it.rawLengths() })
    }

    private fun closeFirstHint() {
        val hint = hasText("Hint: Type the wall length on the number pad, then tap OK.")
        app.awaitNode(hint)
        app.tapDescription("Close hint")
        app.awaitGone(hint)
        app.awaitSettings("the dismissed hint") { EditorHint.NUMPAD in it.dismissedHints }
    }

    private fun measureAndAdjustFirstRoom() {
        listOf("400", "300", "400", "301").forEach { app.measureWall(it, roomIndex = 0) }
        app.tapDescription("Close room")
        app.awaitStored("the closed first room") { it.rooms[0].isClosed }
        app.awaitText("Shape not confirmed")

        app.tapText("Adjust")
        app.scrollAndTap(hasText("Apply adjustment"))
        app.awaitText("Adjusted")

        val room = app.awaitStored("the adjustment") { it.rooms[0].adjustment != null }.rooms[0]
        assertEquals(listOf(4000L, 3000L, 4000L, 3010L), room.rawLengths())
        // The overlay distributes the 10 mm gap: no wall moves further than that, and one of them has to move.
        val corrections =
            room.adjustment!!.adjustedLengthsMm.zip(room.rawLengths()) { adjusted, raw -> abs(adjusted - raw) }
        assertTrue("corrections $corrections", corrections.all { it <= GAP_MM } && corrections.any { it > 0.0 })
    }

    private fun addDoor() {
        app.menu("Add door")
        app.scrollAndTap(hasText("Wall 3"))
        app.enter("Distance (cm)", "50")
        app.enter("Width (cm)", "90")
        app.enter("Height (cm)", "210")
        app.scrollAndTap(hasText("Save opening"))
        app.awaitGone(hasText("Save opening"))

        val room = app.awaitStored("the door") { it.rooms[0].openings.isNotEmpty() }.rooms[0]
        val door = room.openings.single()
        assertEquals(OpeningType.DOOR, door.type)
        assertEquals(room.walls[2].id, door.wallId)
        assertEquals(Length(500), door.offset)
        assertEquals(Length(900), door.width)
        assertEquals(Length(2100), door.height)
    }

    private fun measureSecondRoom() {
        val first = app.stored().rooms[0]
        app.menu("Add room")
        app.awaitStored("the second room") { it.rooms.size == 2 }
        app.awaitNode(hasContentDescription("Wall 1, no value entered"))
        listOf("200", "150", "200", "150").forEach { app.measureWall(it, roomIndex = 1) }
        app.tapDescription("Close room")
        app.awaitText("Shape confirmed")
        app.awaitText("3.00 m²")

        val project = app.awaitStored("the closed second room") { it.rooms[1].isClosed }
        assertEquals(listOf(2000L, 1500L, 2000L, 1500L), project.rooms[1].rawLengths())
        assertEquals("measuring room 2 leaves room 1 alone", first, project.rooms[0])
        // The first room reaches from x = 0 to its first wall of 4 m, a new room is placed to the right of it.
        val secondOrigin = project.rooms[1].placement.originX
        assertTrue("room 2 starts at x = $secondOrigin", secondOrigin > first.walls[0].length.mm)
    }

    private fun dockSecondRoom() {
        app.menu("Dock room")
        leavePlanSelectionWithBack()
        app.scrollAndTap(hasContentDescription("Host wall 2,", substring = true))
        app.scrollAndTap(hasContentDescription("Own wall 1,", substring = true))
        app.scrollAndTap(hasContentDescription("Dock room"))
        app.awaitGone(hasText("Room to dock:", substring = true))

        val project = app.awaitStored("the dock") { it.rooms[1].placement.dock != null }
        val (host, guest) = project.rooms
        assertEquals(
            DockLink(host.id, host.walls[1].id, guest.walls[0].id, Length(0)),
            guest.placement.dock,
        )
        assertDockedOutsideHostWall(host, guest)
    }

    /** The system back action ends only the choice of the host on the plan and returns to the dock sheet. */
    private fun leavePlanSelectionWithBack() {
        val sheet = hasText("Room to dock:", substring = true)
        val pickingHint = hasText("Tap a wall or an opening of another room on the plan.")
        app.scrollAndTap(hasText("Choose on plan"))
        app.awaitNode(pickingHint)
        app.awaitGone(sheet)

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        app.awaitNode(sheet)
        app.awaitGone(pickingHint)
    }

    /**
     * Host wall 2 runs upwards at x = 4 m from y = 0. The own wall of 2 m lies outside of it at the distance of the
     * host wall thickness, flush with the start of the host wall (UX section 3).
     */
    private fun assertDockedOutsideHostWall(
        host: Room,
        guest: Room,
    ) {
        val placement = guest.placement
        val length =
            guest.walls[0]
                .length.mm
                .toDouble()
        val xs = listOf(placement.originX, placement.originX + length * cos(placement.rotationRad))
        val ys = listOf(placement.originY, placement.originY + length * sin(placement.rotationRad))
        val expectedX =
            host.walls[0].length.mm +
                host.walls[1]
                    .thickness.mm
                    .toDouble()
        xs.forEach { assertEquals("x of the docked wall", expectedX, it, PLACEMENT_TOLERANCE_MM) }
        assertEquals("lower end of the docked wall", 0.0, ys.min(), PLACEMENT_TOLERANCE_MM)
        assertEquals("upper end of the docked wall", length, ys.max(), PLACEMENT_TOLERANCE_MM)
    }

    private fun editDetails() {
        app.menu("Room details")
        app.enter("Name", ROOM_NAME)
        app.scrollAndTap(hasText("Bathroom"))
        app.enter("Height (cm)", "260")
        app.enter("Area factor (%)", "50")
        app.scrollAndTap(hasText("Apply"))
        app.awaitGone(hasText("Area factor (%)"))

        val room = app.awaitStored("the room details") { it.rooms[1].name == ROOM_NAME }.rooms[1]
        assertEquals(RoomTypeKey("bathroom"), room.typeKey)
        assertEquals(Length(2600), room.heightOverride)
        assertEquals(AreaFactor(50), room.areaFactor)
    }

    private fun checkOverviewAndChangeProjectHeight() {
        app.menu("Rooms and areas")
        app.awaitText("Rooms and areas")
        listOf(
            "Measured area: 15.02 m²",
            "Credited area: 13.52 m²",
        ).forEach { app.awaitNode(hasText(it)) }
        app.awaitNode(hasText("$ROOM_NAME · Bathroom"))
        assertTrue(!app.exists(hasText("not included in the totals", substring = true)))

        app.enter("Height (cm)", "270")
        app.scrollAndTap(hasText("Apply default height"))
        app.awaitStored("the project height") { it.defaultHeight == Length(2700) }
        app.scrollAndTap(hasText("Close") and !hasContentDescription("Close room"))
        app.awaitGone(hasText("Apply default height"))
    }

    /** Undoes the whole session down to the new project and redoes it; returns the final stored project. */
    private fun undoAndRedoEverything(): Project {
        val final = app.stored()
        val undone = stepThroughHistory("Undo", final)
        assertEquals("one undo step per confirmed edit", SESSION_EDITS, undone.size)

        val empty = undone.last()
        assertEquals(Length(2500), empty.defaultHeight)
        assertEquals(listOf(final.rooms[0].id), empty.rooms.map { it.id })
        assertEquals(Room(final.rooms[0].id), empty.rooms.single())

        val redone = stepThroughHistory("Redo", empty)
        assertEquals(SESSION_EDITS, redone.size)
        // Every redo restores exactly the state the matching undo had left.
        val expected = (listOf(final) + undone.dropLast(1)).reversed()
        assertEquals(expected.map { it.content() }, redone.map { it.content() })
        return redone.last()
    }

    /** Taps [button] until it is disabled and returns the stored project after each tap. */
    private fun stepThroughHistory(
        button: String,
        start: Project,
    ): List<Project> {
        val states = mutableListOf<Project>()
        var previous = start
        val enabled = hasContentDescription(button) and isEnabled()
        while (app.exists(enabled)) {
            check(states.size <= SESSION_EDITS) { "$button does not end" }
            app.tapDescription(button)
            val before = previous.content()
            previous = app.awaitStored("$button step ${states.size + 1}") { it.content() != before }
            states += previous
        }
        app.node(hasContentDescription(button)).assertIsNotEnabled()
        return states
    }

    private fun exportAsSvg() {
        app.menu("Export")
        app.awaitText("Format")
        app.tapText("SVG")
        app.awaitNode(hasText("Result: scale", substring = true))
        assertTrue("no export warnings", !app.exists(hasText("Please note")))

        val application = RuntimeEnvironment.getApplication()
        app.tapText("Share")
        app.await("the share sheet") { shadowOf(application).peekNextStartedActivity() != null }
        val chooser = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
        val uri = IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)!!
        val svg = application.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        assertTrue("an SVG document", svg.contains("<svg"))
        assertTrue("the room name is drawn", svg.contains(ROOM_NAME))
    }

    private fun leaveAndReopen(final: Project) {
        app.tapDescription("Back")
        app.awaitNode(hasContentDescription("Undo"))
        app.tapDescription("Back")
        app.awaitText("New project")
        app.awaitNode(hasText("2 rooms", substring = true))

        app.tapText(PROJECT_NAME)
        app.awaitNode(hasContentDescription("Floor plan with 2 rooms and 8 walls"))
        app.awaitText("Adjusted")
        app.node(hasContentDescription("Undo")).assertIsNotEnabled()
        app.node(hasContentDescription("Redo")).assertIsNotEnabled()

        app.menu("Rooms and areas")
        app.awaitNode(hasText("Measured area: 15.02 m²"))
        app.awaitNode(hasText("$ROOM_NAME · Bathroom"))
        assertTrue(!app.exists(hasText("Shape not confirmed")))
        assertEquals(final, app.stored())
    }

    private fun Project.content(): Project = copy(updatedAtEpochMs = 0)

    private fun Room.rawLengths(): List<Long> = walls.map { it.length.mm }

    /** Production services, but everything runs on the main looper; ids and time are repeatable. */
    @Module
    @InstallIn(SingletonComponent::class)
    object MainLooperRuntimeModule {
        @Provides
        @Singleton
        fun dispatchers(): DispatcherProvider =
            object : DispatcherProvider {
                override val main: CoroutineDispatcher = Dispatchers.Main.immediate
                override val default: CoroutineDispatcher = Dispatchers.Main
                override val io: CoroutineDispatcher = Dispatchers.Main
            }

        @Provides
        @Singleton
        fun clock(): EpochClock = FakeEpochClock()

        @Provides
        @Singleton
        fun ids(): IdGenerator = SequentialIdGenerator("id")

        @Provides
        @Singleton
        fun applicationScope(dispatchers: DispatcherProvider): ApplicationScope = ApplicationScope.create(dispatchers)
    }

    private companion object {
        const val PROJECT_NAME = "Flat"

        /** Distinct from every room type name, so that finding it proves the typed name arrived. */
        const val ROOM_NAME = "Lavender"
    }
}
