package app.plansnap.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Access to room documents. Every change of a room also touches the change time of its project. */
@Dao
abstract class RoomDao {
    /** Observes the rooms of a project in display order. */
    @Query("SELECT * FROM rooms WHERE projectId = :projectId ORDER BY position ASC, id ASC")
    abstract fun observeByProject(projectId: String): Flow<List<RoomEntity>>

    /** Returns the rooms of a project in display order. */
    @Query("SELECT * FROM rooms WHERE projectId = :projectId ORDER BY position ASC, id ASC")
    abstract suspend fun getByProject(projectId: String): List<RoomEntity>

    /** Returns those of [roomIds] that are stored in any project. At most 500 ids per call. */
    @Query("SELECT id FROM rooms WHERE id IN (:roomIds)")
    abstract suspend fun existingIds(roomIds: List<String>): List<String>

    /** Returns one room or null. */
    @Query("SELECT * FROM rooms WHERE id = :roomId")
    abstract suspend fun get(roomId: String): RoomEntity?

    /**
     * Inserts or updates room rows in place, so drafts of existing rooms survive.
     * Fails on a missing project. Prefer [save] to keep the project time current.
     */
    @Upsert
    abstract suspend fun upsert(rooms: List<RoomEntity>)

    /** Deletes one room and its draft. Returns the number of deleted rooms. Prefer [remove]. */
    @Query("DELETE FROM rooms WHERE id = :roomId AND projectId = :projectId")
    abstract suspend fun delete(
        projectId: String,
        roomId: String,
    ): Int

    /** Sets the change time of a project. Returns the number of changed rows, 0 if it does not exist. */
    @Query("UPDATE projects SET updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId")
    abstract suspend fun touchProject(
        projectId: String,
        updatedAtEpochMs: Long,
    ): Int

    /**
     * Writes the given rooms and the change time of their project in one transaction.
     * Rooms may all belong to the same project only. Nothing is written if any step fails,
     * for example because the project does not exist.
     *
     * @throws IllegalStateException if the project does not exist.
     */
    @Transaction
    open suspend fun save(
        projectId: String,
        rooms: List<RoomEntity>,
        updatedAtEpochMs: Long,
    ) {
        require(rooms.all { it.projectId == projectId }) { "All rooms must belong to project $projectId" }
        check(touchProject(projectId, updatedAtEpochMs) == 1) { "Unknown project $projectId" }
        upsert(rooms)
    }

    /**
     * Deletes a room (its draft cascades) and touches the project in one transaction.
     *
     * @return true if the room existed.
     * @throws IllegalStateException if the project does not exist.
     */
    @Transaction
    open suspend fun remove(
        projectId: String,
        roomId: String,
        updatedAtEpochMs: Long,
    ): Boolean {
        check(touchProject(projectId, updatedAtEpochMs) == 1) { "Unknown project $projectId" }
        return delete(projectId, roomId) == 1
    }
}
