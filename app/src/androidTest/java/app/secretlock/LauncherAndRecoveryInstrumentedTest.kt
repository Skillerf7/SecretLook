package app.secretlock

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.secretlock.data.Store
import app.secretlock.system.LauncherVisibility
import app.secretlock.system.Permissions
import app.secretlock.ui.MainActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Gerätetests – benötigen Gerät/Emulator (z. B. ./gradlew connectedDebugAndroidTest). */
@RunWith(AndroidJUnit4::class)
class LauncherAndRecoveryInstrumentedTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun hasLauncherEntry() = ctx.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(ctx.packageName), 0,
    ).isNotEmpty()

    @After fun restore() { LauncherVisibility.show(ctx) }

    @Test fun launcherEntryCanBeHiddenAndRestored() {
        LauncherVisibility.setHiddenUnchecked(ctx, true)
        assertTrue(LauncherVisibility.isHidden(ctx)); assertFalse(hasLauncherEntry())
        LauncherVisibility.setHiddenUnchecked(ctx, false)
        assertFalse(LauncherVisibility.isHidden(ctx)); assertTrue(hasLauncherEntry())
    }

    @Test fun managementActivityStaysReachableWhileHidden() {
        LauncherVisibility.setHiddenUnchecked(ctx, true)
        assertNotNull(ctx.packageManager.resolveActivity(Intent(ctx, MainActivity::class.java), 0))
    }

    @Test fun mainActivityIsNeverDisabledByHiding() {
        LauncherVisibility.setHiddenUnchecked(ctx, true)
        val d = LauncherVisibility.diagnose(ctx)
        assertTrue(d.mainActivityResolvable)
        assertFalse(d.mainActivityState == "deaktiviert")
        assertEquals(0, d.launcherEntries)
    }

    @Test fun showKeepsExactlyOneLauncherEntry() {
        LauncherVisibility.show(ctx)
        assertEquals(1, LauncherVisibility.diagnose(ctx).launcherEntries)
    }

    @Test fun hidingIsRefusedWithoutRecoveryWay() {
        Store.get(ctx).confirmedWays().forEach { Store.get(ctx).setWayConfirmed(it, false) }
        assertTrue(LauncherVisibility.hide(ctx).isNotEmpty())
        assertFalse(LauncherVisibility.isHidden(ctx))
    }

    @Test fun missingAccessibilityPermissionIsDetected() {
        // Setzt voraus, dass die Bedienungshilfe auf dem Testgerät NICHT eingeschaltet ist.
        assertFalse(Permissions.accessibilityEnabled(ctx))
    }
}
