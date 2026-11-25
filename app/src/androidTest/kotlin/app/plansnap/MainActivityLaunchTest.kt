package app.plansnap

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Test

/** Starts the real app with the production Hilt graph on a device and waits until the activity is resumed. */
class MainActivityLaunchTest {
    @Test
    fun mainActivityStartsAndResumes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
