package app.plansnap.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.GeometryTolerances
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

/** Largest absolute tolerance in mm that is stored, equal to the longest wall. Larger values are invalid. */
private const val MAX_TOLERANCE_ABS_MM: Long = GeometryTolerances.MAX_WALL_MM

private object Keys {
    val unit = stringPreferencesKey("length_unit")
    val toleranceAbsMm = longPreferencesKey("tolerance_abs_mm")
    val toleranceRelative = doublePreferencesKey("tolerance_relative")
    val grid = booleanPreferencesKey("grid_enabled")
    val snap = booleanPreferencesKey("snap_enabled")
    val thicknessMm = longPreferencesKey("last_thickness_mm")
    val hints = stringSetPreferencesKey("dismissed_hints")
}

/**
 * [SettingsRepository] on top of a Preferences [DataStore].
 *
 * Every stored field is validated on its own while reading. An unknown, out-of-range or missing value falls back to
 * the default of that field, the other fields stay intact. An [IOException] while reading yields the defaults.
 * [update] runs the transform and the write in one atomic `edit`. A transform that throws, for example because the
 * [AppSettings] invariants are violated, leaves the stored settings unchanged and propagates to the caller. A
 * result with an absolute tolerance above the storable maximum is rejected with
 * [DomainError.InvalidValue] and not written.
 *
 * @param dataStore storage of the settings.
 * @param dispatchers source of the dispatcher for storage access.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : SettingsRepository {
    override val settings: Flow<AppSettings> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { it.toSettings() }
            .distinctUntilChanged()
            .flowOn(dispatchers.io)

    override suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit> =
        withContext(dispatchers.io) {
            try {
                var rejected: DomainError? = null
                dataStore.edit { preferences ->
                    val next = transform(preferences.toSettings())
                    if (next.tolerance.absoluteMm > MAX_TOLERANCE_ABS_MM) {
                        rejected = DomainError.InvalidValue(ValueField.TOLERANCE, InvalidValueReason.TOO_LARGE)
                    } else {
                        preferences.write(next)
                    }
                }
                rejected?.let { DomainResult.Failure(it) } ?: DomainResult.Success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: IOException) {
                DomainResult.Failure(DomainError.Storage(StorageProblem.WRITE_FAILED))
            }
        }
}

/** Reads [key], treating a value stored with a different type as absent. */
private inline fun <reified T : Any> Preferences.readOrNull(key: Preferences.Key<T>): T? = asMap()[key] as? T

private fun Preferences.toSettings(): AppSettings {
    val defaults = AppSettings()
    val unit = readOrNull(Keys.unit)?.let { name -> LengthUnit.entries.firstOrNull { it.name == name } }
    val absoluteMm =
        readOrNull(Keys.toleranceAbsMm)?.takeIf { it in GeometryTolerances.MIN_TOLERANCE_ABS_MM..MAX_TOLERANCE_ABS_MM }
    val relative = readOrNull(Keys.toleranceRelative)?.takeIf { it.isFinite() && it in 0.0..1.0 }
    val thickness = readOrNull(Keys.thicknessMm)?.takeIf { it in 0L..GeometryTolerances.MAX_THICKNESS_MM }
    val hints = readOrNull(Keys.hints)?.mapNotNull { name -> EditorHint.entries.firstOrNull { it.name == name } }
    return AppSettings(
        lengthUnit = unit ?: defaults.lengthUnit,
        tolerance =
            ClosureTolerance(
                absoluteMm = absoluteMm ?: defaults.tolerance.absoluteMm,
                relative = relative ?: defaults.tolerance.relative,
            ),
        gridEnabled = readOrNull(Keys.grid) ?: defaults.gridEnabled,
        snapEnabled = readOrNull(Keys.snap) ?: defaults.snapEnabled,
        lastThickness = thickness?.let(::Length) ?: defaults.lastThickness,
        dismissedHints = hints?.toImmutableSet() ?: defaults.dismissedHints,
    )
}

private fun MutablePreferences.write(settings: AppSettings) {
    this[Keys.unit] = settings.lengthUnit.name
    this[Keys.toleranceAbsMm] = settings.tolerance.absoluteMm
    this[Keys.toleranceRelative] = settings.tolerance.relative
    this[Keys.grid] = settings.gridEnabled
    this[Keys.snap] = settings.snapEnabled
    this[Keys.thicknessMm] = settings.lastThickness.mm
    this[Keys.hints] = settings.dismissedHints.mapTo(mutableSetOf()) { it.name }
}
