package app.plansnap

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Smoke test on a real device: checks the installed app package (REQ-NFR-004, REQ-NFR-005). */
class PackageSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun targetPackageIsPlanSnap() {
        assertEquals("app.plansnap", context.packageName)
    }

    @Test
    fun installedPackageHasNoInternetPermission() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toList().orEmpty()
        assertFalse("INTERNET must not be requested: $requested", Manifest.permission.INTERNET in requested)
        assertTrue(
            "INTERNET must not be granted",
            context.checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED,
        )
    }
}
