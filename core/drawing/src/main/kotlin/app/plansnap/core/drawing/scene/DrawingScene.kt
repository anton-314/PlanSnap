package app.plansnap.core.drawing.scene

import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.ShapeStatus
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlin.math.max
import kotlin.math.min

/**
 * Axis-aligned bounds in world millimeters. All values are finite and `min <= max`.
 *
 * @throws IllegalArgumentException if a value is not finite or a minimum exceeds its maximum.
 */
data class WorldBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
) {
    init {
        require(minX.isFinite() && minY.isFinite() && maxX.isFinite() && maxY.isFinite()) {
            "Bounds must be finite"
        }
        require(minX <= maxX && minY <= maxY) { "Bounds minimum must not exceed maximum" }
    }

    /** Extent along x. */
    val width: Double get() = maxX - minX

    /** Extent along y. */
    val height: Double get() = maxY - minY

    /** Returns the smallest bounds containing this and [other]. */
    fun union(other: WorldBounds): WorldBounds =
        WorldBounds(min(minX, other.minX), min(minY, other.minY), max(maxX, other.maxX), max(maxY, other.maxY))

    /** Whether both bounds share at least one point, touching edges included. */
    fun intersects(other: WorldBounds): Boolean =
        minX <= other.maxX && other.minX <= maxX && minY <= other.maxY && other.minY <= maxY
}

/** Semantic drawing style. The renderer maps it to the palette, the export always uses the light one. */
enum class SceneStyle {
    /** Filled wall band. */
    WALL_BAND,

    /** Wall of thickness zero, drawn as a solid line. */
    WALL_LINE,

    /** Wall of thickness zero of an unconfirmed room, drawn as a dashed line (UX section 3). */
    WALL_UNCONFIRMED,

    /** Opening symbol. */
    OPENING,

    /** Opening that no longer fits on its wall. */
    OPENING_OUT_OF_BOUNDS,

    /** Measured diagonal. */
    DIAGONAL,

    /** Dimension line. */
    DIMENSION,

    /** Regular label. */
    LABEL,

    /** Warning mark. */
    WARNING,

    /** Conflict mark. */
    CONFLICT,

    /**
     * Interior of a room with a confirmed or adjusted shape (ADR 0008, CR-W2-04): a light interior tint, never the wall
     * color, and the inner contour stroked as a solid line.
     */
    ROOM_BODY,

    /**
     * Interior of a room whose shape is not confirmed (ADR 0008, CR-W2-04): the same tint as [ROOM_BODY] and the inner
     * contour stroked as a dashed line (UX section 3).
     */
    ROOM_BODY_UNCONFIRMED,
}

/** Model object an element belongs to, used for selection. */
sealed interface SceneTarget {
    /** Owning room. */
    val roomId: RoomId

    /** The room itself. */
    data class RoomBody(
        override val roomId: RoomId,
    ) : SceneTarget

    /** A wall. */
    data class Wall(
        override val roomId: RoomId,
        val wallId: WallId,
    ) : SceneTarget

    /** The corner at [index], not negative. */
    data class Corner(
        override val roomId: RoomId,
        val index: Int,
    ) : SceneTarget {
        init {
            require(index >= 0) { "Corner index must not be negative: $index" }
        }
    }

    /** A diagonal. */
    data class Diagonal(
        override val roomId: RoomId,
        val diagonalId: DiagonalId,
    ) : SceneTarget

    /** An opening. */
    data class Opening(
        override val roomId: RoomId,
        val openingId: OpeningId,
    ) : SceneTarget
}

/** Language-independent label content. Units, number format and translations are applied at the output edge. */
sealed interface LabelText {
    /** User text such as a room name or notes, drawn as is. */
    data class Literal(
        val text: String,
    ) : LabelText

    /** Length in millimeters, finite and not negative. */
    data class Dimension(
        val mm: Double,
    ) : LabelText {
        init {
            require(mm.isFinite() && mm >= 0.0) { "Invalid dimension: $mm" }
        }
    }

    /** Area in square millimeters, finite and not negative. */
    data class Area(
        val mm2: Double,
    ) : LabelText {
        init {
            require(mm2.isFinite() && mm2 >= 0.0) { "Invalid area: $mm2" }
        }
    }

    /** Interior angle in degrees, finite. */
    data class InteriorAngle(
        val degrees: Double,
    ) : LabelText {
        init {
            require(degrees.isFinite()) { "Invalid angle: $degrees" }
        }
    }

    /** Room type template, translated at the output edge (REQ-MOD-011). */
    data class RoomType(
        val key: RoomTypeKey,
    ) : LabelText

    /** The mark "shape not confirmed" (REQ-EXP-006). */
    data object ShapeNotConfirmed : LabelText

    /**
     * The mark "measurements contradict each other" of a `CONFLICT` room (UX section 3, CR-W2-04). The explanation of
     * the reason belongs to the editor, not to the plan.
     */
    data object ShapeConflict : LabelText
}

/** Drawing primitive in world coordinates. */
sealed interface SceneElement {
    /** Semantic style. */
    val style: SceneStyle

    /** Selectable model object, `null` for pure decoration. */
    val target: SceneTarget?

    /** Filled polygon. */
    data class Fill(
        val polygon: Polygon2,
        override val style: SceneStyle,
        override val target: SceneTarget?,
    ) : SceneElement

    /** Polyline with at least two points. [closed] connects the last point to the first. */
    data class Polyline(
        val points: ImmutableList<Vec2>,
        val closed: Boolean,
        override val style: SceneStyle,
        override val target: SceneTarget?,
    ) : SceneElement {
        init {
            require(points.size >= MIN_POINTS) { "A polyline needs at least $MIN_POINTS points" }
        }

        private companion object {
            const val MIN_POINTS = 2
        }
    }

    /** Circular arc, for example a door swing. Angles are counter-clockwise in world coordinates. */
    data class Arc(
        val center: Vec2,
        val radiusMm: Double,
        val startRad: Double,
        val sweepRad: Double,
        override val style: SceneStyle,
        override val target: SceneTarget?,
    ) : SceneElement {
        init {
            require(radiusMm.isFinite() && radiusMm > 0.0) { "Invalid radius: $radiusMm" }
            require(startRad.isFinite() && sweepRad.isFinite()) { "Arc angles must be finite" }
        }
    }

    /**
     * Text centered on [anchor], with its nominal height in world millimeters.
     *
     * A label with a [blockAnchor] is one line of a multi-line label block, such as the room label (ADR 0008, addendum
     * CR-W2-05). All lines of a block share the same [blockAnchor], and [anchor] is the nominal position of the line
     * for text drawn at exactly [heightMm]. A renderer that draws the text `k` times as large as [heightMm] in world
     * terms, for example the enlarged export text or the constant screen size of the editor, places the line at
     * `blockAnchor + k · (anchor − blockAnchor)`, so the lines keep their spacing relative to the text and never
     * overlap. A label without [blockAnchor] is a single line, such as a dimension or angle, and stays at [anchor].
     * Bounds and hit tests use the nominal [anchor].
     *
     * @property blockAnchor common anchor of the label block, `null` for a single-line label.
     */
    data class Label(
        val anchor: Vec2,
        val text: LabelText,
        val rotationRad: Double,
        val heightMm: Double,
        override val style: SceneStyle,
        override val target: SceneTarget?,
        val blockAnchor: Vec2? = null,
    ) : SceneElement {
        init {
            require(rotationRad.isFinite()) { "Label rotation must be finite" }
            require(heightMm.isFinite() && heightMm > 0.0) { "Invalid label height: $heightMm" }
        }
    }
}

/**
 * Scene of one room, the unit of caching (ADR 0008).
 *
 * @property bounds bounds of all elements, `null` for a room without elements.
 * @property elements primitives in drawing order.
 */
data class RoomScene(
    val roomId: RoomId,
    val status: ShapeStatus,
    val bounds: WorldBounds?,
    val elements: ImmutableList<SceneElement>,
) {
    init {
        require((bounds == null) == elements.isEmpty()) { "Bounds are absent exactly for an empty scene" }
    }
}

/**
 * Pure scene in world millimeters: x to the right, y up (ARCHITECTURE section 8).
 *
 * @property rooms room scenes in project order with unique ids.
 * @property bounds union of all room bounds, `null` if nothing is drawn.
 */
data class DrawingScene(
    val rooms: ImmutableList<RoomScene>,
    val bounds: WorldBounds?,
) {
    init {
        require(rooms.distinctBy { it.roomId }.size == rooms.size) { "Room ids must be unique" }
        require((bounds == null) == rooms.all { it.bounds == null }) { "Scene bounds must match its rooms" }
    }
}

/**
 * Options of the scene construction.
 *
 * @property exportableOnly omits `OPEN` and `CONFLICT` rooms, as PDF, PNG and SVG require (DOMAIN section 12).
 * @property includeNotes draws room notes (REQ-MOD-012).
 */
data class SceneOptions(
    val exportableOnly: Boolean = false,
    val includeNotes: Boolean = false,
)

/** Builds scenes from the model. Pure and deterministic. */
interface SceneBuilder {
    /** Builds the scene of one room in world coordinates. */
    fun buildRoom(
        room: Room,
        evaluation: RoomEvaluation,
        options: SceneOptions,
    ): RoomScene

    /**
     * Builds the scene of [project]. [evaluations] must contain every room.
     *
     * Not cancellable while running. A caller that must be cancellable calls [buildRoom] per room and checks for
     * cancellation between the calls (ARCHITECTURE section 5).
     */
    fun build(
        project: Project,
        evaluations: ImmutableMap<RoomId, RoomEvaluation>,
        options: SceneOptions,
    ): DrawingScene
}
