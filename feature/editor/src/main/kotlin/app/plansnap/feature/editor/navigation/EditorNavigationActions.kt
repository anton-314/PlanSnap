package app.plansnap.feature.editor.navigation

import androidx.compose.runtime.Immutable

/**
 * Navigation callbacks of the editor. The editor knows no routes; the app maps the callbacks to destinations.
 *
 * @property onBack Returns to the previous screen.
 * @property onOpenExport Opens the export screen for the open project.
 */
@Immutable
data class EditorNavigationActions(
    val onBack: () -> Unit,
    val onOpenExport: () -> Unit,
)
