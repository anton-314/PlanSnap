package app.plansnap.feature.editor.hints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.feature.editor.input.editorState
import app.plansnap.feature.editor.shape.RecordingHost
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorEffect
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.feature.editor.state.ReloadProject
import app.plansnap.feature.editor.state.emptyProject
import app.plansnap.testing.fixtures.fakes.FakeSettingsRepository
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

/** Settings storage whose writes wait for [gate] and are counted. */
private class GatedSettings(
    private val delegate: FakeSettingsRepository,
) : SettingsRepository by delegate {
    var gate: CompletableDeferred<Unit>? = null
    var writes = 0

    override suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit> {
        writes++
        gate?.await()
        return delegate.update(transform)
    }
}

/** [HintsHandler] inside the real view model, with the settings repository that the settings screen writes too. */
@OptIn(ExperimentalCoroutinesApi::class)
class HintsViewModelTest {
    private class Fixture(
        scope: TestScope,
    ) {
        val rig = EditorRig(scope, emptyProject())
        val settings = GatedSettings(rig.settingsRepository)

        init {
            rig.handlers += HintsHandler(settings, ApplicationScope(scope.backgroundScope))
        }

        val dismissed: Set<EditorHint> get() = rig.settingsRepository.current.dismissedHints

        /** What the settings screen does for "reset hints" (`SettingsViewModel.onResetHints`). */
        suspend fun resetHints() {
            rig.settingsRepository.update { it.copy(dismissedHints = persistentSetOf()) }
        }
    }

    private fun EditorViewModel.hint(): EditorHint? = activeHint(state.value)

    private fun EditorViewModel.closing(): Set<EditorHint> =
        state.value
            .section(HintsState::class)
            ?.closing
            .orEmpty()

    private fun EditorViewModel.dismiss(hint: EditorHint) = onIntent(HintsIntent.Dismiss(hint))

    private fun ViewModelStore.adopt(viewModel: EditorViewModel): EditorViewModel {
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") // The factory only ever serves the one EditorViewModel of this test.
                override fun <T : ViewModel> create(
                    modelClass: KClass<T>,
                    extras: CreationExtras,
                ): T = viewModel as T
            }
        return ViewModelProvider.create(this, factory)[EditorViewModel::class]
    }

    @Test
    fun `given a first start when the hint is closed then it is stored and gone`() =
        runTest {
            val fixture = Fixture(this)
            val viewModel = fixture.rig.viewModel()
            runCurrent()
            assertEquals(EditorHint.NUMPAD, viewModel.hint())

            viewModel.dismiss(EditorHint.NUMPAD)
            runCurrent()

            assertNull(viewModel.hint())
            assertEquals(setOf(EditorHint.NUMPAD), fixture.dismissed)
            assertTrue(viewModel.closing().isEmpty())
        }

    @Test
    fun `given a slow storage when the hint is closed then it is hidden at once`() =
        runTest {
            val fixture = Fixture(this)
            val gate = CompletableDeferred<Unit>()
            fixture.settings.gate = gate
            val viewModel = fixture.rig.viewModel()
            runCurrent()

            viewModel.dismiss(EditorHint.NUMPAD)

            assertNull(viewModel.hint())
            runCurrent()
            assertNull(viewModel.hint())
            assertTrue(fixture.dismissed.isEmpty())
            gate.complete(Unit)
            runCurrent()
            assertNull(viewModel.hint())
            assertEquals(setOf(EditorHint.NUMPAD), fixture.dismissed)
        }

    @Test
    fun `given a hint closed twice when the first write runs then it is written once`() =
        runTest {
            val fixture = Fixture(this)
            fixture.settings.gate = CompletableDeferred()
            val viewModel = fixture.rig.viewModel()
            runCurrent()

            viewModel.dismiss(EditorHint.NUMPAD)
            viewModel.dismiss(EditorHint.NUMPAD)
            runCurrent()
            fixture.settings.gate?.complete(Unit)
            runCurrent()
            viewModel.dismiss(EditorHint.NUMPAD)
            runCurrent()

            assertEquals(1, fixture.settings.writes)
        }

    @Test
    fun `given two hints closed in a row when stored then both are dismissed`() =
        runTest {
            val fixture = Fixture(this)
            val viewModel = fixture.rig.viewModel()
            runCurrent()

            viewModel.dismiss(EditorHint.NUMPAD)
            viewModel.dismiss(EditorHint.DIRECTION)
            runCurrent()

            assertEquals(setOf(EditorHint.NUMPAD, EditorHint.DIRECTION), fixture.dismissed)
        }

    @Test
    fun `given a closed hint when the editor is opened again then it stays gone`() =
        runTest {
            val fixture = Fixture(this)
            val first = fixture.rig.viewModel()
            runCurrent()
            first.dismiss(EditorHint.NUMPAD)
            runCurrent()

            val second = fixture.rig.viewModel()
            runCurrent()

            assertNull(second.hint())
        }

    @Test
    fun `given the editor is left right after closing then the dismissal is still stored`() =
        runTest {
            val fixture = Fixture(this)
            val store = ViewModelStore()
            val viewModel = store.adopt(fixture.rig.viewModel())
            runCurrent()

            viewModel.dismiss(EditorHint.NUMPAD)
            store.clear()
            assertTrue(fixture.dismissed.isEmpty())
            runCurrent()

            assertEquals(setOf(EditorHint.NUMPAD), fixture.dismissed)
        }

    @Test
    fun `given closed hints when reset in the settings then a new editor shows them again`() =
        runTest {
            val fixture = Fixture(this)
            val first = fixture.rig.viewModel()
            runCurrent()
            first.dismiss(EditorHint.NUMPAD)
            runCurrent()
            fixture.resetHints()

            val second = fixture.rig.viewModel()
            runCurrent()

            assertEquals(EditorHint.NUMPAD, second.hint())
        }

    @Test
    fun `given an open editor when the hints are reset then the hint appears again and can be closed again`() =
        runTest {
            val fixture = Fixture(this)
            val viewModel = fixture.rig.viewModel()
            runCurrent()
            viewModel.dismiss(EditorHint.NUMPAD)
            runCurrent()

            fixture.resetHints()
            runCurrent()

            assertEquals(EditorHint.NUMPAD, viewModel.hint())
            viewModel.dismiss(EditorHint.NUMPAD)
            runCurrent()
            assertNull(viewModel.hint())
            assertEquals(setOf(EditorHint.NUMPAD), fixture.dismissed)
        }

    @Test
    fun `given a failing storage when the hint is closed then the failure is reported and the hint stays hidden`() =
        runTest {
            val fixture = Fixture(this)
            val failure = DomainError.Storage(StorageProblem.WRITE_FAILED)
            fixture.rig.settingsRepository.failures
                .failNext(FakeSettingsRepository.Operation.UPDATE, failure)
            val viewModel = fixture.rig.viewModel()
            runCurrent()
            val effects = mutableListOf<EditorEffect>()
            backgroundScope.launch { effects += viewModel.effects.first() }

            viewModel.dismiss(EditorHint.NUMPAD)
            runCurrent()

            assertEquals(listOf<EditorEffect>(EditorEffect.Error(failure)), effects)
            assertNull(viewModel.hint())
            assertTrue(fixture.dismissed.isEmpty())
            val reopened = fixture.rig.viewModel()
            runCurrent()
            assertEquals(EditorHint.NUMPAD, reopened.hint())
        }

    @Test
    fun `given walls added one by one then each step shows only its own hint`() =
        runTest {
            val fixture = Fixture(this)
            val viewModel = fixture.rig.viewModel()
            runCurrent()
            val shown = mutableListOf(viewModel.hint())

            repeat(3) {
                viewModel.onIntent(CoreIntent.InputChanged("400"))
                viewModel.onIntent(CoreIntent.ConfirmWall)
                runCurrent()
                shown += viewModel.hint()
            }

            assertEquals(
                listOf(EditorHint.NUMPAD, EditorHint.DIRECTION, EditorHint.DIRECTION, EditorHint.CLOSE),
                shown,
            )
        }

    @Test
    fun `given another intent when offered to the handler then it is not consumed`() =
        runTest {
            val settings = FakeSettingsRepository()
            val handler = HintsHandler(settings, ApplicationScope(backgroundScope))
            val host = RecordingHost(editorState())

            assertFalse(handler.handle(ReloadProject, host))
            runCurrent()
            assertTrue(settings.current.dismissedHints.isEmpty())
        }
}
