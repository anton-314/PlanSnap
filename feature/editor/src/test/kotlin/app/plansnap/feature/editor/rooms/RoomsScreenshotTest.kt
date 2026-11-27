package app.plansnap.feature.editor.rooms

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
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.WallId
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the dock sheet and the hint bar for choosing the host on the plan. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h1100dp-port-notnight-mdpi")
class RoomsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val chosen =
        DockDraft(
            guestId = GUEST_ID,
            hostRoomId = HOST_ID,
            hostWallId = WallId("host0"),
            ownWallId = WallId("guest0"),
        )

    @Test
    fun `given a fresh dock sheet when light then it matches the baseline`() =
        sheet("rooms-dock-empty.png", DockDraft(guestId = GUEST_ID, hostRoomId = HOST_ID))

    @Test
    fun `given a chosen host wall when light then it matches the baseline`() = sheet("rooms-dock-light.png", chosen)

    @Test
    fun `given a chosen host wall when dark then it matches the baseline`() =
        sheet("rooms-dock-dark.png", chosen, dark = true)

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h1100dp-port-notnight-mdpi")
    fun `given an opening as host when german then it matches the baseline`() =
        sheet("rooms-dock-opening-de.png", chosen.copy(hostOpeningId = DOOR_ID, offsetText = "12,5"))

    @Test
    fun `given a rejected link when shown then the explanation is in the baseline`() =
        sheet("rooms-dock-error.png", chosen.copy(error = DomainError.RuleViolation(DomainRule.DOCK_CYCLE)))

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h2000dp-port-notnight-mdpi")
    fun `given font scale 2 when shown then it matches the baseline`() =
        sheet("rooms-dock-fontscale-2.png", chosen, fontScale = 2f)

    @Test
    fun `given the selection on the plan when shown then the hint bar matches the baseline`() {
        val state = roomsState().withSection(RoomsState::class, RoomsState(dock = chosen.copy(picking = true)))
        capture("rooms-picking.png") { RoomsHost(state, {}) }
    }

    private fun sheet(
        fileName: String,
        draft: DockDraft,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        val state = roomsState()
        capture(fileName, dark, fontScale) { DockSheetContent(draft, state, {}) }
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
