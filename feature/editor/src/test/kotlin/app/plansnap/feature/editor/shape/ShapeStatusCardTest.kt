package app.plansnap.feature.editor.shape

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.determinacy.MeasurementControl
import app.plansnap.core.geometry.determinacy.MeasurementRecommendation
import app.plansnap.core.geometry.determinacy.UncontrolledMeasurement
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.state.EditorIntent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h800dp")
class ShapeStatusCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<EditorIntent>()

    private fun show(
        room: Room,
        result: DomainResult<RoomEvaluation>? = DomainResult.Success(evaluate(room)),
    ) {
        compose.setContent {
            PlanSnapTheme {
                ShapeStatusCard(room, result, LengthUnit.CENTIMETER, { intents += it })
            }
        }
    }

    @Test
    fun `given a confirmed room then the card shows the status and the area`() {
        show(almostRectangle(3000))
        compose.onNodeWithText("Shape confirmed").assertIsDisplayed()
        compose.onNodeWithText("12.00 m²").assertIsDisplayed()
        compose.onNodeWithText("Küche").assertIsDisplayed()
    }

    @Test
    fun `given an open room then no card is shown`() {
        show(roomOf(4000, 3000, 4000))
        compose.onNodeWithText("Shape confirmed").assertDoesNotExist()
        compose.onNodeWithText("Shape not confirmed").assertDoesNotExist()
    }

    @Test
    fun `given a small gap then the card names the correction and opens the adjustment`() {
        show(almostRectangle(3030))
        compose.onNodeWithText("Shape not confirmed").assertIsDisplayed()
        compose.onNodeWithText("The room is almost closed. Largest correction: 1.1 cm.").assertIsDisplayed()
        compose.onNodeWithText("Adjust").performClick()
        assertEquals(listOf<EditorIntent>(ShapeIntent.OpenAdjustment), intents)
    }

    @Test
    fun `given an adjusted room then the card offers to take the adjustment back`() {
        show(almostRectangle(3030).withOverlay())
        compose.onNodeWithText("Adjusted").assertIsDisplayed()
        compose.onNodeWithText("Undo adjustment").performClick()
        assertEquals(listOf<EditorIntent>(ShapeIntent.RevertAdjustment), intents)
    }

    @Test
    fun `given missing measurements then the card recommends the diagonal and enters it with its corners`() {
        val room = underdeterminedRoom()
        val proposed = evaluate(room) as RoomEvaluation.Proposed
        val recommendation = proposed.recommendation as MeasurementRecommendation.DiagonalBetween
        show(room, DomainResult.Success(proposed))
        compose
            .onNodeWithText(
                "Measurements are missing to fix the shape. The plan shows a proposal.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Measure the diagonal from corner 1 to corner 3.").assertIsDisplayed()
        compose.onNodeWithText("Add this diagonal").performClick()
        assertEquals(
            listOf<EditorIntent>(ShapeIntent.OpenDiagonal(recommendation.fromCorner, recommendation.toCorner)),
            intents,
        )
    }

    @Test
    fun `given a recommended angle then the card enters the angle of that corner`() {
        val room = underdeterminedRoom()
        val proposed = evaluate(room) as RoomEvaluation.Proposed
        val withAngle =
            proposed.copy(
                determinacy = proposed.determinacy.copy(recommendation = MeasurementRecommendation.CornerAngle(2)),
            )
        show(room, DomainResult.Success(withAngle))
        compose.onNodeWithText("Measure the angle at corner 3.").assertIsDisplayed()
        compose.onNodeWithText("Enter this angle").performClick()
        assertEquals(listOf<EditorIntent>(ShapeIntent.OpenCorner(CornerTarget.Existing(2))), intents)
    }

    @Test
    fun `given an impossible wall then the card explains the conflict in words and with a symbol`() {
        show(impossibleRoom())
        compose.onNodeWithText("Measurements conflict").assertIsDisplayed()
        compose
            .onNodeWithText(
                "One wall is as long as all other walls together or longer. No room can have that shape. " +
                    "Check the longest wall.",
            ).assertIsDisplayed()
    }

    @Test
    fun `given an impossible diagonal then the card names its corners`() {
        show(roomOf(4000, 3000, 4000, 3000, closed = true, diagonals = listOf(diagonalOf(0, 2, 9000))))
        compose
            .onNodeWithText(
                "The diagonal from corner 1 to corner 3 is impossible with these walls. It is too long or too short. " +
                    "Change this measurement.",
            ).assertIsDisplayed()
    }

    @Test
    fun `given a room that could not be evaluated then the card says why`() {
        show(almostRectangle(3000), DomainResult.Failure(DomainError.Storage(StorageProblem.READ_FAILED)))
        compose.onNodeWithText("Measurements conflict").assertIsDisplayed()
        compose
            .onNodeWithText(
                "This room could not be evaluated. The project could not be read from the device.",
            ).assertIsDisplayed()
    }

    @Test
    fun `given unchecked measurements then each is listed with its recommended extra measurement`() {
        val room = almostRectangle(3000)
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
        val confirmed = (evaluate(room) as RoomEvaluation.Confirmed).copy(measurementControl = control)
        show(room, DomainResult.Success(confirmed))
        compose.onNodeWithText("Measurements without a check").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Not checked: wall 2. An error here would go unnoticed. " +
                    "Measure the diagonal from corner 1 to corner 3.",
            ).assertIsDisplayed()
        compose
            .onNodeWithText(
                "Not checked: angle at corner 4. An error here would go unnoticed. " +
                    "No single extra measurement would help.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Add this diagonal").performClick()
        assertEquals(listOf<EditorIntent>(ShapeIntent.OpenDiagonal(0, 2)), intents)
    }
}
