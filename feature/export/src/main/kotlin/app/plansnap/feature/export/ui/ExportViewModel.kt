package app.plansnap.feature.export.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.plot.PlotSelection
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportPreflight
import app.plansnap.core.export.ExportPreflightReport
import app.plansnap.core.export.ExportRequest
import app.plansnap.core.export.ExportSummary
import app.plansnap.core.export.Exporter
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.RoomId
import app.plansnap.feature.export.data.ExportFileNames
import app.plansnap.feature.export.data.ExportFiles
import app.plansnap.feature.export.data.ExportTarget
import app.plansnap.feature.export.data.PlotPreview
import app.plansnap.feature.export.data.ShareRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

/** One-time instructions of the view model to the screen. */
sealed interface ExportEvent {
    /** Show the share sheet for the finished file. */
    data class Share(
        val request: ShareRequest,
    ) : ExportEvent

    /**
     * Open the system document picker and report the result with [ExportViewModel.onDocumentChosen].
     *
     * @property fileName suggested file name.
     * @property format format to be saved, which decides the MIME type of the picker.
     */
    data class ChooseDocument(
        val fileName: String,
        val format: ExportFormat,
    ) : ExportEvent
}

/**
 * Holds the export options, runs the preflight and writes the file for sharing or saving (REQ-EXP-002, REQ-EXP-005,
 * REQ-EXP-006).
 *
 * The project is read once and every room evaluated with the tolerance from the settings. Scale and paper default to
 * automatic and are only overridden on request, the orientation always stays automatic (ADR 0009, CR-W2-04). Only one
 * export runs at a time, a second start is ignored. The write happens on the injected dispatcher; a cancelled or failed
 * export removes its partial file. Cancelling the document picker is not an error.
 */
@Suppress("TooManyFunctions") // One function per user intent and step of the export.
@HiltViewModel
class ExportViewModel
    @Inject
    @Suppress("LongParameterList") // Constructor injection needs every dependency as its own parameter.
    constructor(
        private val savedState: SavedStateHandle,
        private val projects: ProjectRepository,
        private val settings: SettingsRepository,
        private val evaluator: RoomEvaluator,
        private val preflight: ExportPreflight,
        private val preview: PlotPreview,
        exporters: Set<@JvmSuppressWildcards Exporter>,
        private val files: ExportFiles,
        private val dispatchers: DispatcherProvider,
    ) : ViewModel() {
        private val projectId = ProjectId(requireNotNull(savedState.get<String>(PROJECT_ID_KEY)) { "projectId" })
        private val exporters: Map<ExportFormat, Exporter> = exporters.associateBy { it.format }
        private val mutableState = MutableStateFlow<ExportUiState>(ExportUiState.Loading)
        private val mutableEvents = Channel<ExportEvent>(Channel.BUFFERED)
        private var session: Session? = null
        private var previewJob: Job? = null
        private var lastAction: ExportAction? = null
        private var deferredDocument: Uri? = null

        /** State of the dialog. */
        val state: StateFlow<ExportUiState> = mutableState.asStateFlow()

        /** One-time instructions for the screen. */
        val events: Flow<ExportEvent> = mutableEvents.receiveAsFlow()

        init {
            check(ExportFormat.entries.all { it in this.exporters }) { "An exporter is missing" }
            load()
        }

        /** Reads and evaluates the project again after a failure. */
        fun onRetryLoad() {
            if (mutableState.value is ExportUiState.Failed) load()
        }

        /** Chooses the output [format]. */
        fun onFormatSelected(format: ExportFormat) {
            savedState[FORMAT_KEY] = format.name
            updateReady { it.copy(format = format, error = null, notice = null) }
            refreshFit()
        }

        /** Chooses the [scale], `null` for automatic. */
        fun onScaleSelected(scale: PlotScale?) {
            savedState[SCALE_KEY] = scale?.name
            updateReady { it.copy(scale = scale, error = null) }
            refreshFit()
        }

        /** Chooses the [paper], `null` for automatic. */
        fun onPaperSelected(paper: PaperSize?) {
            savedState[PAPER_KEY] = paper?.name
            updateReady { it.copy(paper = paper, error = null) }
            refreshFit()
        }

        /** Writes the file to the cache and hands it to the share sheet. */
        fun onShare() {
            val ready = mutableState.value as? ExportUiState.Ready ?: return
            val fileName = ExportFileNames.forProject(ready.projectName, ready.format)
            launchExport(ExportAction.SHARE) { files.createCacheTarget(fileName) }
        }

        /** Asks the screen to open the document picker. */
        fun onSave() {
            val ready = mutableState.value as? ExportUiState.Ready ?: return
            if (!ready.canExport) return
            savedState[AWAITING_KEY] = true
            updateReady { it.copy(awaitingDocument = true, error = null, notice = null) }
            mutableEvents.trySend(
                ExportEvent.ChooseDocument(ExportFileNames.forProject(ready.projectName, ready.format), ready.format),
            )
        }

        /**
         * Takes the result of the document picker. A `null` [uri] means the user cancelled, which is no error.
         *
         * After a process death the result can arrive before the project is loaded. It is then held back and served
         * with the restored options as soon as the load is done.
         */
        fun onDocumentChosen(uri: Uri?) {
            savedState[AWAITING_KEY] = false
            if (session == null && mutableState.value !is ExportUiState.Ready) {
                if (uri != null) deferredDocument = uri
                return
            }
            updateReady { it.copy(awaitingDocument = false) }
            if (uri != null) launchExport(ExportAction.SAVE) { files.openDocument(uri) }
        }

        /** Repeats the action that failed last. */
        fun onRetry() {
            when (lastAction) {
                ExportAction.SHARE -> onShare()
                ExportAction.SAVE -> onSave()
                null -> Unit
            }
        }

        /** Hides the error message. */
        fun onErrorDismissed() = updateReady { it.copy(error = null) }

        /** Hides the confirmation. */
        fun onNoticeDismissed() = updateReady { it.copy(notice = null) }

        private fun load() {
            mutableState.value = ExportUiState.Loading
            viewModelScope.launch(dispatchers.main) {
                mutableState.value = readSession()
                refreshFit()
                previewJob?.join()
                serveDeferredDocument()
            }
        }

        /** Exports into a document that arrived before the load was done, or rejects it with a visible error. */
        private suspend fun serveDeferredDocument() {
            val uri = deferredDocument ?: return
            deferredDocument = null
            val current = mutableState.value
            if (current is ExportUiState.Ready) {
                updateReady { it.copy(awaitingDocument = false) }
                val fit = (mutableState.value as ExportUiState.Ready).fit
                if (fit is PlanFit.Unavailable) {
                    updateReady { it.copy(error = fit.error) }
                    discardDocument(uri)
                } else {
                    launchExport(ExportAction.SAVE) { files.openDocument(uri) }
                }
            } else {
                // Failed shows its own error and Empty explains itself; the empty document must not stay behind.
                discardDocument(uri)
            }
        }

        private suspend fun discardDocument(uri: Uri) {
            withContext(dispatchers.io) {
                try {
                    files.openDocument(uri).let {
                        runCatching { it.stream.close() }
                        it.discard()
                    }
                } catch (_: IOException) {
                    // The document cannot be reached either; there is nothing left to remove.
                }
            }
        }

        @Suppress("ReturnCount") // Early exits for each failed load step keep the happy path flat.
        private suspend fun readSession(): ExportUiState {
            val project =
                when (val result = projects.getProject(projectId)) {
                    is DomainResult.Success -> result.value
                    is DomainResult.Failure -> return ExportUiState.Failed(result.error)
                }
            if (project.rooms.isEmpty()) return ExportUiState.Empty(project.name)
            val appSettings = settings.settings.first()
            val evaluations = LinkedHashMap<RoomId, RoomEvaluation>()
            for (room in project.rooms) {
                when (val result = evaluator.evaluate(room, appSettings.tolerance)) {
                    is DomainResult.Success -> evaluations[room.id] = result.value
                    is DomainResult.Failure -> return ExportUiState.Failed(result.error)
                }
            }
            val loaded = Session(project, evaluations.toImmutableMap())
            session = loaded
            val report = preflight.check(project, loaded.evaluations)
            val ready =
                ExportUiState.Ready(
                    projectName = project.name,
                    format = restored(FORMAT_KEY, ExportFormat.entries) ?: ExportFormat.PDF,
                    scale = restored(SCALE_KEY, PlotScale.entries),
                    paper = restored(PAPER_KEY, PaperSize.entries),
                    unit = appSettings.lengthUnit,
                    findings = findings(project, report),
                    fit = PlanFit.Checking,
                    awaitingDocument = savedState.get<Boolean>(AWAITING_KEY) == true,
                )
            return ready
        }

        private fun <T : Enum<T>> restored(
            key: String,
            entries: List<T>,
        ): T? = savedState.get<String>(key)?.let { name -> entries.firstOrNull { it.name == name } }

        @Suppress("ReturnCount") // Early exits for missing session and unchanged selection.
        private fun refreshFit() {
            val loaded = session ?: return
            previewJob?.cancel()
            val ready = mutableState.value as? ExportUiState.Ready ?: return
            if (!ready.isDrawingFormat) {
                updateReady { it.copy(fit = PlanFit.NotApplicable) }
                return
            }
            val selection = PlotSelection(ready.scale, ready.paper)
            previewJob =
                viewModelScope.launch(dispatchers.main) {
                    val fit =
                        when (val result = preview.plan(loaded.project, loaded.evaluations, selection)) {
                            is DomainResult.Success -> {
                                PlanFit.Fits(result.value.scale, result.value.paper, result.value.orientation)
                            }

                            is DomainResult.Failure -> {
                                PlanFit.Unavailable(result.error)
                            }
                        }
                    updateReady { it.copy(fit = fit) }
                }
        }

        private fun launchExport(
            action: ExportAction,
            open: () -> ExportTarget,
        ) {
            val loaded = session ?: return
            var snapshot: ExportUiState.Ready? = null
            mutableState.update { current ->
                snapshot = null
                if (current is ExportUiState.Ready && current.canExport) {
                    snapshot = current
                    current.copy(running = action, error = null, notice = null)
                } else {
                    current
                }
            }
            val ready = snapshot ?: return
            lastAction = action
            val request =
                ExportRequest(
                    project = loaded.project,
                    evaluations = loaded.evaluations,
                    format = ready.format,
                    lengthUnit = ready.unit,
                    locale = Locale.getDefault(),
                    selection = PlotSelection(ready.scale, ready.paper),
                )
            viewModelScope.launch(dispatchers.main) {
                val outcome = withContext(dispatchers.io) { write(request, open) }
                finish(action, request.format, ready.projectName, outcome)
            }
        }

        @Suppress("ReturnCount") // One exit per outcome of the write.
        private suspend fun write(
            request: ExportRequest,
            open: () -> ExportTarget,
        ): Written {
            val target =
                try {
                    open()
                } catch (e: IOException) {
                    return Written(DomainResult.Failure(ioError(e)), null)
                }
            var succeeded = false
            try {
                val result = target.stream.use { exporters.getValue(request.format).export(request, it) }
                succeeded = result is DomainResult.Success
                return Written(result, target.uri)
            } catch (e: IOException) {
                return Written(DomainResult.Failure(ioError(e)), null)
            } finally {
                if (!succeeded) target.discard()
            }
        }

        private fun finish(
            action: ExportAction,
            format: ExportFormat,
            projectName: String,
            written: Written,
        ) {
            val uri = written.uri
            val result = written.result
            when {
                result is DomainResult.Failure -> {
                    updateReady { it.copy(running = null, error = result.error) }
                }

                action == ExportAction.SHARE && uri != null -> {
                    updateReady { it.copy(running = null) }
                    val fileName = ExportFileNames.forProject(projectName, format)
                    mutableEvents.trySend(ExportEvent.Share(ShareRequest(uri, format, fileName)))
                }

                else -> {
                    updateReady { it.copy(running = null, notice = ExportNotice.Saved(format)) }
                }
            }
        }

        private fun updateReady(transform: (ExportUiState.Ready) -> ExportUiState.Ready) {
            mutableState.update { if (it is ExportUiState.Ready) transform(it) else it }
        }

        private fun ioError(e: IOException): DomainError {
            val text = e.message.orEmpty()
            val noSpace = text.contains("ENOSPC") || text.contains("No space", ignoreCase = true)
            return DomainError.Export(if (noSpace) ExportProblem.NO_SPACE else ExportProblem.WRITE_FAILED)
        }

        private fun findings(
            project: Project,
            report: ExportPreflightReport,
        ): PreflightFindings {
            val refs = project.rooms.mapIndexed { index, room -> room.id to RoomRef(index + 1, room.name) }.toMap()

            fun refsOf(ids: Collection<RoomId>) = project.rooms.filter { it.id in ids }.map { refs.getValue(it.id) }
            val openings =
                project.rooms.mapNotNull { room ->
                    val count = report.outOfBoundsOpenings.count { it.roomId == room.id }
                    if (count > 0) OpeningFinding(refs.getValue(room.id), count) else null
                }
            return PreflightFindings(
                omittedRooms = refsOf(report.omittedRooms).toImmutableList(),
                unconfirmedRooms = refsOf(report.unconfirmedRooms).toImmutableList(),
                outOfBoundsOpenings = openings.toImmutableList(),
                isDrawingEmpty = report.isDrawingEmpty,
            )
        }

        private class Session(
            val project: Project,
            val evaluations: ImmutableMap<RoomId, RoomEvaluation>,
        )

        private class Written(
            val result: DomainResult<ExportSummary>,
            val uri: Uri?,
        )

        /** Name of the navigation argument that carries the project id. */
        companion object {
            /** Key in the `SavedStateHandle`, the property name of `ExportRoute`. */
            const val PROJECT_ID_KEY: String = "projectId"

            private const val FORMAT_KEY = "export.format"
            private const val SCALE_KEY = "export.scale"
            private const val PAPER_KEY = "export.paper"
            private const val AWAITING_KEY = "export.awaitingDocument"
        }
    }
