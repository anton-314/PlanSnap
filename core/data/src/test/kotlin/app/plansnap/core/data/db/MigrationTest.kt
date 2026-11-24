package app.plansnap.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            PlanSnapDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun `schema version 1 is created from the exported schema and validates`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO projects VALUES ('p1', 'Flat', 2500.0, 1, 0, 1, 2)")
            db.execSQL("INSERT INTO rooms VALUES ('r1', 'p1', 0, '{}')")
            db.execSQL("INSERT INTO editor_drafts VALUES ('p1', NULL, 'draft', 3)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 1, true, *PlanSnapMigrations.ALL).use { db ->
            db.query("SELECT COUNT(*) FROM editor_drafts").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `migration registry reaches the current version without gaps`() {
        assertEquals(PlanSnapDatabase.VERSION - 1, PlanSnapMigrations.ALL.size)
        PlanSnapMigrations.ALL.forEachIndexed { index, migration ->
            assertEquals(index + 1, migration.startVersion)
            assertEquals(index + 2, migration.endVersion)
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
