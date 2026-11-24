package app.plansnap.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.component.PlanSnapIconButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.component.PlanSnapNumericKey
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.component.PlanSnapStatusBadge
import app.plansnap.core.designsystem.component.ShapeStatus
import app.plansnap.core.designsystem.theme.PlanSnapTheme

/** All base components with test-only labels, shared by the screenshot tests. */
@Composable
internal fun ComponentGallery(darkTheme: Boolean) {
    PlanSnapTheme(darkTheme = darkTheme) {
        Surface {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlanSnapStatusBadge(ShapeStatus.Confirmed, "Closed 11.31 m2")
                PlanSnapStatusBadge(ShapeStatus.Unconfirmed, "Shape not confirmed")
                PlanSnapStatusBadge(ShapeStatus.Conflicting, "Measurements conflict")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanSnapButton("OK", onClick = {})
                    PlanSnapButton("Disabled", onClick = {}, enabled = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanSnapOutlinedButton("Left", onClick = {})
                    PlanSnapIconButton(PlanSnapIcons.Back, "Back", onClick = {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanSnapNumericKey("7", onClick = {})
                    PlanSnapNumericKey("8", onClick = {})
                    PlanSnapNumericKey(",", onClick = {}, contentDescription = "Decimal separator")
                }
            }
        }
    }
}
