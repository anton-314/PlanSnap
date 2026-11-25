package app.plansnap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.navigation.PlanSnapNavHost
import dagger.hilt.android.AndroidEntryPoint

/** The single activity. It hosts the navigation graph inside the PlanSnap theme. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlanSnapTheme {
                PlanSnapNavHost()
            }
        }
    }
}
