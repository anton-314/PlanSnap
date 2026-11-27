package app.plansnap.feature.editor.config

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.opening.DefaultOpeningRules
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.EditorDraft
import app.plansnap.core.drawing.viewport.DefaultViewportTransform
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.Project
import app.plansnap.feature.editor.canvas.PlanViewMath
import app.plansnap.feature.editor.details.DetailsHandler
import app.plansnap.feature.editor.hints.HintsHandler
import app.plansnap.feature.editor.navigation.EditorNavigationActions
import app.plansnap.feature.editor.navigation.EditorRoute
import app.plansnap.feature.editor.opening.OpeningHandler
import app.plansnap.feature.editor.rooms.RoomsHandler
import app.plansnap.feature.editor.save.GatedSaver
import app.plansnap.feature.editor.shape.ShapeHandler
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorStore
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.feature.editor.state.PROJECT_ID
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController

/**
 * The complete editor in a real activity: the real view model with every handler over fake storage, obtained through
 * the view model store of the activity, so that its `SavedStateHandle` is the one of the platform.
 *
 * [resize] destroys and recreates the activity for another window, as a rotation or a fold does: the view model is
 * retained and the composition starts again from the saved instance state. [killProcess] also drops the view model and
 * restores only from the parcelled instance state and the storage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ConfigEditor(
    private val compose: ComposeTestRule,
    project: Project,
) {
    private val scope = TestScope()
    val rig = EditorRig(scope, project, AppSettings())

    /** The saver the view model uses; set its gate to hold saves. */
    val saver = GatedSaver(rig.saver)

    private var processes = 0
    private lateinit var controller: ActivityController<ComponentActivity>

    lateinit var viewModel: EditorViewModel
        private set

    val state: EditorUiState get() = viewModel.state.value

    private val activity: ComponentActivity get() = controller.get()

    /** Starts the first process in the window [device] and waits for the loaded editor. */
    fun launch(device: DeviceConfig = DeviceConfig.PHONE_PORTRAIT): ConfigEditor {
        RuntimeEnvironment.setQualifiers(device.qualifiers)
        registerHandlers()
        controller = newController().setup()
        show()
        assertWindow(device)
        return this
    }

    /** Changes the window to [device] by recreating the activity; the view model survives. */
    fun resize(device: DeviceConfig) {
        val oldActivity = activity
        val oldViewModel = viewModel
        RuntimeEnvironment.setQualifiers(device.qualifiers)
        controller.recreate()
        assertNotSame("The activity must be recreated", oldActivity, activity)
        show()
        assertSame("The view model must be retained", oldViewModel, viewModel)
        assertWindow(device)
    }

    /** Walks through [DeviceConfig.TOUR] and runs [check] in every window. */
    fun tour(check: (DeviceConfig) -> Unit) {
        DeviceConfig.TOUR.forEach { device ->
            resize(device)
            try {
                check(device)
            } catch (failure: AssertionError) {
                throw AssertionError("In $device: ${failure.message}", failure)
            }
        }
    }

    /**
     * Ends the process and starts a new one. Only the storage and, if [keepInstanceState], the instance state of the
     * activity survive; without it the system has discarded the task.
     */
    fun killProcess(keepInstanceState: Boolean = true) {
        val saved = Bundle()
        controller.saveInstanceState(saved).pause().stop()
        val parcelled = saved.throughParcel()
        controller.destroy()
        settle()
        val oldViewModel = viewModel
        registerHandlers()
        controller = newController()
        if (keepInstanceState) {
            controller
                .create(parcelled)
                .start()
                .restoreInstanceState(parcelled)
                .postCreate(parcelled)
                .resume()
                .visible()
        } else {
            controller.setup()
        }
        show()
        assertNotSame("A new process has a new view model", oldViewModel, viewModel)
    }

    /** Lets the view model and the evaluator run on the virtual clock, then lets Compose draw the result. */
    fun settle() {
        scope.advanceUntilIdle()
        compose.waitForIdle()
        scope.runCurrent()
        compose.waitForIdle()
    }

    /** The stored draft of the running input, `null` if there is none. */
    fun storedDraft(): EditorDraft? = runBlocking { (rig.drafts.getDraft(PROJECT_ID) as DomainResult.Success).value }

    /** A key of the editor itself, with the visible [label]. */
    fun key(label: String): SemanticsNodeInteraction = compose.onNode(hasText(label) and hasClickAction())

    /** The topmost key with the visible [label]: the one of the open sheet above the editor. */
    fun sheetKey(label: String): SemanticsNodeInteraction =
        compose.onAllNodes(hasText(label) and hasClickAction()).onLast()

    /** The topmost node with the content description [description]. */
    fun described(description: String): SemanticsNodeInteraction =
        compose.onAllNodes(hasContentDescription(description)).onLast()

    /** Scrolls to each of the keys of the editor and taps it. */
    fun type(vararg labels: String) {
        labels.forEach { key(it).performScrollTo().performClick() }
        settle()
    }

    /** Scrolls to each of the keys of the open sheet and taps it. */
    fun sheetType(vararg labels: String) {
        labels.forEach { sheetKey(it).performScrollTo().performClick() }
        settle()
    }

    /** Taps [node], scrolling it into view first if it lies in a scrollable container. */
    fun tap(node: SemanticsNodeInteraction) {
        val scrollable = hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())
        if (scrollable) node.performScrollTo()
        node.performClick()
        settle()
    }

    fun tapDescribed(description: String) = tap(described(description))

    fun tapSheet(label: String) = tap(sheetKey(label))

    fun openMenu() = tapDescribed("More actions")

    fun chooseFromMenu(label: String) {
        openMenu()
        tapSheet(label)
    }

    /** The plan canvas. */
    fun plan(): SemanticsNodeInteraction = compose.onNode(hasContentDescription("Floor plan", substring = true))

    /** The point of the plan, in its own pixels, at which the current view shows the world point [world]. */
    fun onPlan(world: Vec2): Offset {
        val size = plan().fetchSemanticsNode().size
        val viewport = PlanViewMath().viewport(checkNotNull(state.view), size.width, size.height)
        val screen = DefaultViewportTransform().worldToScreen(viewport, world)
        return Offset(screen.x.toFloat(), screen.y.toFloat())
    }

    private fun assertWindow(device: DeviceConfig) {
        val configuration = activity.resources.configuration
        assertEquals(device.widthDp, configuration.screenWidthDp)
        assertEquals(device.heightDp, configuration.screenHeightDp)
    }

    private fun newController(): ActivityController<ComponentActivity> {
        val intent =
            Intent(RuntimeEnvironment.getApplication(), ComponentActivity::class.java)
                .putExtra(EditorStore.KEY_PROJECT_ID, PROJECT_ID.value)
        return Robolectric.buildActivity(ComponentActivity::class.java, intent)
    }

    private fun registerHandlers() {
        // Ids of a later process must not repeat those of an earlier one.
        val ids = SequentialIdGenerator("process${processes++}")
        val applicationScope = ApplicationScope(scope.backgroundScope)
        rig.handlers.clear()
        rig.handlers += ShapeHandler(ids)
        rig.handlers += OpeningHandler(ids, DefaultOpeningRules())
        rig.handlers += RoomsHandler(ids, rig.settingsRepository, applicationScope)
        rig.handlers += DetailsHandler()
        rig.handlers += HintsHandler(rig.settingsRepository, applicationScope)
    }

    private fun show() {
        val factory = viewModelFactory { initializer { rig.viewModel(createSavedStateHandle(), saver) } }
        val shown = ViewModelProvider(activity, factory)[EditorViewModel::class.java]
        viewModel = shown
        activity.setContent {
            PlanSnapTheme {
                EditorRoute(
                    state = shown.state,
                    effects = shown.effects,
                    onIntent = shown::onIntent,
                    actions = EditorNavigationActions(onBack = {}, onOpenExport = {}),
                )
            }
        }
        settle()
    }

    private fun Bundle.throughParcel(): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(this)
            val bytes = parcel.marshall()
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            return checkNotNull(parcel.readBundle(ComponentActivity::class.java.classLoader))
        } finally {
            parcel.recycle()
        }
    }
}
