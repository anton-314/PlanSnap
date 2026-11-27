package app.plansnap.e2e

import android.os.Looper
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.EditorDraft
import app.plansnap.core.domain.repository.EditorDraftRepository
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.core.model.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import org.robolectric.Shadows.shadowOf

private const val TIMEOUT_MS = 20_000L

/**
 * Operates the running app through its semantics tree, the way a user does, and reads what the production
 * repositories have stored. Every wait is a wait for an observable condition; nothing waits for a fixed time.
 */
internal class E2eRobot(
    private val compose: ComposeTestRule,
    private val projects: ProjectRepository,
    private val drafts: EditorDraftRepository,
    private val settings: SettingsRepository,
) {
    /** Waits until [condition] holds while the main looper keeps running. */
    fun await(
        what: String,
        condition: () -> Boolean,
    ) {
        compose.waitUntil(what, TIMEOUT_MS) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
        compose.waitForIdle()
    }

    fun exists(matcher: SemanticsMatcher): Boolean = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    fun awaitNode(matcher: SemanticsMatcher) = await("node ${matcher.description}") { exists(matcher) }

    fun awaitText(text: String) = awaitNode(hasText(text))

    fun awaitGone(matcher: SemanticsMatcher) = await("no node ${matcher.description}") { !exists(matcher) }

    /** The single node matching [matcher], waited for. */
    fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        awaitNode(matcher)
        return compose.onNode(matcher)
    }

    /** Taps the single enabled, clickable node matching [matcher]. */
    fun tap(matcher: SemanticsMatcher) {
        val target = matcher and hasClickAction() and isEnabled()
        node(target).performClick()
        compose.waitForIdle()
    }

    /** Like [tap] for content of a scrollable sheet or form, which is scrolled into view first. */
    fun scrollAndTap(matcher: SemanticsMatcher) {
        val target = matcher and hasClickAction() and isEnabled()
        node(target).performScrollTo().performClick()
        compose.waitForIdle()
    }

    fun tapText(text: String) = tap(hasText(text))

    fun tapDescription(description: String) = tap(hasContentDescription(description))

    fun menu(entry: String) {
        tapDescription("More actions")
        tapText(entry)
    }

    /** Creates a project called [name] in the dialog of the project list. */
    fun createProject(name: String) {
        tapText("New project")
        val field = hasSetTextAction() and hasText("Project name")
        node(field).performTextReplacement(name)
        tapText("Create")
        awaitGone(field)
    }

    /** Replaces the content of the text field labelled [label]. */
    fun enter(
        label: String,
        value: String,
    ) {
        node(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)
        compose.waitForIdle()
    }

    /** Types [digits] on the keypad of the input bar. */
    fun type(digits: String) = digits.forEach { tapText(it.toString()) }

    /** Types a wall length, confirms it and waits until the wall is stored in the selected room [roomIndex]. */
    fun measureWall(
        centimeters: String,
        roomIndex: Int,
    ) {
        val before = stored().rooms[roomIndex].walls.size
        type(centimeters)
        tapDescription("Confirm wall")
        awaitStored("wall $centimeters in room $roomIndex") {
            it.rooms[roomIndex].walls.size == before + 1
        }
        awaitNode(hasContentDescription("Wall ${before + 2},", substring = true))
    }

    /** The only project as the production repository returns it now. */
    fun stored(): Project = awaitStored("a stored project") { true }

    /** Waits until the stored project satisfies [predicate] and returns it. */
    fun awaitStored(
        what: String,
        predicate: (Project) -> Boolean,
    ): Project = awaitValue(what, { loadProject() }) { it != null && predicate(it) }!!

    fun awaitDraft(
        what: String,
        predicate: (EditorDraft?) -> Boolean,
    ) {
        awaitValue(what, {
            val id = loadProject()?.id ?: return@awaitValue null
            (drafts.getDraft(id) as DomainResult.Success).value
        }, predicate)
    }

    fun awaitSettings(
        what: String,
        predicate: (AppSettings) -> Boolean,
    ) {
        awaitValue(what, { settings.settings.first() }, predicate)
    }

    private suspend fun loadProject(): Project? {
        val summaries = projects.observeProjects().first()
        check(summaries.size <= 1) { "The flows work with one project, found ${summaries.size}" }
        val id = summaries.singleOrNull()?.id ?: return null
        return when (val result = projects.getProject(id)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> error("Reading the project failed: ${result.error}")
        }
    }

    /** Reads with [read] on the main dispatcher, again and again, until the result satisfies [predicate]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> awaitValue(
        what: String,
        read: suspend () -> T,
        predicate: (T) -> Boolean,
    ): T {
        val scope = CoroutineScope(Dispatchers.Main)
        var pending: Deferred<T> = scope.async { read() }
        var last: Result<T>? = null
        try {
            await(what) {
                if (!pending.isCompleted) return@await false
                val value = pending.getCompleted()
                last = Result.success(value)
                predicate(value).also { matched -> if (!matched) pending = scope.async { read() } }
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for $what. Last value: ${last?.getOrNull()}", timeout)
        }
        return last!!.getOrThrow()
    }
}
