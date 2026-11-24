package app.plansnap.core.document

import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Opening
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomPlacement

internal const val KIND_UNSPECIFIED = "UNSPECIFIED"
internal const val KIND_DECLARED_RIGHT = "DECLARED_RIGHT"
internal const val KIND_MEASURED = "MEASURED"

internal fun Project.toDto(): ProjectDto =
    ProjectDto(
        id = id.value,
        name = name,
        defaultHeightMm = defaultHeight.mm,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        rooms = rooms.map { it.toDto() },
    )

internal fun Room.toDto(): RoomDto =
    RoomDto(
        id = id.value,
        name = name,
        typeKey = typeKey?.value,
        notes = notes,
        heightOverrideMm = heightOverride?.mm,
        areaFactorPercent = areaFactor.percent,
        walls = walls.map { WallDto(it.id.value, it.length.mm, it.thickness.mm) },
        corners = corners.map { it.toDto() },
        diagonals = diagonals.map { DiagonalDto(it.id.value, it.fromCorner, it.toCorner, it.length.mm) },
        openings = openings.map { it.toDto() },
        isClosed = isClosed,
        adjustment = adjustment?.toDto(),
        placement = placement.toDto(),
    )

private fun CornerSpec.toDto(): CornerDto =
    when (this) {
        CornerSpec.Unspecified -> CornerDto(KIND_UNSPECIFIED, null, null)
        is CornerSpec.DeclaredRight -> CornerDto(KIND_DECLARED_RIGHT, direction.name, null)
        is CornerSpec.Measured -> CornerDto(KIND_MEASURED, direction.name, angle.milliDegrees)
    }

private fun Opening.toDto(): OpeningDto =
    OpeningDto(
        id = id.value,
        wallId = wallId.value,
        type = type.name,
        offsetMm = offset.mm,
        widthMm = width.mm,
        heightMm = height?.mm,
        sillHeightMm = sillHeight?.mm,
        swing = swing?.name,
    )

private fun ClosureAdjustment.toDto(): AdjustmentDto =
    AdjustmentDto(inputFingerprint, solverVersion, adjustedLengthsMm, adjustedTurnsRad)

private fun RoomPlacement.toDto(): PlacementDto =
    PlacementDto(
        originX = originX,
        originY = originY,
        rotationRad = rotationRad,
        dock =
            dock?.let {
                DockDto(
                    hostRoomId = it.hostRoomId.value,
                    hostWallId = it.hostWallId.value,
                    ownWallId = it.ownWallId.value,
                    hostOffsetMm = it.hostOffset.mm,
                    hostOpeningId = it.hostOpeningId?.value,
                )
            },
    )
