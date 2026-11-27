package app.plansnap.feature.editor.shape

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

    @Test
    fun `given a toggle then the header collapses and expands the card and reports its state`() {
        val room = almostRectangle(3030)
        compose.setContent {
            PlanSnapTheme {
                var expanded by remember { mutableStateOf(true) }
                ShapeStatusCard(
                    room,
                    DomainResult.Success(evaluate(room)),
                    LengthUnit.CENTIMETER,
                    { intents += it },
                    toggle = StatusCardToggle(expanded) { expanded = it },
                )
            }
        }
        val header = compose.onNode(hasText("Küche", substring = true) and hasClickAction())
        header.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        header.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithText("Adjust").assertIsDisplayed()

        header.performClick()
        compose.onNodeWithText("Adjust").assertDoesNotExist()
        header.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        compose.onNodeWithText("Shape not confirmed", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Küche", substring = true).assertIsDisplayed()
        compose.onNodeWithText("12.", substring = true).assertIsDisplayed()

        header.performClick()
        compose.onNodeWithText("Adjust").assertIsDisplayed()
    }

    @Test
    fun `given no toggle then the header is no button`() {
        show(almostRectangle(3030))
        compose.onNode(hasText("Küche", substring = true) and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `given a card without details then no empty area follows the header`() {
        show(almostRectangle(3000))
        val card = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val badge = compose.onNodeWithText("Shape confirmed").fetchSemanticsNode().boundsInRoot
        assertEquals(badge.top - card.top, card.bottom - badge.bottom, 1f)
    }

    @Test
    fun `given font scale 2 and an expanded card then status name and area are complete`() {
        showAtDoubleFont(almostRectangle(3030), maxHeight = 176.dp, expanded = true)
        listOf("Shape not confirmed", "Küche", "12.06 m²").forEach { compose.assertTextComplete(it) }
    }

    @Test
    fun `given font scale 2 and only 64 dp then the collapsed card shows status name and area completely`() {
        showAtDoubleFont(almostRectangle(3030), maxHeight = 64.dp, expanded = false)
        listOf("Shape not confirmed", "Küche", "12.06 m²").forEach { compose.assertTextComplete(it) }
    }

    @Test
    fun `given a confirmed room at font scale 2 and 64 dp then the collapsed card is complete`() {
        showAtDoubleFont(almostRectangle(3000), maxHeight = 64.dp, expanded = false)
        listOf("Shape confirmed", "Küche", "12.00 m²").forEach { compose.assertTextComplete(it) }
    }

    @Test
    fun `given a conflict at font scale 2 and 64 dp then the collapsed card is complete`() {
        showAtDoubleFont(impossibleRoom(), maxHeight = 64.dp, expanded = false)
        listOf("Measurements conflict", "Küche").forEach { compose.assertTextComplete(it) }
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h800dp")
    fun `given a german conflict at font scale 2 and 64 dp then the collapsed card is complete`() {
        showAtDoubleFont(impossibleRoom(), maxHeight = 64.dp, expanded = false)
        listOf("Maße widersprüchlich", "Küche").forEach { compose.assertTextComplete(it) }
    }

    @Test
    fun `given a very long name at font scale 2 then only the name is cut off`() {
        val name = "Wohnzimmer mit offener Küche und Essbereich"
        showAtDoubleFont(almostRectangle(3030).copy(name = name), maxHeight = 64.dp, expanded = false)
        listOf("Shape not confirmed", "12.06 m²").forEach { compose.assertTextComplete(it) }
        compose.onNodeWithText(name, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun showAtDoubleFont(
        room: Room,
        maxHeight: Dp,
        expanded: Boolean,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PlanSnapTheme {
                    ShapeStatusCard(
                        room,
                        DomainResult.Success(evaluate(room)),
                        LengthUnit.CENTIMETER,
                        {},
                        modifier = Modifier.width(CARD_WIDTH).heightIn(max = maxHeight),
                        toggle = StatusCardToggle(expanded) {},
                    )
                }
            }
        }
    }

    private companion object {
        /** Width of the card on a 360 dp phone with its 8 dp padding. */
        val CARD_WIDTH = 344.dp
    }
}
