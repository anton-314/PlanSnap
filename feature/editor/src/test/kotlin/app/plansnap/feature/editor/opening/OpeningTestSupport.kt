package app.plansnap.feature.editor.opening

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.opening.DefaultOpeningRules
import app.plansnap.core.domain.validation.OpeningRules
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Room
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.shape.RecordingHost
import app.plansnap.feature.editor.shape.roomOf
import app.plansnap.feature.editor.shape.stateOf
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.collections.immutable.toImmutableList

/** A door on [wall] from [offsetMm] with [widthMm]. */
internal fun door(
    id: String,
    wall: String,
    offsetMm: Long,
    widthMm: Long,
): Opening =
    Opening(
        id = OpeningId(id),
        wallId = WallId(wall),
        type = OpeningType.DOOR,
        offset = Length(offsetMm),
        width = Length(widthMm),
        height = Length(2100),
        sillHeight = null,
        swing = DoorSwing.HINGE_LEFT_INWARD,
    )

/** A closed room of 4000, 3000, 4000, 3000 with [openings]. Its walls are `w0` to `w3`. */
internal fun openingRoom(vararg openings: Opening): Room =
    roomOf(4000, 3000, 4000, 3000, closed = true).copy(openings = openings.toList().toImmutableList())

/** [OpeningRules] that counts how often the domain is asked, to prove that nothing asks it per frame. */
internal class CountingRules(
    private val delegate: OpeningRules = DefaultOpeningRules(),
) : OpeningRules {
    var statusCalls = 0
        private set
    var checkCalls = 0
        private set

    override fun statusOf(
        room: Room,
        opening: Opening,
    ): OpeningStatus {
        statusCalls++
        return delegate.statusOf(room, opening)
    }

    override fun check(
        room: Room,
        opening: Opening,
    ): DomainResult<Unit> {
        checkCalls++
        return delegate.check(room, opening)
    }
}

/** The handler against a recording host and the real opening rules. */
internal class OpeningHarness(
    initial: EditorUiState,
    val rules: CountingRules = CountingRules(),
) {
    val host = RecordingHost(initial)
    val intents: MutableList<EditorIntent> = mutableListOf()
    private val handler = OpeningHandler(SequentialIdGenerator("gen"), rules)

    /** The state of the opening section. */
    val opening: OpeningState get() = host.state.value.section(OpeningState::class) ?: OpeningState()

    /** The open form. */
    val form: OpeningSheet.Form get() = opening.sheet as OpeningSheet.Form

    /** Edits that were submitted as commands. */
    val submitted: List<ProjectEdit> get() = host.submitted

    /** Handles every intent in order and returns whether the last one was consumed. */
    fun send(vararg all: EditorIntent): Boolean {
        var consumed = false
        all.forEach {
            intents += it
            consumed = handler.handle(it, host)
        }
        return consumed
    }

    /** Handles [intent] the way the screen would after it sent it to the view model. */
    fun onIntent(intent: EditorIntent) {
        send(intent)
    }
}

/** An editor state for [room] with the real evaluation. */
internal fun stateWith(room: Room): EditorUiState = stateOf(room)

/** The current state of [harness] as Compose state. */
@Composable
internal fun OpeningHarness.stateAsState(): State<EditorUiState> = remember(this) { host.state }.collectAsState()
