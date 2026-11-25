package app.plansnap.feature.export.ui

import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.export.ExportFormat

/**
 * Reactions of the export screen to user input.
 *
 * @property onFormatSelected a format was chosen.
 * @property onScaleSelected a scale was chosen, `null` for automatic.
 * @property onPaperSelected a paper was chosen, `null` for automatic.
 * @property onShare the user wants to share the file.
 * @property onSave the user wants to save the file.
 * @property onRetry repeat the failed export.
 * @property onRetryLoad read the project again.
 * @property onErrorDismissed hide the error message.
 * @property onNoticeDismissed hide the confirmation.
 */
data class ExportActions(
    val onFormatSelected: (ExportFormat) -> Unit,
    val onScaleSelected: (PlotScale?) -> Unit,
    val onPaperSelected: (PaperSize?) -> Unit,
    val onShare: () -> Unit,
    val onSave: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryLoad: () -> Unit,
    val onErrorDismissed: () -> Unit,
    val onNoticeDismissed: () -> Unit,
)
