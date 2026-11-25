package app.plansnap.feature.settings.ui

import app.cash.turbine.test
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.core.geometry.primitives.ClosureTolerance
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSettingsRepository()
    private var previousLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(previousLocale)
    }

    private fun viewModel() = SettingsViewModel(repository, TestDispatchers(dispatcher))

    private suspend fun app.cash.turbine.ReceiveTurbine<SettingsUiState>.loaded(): SettingsUiState.Loaded {
        var item = awaitItem()
        while (item !is SettingsUiState.Loaded) item = awaitItem()
        return item
    }

    private fun TestScope.settle() = advanceUntilIdle()

    @Test
    fun `given stored settings when observed then state mirrors them`() =
        runTest(dispatcher) {
            repository.update { AppSettings(lengthUnit = LengthUnit.METER, gridEnabled = false, snapEnabled = false) }
            viewModel().state.test {
                assertEquals(SettingsUiState.Loading, awaitItem())
                val state = loaded()
                assertEquals(LengthUnit.METER, state.unit)
                assertEquals("0,050", state.absoluteToleranceText)
                assertEquals("1", state.relativeToleranceText)
                assertEquals(false, state.gridEnabled)
                assertEquals(false, state.snapEnabled)
            }
        }

    @Test
    fun `given a unit choice when selected then it is stored at once and the tolerance text follows`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.state.test {
                assertEquals(SettingsUiState.Loading, awaitItem())
                assertEquals("5,0", loaded().absoluteToleranceText)
                vm.onUnitSelected(LengthUnit.MILLIMETER)
                settle()
                val state = loaded()
                assertEquals(LengthUnit.MILLIMETER, state.unit)
                assertEquals("50", state.absoluteToleranceText)
            }
            assertEquals(LengthUnit.MILLIMETER, repository.value.lengthUnit)
        }

    @Test
    fun `given grid and snap switches when toggled then each is stored`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onGridChanged(false)
            vm.onSnapChanged(false)
            settle()
            assertEquals(false, repository.value.gridEnabled)
            assertEquals(false, repository.value.snapEnabled)
            vm.onGridChanged(true)
            settle()
            assertEquals(true, repository.value.gridEnabled)
            assertEquals(false, repository.value.snapEnabled)
        }

    @Test
    fun `given dismissed hints when reset then none stays dismissed`() =
        runTest(dispatcher) {
            repository.update { it.copy(dismissedHints = persistentSetOf(EditorHint.NUMPAD, EditorHint.CLOSE)) }
            val vm = viewModel()
            vm.state.test {
                assertEquals(SettingsUiState.Loading, awaitItem())
                assertEquals(2, loaded().dismissedHintCount)
                vm.onResetHints()
                settle()
                assertEquals(0, loaded().dismissedHintCount)
            }
            assertTrue(repository.value.dismissedHints.isEmpty())
        }

    @Test
    fun `given a valid absolute tolerance in centimeters when entered then it is stored in millimeters`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAbsoluteToleranceChanged("7,5")
            settle()
            assertEquals(75L, repository.value.tolerance.absoluteMm)
            assertEquals(0.01, repository.value.tolerance.relative, 0.0)
        }

    @Test
    fun `given the unit meters when an absolute tolerance with dot is entered then it is converted`() =
        runTest(dispatcher) {
            repository.update { it.copy(lengthUnit = LengthUnit.METER) }
            val vm = viewModel()
            vm.onAbsoluteToleranceChanged("0.1")
            settle()
            assertEquals(100L, repository.value.tolerance.absoluteMm)
        }

    @Test
    fun `given invalid absolute tolerances when entered then nothing is stored and the reason is shown`() =
        runTest(dispatcher) {
            val cases =
                mapOf(
                    "abc" to InvalidValueReason.MALFORMED,
                    "" to InvalidValueReason.MALFORMED,
                    "0" to InvalidValueReason.TOO_SMALL,
                    "-3" to InvalidValueReason.TOO_SMALL,
                    "0,05" to InvalidValueReason.NOT_REPRESENTABLE,
                    "99999999999999999999" to InvalidValueReason.TOO_LARGE,
                )
            val vm = viewModel()
            vm.state.test {
                assertEquals(SettingsUiState.Loading, awaitItem())
                loaded()
                cases.forEach { (text, reason) ->
                    vm.onAbsoluteToleranceChanged(text)
                    settle()
                    val state = loaded()
                    assertEquals("for '$text'", reason, state.absoluteToleranceError)
                    assertEquals(text, state.absoluteToleranceText)
                }
            }
            assertEquals(ClosureTolerance(), repository.value.tolerance)
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `given an invalid absolute tolerance when corrected then the error clears and the value is stored`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.state.test {
                assertEquals(SettingsUiState.Loading, awaitItem())
                loaded()
                vm.onAbsoluteToleranceChanged("0")
                settle()
                assertEquals(InvalidValueReason.TOO_SMALL, loaded().absoluteToleranceError)
                vm.onAbsoluteToleranceChanged("2")
                settle()
                assertNull(loaded().absoluteToleranceError)
            }
            assertEquals(20L, repository.value.tolerance.absoluteMm)
        }

    @Test
    fun `given a valid relative tolerance when entered then it is stored as a fraction`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onRelativeToleranceChanged("2,5")
            settle()
            assertEquals(0.025, repository.value.tolerance.relative, 1e-12)
            vm.onRelativeToleranceChanged("0")
            settle()
            assertEquals(0.0, repository.value.tolerance.relative, 0.0)
            vm.onRelativeToleranceChanged("100")
            settle()
            assertEquals(1.0, repository.value.tolerance.relative, 0.0)
        }

    @Test
    fun `given invalid relative tolerances when entered then nothing is stored and the reason is shown`() =
        runTest(dispatcher) {
            val cases =
                mapOf(
                    "x" to InvalidValueReason.MALFORMED,
                    "-1" to InvalidValueReason.TOO_SMALL,
                    "100,1" to InvalidValueReason.TOO_LARGE,
                )
            val vm = viewModel()
            vm.state.test {
                assertEquals(SettingsUiState.Loading, awaitItem())
                loaded()
                cases.forEach { (text, reason) ->
                    vm.onRelativeToleranceChanged(text)
                    settle()
                    assertEquals("for '$text'", reason, loaded().relativeToleranceError)
                }
            }
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `given a failing repository when a switch is toggled then the error is exposed and can be dismissed`() =
        runTest(dispatcher) {
            val error = DomainError.Storage(StorageProblem.WRITE_FAILED)
            repository.failure = error
            val vm = viewModel()
            vm.state.test {
                assertEquals(SettingsUiState.Loading, awaitItem())
                loaded()
                vm.onGridChanged(false)
                settle()
                val failed = loaded()
                assertEquals(error, failed.saveError)
                assertEquals(true, failed.gridEnabled)
                vm.onSaveErrorDismissed()
                settle()
                assertNull(loaded().saveError)
            }
        }

    @Test
    fun `given a past failure when a later save succeeds then the error disappears`() =
        runTest(dispatcher) {
            repository.failure = DomainError.Storage(StorageProblem.NO_SPACE)
            val vm = viewModel()
            vm.state.test {
                assertEquals(SettingsUiState.Loading, awaitItem())
                loaded()
                vm.onSnapChanged(false)
                settle()
                assertEquals(DomainError.Storage(StorageProblem.NO_SPACE), loaded().saveError)
                repository.failure = null
                vm.onSnapChanged(false)
                settle()
                assertNull(loaded().saveError)
            }
        }
}
