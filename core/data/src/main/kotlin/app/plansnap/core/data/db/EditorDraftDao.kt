package app.plansnap.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Access to unconfirmed editor input that must survive process death (REQ-DATA-002), one draft per project. */
@Dao
interface EditorDraftDao {
    /** Inserts or replaces the draft of its project. Fails if the project or a given room does not exist. */
    @Upsert
    suspend fun upsert(draft: EditorDraftEntity)

    /** Returns the draft of a project or null. */
    @Query("SELECT * FROM editor_drafts WHERE projectId = :projectId")
    suspend fun get(projectId: String): EditorDraftEntity?

    /** Observes the draft of a project, emitting null while there is none. */
    @Query("SELECT * FROM editor_drafts WHERE projectId = :projectId")
    fun observe(projectId: String): Flow<EditorDraftEntity?>

    /** Deletes the draft of a project. Returns the number of deleted drafts. */
    @Query("DELETE FROM editor_drafts WHERE projectId = :projectId")
    suspend fun delete(projectId: String): Int
}
