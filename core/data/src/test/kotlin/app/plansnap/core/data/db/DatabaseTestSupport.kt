package app.plansnap.core.data.db

import androidx.room.Room
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment

internal fun inMemoryDatabase(): PlanSnapDatabase {
    val context = RuntimeEnvironment.getApplication()
    return Room
        .inMemoryDatabaseBuilder(context, PlanSnapDatabase::class.java)
        .allowMainThreadQueries()
        .build()
}

internal fun project(
    id: String = "p1",
    name: String = "Flat",
    updatedAt: Long = 100L,
) = ProjectEntity(
    id = id,
    name = name,
    defaultHeightMm = 2500.0,
    formatMajor = 1,
    formatMinor = 0,
    createdAtEpochMs = 10L,
    updatedAtEpochMs = updatedAt,
)

internal fun room(
    id: String,
    projectId: String = "p1",
    position: Int = 0,
    json: String = "{\"id\":\"$id\"}",
) = RoomEntity(id = id, projectId = projectId, position = position, documentJson = json)

internal suspend inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue("Expected ${T::class.simpleName} but was $failure", failure is T)
}
