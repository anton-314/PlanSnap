package app.plansnap.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/** Room database of PlanSnap, schema exported to `core/data/schemas` (ARCHITECTURE section 7). */
@Database(
    entities = [ProjectEntity::class, RoomEntity::class, EditorDraftEntity::class],
    version = PlanSnapDatabase.VERSION,
    exportSchema = true,
)
abstract class PlanSnapDatabase : RoomDatabase() {
    /** Project metadata. */
    abstract fun projectDao(): ProjectDao

    /** Room documents. */
    abstract fun roomDao(): RoomDao

    /** Editor drafts. */
    abstract fun editorDraftDao(): EditorDraftDao

    /** Constants and factory. */
    companion object {
        /** Current schema version. Raising it requires an entry in [PlanSnapMigrations.ALL] and an exported schema. */
        const val VERSION: Int = 1

        /** File name of the database. */
        const val FILE_NAME: String = "plansnap.db"

        /** Opens the on-disk database with all migrations registered. */
        @Suppress("SpreadOperator") // Called once per process with a short array.
        fun open(context: Context): PlanSnapDatabase =
            Room
                .databaseBuilder(context.applicationContext, PlanSnapDatabase::class.java, FILE_NAME)
                .addMigrations(*PlanSnapMigrations.ALL)
                .build()
    }
}

/** Registry of schema migrations. */
object PlanSnapMigrations {
    /**
     * All migrations in ascending order. Each one has a migration test against the exported schemas.
     * Empty while schema version 1 is the only version.
     */
    val ALL: Array<Migration> = emptyArray()
}
