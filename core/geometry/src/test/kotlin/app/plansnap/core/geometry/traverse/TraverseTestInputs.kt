package app.plansnap.core.geometry.traverse

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Room

/** Maps a model room to the model-free input, as `:core:domain` does. */
fun Room.toShapeInput(): ShapeInput =
    ShapeInput(
        lengthsMm = walls.map { it.length.mm },
        corners =
            corners.map {
                when (it) {
                    is CornerSpec.DeclaredRight -> CornerInput.DeclaredRight(it.direction)
                    is CornerSpec.Measured -> CornerInput.Measured(it.direction, it.angle)
                    CornerSpec.Unspecified -> CornerInput.Free
                }
            },
        diagonals = diagonals.map { DiagonalInput(it.fromCorner, it.toCorner, it.length.mm) },
    )

/** Left turn by [turnMilliDegrees] (`α = 180° − τ`), or right turn for a negative value. */
fun turnCorner(turnMilliDegrees: Int): CornerInput.Measured =
    if (turnMilliDegrees >= 0) {
        CornerInput.Measured(TurnDirection.LEFT, Angle(STRAIGHT_MILLI_DEGREES - turnMilliDegrees))
    } else {
        CornerInput.Measured(TurnDirection.RIGHT, Angle(STRAIGHT_MILLI_DEGREES + turnMilliDegrees))
    }

/** Outline with declared right angles in [direction] at every corner after the free closing corner. */
fun rightAngled(
    vararg lengthsMm: Long,
    direction: TurnDirection = TurnDirection.LEFT,
    closing: CornerInput = CornerInput.Free,
    diagonals: List<DiagonalInput> = emptyList(),
): ShapeInput =
    ShapeInput(
        lengthsMm = lengthsMm.toList(),
        corners = listOf(closing) + List(lengthsMm.size - 1) { CornerInput.DeclaredRight(direction) },
        diagonals = diagonals,
    )

const val STRAIGHT_MILLI_DEGREES = 180_000
