package app.plansnap.testing.fixtures.rooms

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.toImmutableList

private const val RIGHT_MILLI_DEGREES = 90_000

/**
 * Closed room from corner tokens: `F` unspecified, `L` or `R` declared right angle, `M<milliDegrees>` measured to the
 * left and `m<milliDegrees>` measured to the right.
 */
internal fun mixedRoom(
    id: String,
    lengths: String,
    corners: String,
    diagonals: String,
): Room = closedRoom(id, lengths, corners.split(' ').map(::cornerOf), diagonals)

private fun cornerOf(token: String): CornerSpec =
    when (token[0]) {
        'F' -> CornerSpec.Unspecified
        'M' -> CornerSpec.Measured(TurnDirection.LEFT, Angle(token.drop(1).toInt()))
        'm' -> CornerSpec.Measured(TurnDirection.RIGHT, Angle(token.drop(1).toInt()))
        else -> CornerSpec.DeclaredRight(directionOf(token[0]))
    }

/** Closed room with a free closing corner and declared right angles in [directions] (index 0 is not used). */
internal fun declaredRoom(
    id: String,
    lengths: String,
    directions: String,
    diagonals: String,
): Room =
    closedRoom(
        id,
        lengths,
        directions.mapIndexed { i, code ->
            if (i == 0) CornerSpec.Unspecified else CornerSpec.DeclaredRight(directionOf(code))
        },
        diagonals,
    )

/** Closed room in which every corner, including `V_0`, is measured as 90 degrees in [directions]. */
internal fun measuredRoom(
    id: String,
    lengths: String,
    directions: String,
): Room =
    closedRoom(
        id,
        lengths,
        directions.map { CornerSpec.Measured(directionOf(it), Angle(RIGHT_MILLI_DEGREES)) },
        "",
    )

private fun closedRoom(
    id: String,
    lengths: String,
    corners: List<CornerSpec>,
    diagonals: String,
): Room =
    Room(
        id = RoomId(id),
        walls =
            lengths
                .split(' ')
                .mapIndexed { i, mm ->
                    Wall(WallId("$id-w$i"), Length(mm.toLong()), Length(GeometryTolerances.DEFAULT_THICKNESS_MM))
                }.toImmutableList(),
        corners = corners.toImmutableList(),
        diagonals =
            diagonals
                .split(' ')
                .filter { it.isNotEmpty() }
                .mapIndexed { i, spec ->
                    val (from, to, mm) = spec.split('-', '=')
                    Diagonal(DiagonalId("$id-d$i"), from.toInt(), to.toInt(), Length(mm.toLong()))
                }.toImmutableList(),
        isClosed = true,
    )

private fun directionOf(code: Char): TurnDirection = if (code == 'L') TurnDirection.LEFT else TurnDirection.RIGHT
