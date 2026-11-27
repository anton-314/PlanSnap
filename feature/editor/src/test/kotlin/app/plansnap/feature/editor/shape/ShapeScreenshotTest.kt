package app.plansnap.feature.editor.shape

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.determinacy.MeasurementControl
import app.plansnap.core.geometry.determinacy.MeasurementRecommendation
import app.plansnap.core.geometry.determinacy.UncontrolledMeasurement
import app.plansnap.core.model.Room
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/** Screenshots of the shape sheets, the dialog for a large correction and the status card. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h800dp-port-notnight-mdpi")
class ShapeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val room = roomOf(4000, 3000, 4000)
    private val closed = roomOf(3000, 3000, 3000, 5000, closed = true)

    private val diagonal = ShapeSheet.Diagonal(0, 2, "424,3")
    private val thickness = ShapeSheet.Thickness("12")

    private fun corner(text: String) = ShapeSheet.Corner(CornerTarget.Next, TurnDirection.LEFT, text)

    @Test
    fun `given the angle sheet when light then it matches the baseline`() =
        capture("shape-corner-light.png") {
            CornerSheetContent(corner("75"), room, ShapeState(corner("75")), {})
        }

    @Test
    fun `given the angle sheet when dark then it matches the baseline`() =
        capture("shape-corner-dark.png", dark = true) {
            CornerSheetContent(corner("75"), room, ShapeState(corner("75")), {})
        }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h800dp-port-notnight-mdpi")
    fun `given the angle sheet when german then it matches the baseline`() =
        capture("shape-corner-de.png") {
            CornerSheetContent(corner("75,5"), room, ShapeState(corner("75,5")), {})
        }

    @Test
    fun `given an angle that is out of range when shown then the explanation is in the baseline`() {
        val error = ShapeValues.errorOf(corner("181"), LengthUnit.CENTIMETER, Locale.US)
        capture(
            "shape-corner-error.png",
        ) { CornerSheetContent(corner("181"), room, ShapeState(corner("181"), error), {}) }
    }

    @Test
    fun `given the diagonal sheet when shown then it matches the baseline`() =
        capture("shape-diagonal.png") {
            DiagonalSheetContent(diagonal, closed, LengthUnit.CENTIMETER, ShapeState(diagonal), {})
        }

    @Test
    fun `given the thickness sheet when shown then it matches the baseline`() =
        capture("shape-thickness.png") {
            ThicknessSheetContent(thickness, LengthUnit.CENTIMETER, ShapeState(thickness), {})
        }

    @Test
    fun `given a simple proposal when the adjustment sheet is shown then it matches the baseline`() {
        val small = almostRectangle(3030)
        capture("shape-adjust-simple.png") {
            AdjustmentSheetContent(small, evaluate(small) as RoomEvaluation.NeedsAdjustment, LengthUnit.CENTIMETER, {})
        }
    }

    @Test
    fun `given a large correction when the adjustment sheet is shown then it matches the baseline`() {
        val large = largeCorrectionRoom()
        capture("shape-adjust-explicit.png") {
            AdjustmentSheetContent(large, evaluate(large) as RoomEvaluation.NeedsAdjustment, LengthUnit.CENTIMETER, {})
        }
    }

    @Test
    fun `given a large correction when the adjustment sheet has font scale 2 then it matches the baseline`() {
        val large = largeCorrectionRoom()
        capture("shape-adjust-explicit-fontscale-2.png", fontScale = 2f) {
            AdjustmentSheetContent(large, evaluate(large) as RoomEvaluation.NeedsAdjustment, LengthUnit.CENTIMETER, {})
        }
    }

    @Test
    fun `given the dialog for a large correction when light then it matches the baseline`() {
        val large = largeCorrectionRoom()
        capture("shape-explicit-dialog-light.png") {
            ExplicitConfirmationContent(
                large,
                evaluate(large) as RoomEvaluation.NeedsAdjustment,
                LengthUnit.CENTIMETER,
                {},
            )
        }
    }

    @Test
    fun `given the dialog for a large correction when dark then it matches the baseline`() {
        val large = largeCorrectionRoom()
        capture("shape-explicit-dialog-dark.png", dark = true) {
            ExplicitConfirmationContent(
                large,
                evaluate(large) as RoomEvaluation.NeedsAdjustment,
                LengthUnit.CENTIMETER,
                {},
            )
        }
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h800dp-port-notnight-mdpi")
    fun `given the dialog for a large correction when german then it matches the baseline`() {
        val large = largeCorrectionRoom()
        capture("shape-explicit-dialog-de.png") {
            ExplicitConfirmationContent(
                large,
                evaluate(large) as RoomEvaluation.NeedsAdjustment,
                LengthUnit.CENTIMETER,
                {},
            )
        }
    }

    @Test
    fun `given a confirmed room when the card is shown then it matches the baseline`() =
        card("shape-card-confirmed.png", almostRectangle(3000))

    @Test
    fun `given a small gap when the card is shown then it matches the baseline`() =
        card("shape-card-needs-adjustment.png", almostRectangle(3030))

    @Test
    fun `given an adjusted room when the card is shown then it matches the baseline`() =
        card("shape-card-adjusted.png", almostRectangle(3030).withOverlay())

    @Test
    fun `given missing measurements when the card is shown then it matches the baseline`() =
        card("shape-card-proposed.png", underdeterminedRoom())

    @Test
    fun `given an impossible wall when the card is shown then it matches the baseline`() =
        card("shape-card-conflict.png", impossibleRoom())

    @Test
    fun `given an impossible wall when the card is dark then it matches the baseline`() =
        card("shape-card-conflict-dark.png", impossibleRoom(), dark = true)

    @Test
    fun `given unchecked measurements when the card is shown then it matches the baseline`() {
        val base = almostRectangle(3000)
        val control =
            MeasurementControl(
                redundancy = 2,
                uncontrolled =
                    listOf(
                        UncontrolledMeasurement(
                            ObservationRef.WallLength(1),
                            0.1,
                            MeasurementRecommendation.DiagonalBetween(0, 2),
                        ),
                        UncontrolledMeasurement(ObservationRef.CornerAngle(3), 0.2, null),
                    ),
            )
        val evaluation = (evaluate(base) as RoomEvaluation.Confirmed).copy(measurementControl = control)
        card("shape-card-uncontrolled.png", base, evaluation)
    }

    @Test
    fun `given a collapsed card when shown then it matches the baseline`() =
        collapsedCard("shape-card-collapsed.png", almostRectangle(3030))

    @Test
    fun `given a collapsed card at font scale 2 when shown then it matches the baseline`() =
        collapsedCard("shape-card-collapsed-fontscale-2.png", almostRectangle(3030), fontScale = 2f)

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h800dp-port-notnight-mdpi")
    fun `given a collapsed german conflict at font scale 2 when dark then it matches the baseline`() =
        collapsedCard("shape-card-collapsed-fontscale-2-de-dark.png", impossibleRoom(), fontScale = 2f, dark = true)

    private fun collapsedCard(
        fileName: String,
        room: Room,
        fontScale: Float = 1f,
        dark: Boolean = false,
    ) = capture(fileName, dark = dark, fontScale = fontScale) {
        Box(Modifier.padding(8.dp)) {
            ShapeStatusCard(
                room,
                DomainResult.Success(evaluate(room)),
                LengthUnit.CENTIMETER,
                {},
                toggle = StatusCardToggle(false) {},
            )
        }
    }

    private fun card(
        fileName: String,
        room: Room,
        evaluation: RoomEvaluation = evaluate(room),
        dark: Boolean = false,
    ) = capture(fileName, dark = dark) {
        Box(Modifier.padding(8.dp)) {
            ShapeStatusCard(room, DomainResult.Success(evaluation), LengthUnit.CENTIMETER, {})
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
