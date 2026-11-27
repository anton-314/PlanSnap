package app.plansnap.feature.editor.history

import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorViewModel
import org.junit.Assert.assertEquals

/** What an undo step has to bring back: the confirmed project, its evaluations and the plan drawn from them. */
internal data class Snapshot(
    val project: Project,
    val evaluations: Map<RoomId, RoomEvaluation>,
    val scene: DrawingScene,
)

/** The current [Snapshot] of the editor. Fails if the editor has not drawn a plan yet. */
internal fun EditorViewModel.snapshot(): Snapshot =
    Snapshot(
        project = checkNotNull(state.value.project),
        evaluations = state.value.evaluations,
        scene = checkNotNull(state.value.scene),
    )

/** Asserts that the repository holds [expected]. Only the time stamp belongs to the saver, not to the history. */
internal fun EditorRig.assertStored(
    expected: Project,
    message: String = "Stored project",
) = assertEquals(message, expected, stored.copy(updatedAtEpochMs = expected.updatedAtEpochMs))

/** Asserts that plan, evaluations and repository all show [expected]. */
internal fun assertShows(
    expected: Snapshot,
    rig: EditorRig,
    viewModel: EditorViewModel,
    message: String,
) {
    assertEquals(message, expected, viewModel.snapshot())
    rig.assertStored(expected.project, message)
}
