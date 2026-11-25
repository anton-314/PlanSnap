package app.plansnap.core.drawing.scene

import app.plansnap.core.domain.evaluation.EvaluatedShape
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.isExportable
import app.plansnap.core.domain.evaluation.isUnconfirmed
import app.plansnap.core.geometry.bands.BandInput
import app.plansnap.core.geometry.bands.DefaultWallBandBuilder
import app.plansnap.core.geometry.bands.WallBand
import app.plansnap.core.geometry.bands.WallBandBuilder
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.Traverse
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import app.plansnap.core.common.units.Angle as RawAngle

/**
 * [SceneBuilder] after ADR 0008 and DOMAIN sections 2, 4, 8, 9 and 11. Pure and deterministic: equal arguments give
 * equal scenes, and the scene of a room depends only on that room, its evaluation and the options.
 *
 * Every point is in world millimeters, `p_world = R(rotation) · p_local + origin` with the placement of the room.
 *
 * **Room with a shape** (`CONFIRMED`, `NEEDS_ADJUSTMENT`, `ADJUSTED`, `PROPOSED`), elements in drawing order:
 *
 * 1. The room body: a [SceneElement.Fill] of the inner contour with a [SceneTarget.RoomBody] target, in
 *    [SceneStyle.WALL_LINE], or in [SceneStyle.WALL_UNCONFIRMED] for an unconfirmed room. It is the stroked inner
 *    contour, the dashed outline of an unconfirmed room (UX section 3), and makes the interior hittable.
 * 2. One band per wall from [WallBandBuilder] (DOMAIN section 8): a [SceneStyle.WALL_BAND] fill for a positive
 *    thickness, otherwise a polyline in the style of the room body. Each selects its wall.
 * 3. Bevel triangles in [SceneStyle.WALL_BAND], selecting their corner.
 * 4. Openings after [OpeningSymbols], in [SceneStyle.OPENING_OUT_OF_BOUNDS] if their derived status is
 *    [OpeningStatus.OUT_OF_BOUNDS], otherwise in [SceneStyle.OPENING].
 * 5. Diagonals with their raw length.
 * 6. One dimension per wall with the effective length of the displayed shape (REQ-UX-004).
 * 7. The interior angle at every measured corner.
 * 8. Room labels, centered inside the room: name, room type, area, the mark [LabelText.ShapeNotConfirmed] and, with
 *    [SceneOptions.includeNotes], the notes. An unconfirmed room shows area and mark in [SceneStyle.WARNING], so the
 *    mark sits at the room and at its area (REQ-EXP-006).
 * 9. One marker per corner, a polyline of length zero in [SceneStyle.WALL_LINE] that draws nothing and selects the
 *    corner.
 *
 * **Room without a shape** (`OPEN`, `CONFLICT`): the chain walked with the raw lengths and the entered turns, for
 * `OPEN` the chain of the evaluation. Walls are lines without bands, in [SceneStyle.WALL_LINE], or in
 * [SceneStyle.CONFLICT] for a conflict, which also marks the impossible diagonal. Openings, diagonals, dimensions with
 * the raw lengths, name, type and notes at the mean of the chain points and the corner markers follow as above. There
 * is no room body, because there is no closed outline. The outer side of a chain follows the orientation `s` of the
 * entered turns (DOMAIN section 3).
 *
 * With [SceneOptions.exportableOnly], [buildRoom] returns an empty scene for an `OPEN` or `CONFLICT` room and [build]
 * leaves such rooms out.
 *
 * @param bandBuilder wall bands of DOMAIN section 8.
 * @param traverse walks the chain of a conflict room.
 */
class DefaultSceneBuilder(
    private val bandBuilder: WallBandBuilder = DefaultWallBandBuilder(),
    private val traverse: Traverse = DefaultTraverse(),
) : SceneBuilder {
    /**
     * @throws IllegalArgumentException if [evaluation] belongs to another room or its shape has not one vertex per
     * wall.
     */
    override fun buildRoom(
        room: Room,
        evaluation: RoomEvaluation,
        options: SceneOptions,
    ): RoomScene {
        require(evaluation.roomId == room.id) { "Evaluation of ${evaluation.roomId.value} given for ${room.id.value}" }
        val shape = evaluation.shape
        val elements =
            when {
                options.exportableOnly && !evaluation.status.isExportable -> emptyList()
                shape != null -> shapeElements(room, evaluation, shape, options)
                else -> chainElements(room, evaluation, options)
            }
        return RoomScene(room.id, evaluation.status, SceneBounds.of(elements), elements.toImmutableList())
    }

    /** @throws IllegalArgumentException if [evaluations] lacks a room of [project]. */
    override fun build(
        project: Project,
        evaluations: ImmutableMap<RoomId, RoomEvaluation>,
        options: SceneOptions,
    ): DrawingScene {
        val missing = project.rooms.firstOrNull { it.id !in evaluations }
        require(missing == null) { "Missing evaluation of room ${missing?.id?.value}" }
        val rooms =
            project.rooms.mapNotNull { room ->
                val evaluation = evaluations.getValue(room.id)
                if (options.exportableOnly && !evaluation.status.isExportable) {
                    null
                } else {
                    buildRoom(room, evaluation, options)
                }
            }
        return DrawingScene(rooms.toImmutableList(), rooms.mapNotNull { it.bounds }.reduceOrNull(WorldBounds::union))
    }

    private fun shapeElements(
        room: Room,
        evaluation: RoomEvaluation,
        shape: EvaluatedShape,
        options: SceneOptions,
    ): List<SceneElement> {
        require(shape.outline.vertices.size == room.walls.size) { "Expected one shape vertex per wall" }
        val transform = WorldTransform(room.placement)
        val vertices = shape.outline.vertices.map(transform::apply)
        val outline = Polygon2(vertices)
        val sign = shape.winding.sign
        val unconfirmed = evaluation.status.isUnconfirmed
        val lineStyle = if (unconfirmed) SceneStyle.WALL_UNCONFIRMED else SceneStyle.WALL_LINE
        val thicknesses = room.walls.map { it.thickness.mm }
        val walls =
            wallFrames(room.walls.map { it.id }, vertices, sign, thicknesses.map { it.toDouble() }, shape.lengthsMm)
        val layout = bandBuilder.build(BandInput(outline, shape.winding, thicknesses))
        val body = SceneTarget.RoomBody(room.id)
        return buildList {
            add(SceneElement.Fill(outline, lineStyle, body))
            layout.bands.forEach { add(band(room.id, walls, it, lineStyle)) }
            layout.corners.forEach { corner ->
                corner.fill?.let {
                    add(SceneElement.Fill(it, SceneStyle.WALL_BAND, SceneTarget.Corner(room.id, corner.cornerIndex)))
                }
            }
            addAll(openings(room, evaluation, walls))
            room.diagonals.forEach {
                addAll(
                    Annotations.diagonal(
                        room.id,
                        it,
                        vertices[it.fromCorner],
                        vertices[it.toCorner],
                        SceneStyle.DIAGONAL,
                    ),
                )
            }
            walls.forEach { addAll(Annotations.dimension(room.id, it)) }
            room.corners.forEachIndexed { i, spec ->
                if (spec is CornerSpec.Measured) {
                    add(
                        Annotations.interiorAngle(
                            room.id,
                            walls[(i + walls.size - 1) % walls.size],
                            walls[i],
                            shape.turnsRad[i],
                            sign,
                        ),
                    )
                }
            }
            addAll(
                RoomLabels.stacked(
                    RoomLabels.interiorPoint(vertices),
                    labelLines(room, options, shape.areaMm2, unconfirmed),
                    body,
                ),
            )
            vertices.forEachIndexed { i, vertex -> add(Annotations.cornerMarker(room.id, i, vertex)) }
        }
    }

    private fun chainElements(
        room: Room,
        evaluation: RoomEvaluation,
        options: SceneOptions,
    ): List<SceneElement> {
        val rawLengths = room.walls.map { it.length.mm.toDouble() }
        val local = (evaluation as? RoomEvaluation.Open)?.chain ?: traverse.walk(rawLengths, enteredTurnsRad(room))
        require(local.size == room.walls.size + 1) { "Expected one chain point more than walls" }
        val transform = WorldTransform(room.placement)
        val points = local.map(transform::apply)
        val conflict = evaluation as? RoomEvaluation.Conflict
        val wallStyle = if (conflict == null) SceneStyle.WALL_LINE else SceneStyle.CONFLICT
        val walls = wallFrames(room.walls.map { it.id }, points, chainSign(room), rawLengths.map { 0.0 }, rawLengths)
        return buildList {
            walls.forEach { add(segment(it.start, it.end, wallStyle, SceneTarget.Wall(room.id, it.wallId))) }
            addAll(openings(room, evaluation, walls))
            room.diagonals.forEach {
                val style = if (it.id == conflict?.diagonalId) SceneStyle.CONFLICT else SceneStyle.DIAGONAL
                addAll(Annotations.diagonal(room.id, it, points[it.fromCorner], points[it.toCorner], style))
            }
            walls.forEach { addAll(Annotations.dimension(room.id, it)) }
            val lines = labelLines(room, options, areaMm2 = null, unconfirmed = false)
            addAll(RoomLabels.stacked(RoomLabels.mean(points), lines, SceneTarget.RoomBody(room.id)))
            walls.forEach { add(Annotations.cornerMarker(room.id, it.index, it.start)) }
        }
    }

    private fun band(
        roomId: RoomId,
        walls: List<WallFrame>,
        band: WallBand,
        lineStyle: SceneStyle,
    ): SceneElement {
        val target = SceneTarget.Wall(roomId, walls[band.wallIndex].wallId)
        return when (band) {
            is WallBand.Filled -> SceneElement.Fill(band.shape, SceneStyle.WALL_BAND, target)
            is WallBand.Line -> segment(band.start, band.end, lineStyle, target)
        }
    }

    private fun openings(
        room: Room,
        evaluation: RoomEvaluation,
        walls: List<WallFrame>,
    ): List<SceneElement> {
        val byId = walls.associateBy { it.wallId }
        return room.openings.flatMap { opening ->
            val outOfBounds = evaluation.openingStatuses[opening.id] == OpeningStatus.OUT_OF_BOUNDS
            val style = if (outOfBounds) SceneStyle.OPENING_OUT_OF_BOUNDS else SceneStyle.OPENING
            OpeningSymbols.elements(
                byId.getValue(opening.wallId),
                opening,
                style,
                SceneTarget.Opening(room.id, opening.id),
            )
        }
    }

    private fun labelLines(
        room: Room,
        options: SceneOptions,
        areaMm2: Double?,
        unconfirmed: Boolean,
    ): List<RoomLabels.Line> =
        buildList {
            if (room.name.isNotBlank()) add(RoomLabels.Line(LabelText.Literal(room.name), SceneStyle.LABEL))
            room.typeKey?.let { add(RoomLabels.Line(LabelText.RoomType(it), SceneStyle.LABEL)) }
            val markStyle = if (unconfirmed) SceneStyle.WARNING else SceneStyle.LABEL
            areaMm2?.let { add(RoomLabels.Line(LabelText.Area(it), markStyle)) }
            if (unconfirmed) add(RoomLabels.Line(LabelText.ShapeNotConfirmed, SceneStyle.WARNING))
            if (options.includeNotes && room.notes.isNotBlank()) {
                add(RoomLabels.Line(LabelText.Literal(room.notes), SceneStyle.LABEL))
            }
        }

    private fun enteredTurns(room: Room): List<RawAngle> =
        room.corners.map {
            when (it) {
                is CornerSpec.DeclaredRight -> RawAngle.RIGHT.toTurn(it.direction)
                is CornerSpec.Measured -> it.angle.toTurn(it.direction)
                CornerSpec.Unspecified -> RawAngle.ZERO
            }
        }

    /** Entered turns `τ_i` in radians. The entry at index 0 is ignored by the walk. */
    private fun enteredTurnsRad(room: Room): List<Double> = enteredTurns(room).map { it.toRadians() }

    /** Orientation `s` of the entered turns: `+1` if `Σ_{i≥1} τ̂_i ≥ 0`, exact in millidegrees (DOMAIN section 3). */
    private fun chainSign(room: Room): Int =
        if (enteredTurns(room).drop(1).sumOf { it.milliDegrees.toLong() } >= 0L) 1 else -1
}
