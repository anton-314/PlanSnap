package app.plansnap.feature.projects.ui.states

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.projects.ProjectListState
import app.plansnap.feature.projects.ProjectsError
import app.plansnap.feature.projects.ProjectsScreen
import app.plansnap.feature.projects.ProjectsScreenActions
import app.plansnap.feature.projects.ProjectsUiState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the error state of the project list and of its states in dark theme, German and at 200 percent font
 * scale. The light skeleton and empty state are covered by `projects-loading.png` and `projects-empty.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class ProjectsStatesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val failed = ProjectsUiState(list = ProjectListState.Failed(ProjectsError.READ_FAILED))
    private val empty = ProjectsUiState(list = ProjectListState.Loaded(persistentListOf()))

    @Test
    fun `given a failed list when shown then it matches the baseline`() = capture(failed, "states-projects-failed.png")

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given a failed list when dark and german then it matches the baseline`() =
        capture(failed, "states-projects-failed-dark-de.png", darkTheme = true)

    @Test
    fun `given a failed list when the font scale is 2 then it matches the baseline`() =
        capture(failed, "states-projects-failed-fontscale-2.png", fontScale = 2f)

    @Test
    fun `given no projects when the font scale is 2 then it matches the baseline`() =
        capture(empty, "states-projects-empty-fontscale-2.png", fontScale = 2f)

    @Test
    fun `given a loading list when dark then it matches the baseline`() =
        capture(ProjectsUiState(), "states-projects-loading-dark.png", darkTheme = true)

    private fun capture(
        state: ProjectsUiState,
        fileName: String,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PlanSnapTheme(darkTheme = darkTheme) {
                    ProjectsScreen(state = state, actions = NO_ACTIONS)
                }
            }
        }
        compose.onRoot().captureRoboImage(filePath = fileName, roborazziOptions = STRICT_OPTIONS)
    }

    private companion object {
        val NO_ACTIONS = ProjectsScreenActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

        /** Any pixel difference fails; the renderer is deterministic, so no tolerance is needed. */
        val STRICT_OPTIONS =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))
    }
}
