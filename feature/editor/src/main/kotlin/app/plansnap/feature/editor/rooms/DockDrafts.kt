package app.plansnap.feature.editor.rooms

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.DecimalLengthParser
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.placement.DockTopology
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.toImmutableList

/**
 * The rules of the dock sheet. It only turns choices into a [DockLink] and asks the domain whether the link is
 * possible. Where the room ends up is computed by the domain when the command runs (DOMAIN section 10).
 */
internal object DockDrafts {
    /**
     * The sheet for docking the selected room, or `null` if that is not possible: the room needs a wall, and another
     * room needs a wall to dock to. A single possible host is chosen already.
     */
    fun open(state: EditorUiState): DockDraft? {
        val guest = state.selectedRoom?.takeIf { it.walls.isNotEmpty() }
        val hosts = guest?.let { hostsFor(state, it.id) }.orEmpty()
        return if (guest == null || hosts.isEmpty()) {
            null
        } else {
            DockDraft(guestId = guest.id, hostRoomId = hosts.singleOrNull()?.id)
        }
    }

    /** The rooms [guestId] can be docked to: every other room that has a wall. */
    fun hostsFor(
        state: EditorUiState,
        guestId: RoomId,
    ): List<Room> =
        state.project
            ?.rooms
            .orEmpty()
            .filter { it.id != guestId && it.walls.isNotEmpty() }

    /** Whether the offset text of [draft] is a length, and its signed value, in [unit]. */
    fun offsetOf(
        draft: DockDraft,
        unit: LengthUnit,
    ): Length? {
        val text = draft.offsetText.ifBlank { DockDraft.DEFAULT_OFFSET_TEXT }
        val parsed = DecimalLengthParser.parse(text, unit, ValueField.OPENING_OFFSET)
        return (parsed as? DomainResult.Success)?.value?.let { if (draft.offsetNegative) Length(-it.mm) else it }
    }

    /** The link of [draft], `null` while it is incomplete or its offset is not a length. */
    fun linkOf(
        draft: DockDraft,
        unit: LengthUnit,
    ): DockLink? {
        val offset = offsetOf(draft, unit)
        val host = draft.hostRoomId?.let { room -> draft.hostWallId?.let { wall -> room to wall } }
        val own = draft.ownWallId
        return if (offset == null || host == null || own == null) {
            null
        } else {
            DockLink(host.first, host.second, own, offset, draft.hostOpeningId)
        }
    }

    /**
     * Returns [draft] with the validity of its choices recomputed: the offset, and the domain's reasons why the link
     * is impossible (host or wall missing, opening on another wall, cycle). The command checks the same rules again
     * when it runs; here they only explain the problem before the user confirms.
     */
    fun validated(
        draft: DockDraft,
        state: EditorUiState,
    ): DockDraft {
        val unit = state.settings.lengthUnit
        val project = state.project
        val guest = project?.rooms?.firstOrNull { it.id == draft.guestId }
        val link = linkOf(draft, unit)
        val error = if (project == null || guest == null || link == null) null else problemOf(project, guest, link)
        return draft.copy(offsetInvalid = offsetOf(draft, unit) == null, error = error)
    }

    private fun problemOf(
        project: Project,
        guest: Room,
        link: DockLink,
    ): DomainError? =
        DockTopology.linkError(project, guest, link) ?: run {
            val docked =
                project.copy(
                    rooms =
                        project.rooms
                            .map { if (it.id == guest.id) it.copy(placement = it.placement.copy(dock = link)) else it }
                            .toImmutableList(),
                )
            (DockTopology.resolutionOrder(docked) as? DomainResult.Failure)?.error
        }
}
