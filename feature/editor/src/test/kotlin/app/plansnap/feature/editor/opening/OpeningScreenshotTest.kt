package app.plansnap.feature.editor.opening

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Room
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.shape.SHAPE_ROOM
import app.plansnap.feature.editor.shape.stateOf
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the opening sheets: empty, with a conflict, in German and at 200 % font size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h800dp-port-notnight-mdpi")
class OpeningScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val room = openingRoom(door("o1", "w0", 0, 900), door("o2", "w0", 3500, 900))
    private val emptyRoom = openingRoom()

    private fun form(
        intent: OpeningIntent,
        vararg changes: OpeningIntent,
        roomOf: Room = emptyRoom,
    ): Pair<OpeningSheet.Form, OpeningState> {
        val harness = OpeningHarness(stateOf(roomOf))
        harness.send(intent, *changes)
        return harness.form to harness.opening
    }

    private fun newDoor(vararg changes: OpeningIntent) =
        form(OpeningIntent.Add(SHAPE_ROOM, WallId("w0"), OpeningType.DOOR), *changes)

    @Test
    fun `given an empty wall when the sheet is shown then it matches the baseline`() =
        capture("opening-wall-empty.png") {
            WallSheetContent(
                emptyRoom,
                OpeningSheet.Wall(SHAPE_ROOM, WallId("w1")),
                emptyMap(),
                LengthUnit.CENTIMETER,
            ) {}
        }

    @Test
    fun `given a wall with openings when the sheet is shown then it matches the baseline`() {
        val statuses = stateOf(room).evaluations.getValue(SHAPE_ROOM).openingStatuses
        capture("opening-wall-list.png") {
            WallSheetContent(room, OpeningSheet.Wall(SHAPE_ROOM, WallId("w0")), statuses, LengthUnit.CENTIMETER) {}
        }
    }

    @Test
    fun `given a new door when the form is shown then it matches the baseline`() {
        val (sheet, state) = newDoor()
        capture("opening-form-new.png") { OpeningFormContent(emptyRoom, sheet, state, LengthUnit.CENTIMETER) {} }
    }

    @Test
    fun `given a door that is too wide when the form is shown then the conflict is in the baseline`() {
        val (sheet, state) = newDoor(OpeningIntent.WidthChanged("500"))
        capture("opening-form-conflict.png") { OpeningFormContent(emptyRoom, sheet, state, LengthUnit.CENTIMETER) {} }
    }

    @Test
    fun `given a stored opening when the form is shown dark then it matches the baseline`() {
        val (sheet, state) = form(OpeningIntent.Edit(SHAPE_ROOM, OpeningId("o1")), roomOf = room)
        capture("opening-form-edit-dark.png", dark = true) {
            OpeningFormContent(room, sheet, state, LengthUnit.CENTIMETER) {}
        }
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h800dp-port-notnight-mdpi")
    fun `given a conflict when the form is german then it matches the baseline`() {
        val (sheet, state) = newDoor(OpeningIntent.WidthChanged("500"))
        capture(
            "opening-form-conflict-de.png",
        ) { OpeningFormContent(emptyRoom, sheet, state, LengthUnit.CENTIMETER) {} }
    }

    @Test
    fun `given a conflict when the font scale is 2 then it matches the baseline`() {
        val (sheet, state) = newDoor(OpeningIntent.WidthChanged("500"))
        capture("opening-form-conflict-fontscale-2.png", fontScale = 2f) {
            OpeningFormContent(emptyRoom, sheet, state, LengthUnit.CENTIMETER) {}
        }
    }

    @Test
    fun `given a wall with openings when the font scale is 2 then it matches the baseline`() {
        val statuses = stateOf(room).evaluations.getValue(SHAPE_ROOM).openingStatuses
        capture("opening-wall-list-fontscale-2.png", fontScale = 2f) {
            WallSheetContent(room, OpeningSheet.Wall(SHAPE_ROOM, WallId("w0")), statuses, LengthUnit.CENTIMETER) {}
        }
    }

    private fun capture(
        fileName: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PlanSnapTheme(darkTheme = dark) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        content()
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = fileName, roborazziOptions = STRICT_OPTIONS)
    }

    private companion object {
        /** Any pixel difference fails; the renderer is deterministic, so no tolerance is needed. */
        val STRICT_OPTIONS =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))
    }
}
