package app.plansnap

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checks the merged manifest: no backup (REQ-DATA-003) and no permission (REQ-NFR-004). */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ManifestTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun backupIsDisabled() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals("allowBackup must be false", 0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun noSystemPermissionIsRequested() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toList().orEmpty()
        assertTrue("Unexpected permissions: $requested", requested.all { it.startsWith("app.plansnap.") })
    }

    @Test
    fun launcherActivityIsMainActivity() {
        val intent =
            android.content
                .Intent(
                    android.content.Intent.ACTION_MAIN,
                ).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        intent.setPackage(context.packageName)
        val resolved = context.packageManager.queryIntentActivities(intent, 0)
        assertEquals(listOf(MainActivity::class.java.name), resolved.map { it.activityInfo.name })
    }
}
