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
import app.plansnap.core.model.inputFingerprint
import kotlinx.collections.immutable.persistentListOf

/** Hand-built project exercising every field of the format. Changing it requires regenerating the golden file. */
object TestProjects {
    fun walls(
        prefix: String,
        vararg lengths: Long,
    ) = lengths.mapIndexed { i, l -> Wall(WallId("$prefix${i + 1}"), Length(l), Length(300)) }

    private val doorId = OpeningId("door-1")

    private fun openings() =
        persistentListOf(
            Opening(
                doorId,
                WallId("a1"),
                OpeningType.DOOR,
                Length(500),
                Length(900),
                Length(2010),
                null,
                DoorSwing.HINGE_RIGHT_OUTWARD,
            ),
            Opening(
                OpeningId("win-1"),
                WallId("a2"),
                OpeningType.WINDOW,
                Length(1000),
                Length(1200),
                Length(1300),
                Length(900),
                null,
            ),
            Opening(OpeningId("pas-1"), WallId("a3"), OpeningType.PASSAGE, Length(0), Length(1000), null, null, null),
        )

    private fun roomA(): Room {
        val room =
            Room(
                id = RoomId("room-a"),
                name = "Wohnzimmer \"Süd\" äöü 😀",
                typeKey = RoomTypeKey("living"),
                notes = "line1\nline2\ttab",
                heightOverride = Length(2600),
                areaFactor = AreaFactor(50),
                walls = walls("a", 5000, 4000, 5000, 4000).let { persistentListOf(*it.toTypedArray()) },
                corners =
                    persistentListOf(
                        CornerSpec.Unspecified,
                        CornerSpec.DeclaredRight(TurnDirection.LEFT),
                        CornerSpec.Measured(TurnDirection.RIGHT, Angle(89_999)),
                        CornerSpec.Measured(TurnDirection.LEFT, Angle(180_000)),
                    ),
                diagonals = persistentListOf(Diagonal(DiagonalId("d1"), 0, 2, Length(6403))),
                openings = openings(),
                isClosed = true,
                placement = RoomPlacement(-0.0, 1234.5678901234567, 0.30000000000000004, null),
            )
        return room.copy(
            adjustment =
                ClosureAdjustment(
                    inputFingerprint = room.inputFingerprint(),
                    solverVersion = 3,
                    adjustedLengthsMm = persistentListOf(5000.1234567890123, 3999.9, 5000.0, 4e-5 + 1.0),
                    adjustedTurnsRad = persistentListOf(1.5707963267948966, -0.0, 5e-324, -1.0e300),
                ),
        )
    }

    private fun roomB(): Room =
        Room(
            id = RoomId("room-b"),
            name = "Flur",
            walls = walls("b", 3000, 4000, 5000).let { persistentListOf(*it.toTypedArray()) },
            corners =
                persistentListOf(
                    CornerSpec.Measured(TurnDirection.LEFT, Angle(90_000)),
                    CornerSpec.Measured(TurnDirection.LEFT, Angle(53_130)),
                    CornerSpec.Measured(TurnDirection.LEFT, Angle(36_870)),
                ),
            isClosed = true,
            placement =
                RoomPlacement(
                    5300.0,
                    0.0,
                    Math.PI,
                    DockLink(RoomId("room-a"), WallId("a1"), WallId("b1"), Length(-250), doorId),
                ),
        )

    fun representative(): Project =
        Project(
            id = ProjectId("project-1"),
            name = "Musterprojekt",
            defaultHeight = Length(2500),
            rooms = persistentListOf(roomA(), roomB(), Room(id = RoomId("room-c"))),
            createdAtEpochMs = 1_700_000_000_000L,
            updatedAtEpochMs = 1_700_000_123_456L,
        )
}
