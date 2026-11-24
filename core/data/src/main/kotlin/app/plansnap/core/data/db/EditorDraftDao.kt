package app.plansnap.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Access to unconfirmed editor input that must survive process death (REQ-DATA-002). */
@Dao
interface EditorDraftDao {
    /** Inserts or updates the draft of a room. Fails if the project or room does not exist. */
    @Upsert
    suspend fun upsert(draft: EditorDraftEntity)

    /** Returns the draft of a room or null. */
    @Query("SELECT * FROM editor_drafts WHERE projectId = :projectId AND roomId = :roomId")
    suspend fun get(
        projectId: String,
        roomId: String,
    ): EditorDraftEntity?

    /** Observes the draft of a room, emitting null while there is none. */
    @Query("SELECT * FROM editor_drafts WHERE projectId = :projectId AND roomId = :roomId")
    fun observe(
        projectId: String,
        roomId: String,
    ): Flow<EditorDraftEntity?>

    /** Returns all drafts of a project, for restoring after a restart. */
    @Query("SELECT * FROM editor_drafts WHERE projectId = :projectId ORDER BY updatedAtEpochMs DESC, roomId ASC")
    suspend fun getByProject(projectId: String): List<EditorDraftEntity>

    /** Deletes the draft of a room. Returns the number of deleted drafts. */
    @Query("DELETE FROM editor_drafts WHERE projectId = :projectId AND roomId = :roomId")
    suspend fun delete(
        projectId: String,
        roomId: String,
    ): Int
}
