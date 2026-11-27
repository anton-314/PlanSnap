package app.plansnap.feature.editor.config

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.plansnap.feature.editor.screen.PLAN_MIN_HEIGHT
import app.plansnap.feature.editor.state.emptyProject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In every window the plan keeps its minimum height and the input bar gives way to it (UX section 2). In low windows
 * the bar has to scroll for that.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [36])
internal class PlanHeightTest(
    private val device: DeviceConfig,
) {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `given no status card then the plan keeps its minimum height`() {
        val editor = ConfigEditor(compose, emptyProject()).launch(device)

        val height =
            editor
                .plan()
                .fetchSemanticsNode()
                .boundsInRoot.height

        assertTrue("Plan is $height high", height >= PLAN_MIN_HEIGHT.value)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun devices(): List<DeviceConfig> = DeviceConfig.entries
    }
}
