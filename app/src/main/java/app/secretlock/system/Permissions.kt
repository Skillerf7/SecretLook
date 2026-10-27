package app.secretlock.system

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import app.secretlock.domain.PermissionStatus
import app.secretlock.service.LockAccessibilityService

object Permissions {
    fun accessibilityEnabled(ctx: Context): Boolean {
        val cr = ctx.contentResolver
        if (Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, LockAccessibilityService::class.java)
        return list.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun exactAlarmsAllowed(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() else true

    fun autoTimeEnabled(ctx: Context): Boolean =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.AUTO_TIME, 0) == 1

    fun bootCount(ctx: Context): Long =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1).toLong()

    fun status(ctx: Context) = PermissionStatus(
        accessibilityEnabled = accessibilityEnabled(ctx),
        exactAlarmsAllowed = exactAlarmsAllowed(ctx),
        autoTimeEnabled = autoTimeEnabled(ctx),
    )
}
