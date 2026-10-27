package app.secretlock.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.secretlock.data.Store
import app.secretlock.domain.HidePolicy

/**
 * Blendet NUR den Launcher-Eintrag (activity-alias) aus. Die App bleibt installiert, sichtbar in
 * Einstellungen > Apps und in der Liste der Bedienungshilfen; Zeitpläne laufen unverändert weiter.
 */
object LauncherVisibility {
    private const val ALIAS = "app.secretlock.LauncherAlias"

    private fun alias(ctx: Context) = ComponentName(ctx, ALIAS)

    fun isHidden(ctx: Context): Boolean =
        ctx.packageManager.getComponentEnabledSetting(alias(ctx)) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setHiddenUnchecked(ctx: Context, hidden: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            alias(ctx),
            if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Blendet nur aus, wenn alle Voraussetzungen (inkl. bestätigtem Wiederherstellungsweg) erfüllt sind. */
    fun hide(ctx: Context): List<String> {
        val store = Store.get(ctx)
        val blockers = HidePolicy.blockers(Permissions.accessibilityEnabled(ctx), store.rules().count { it.enabled }, store.confirmedWays())
        if (blockers.isEmpty()) setHiddenUnchecked(ctx, true)
        return blockers
    }

    fun show(ctx: Context) = setHiddenUnchecked(ctx, false)
}
