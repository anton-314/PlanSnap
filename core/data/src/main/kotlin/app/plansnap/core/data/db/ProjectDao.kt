package app.plansnap.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Access to project metadata and project-wide atomic operations. */
@Dao
abstract class ProjectDao {
    /** Observes all projects, most recently changed first. */
    @Query("SELECT * FROM projects ORDER BY updatedAtEpochMs DESC, id ASC")
    abstract fun observeAll(): Flow<List<ProjectEntity>>

    /** Observes one project, emitting null while it does not exist. */
    @Query("SELECT * FROM projects WHERE id = :projectId")
    abstract fun observe(projectId: String): Flow<ProjectEntity?>

    /** Returns one project or null. */
    @Query("SELECT * FROM projects WHERE id = :projectId")
    abstract suspend fun get(projectId: String): ProjectEntity?

    /** Inserts the project row only, failing if the id exists. Prefer [create]. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(project: ProjectEntity)

    /** Inserts room rows only, failing if an id exists. Prefer [create]. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRooms(rooms: List<RoomEntity>)

    /**
     * Renames a project and sets its change time.
     *
     * @return number of changed rows, 0 if the project does not exist.
     */
    @Query("UPDATE projects SET name = :name, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId")
    abstract suspend fun rename(
        projectId: String,
        name: String,
        updatedAtEpochMs: Long,
    ): Int

    /**
     * Deletes a project. Rooms and drafts are deleted by the foreign key cascade.
     *
     * @return number of deleted projects, 0 if it does not exist.
     */
    @Query("DELETE FROM projects WHERE id = :projectId")
    abstract suspend fun delete(projectId: String): Int

    /**
     * Creates a project together with all its rooms in one transaction.
     * Fails without leaving any row if the id of the project or of any room already exists.
     */
    @Transaction
    open suspend fun create(
        project: ProjectEntity,
        rooms: List<RoomEntity>,
    ) {
        insert(project)
        insertRooms(rooms)
    }
}
