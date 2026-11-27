package app.plansnap.feature.export.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.evaluation.DefaultRoomEvaluator
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.drawing.plot.DefaultPlotLayout
import app.plansnap.core.drawing.plot.PageOrientation
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportPreflight
import app.plansnap.core.export.ExportPreflightReport
import app.plansnap.core.export.Exporter
import app.plansnap.core.export.RoomOpeningRef
import app.plansnap.core.export.preflight.DefaultExportPreflight
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.feature.export.data.DefaultPlotPreview
import app.plansnap.feature.export.data.ExportFileNames
import app.plansnap.feature.export.data.PlotPreview
import app.plansnap.feature.export.data.mimeType
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExportViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = SingleDispatchers(testDispatcher)
    private val files = FakeFiles()
    private val exporters = ExportFormat.entries.associateWith { FakeExporter(it) }
    private var previousLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(previousLocale)
    }

    @Suppress("LongParameterList") // Test factory with a default for every collaborator.
    private fun viewModel(
        project: DomainResult<Project>,
        settings: AppSettings = AppSettings(),
        preflight: ExportPreflight = DefaultExportPreflight(),
        evaluator: RoomEvaluator = DefaultRoomEvaluator(dispatchers),
        exporterSet: Set<Exporter> = exporters.values.toSet(),
        savedState: SavedStateHandle = SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to "p1")),
        preview: PlotPreview = DefaultPlotPreview(DefaultSceneBuilder(), DefaultPlotLayout(), dispatchers),
    ) = ExportViewModel(
        savedState = savedState,
        projects = FakeProjects(project),
        settings = FakeSettings(settings),
        evaluator = evaluator,
        preflight = preflight,
        preview = preview,
        exporters = exporterSet,
        files = files,
        dispatchers = dispatchers,
    )

    private fun viewModel(
        vararg rooms: Room,
        savedState: SavedStateHandle = SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to "p1")),
    ) = viewModel(DomainResult.Success(ExportRooms.project(*rooms)), savedState = savedState)

    private fun TestScope.ready(viewModel: ExportViewModel): ExportUiState.Ready {
        advanceUntilIdle()
        return viewModel.state.value as ExportUiState.Ready
    }

    private fun TestScope.collectEvents(viewModel: ExportViewModel): List<ExportEvent> {
        val events = mutableListOf<ExportEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        return events
    }

    private val ExportViewModel.current get() = state.value as ExportUiState.Ready

    @Test
    fun `given a confirmed room when loaded then defaults are automatic and the page is known`() =
        runTest(testDispatcher) {
            val viewModel = viewModel(ExportRooms.confirmed)
            assertEquals(ExportUiState.Loading, viewModel.state.value)
            val state = ready(viewModel)
            assertEquals(ExportFormat.PDF, state.format)
            assertNull(state.scale)
            assertNull(state.paper)
            assertEquals(LengthUnit.CENTIMETER, state.unit)
            assertEquals(PlanFit.Fits(PlotScale.ONE_TO_50, PaperSize.A4, PageOrientation.LANDSCAPE), state.fit)
            assertFalse(state.findings.hasFindings)
            assertTrue(state.canExport)
        }

    @Test
    fun `given rooms of every status when loaded then the findings name them in project order`() =
        runTest(testDispatcher) {
            val viewModel =
                viewModel(ExportRooms.confirmed, ExportRooms.unconfirmed, ExportRooms.conflict, ExportRooms.open)
            val findings = ready(viewModel).findings
            assertEquals(listOf(RoomRef(3, "Shed"), RoomRef(4, "")), findings.omittedRooms)
            assertEquals(listOf(RoomRef(2, "")), findings.unconfirmedRooms)
            assertTrue(findings.outOfBoundsOpenings.isEmpty())
            assertFalse(findings.isDrawingEmpty)
        }

    @Test
    fun `given a preflight with out of bounds openings when loaded then they are counted per room`() =
        runTest(testDispatcher) {
            val rooms = arrayOf(ExportRooms.confirmed, ExportRooms.unconfirmed)
            val preflight =
                ExportPreflight { _, _ ->
                    ExportPreflightReport(
                        exportableRooms = rooms.map { it.id }.toImmutableSet(),
                        omittedRooms = persistentSetOf(),
                        unconfirmedRooms = persistentSetOf(),
                        outOfBoundsOpenings =
                            persistentSetOf(
                                RoomOpeningRef(ExportRooms.unconfirmed.id, OpeningId("a")),
                                RoomOpeningRef(ExportRooms.unconfirmed.id, OpeningId("b")),
                            ),
                    )
                }
            val viewModel = viewModel(DomainResult.Success(ExportRooms.project(*rooms)), preflight = preflight)
            val findings = ready(viewModel).findings
            assertEquals(listOf(OpeningFinding(RoomRef(2, ""), 2)), findings.outOfBoundsOpenings)
        }

    @Test
    fun `given only an open room when loaded then drawing formats are blocked but JSON is not`() =
        runTest(testDispatcher) {
            val viewModel = viewModel(ExportRooms.open)
            val state = ready(viewModel)
            assertTrue(state.findings.isDrawingEmpty)
            assertEquals(PlanFit.Unavailable(DomainError.Export(ExportProblem.NOTHING_TO_EXPORT)), state.fit)
            assertFalse(state.canExport)
            viewModel.onFormatSelected(ExportFormat.JSON)
            val json = ready(viewModel)
            assertEquals(PlanFit.NotApplicable, json.fit)
            assertTrue(json.canExport)
        }

    @Test
    fun `given a project without rooms when loaded then the empty state is shown`() =
        runTest(testDispatcher) {
            val viewModel = viewModel(DomainResult.Success(ExportRooms.project()))
            advanceUntilIdle()
            assertEquals(ExportUiState.Empty("Flat <1>"), viewModel.state.value)
        }

    @Test
    fun `given only rooms without walls when loaded then the empty state is shown`() =
        runTest(testDispatcher) {
            val viewModel = viewModel(ExportRooms.bare, ExportRooms.bare.copy(id = RoomId("bare-2")))
            advanceUntilIdle()
            assertEquals(ExportUiState.Empty("Flat <1>"), viewModel.state.value)
        }

    @Test
    fun `given one room with walls beside a room without walls when loaded then the options are shown`() =
        runTest(testDispatcher) {
            val state = ready(viewModel(ExportRooms.bare, ExportRooms.confirmed))
            assertTrue(state.canExport)
        }

    @Test
    fun `given a missing project when loaded then it fails and retry reads again`() =
        runTest(testDispatcher) {
            val missing = DomainResult.Failure(notFound())
            val viewModel = viewModel(missing)
            advanceUntilIdle()
            assertEquals(ExportUiState.Failed(notFound()), viewModel.state.value)
            viewModel.onRetryLoad()
            advanceUntilIdle()
            assertEquals(ExportUiState.Failed(notFound()), viewModel.state.value)
        }

    @Test
    fun `given a failing evaluation when loaded then the error is shown`() =
        runTest(testDispatcher) {
            val error = DomainError.RuleViolation(DomainRule.TOO_MANY_WALLS)
            val failing =
                object : RoomEvaluator {
                    override suspend fun evaluate(
                        room: Room,
                        tolerance: ClosureTolerance,
                    ): DomainResult<RoomEvaluation> = DomainResult.Failure(error)
                }
            val viewModel =
                viewModel(DomainResult.Success(ExportRooms.project(ExportRooms.confirmed)), evaluator = failing)
            advanceUntilIdle()
            assertEquals(ExportUiState.Failed(error), viewModel.state.value)
        }

    @Test
    fun `given a plan too large for A3 when loaded then the page is unavailable`() =
        runTest(testDispatcher) {
            val state = ready(viewModel(ExportRooms.huge))
            assertEquals(PlanFit.Unavailable(DomainError.Export(ExportProblem.PLAN_TOO_LARGE)), state.fit)
            assertFalse(state.canExport)
        }

    @Test
    fun `given a wide plan when scale and paper are chosen then the fit follows and a mismatch is explained`() =
        runTest(testDispatcher) {
            val viewModel = viewModel(ExportRooms.wide)
            val automatic = ready(viewModel).fit as PlanFit.Fits
            assertEquals(PlotScale.ONE_TO_200, automatic.scale)

            viewModel.onScaleSelected(PlotScale.ONE_TO_50)
            val mismatch = ready(viewModel)
            assertEquals(
                PlanFit.Unavailable(DomainError.Export(ExportProblem.PLAN_DOES_NOT_FIT_SELECTION)),
                mismatch.fit,
            )
            assertFalse(mismatch.canExport)

            viewModel.onScaleSelected(null)
            viewModel.onPaperSelected(PaperSize.A3)
            val fixed = ready(viewModel)
            assertEquals(PaperSize.A3, (fixed.fit as PlanFit.Fits).paper)
            assertTrue(fixed.canExport)
        }

    @Test
    fun `given each format when shared then the file name, mime type and request match`() =
        runTest(testDispatcher) {
            val viewModel = viewModel(ExportRooms.confirmed)
            val events = collectEvents(viewModel)
            ready(viewModel)
            ExportFormat.entries.forEach { format ->
                viewModel.onFormatSelected(format)
                ready(viewModel)
                viewModel.onShare()
                advanceUntilIdle()
            }
            val shares = events.filterIsInstance<ExportEvent.Share>()
            assertEquals(ExportFormat.entries, shares.map { it.request.format })
            shares.forEach {
                assertEquals(ExportFileNames.forProject("Flat <1>", it.request.format), it.request.fileName)
                assertEquals("Flat_1.${it.request.format.name.lowercase()}", it.request.fileName)
                assertTrue(
                    it.request.uri
                        .toString()
                        .endsWith(it.request.fileName),
                )
            }
            assertEquals(
                listOf("application/pdf", "image/png", "image/svg+xml", "application/json"),
                shares.map { it.request.format.mimeType },
            )
            ExportFormat.entries.forEach { assertEquals(1, exporters.getValue(it).requests.size) }
            assertTrue(files.targets.all { it.closed && !it.discarded })
            assertEquals(
                "content-PDF",
                files.targets
                    .first()
                    .buffer
                    .toString(),
            )
            assertNull(viewModel.current.error)
            assertNull(viewModel.current.running)
        }

    @Test
    fun `given choices and settings when exported then the request carries unit, locale and selection`() =
        runTest(testDispatcher) {
            val viewModel =
                viewModel(
                    DomainResult.Success(ExportRooms.project(ExportRooms.confirmed)),
                    AppSettings(lengthUnit = LengthUnit.METER),
                )
            ready(viewModel)
            viewModel.onScaleSelected(PlotScale.ONE_TO_100)
            viewModel.onPaperSelected(PaperSize.A3)
            ready(viewModel)
            viewModel.onShare()
            advanceUntilIdle()
            val request = exporters.getValue(ExportFormat.PDF).requests.single()
            assertEquals(LengthUnit.METER, request.lengthUnit)
            assertEquals(Locale.GERMANY, request.locale)
            assertEquals(PlotScale.ONE_TO_100, request.selection.scale)
            assertEquals(PaperSize.A3, request.selection.paper)
            assertEquals(
                ExportRooms.confirmed.id,
                request.project.rooms
                    .single()
                    .id,
            )
        }

    @Test
    fun `given save when a document is chosen then the file is written there and confirmed`() =
        runTest(testDispatcher) {
            val viewModel = viewModel(ExportRooms.confirmed)
            val events = collectEvents(viewModel)
            ready(viewModel)
            viewModel.onFormatSelected(ExportFormat.SVG)
            ready(viewModel)
            viewModel.onSave()
            advanceUntilIdle()
            assertEquals(listOf(ExportEvent.ChooseDocument("Flat_1.svg", ExportFormat.SVG)), events)
            assertTrue(viewModel.current.awaitingDocument)
            assertFalse(viewModel.current.canExport)

            val document = Uri.parse("content://docs/tree/Flat_1.svg")
            viewModel.onDocumentChosen(document)
            advanceUntilIdle()
            val state = viewModel.current
            assertFalse(state.awaitingDocument)
            assertNull(state.running)
            assertEquals(ExportNotice.Saved(ExportFormat.SVG), state.notice)
            assertEquals(document, files.targets.single().uri)
            assertEquals(
                "content-SVG",
                files.targets
                    .single()
                    .buffer
                    .toString(),
            )
            assertTrue(files.targets.single().closed)
            assertEquals(1, events.size)
        }

    @Test
    fun `given save when the picker is cancelled then nothing is written and no error is shown`() =
        runTest(testDispatcher) {
            val viewModel = viewModel(ExportRooms.confirmed)
            ready(viewModel)
            viewModel.onSave()
            viewModel.onDocumentChosen(null)
            advanceUntilIdle()
            val state = viewModel.current
            assertFalse(state.awaitingDocument)
            assertNull(state.error)
            assertNull(state.notice)
            assertTrue(state.canExport)
            assertTrue(files.targets.isEmpty())
            assertTrue(exporters.values.all { it.requests.isEmpty() })
        }

    @Test
    fun `given a running export when started again then the second start is ignored`() =
        runTest(testDispatcher) {
            val gate = CompletableDeferred<Unit>()
            exporters.getValue(ExportFormat.PDF).gate = gate
            val viewModel = viewModel(ExportRooms.confirmed)
            val events = collectEvents(viewModel)
            ready(viewModel)
            viewModel.onShare()
            viewModel.onShare()
            viewModel.onSave()
            advanceUntilIdle()
            assertEquals(ExportAction.SHARE, viewModel.current.running)
            assertTrue(viewModel.current.isBusy)
            assertFalse(viewModel.current.canExport)
            assertEquals(1, exporters.getValue(ExportFormat.PDF).requests.size)
            assertFalse(events.any { it is ExportEvent.ChooseDocument })

            gate.complete(Unit)
            advanceUntilIdle()
            assertNull(viewModel.current.running)
            assertEquals(1, events.filterIsInstance<ExportEvent.Share>().size)
            assertEquals(1, files.targets.size)
        }

    @Test
    fun `given a failing exporter when shared then the error is kept, the partial file removed and retry works`() =
        runTest(testDispatcher) {
            val exporter = exporters.getValue(ExportFormat.PDF)
            exporter.failure = DomainError.Export(ExportProblem.ENCODING_FAILED)
            val viewModel = viewModel(ExportRooms.confirmed)
            val events = collectEvents(viewModel)
            ready(viewModel)
            viewModel.onShare()
            advanceUntilIdle()
            assertEquals(DomainError.Export(ExportProblem.ENCODING_FAILED), viewModel.current.error)
            assertNull(viewModel.current.running)
            assertTrue(files.targets.single().discarded)
            assertTrue(events.isEmpty())

            exporter.failure = null
            viewModel.onRetry()
            advanceUntilIdle()
            assertNull(viewModel.current.error)
            assertEquals(1, events.filterIsInstance<ExportEvent.Share>().size)
            assertFalse(files.targets.last().discarded)
        }

    @Test
    fun `given a failed share when the error is dismissed then it disappears`() =
        runTest(testDispatcher) {
            exporters.getValue(ExportFormat.PDF).failure = DomainError.Export(ExportProblem.NO_SPACE)
            val viewModel = viewModel(ExportRooms.confirmed)
            ready(viewModel)
            viewModel.onShare()
            advanceUntilIdle()
            assertEquals(DomainError.Export(ExportProblem.NO_SPACE), viewModel.current.error)
            viewModel.onErrorDismissed()
            assertNull(viewModel.current.error)
        }

    @Test
    fun `given storage errors when the target cannot be opened or written then they map to export problems`() =
        runTest(testDispatcher) {
            val viewModel = viewModel(ExportRooms.confirmed)
            ready(viewModel)

            files.openFailure = IOException("open: ENOSPC (No space left on device)")
            viewModel.onShare()
            advanceUntilIdle()
            assertEquals(DomainError.Export(ExportProblem.NO_SPACE), viewModel.current.error)

            files.openFailure = IOException("denied")
            viewModel.onShare()
            advanceUntilIdle()
            assertEquals(DomainError.Export(ExportProblem.WRITE_FAILED), viewModel.current.error)

            files.openFailure = null
            exporters.getValue(ExportFormat.PDF).throwOnWrite = IOException("broken pipe")
            viewModel.onShare()
            advanceUntilIdle()
            assertEquals(DomainError.Export(ExportProblem.WRITE_FAILED), viewModel.current.error)
            assertTrue(files.targets.single().discarded)
            assertTrue(files.targets.single().closed)
        }

    @Test
    fun `given a running export when the view model is cleared then the partial file is removed`() =
        runTest(testDispatcher) {
            exporters.getValue(ExportFormat.PDF).gate = CompletableDeferred()
            val viewModel = viewModel(ExportRooms.confirmed)
            val events = collectEvents(viewModel)
            ready(viewModel)
            viewModel.onShare()
            advanceUntilIdle()
            viewModel.viewModelScope.cancel()
            advanceUntilIdle()
            assertTrue(files.targets.single().discarded)
            assertTrue(files.targets.single().closed)
            assertTrue(events.isEmpty())
        }

    @Test
    fun `given an export that finished when a format is chosen then the old notice is gone`() =
        runTest(testDispatcher) {
            val viewModel = viewModel(ExportRooms.confirmed)
            ready(viewModel)
            viewModel.onSave()
            viewModel.onDocumentChosen(Uri.parse("content://docs/a.pdf"))
            advanceUntilIdle()
            assertEquals(ExportNotice.Saved(ExportFormat.PDF), viewModel.current.notice)
            viewModel.onFormatSelected(ExportFormat.PNG)
            assertNull(viewModel.current.notice)
        }

    @Test
    fun `given the retry without a previous export when called then nothing happens`() =
        runTest(testDispatcher) {
            val viewModel = viewModel(ExportRooms.confirmed)
            ready(viewModel)
            viewModel.onRetry()
            advanceUntilIdle()
            assertTrue(files.targets.isEmpty())
        }

    @Test
    fun `given storage failure when loading then retry succeeds after the repository recovers`() =
        runTest(testDispatcher) {
            val repository = FakeProjects(DomainResult.Failure(DomainError.Storage(StorageProblem.READ_FAILED)))
            val viewModel =
                ExportViewModel(
                    SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to "p1")),
                    repository,
                    FakeSettings(),
                    DefaultRoomEvaluator(dispatchers),
                    DefaultExportPreflight(),
                    DefaultPlotPreview(DefaultSceneBuilder(), DefaultPlotLayout(), dispatchers),
                    exporters.values.toSet(),
                    files,
                    dispatchers,
                )
            advanceUntilIdle()
            assertTrue(viewModel.state.value is ExportUiState.Failed)
            repository.result = DomainResult.Success(ExportRooms.project(ExportRooms.confirmed))
            viewModel.onRetryLoad()
            advanceUntilIdle()
            assertTrue(viewModel.state.value is ExportUiState.Ready)
        }

    private fun SavedStateHandle.recreated() = SavedStateHandle(keys().associateWith { get<Any>(it) })

    @Test
    fun `given a process death when the uri arrives before loading then it is exported with restored options`() =
        runTest(testDispatcher) {
            val savedState = SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to "p1"))
            val before = viewModel(ExportRooms.confirmed, savedState = savedState)
            ready(before)
            before.onFormatSelected(ExportFormat.SVG)
            before.onScaleSelected(PlotScale.ONE_TO_100)
            before.onPaperSelected(PaperSize.A3)
            advanceUntilIdle()
            before.onSave()
            advanceUntilIdle()

            val restored =
                viewModel(
                    DomainResult.Success(ExportRooms.project(ExportRooms.confirmed)),
                    savedState = savedState.recreated(),
                )
            val document = Uri.parse("content://docs/tree/Flat_1.svg")
            restored.onDocumentChosen(document)
            assertTrue(files.targets.isEmpty())
            advanceUntilIdle()

            val state = restored.current
            assertEquals(ExportFormat.SVG, state.format)
            assertEquals(PlotScale.ONE_TO_100, state.scale)
            assertEquals(PaperSize.A3, state.paper)
            assertFalse(state.awaitingDocument)
            assertEquals(ExportNotice.Saved(ExportFormat.SVG), state.notice)
            assertEquals(document, files.targets.single().uri)
            val request = exporters.getValue(ExportFormat.SVG).requests.single()
            assertEquals(PlotScale.ONE_TO_100, request.selection.scale)
            assertEquals(PaperSize.A3, request.selection.paper)
        }

    @Test
    fun `given a restored picker without a result when loaded then the dialog still waits for it`() =
        runTest(testDispatcher) {
            val savedState = SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to "p1"))
            val before = viewModel(ExportRooms.confirmed, savedState = savedState)
            ready(before)
            before.onSave()
            val restored =
                viewModel(
                    DomainResult.Success(ExportRooms.project(ExportRooms.confirmed)),
                    savedState = savedState.recreated(),
                )
            assertTrue(ready(restored).awaitingDocument)
            restored.onDocumentChosen(null)
            assertFalse(restored.current.awaitingDocument)
            assertTrue(files.targets.isEmpty())
        }

    @Test
    fun `given an early uri when the load fails then the error is shown and the empty document is discarded`() =
        runTest(testDispatcher) {
            val savedState =
                SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to "p1", "export.awaitingDocument" to true))
            val error = DomainError.Storage(StorageProblem.READ_FAILED)
            val restored = viewModel(DomainResult.Failure(error), savedState = savedState)
            val document = Uri.parse("content://docs/tree/Flat_1.pdf")
            restored.onDocumentChosen(document)
            advanceUntilIdle()

            assertEquals(ExportUiState.Failed(error), restored.state.value)
            assertEquals(document, files.targets.single().uri)
            assertTrue(files.targets.single().discarded)
            assertTrue(exporters.values.all { it.requests.isEmpty() })
        }

    @Test
    fun `given an early uri when the restored options do not fit then the error is shown and it is discarded`() =
        runTest(testDispatcher) {
            val savedState =
                SavedStateHandle(
                    mapOf(
                        ExportViewModel.PROJECT_ID_KEY to "p1",
                        "export.format" to "PDF",
                        "export.scale" to PlotScale.ONE_TO_50.name,
                        "export.paper" to PaperSize.A4.name,
                        "export.awaitingDocument" to true,
                    ),
                )
            val restored = viewModel(ExportRooms.wide, savedState = savedState)
            restored.onDocumentChosen(Uri.parse("content://docs/tree/Flat_1.pdf"))
            advanceUntilIdle()

            val state = restored.current
            assertTrue(state.error is DomainError.Export)
            assertTrue(files.targets.single().discarded)
            assertTrue(exporters.values.all { it.requests.isEmpty() })
        }

    @Test
    fun `given a uri that arrives after an empty project was loaded then the empty document is discarded`() =
        runTest(testDispatcher) {
            val savedState =
                SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to "p1", "export.awaitingDocument" to true))
            val restored = viewModel(ExportRooms.bare, savedState = savedState)
            advanceUntilIdle()
            restored.onDocumentChosen(Uri.parse("content://docs/tree/Flat_1.pdf"))
            advanceUntilIdle()

            assertTrue(files.targets.single().discarded)
            assertTrue(exporters.values.all { it.requests.isEmpty() })
            assertEquals(ExportUiState.Empty("Flat <1>", documentRejected = true), restored.state.value)
        }

    @Test
    fun `given an early uri when the restored project has no wall then the rejection is shown and it is discarded`() =
        runTest(testDispatcher) {
            val savedState =
                SavedStateHandle(
                    mapOf(
                        ExportViewModel.PROJECT_ID_KEY to "p1",
                        "export.format" to ExportFormat.JSON.name,
                        "export.awaitingDocument" to true,
                    ),
                )
            val restored = viewModel(ExportRooms.bare, savedState = savedState)
            restored.onDocumentChosen(Uri.parse("content://docs/tree/Flat_1.json"))
            advanceUntilIdle()

            assertEquals(ExportUiState.Empty("Flat <1>", documentRejected = true), restored.state.value)
            assertTrue(files.targets.single().discarded)
            assertTrue(exporters.values.all { it.requests.isEmpty() })
        }

    @Test
    fun `given a uri that arrives after the load failed then the empty document is discarded`() =
        runTest(testDispatcher) {
            val savedState =
                SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to "p1", "export.awaitingDocument" to true))
            val restored = viewModel(DomainResult.Failure(notFound()), savedState = savedState)
            advanceUntilIdle()
            restored.onDocumentChosen(Uri.parse("content://docs/tree/Flat_1.pdf"))
            advanceUntilIdle()

            assertEquals(ExportUiState.Failed(notFound()), restored.state.value)
            assertTrue(files.targets.single().discarded)
        }

    @Test
    fun `given a uri that arrives while the page is being checked then it is exported afterwards`() =
        runTest(testDispatcher) {
            val gate = CompletableDeferred<Unit>()
            val real = DefaultPlotPreview(DefaultSceneBuilder(), DefaultPlotLayout(), dispatchers)
            val gated =
                PlotPreview { project, evaluations, selection ->
                    gate.await()
                    real.plan(project, evaluations, selection)
                }
            val savedState =
                SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to "p1", "export.awaitingDocument" to true))
            val restored =
                viewModel(
                    DomainResult.Success(ExportRooms.project(ExportRooms.confirmed)),
                    savedState = savedState,
                    preview = gated,
                )
            advanceUntilIdle()
            assertEquals(PlanFit.Checking, restored.current.fit)
            val document = Uri.parse("content://docs/tree/Flat_1.pdf")
            restored.onDocumentChosen(document)
            advanceUntilIdle()
            assertTrue(files.targets.isEmpty())
            assertTrue(restored.current.isBusy)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(ExportNotice.Saved(ExportFormat.PDF), restored.current.notice)
            assertEquals(document, files.targets.single().uri)
        }
}
