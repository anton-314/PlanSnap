package app.plansnap

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Empty activity that hosts Compose content in Hilt tests, so that `hiltViewModel()` finds its component. It lives in
 * the debug source set and therefore never reaches the release build.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
