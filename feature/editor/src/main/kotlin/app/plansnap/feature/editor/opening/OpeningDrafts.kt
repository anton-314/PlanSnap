package app.plansnap.feature.editor.opening

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.units.DecimalLengthFormatter
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.validation.OpeningRules
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Result of checking a typed [OpeningDraft]. */
internal sealed interface DraftCheck {
    /** The draft is an opening that the commands accept. */
    data class Valid(
        val opening: Opening,
    ) : DraftCheck

    /**
     * The draft cannot be stored.
     *
     * @property valueError a typed value the model does not accept.
     * @property conflict the rule the opening would violate.
     */
    data class Invalid(
        val valueError: DomainError?,
        val conflict: DomainError?,
    ) : DraftCheck
}

/**
 * Interpretation of the opening form. The sheet and its confirmation share it, so what the form accepts is exactly
 * what the command receives. Whether an opening fits on its wall is decided by [OpeningRules] (ADR 0021): it is asked
 * once per change of the form, so the cost does not depend on the frame rate.
 */
internal object OpeningDrafts {
    private const val DOOR_WIDTH_MM = 900L
    private const val WINDOW_WIDTH_MM = 1200L
    private const val PASSAGE_WIDTH_MM = 1000L
    private val PLACEHOLDER_ID = OpeningId("opening-draft")

    /**
     * The form of a new opening of [type] on [wall]: it starts behind the last opening of that wall and has the usual
     * width. The optional values stay blank, so nothing is stored that the user did not choose (REQ-MOD-002).
     */
    fun initial(
        type: OpeningType,
        wall: Wall,
        room: Room,
        unit: LengthUnit,
    ): OpeningDraft {
        val start = room.openings.filter { it.wallId == wall.id }.maxOfOrNull { it.endOffset.mm } ?: 0L
        return OpeningDraft(
            wallId = wall.id,
            type = type,
            offset = format(Length(start), unit),
            width = usualWidth(type, unit),
            height = "",
            sill = "",
            swing = null,
        )
    }

    /** The form values of the stored [opening]. */
    fun of(
        opening: Opening,
        unit: LengthUnit,
    ): OpeningDraft =
        OpeningDraft(
            wallId = opening.wallId,
            type = opening.type,
            offset = format(opening.offset, unit),
            width = format(opening.width, unit),
            height = opening.height?.let { format(it, unit) }.orEmpty(),
            sill = opening.sillHeight?.let { format(it, unit) }.orEmpty(),
            swing = opening.swing,
        )

    /**
     * [draft] as an opening of [type]. Only the usual width of a new opening follows the type. A typed width, the
     * width of a stored opening and all optional values are kept. Values that do not apply to the type stay in the
     * form, so switching back restores them; [check] leaves them out of the opening.
     *
     * @param isNew whether the form creates an opening instead of changing a stored one.
     */
    fun retype(
        draft: OpeningDraft,
        type: OpeningType,
        isNew: Boolean,
        unit: LengthUnit,
    ): OpeningDraft {
        val follows = isNew && draft.width == usualWidth(draft.type, unit)
        return draft.copy(type = type, width = if (follows) usualWidth(type, unit) else draft.width)
    }

    /** The state of the sheets that shows [form] together with what is wrong with it. */
    fun stateOf(
        form: OpeningSheet.Form,
        project: Project?,
        unit: LengthUnit,
        rules: OpeningRules,
    ): OpeningState {
        val invalid = check(form, project, unit, rules) as? DraftCheck.Invalid
        return OpeningState(sheet = form, valueError = invalid?.valueError, conflict = invalid?.conflict)
    }

    /** Checks the typed values of [form] against its room in [project], `null` if the room does not exist. */
    fun check(
        form: OpeningSheet.Form,
        project: Project?,
        unit: LengthUnit,
        rules: OpeningRules,
    ): DraftCheck? {
        val room = project?.rooms?.firstOrNull { it.id == form.roomId } ?: return null
        return when (val parsed = form.draft.toOpening(form.editingId ?: PLACEHOLDER_ID, unit)) {
            is DomainResult.Failure -> {
                DraftCheck.Invalid(parsed.error, null)
            }

            is DomainResult.Success -> {
                val conflict = conflictOf(parsed.value, project, room, rules)
                if (conflict == null) DraftCheck.Valid(parsed.value) else DraftCheck.Invalid(null, conflict)
            }
        }
    }

    /** [length] in [unit] as typed into the form: the number of the locale without superfluous trailing zeros. */
    fun format(
        length: Length,
        unit: LengthUnit,
    ): String {
        val locale = Locale.getDefault()
        val text = DecimalLengthFormatter.format(length, unit, locale)
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return if (separator in text) text.trimEnd('0').trimEnd(separator) else text
    }

    private fun conflictOf(
        opening: Opening,
        project: Project,
        room: Room,
        rules: OpeningRules,
    ): DomainError? {
        val moved = room.openings.any { it.id == opening.id && it.wallId != opening.wallId }
        return if (moved && project.hasRoomDockedTo(room.id, opening.id)) {
            DomainError.RuleViolation(DomainRule.OPENING_HAS_DOCKED_ROOMS)
        } else {
            (rules.check(room, opening) as? DomainResult.Failure)?.error
        }
    }

    /** The precondition of `EditOpening` that is not part of [OpeningRules] (DOMAIN section 11). */
    private fun Project.hasRoomDockedTo(
        hostRoomId: RoomId,
        openingId: OpeningId,
    ): Boolean =
        rooms.any {
            val dock = it.placement.dock
            dock != null && dock.hostRoomId == hostRoomId && dock.hostOpeningId == openingId
        }

    private fun usualWidth(
        type: OpeningType,
        unit: LengthUnit,
    ): String =
        format(
            Length(
                when (type) {
                    OpeningType.DOOR -> DOOR_WIDTH_MM
                    OpeningType.WINDOW -> WINDOW_WIDTH_MM
                    OpeningType.PASSAGE -> PASSAGE_WIDTH_MM
                },
            ),
            unit,
        )
}
