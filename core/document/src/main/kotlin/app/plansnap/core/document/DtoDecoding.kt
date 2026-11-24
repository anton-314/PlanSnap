package app.plansnap.core.document

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.toImmutableList

/** Builds the model. Throws [IllegalArgumentException] for values outside their ranges. */
internal fun ProjectDto.toModel(): Project =
    Project(
        id = ProjectId(id),
        name = name,
        defaultHeight = Length(defaultHeightMm),
        rooms = rooms.map { it.toModel() }.toImmutableList(),
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

/** Builds the model. Throws [IllegalArgumentException] for values outside their ranges. */
internal fun RoomDto.toModel(): Room =
    Room(
        id = RoomId(id),
        name = name,
        typeKey = typeKey?.let { RoomTypeKey(it) },
        notes = notes,
        heightOverride = heightOverrideMm?.let { Length(it) },
        areaFactor = AreaFactor(areaFactorPercent),
        walls = walls.map { Wall(WallId(it.id), Length(it.lengthMm), Length(it.thicknessMm)) }.toImmutableList(),
        corners = corners.map { it.toModel() }.toImmutableList(),
        diagonals =
            diagonals
                .map { Diagonal(DiagonalId(it.id), it.fromCorner, it.toCorner, Length(it.lengthMm)) }
                .toImmutableList(),
        openings = openings.map { it.toModel() }.toImmutableList(),
        isClosed = isClosed,
        adjustment = adjustment?.toModel(),
        placement = placement.toModel(),
    )

private fun CornerDto.toModel(): CornerSpec =
    when (kind) {
        KIND_UNSPECIFIED -> {
            require(direction == null && angleMilliDegrees == null) { "Unspecified corner carries values" }
            CornerSpec.Unspecified
        }

        KIND_DECLARED_RIGHT -> {
            require(angleMilliDegrees == null) { "Declared right corner carries an angle" }
            CornerSpec.DeclaredRight(directionOf(direction))
        }

        KIND_MEASURED -> {
            requireNotNull(angleMilliDegrees) { "Measured corner needs an angle" }
            CornerSpec.Measured(directionOf(direction), Angle(angleMilliDegrees))
        }

        else -> {
            throw IllegalArgumentException("Unknown corner kind: $kind")
        }
    }

private fun directionOf(name: String?): TurnDirection {
    requireNotNull(name) { "Corner needs a direction" }
    return requireNotNull(TurnDirection.entries.firstOrNull { it.name == name }) { "Unknown direction: $name" }
}

private fun swingOf(name: String): DoorSwing =
    requireNotNull(DoorSwing.entries.firstOrNull { it.name == name }) { "Unknown swing: $name" }

private fun OpeningDto.toModel(): Opening =
    Opening(
        id = OpeningId(id),
        wallId = WallId(wallId),
        type = requireNotNull(OpeningType.entries.firstOrNull { it.name == type }) { "Unknown opening type: $type" },
        offset = Length(offsetMm),
        width = Length(widthMm),
        height = heightMm?.let { Length(it) },
        sillHeight = sillHeightMm?.let { Length(it) },
        swing = swing?.let { swingOf(it) },
    )

private fun AdjustmentDto.toModel(): ClosureAdjustment =
    ClosureAdjustment(
        inputFingerprint = inputFingerprint,
        solverVersion = solverVersion,
        adjustedLengthsMm = adjustedLengthsMm.toImmutableList(),
        adjustedTurnsRad = adjustedTurnsRad.toImmutableList(),
    )

private fun PlacementDto.toModel(): RoomPlacement =
    RoomPlacement(
        originX = originX,
        originY = originY,
        rotationRad = rotationRad,
        dock =
            dock?.let {
                DockLink(
                    hostRoomId = RoomId(it.hostRoomId),
                    hostWallId = WallId(it.hostWallId),
                    ownWallId = WallId(it.ownWallId),
                    hostOffset = Length(it.hostOffsetMm),
                    hostOpeningId = it.hostOpeningId?.let { opening -> OpeningId(opening) },
                )
            },
    )
