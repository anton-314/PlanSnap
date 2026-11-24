package app.plansnap.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.cash.turbine.test
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.testing.fixtures.runtime.TestDispatcherProvider
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, "settings.preferences_pb") }

    private val unitKey = stringPreferencesKey("length_unit")
    private val absKey = longPreferencesKey("tolerance_abs_mm")
    private val relKey = doublePreferencesKey("tolerance_relative")
    private val gridKey = booleanPreferencesKey("grid_enabled")
    private val thicknessKey = longPreferencesKey("last_thickness_mm")
    private val hintsKey = stringSetPreferencesKey("dismissed_hints")

    private class Fixture(
        val store: DataStore<Preferences>,
        val repository: DataStoreSettingsRepository,
        private val scope: CoroutineScope,
    ) {
        fun close() = scope.cancel()
    }

    private fun kotlinx.coroutines.test.TestScope.open(): Fixture {
        val scope = CoroutineScope(Job())
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler))
        return Fixture(store, DataStoreSettingsRepository(store, dispatchers), scope)
    }

    @Test
    fun emptyStoreEmitsDefaults() =
        runTest {
            val fixture = open()
            fixture.repository.settings.test {
                assertEquals(AppSettings(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            fixture.close()
        }

    @Test
    fun updatePersistsAllFieldsAndFreshInstanceReadsThem() =
        runTest {
            val expected =
                AppSettings(
                    lengthUnit = LengthUnit.MILLIMETER,
                    tolerance = ClosureTolerance(absoluteMm = 120L, relative = 0.05),
                    gridEnabled = false,
                    snapEnabled = false,
                    lastThickness = Length(240L),
                    dismissedHints = persistentSetOf(EditorHint.NUMPAD, EditorHint.CLOSE),
                )
            val first = open()
            assertEquals(DomainResult.Success(Unit), first.repository.update { expected })
            first.close()

            val second = open()
            second.repository.settings.test {
                assertEquals(expected, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            second.close()
        }

    @Test
    fun updateEmitsNewSettings() =
        runTest {
            val fixture = open()
            fixture.repository.settings.test {
                assertEquals(AppSettings(), awaitItem())
                fixture.repository.update { it.copy(gridEnabled = false) }
                assertEquals(AppSettings(gridEnabled = false), awaitItem())
                fixture.repository.update { it.copy(lastThickness = Length(300L)) }
                assertEquals(AppSettings(gridEnabled = false, lastThickness = Length(300L)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            fixture.close()
        }

    @Test
    fun unchangedUpdateDoesNotEmitAgain() =
        runTest {
            val fixture = open()
            fixture.repository.settings.test {
                assertEquals(AppSettings(), awaitItem())
                fixture.repository.update { it }
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            fixture.close()
        }

    @Test
    fun storedAbsoluteMmBelowOneFallsBackToDefaultWithoutCrash() =
        runTest {
            val fixture = open()
            fixture.store.edit { it[absKey] = 0L }
            fixture.repository.settings.test {
                assertEquals(AppSettings(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            fixture.close()
        }

    @Test
    fun invalidAbsoluteMmFallsBackPerField() =
        runTest {
            val fixture = open()
            for (invalid in listOf(0L, -5L, GeometryTolerances.MAX_WALL_MM + 1)) {
                fixture.store.edit {
                    it[absKey] = invalid
                    it[relKey] = 0.25
                    it[gridKey] = false
                }
                fixture.repository.settings.test {
                    val item = awaitItem()
                    assertEquals(GeometryTolerances.DEFAULT_TOLERANCE_ABS_MM, item.tolerance.absoluteMm)
                    assertEquals(0.25, item.tolerance.relative, 0.0)
                    assertEquals(false, item.gridEnabled)
                    cancelAndIgnoreRemainingEvents()
                }
            }
            fixture.close()
        }

    @Test
    fun boundaryAbsoluteMmValuesAreAccepted() =
        runTest {
            val fixture = open()
            for (valid in listOf(GeometryTolerances.MIN_TOLERANCE_ABS_MM, GeometryTolerances.MAX_WALL_MM)) {
                fixture.store.edit { it[absKey] = valid }
                fixture.repository.settings.test {
                    assertEquals(valid, awaitItem().tolerance.absoluteMm)
                    cancelAndIgnoreRemainingEvents()
                }
            }
            fixture.close()
        }

    @Test
    fun invalidRelativeFallsBackPerField() =
        runTest {
            val fixture = open()
            for (invalid in listOf(Double.NaN, 2.0, -0.1, Double.POSITIVE_INFINITY)) {
                fixture.store.edit {
                    it[relKey] = invalid
                    it[absKey] = 80L
                }
                fixture.repository.settings.test {
                    val item = awaitItem()
                    assertEquals(GeometryTolerances.DEFAULT_TOLERANCE_REL, item.tolerance.relative, 0.0)
                    assertEquals(80L, item.tolerance.absoluteMm)
                    cancelAndIgnoreRemainingEvents()
                }
            }
            fixture.close()
        }

    @Test
    fun unknownUnitThicknessAndHintsFallBack() =
        runTest {
            val fixture = open()
            fixture.store.edit {
                it[unitKey] = "FURLONG"
                it[thicknessKey] = GeometryTolerances.MAX_THICKNESS_MM + 1
                it[hintsKey] = setOf("NUMPAD", "UNKNOWN_HINT")
                it[gridKey] = false
            }
            fixture.repository.settings.test {
                val item = awaitItem()
                assertEquals(AppSettings().lengthUnit, item.lengthUnit)
                assertEquals(AppSettings().lastThickness, item.lastThickness)
                assertEquals(persistentSetOf(EditorHint.NUMPAD), item.dismissedHints)
                assertEquals(false, item.gridEnabled)
                cancelAndIgnoreRemainingEvents()
            }
            fixture.close()
        }

    @Test
    fun negativeThicknessFallsBackToDefault() =
        runTest {
            val fixture = open()
            fixture.store.edit { it[thicknessKey] = -1L }
            fixture.repository.settings.test {
                assertEquals(AppSettings().lastThickness, awaitItem().lastThickness)
                cancelAndIgnoreRemainingEvents()
            }
            fixture.close()
        }

    @Test
    fun valueStoredWithWrongTypeFallsBackWithoutCrash() =
        runTest {
            val fixture = open()
            fixture.store.edit {
                it[stringPreferencesKey("tolerance_abs_mm")] = "oops"
                it[stringPreferencesKey("grid_enabled")] = "yes"
                it[unitKey] = LengthUnit.MILLIMETER.name
            }
            fixture.repository.settings.test {
                val item = awaitItem()
                assertEquals(AppSettings().tolerance, item.tolerance)
                assertEquals(AppSettings().gridEnabled, item.gridEnabled)
                assertEquals(LengthUnit.MILLIMETER, item.lengthUnit)
                cancelAndIgnoreRemainingEvents()
            }
            fixture.close()
        }

    @Test
    fun updateRejectsTooLargeAbsoluteToleranceAndKeepsStoredSettings() =
        runTest {
            val fixture = open()
            val result =
                fixture.repository.update {
                    it.copy(tolerance = ClosureTolerance(absoluteMm = GeometryTolerances.MAX_WALL_MM + 1))
                }
            assertEquals(
                DomainResult.Failure(DomainError.InvalidValue(ValueField.TOLERANCE, InvalidValueReason.TOO_LARGE)),
                result,
            )
            fixture.repository.settings.test {
                assertEquals(AppSettings(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            fixture.close()
        }

    @Test
    fun throwingTransformPropagatesAndLeavesStoredSettingsUnchanged() =
        runTest {
            val fixture = open()
            var thrown: Throwable? = null
            try {
                fixture.repository.update { error("boom") }
            } catch (expected: IllegalStateException) {
                thrown = expected
            }
            assertTrue(thrown != null)
            fixture.repository.settings.test {
                assertEquals(AppSettings(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            fixture.close()
        }
}
