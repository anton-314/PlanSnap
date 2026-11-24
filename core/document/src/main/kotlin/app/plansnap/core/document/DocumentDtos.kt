@file:UseSerializers(StrictLongSerializer::class, StrictIntSerializer::class, StrictDoubleSerializer::class)

package app.plansnap.core.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/** Version header of every document, see [FormatVersion]. */
@Serializable
internal data class VersionDto(
    val major: Int,
    val minor: Int,
)

/** Wire shape of a wall. */
@Serializable
internal data class WallDto(
    val id: String,
    val lengthMm: Long,
    val thicknessMm: Long,
)

/** Wire shape of a corner specification. */
@Serializable
internal data class CornerDto(
    val kind: String,
    val direction: String?,
    val angleMilliDegrees: Int?,
)

/** Wire shape of a diagonal. */
@Serializable
internal data class DiagonalDto(
    val id: String,
    val fromCorner: Int,
    val toCorner: Int,
    val lengthMm: Long,
)

/** Wire shape of an opening. */
@Serializable
internal data class OpeningDto(
    val id: String,
    val wallId: String,
    val type: String,
    val offsetMm: Long,
    val widthMm: Long,
    val heightMm: Long?,
    val sillHeightMm: Long?,
    val swing: String?,
)

/** Wire shape of a dock link. */
@Serializable
internal data class DockDto(
    val hostRoomId: String,
    val hostWallId: String,
    val ownWallId: String,
    val hostOffsetMm: Long,
    val hostOpeningId: String?,
)

/** Wire shape of a placement. */
@Serializable
internal data class PlacementDto(
    val originX: Double,
    val originY: Double,
    val rotationRad: Double,
    val dock: DockDto?,
)

/** Wire shape of a closure adjustment overlay. */
@Serializable
internal data class AdjustmentDto(
    val inputFingerprint: Long,
    val solverVersion: Int,
    val adjustedLengthsMm: List<Double>,
    val adjustedTurnsRad: List<Double>,
)

/** Wire shape of a room. */
@Serializable
internal data class RoomDto(
    val id: String,
    val name: String,
    val typeKey: String?,
    val notes: String,
    val heightOverrideMm: Long?,
    val areaFactorPercent: Int,
    val walls: List<WallDto>,
    val corners: List<CornerDto>,
    val diagonals: List<DiagonalDto>,
    val openings: List<OpeningDto>,
    val isClosed: Boolean,
    val adjustment: AdjustmentDto?,
    val placement: PlacementDto,
)

/** Wire shape of a project. */
@Serializable
internal data class ProjectDto(
    val id: String,
    val name: String,
    val defaultHeightMm: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val rooms: List<RoomDto>,
)

/** Top-level project document. */
@Serializable
internal data class ProjectDocumentDto(
    val formatVersion: VersionDto,
    val project: ProjectDto,
)

/** Top-level room document. */
@Serializable
internal data class RoomDocumentDto(
    val formatVersion: VersionDto,
    val room: RoomDto,
)
