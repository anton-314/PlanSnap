package app.plansnap.feature.editor.details

import androidx.compose.foundation.layout.Box
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
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.feature.editor.shape.evaluate
import app.plansnap.feature.editor.shape.impossibleRoom
import app.plansnap.feature.editor.shape.roomOf
import app.plansnap.feature.editor.state.EditorUiState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the room details sheet and the area overview, light, dark, German and at 200 % font size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h1600dp-port-notnight-mdpi")
class DetailsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val details =
        DetailsSheet.RoomDetails(
            roomId = RoomId("r1"),
            name = "Küche",
            typeKey = RoomTypeKey("kitchen"),
            notes = "Fenster nach Süden",
            heightText = "260.0",
            factorText = "50",
        )

    private val withErrors =
        details.copy(
            heightText = "abc",
            factorText = "101",
            errors =
                persistentMapOf(
                    DetailsField.HEIGHT to
                        DomainError.InvalidValue(ValueField.ROOM_HEIGHT, InvalidValueReason.MALFORMED),
                    DetailsField.FACTOR to
                        DomainError.InvalidValue(ValueField.AREA_FACTOR, InvalidValueReason.TOO_LARGE),
                ),
        )

    private val germanDetails = details.copy(heightText = "260,0")

    private fun overview(): AreaOverview {
        val base = detailsState()
        val open = roomOf(4000, 3000).copy(id = RoomId("open"), name = "Flur")
        val conflict = impossibleRoom().copy(id = RoomId("conflict"), name = "")
        val second =
            roomOf(5000, 2000, 5000, 2000, closed = true)
                .copy(id = RoomId("r2"), name = "Bad", typeKey = RoomTypeKey("bathroom"), heightOverride = Length(2400))
        val project = checkNotNull(base.project)
        val rooms = persistentListOf(project.rooms.single(), second, open, conflict)
        val state: EditorUiState =
            base.copy(
                project = project.copy(rooms = rooms),
                evaluations = rooms.associate { it.id to evaluate(it) }.toImmutableMap(),
            )
        return checkNotNull(areaOverviewOf(state))
    }

    private fun detailsContent(sheet: DetailsSheet.RoomDetails): @Composable () -> Unit =
        { RoomDetailsContent(sheet, LengthUnit.CENTIMETER, Length(2500), {}) }

    private fun overviewContent(projectHeight: String = "250.0"): @Composable () -> Unit =
        { AreaOverviewContent(overview(), DetailsSheet.Overview(projectHeight), LengthUnit.CENTIMETER, {}) }

    @Test
    fun `given the details when light then it matches the baseline`() =
        capture("details-light.png", detailsContent(details))

    @Test
    fun `given the details when dark then it matches the baseline`() =
        capture("details-dark.png", detailsContent(details), dark = true)

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h1600dp-port-notnight-mdpi")
    fun `given the details when german then it matches the baseline`() =
        capture("details-de.png", detailsContent(germanDetails))

    @Test
    fun `given the details with errors when shown then the explanations are in the baseline`() =
        capture("details-errors.png", detailsContent(withErrors))

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h2600dp-port-notnight-mdpi")
    fun `given the details at 200 percent font when german then it matches the baseline`() =
        capture("details-de-font2.png", detailsContent(germanDetails), fontScale = 2f)

    @Test
    fun `given the overview when light then it matches the baseline`() =
        capture("overview-light.png", overviewContent())

    @Test
    fun `given the overview when dark then it matches the baseline`() =
        capture("overview-dark.png", overviewContent(), dark = true)

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h1600dp-port-notnight-mdpi")
    fun `given the overview when german then it matches the baseline`() =
        capture("overview-de.png", overviewContent("250,0"))

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h3200dp-port-notnight-mdpi")
    fun `given the overview at 200 percent font when german then it matches the baseline`() =
        capture("overview-de-font2.png", overviewContent("250,0"), fontScale = 2f)

    private fun capture(
        fileName: String,
        content: @Composable () -> Unit,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PlanSnapTheme(darkTheme = dark) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Box { content() }
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
