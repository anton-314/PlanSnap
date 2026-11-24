package app.plansnap.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Project metadata row (ARCHITECTURE section 7, ADR 0006).
 *
 * @property id stable project id.
 * @property name display name.
 * @property defaultHeightMm default room height in millimetres.
 * @property formatMajor major version of the room document format stored with this project.
 * @property formatMinor minor version of the room document format stored with this project.
 * @property createdAtEpochMs creation time.
 * @property updatedAtEpochMs last change time, also touched by every room change.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val defaultHeightMm: Double,
    val formatMajor: Int,
    val formatMinor: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

/**
 * One room of a project, stored as a JSON document of `:core:document`.
 *
 * @property id stable room id.
 * @property projectId owning project, deleting the project deletes the room.
 * @property position zero-based display order within the project.
 * @property documentJson room document as written by the document codec.
 */
@Entity(
    tableName = "rooms",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["projectId", "position"])],
)
data class RoomEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val position: Int,
    val documentJson: String,
)

/**
 * Unconfirmed editor input of a project that survives process death, one draft per project (REQ-DATA-002).
 *
 * @property projectId owning project, deleting the project deletes the draft.
 * @property roomId room being edited, `null` if none is selected. Deleting the room deletes the draft.
 * @property inputText running input, exactly as typed.
 * @property updatedAtEpochMs time of the last draft change.
 */
@Entity(
    tableName = "editor_drafts",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["roomId"])],
)
data class EditorDraftEntity(
    @PrimaryKey val projectId: String,
    val roomId: String?,
    val inputText: String,
    val updatedAtEpochMs: Long,
)
