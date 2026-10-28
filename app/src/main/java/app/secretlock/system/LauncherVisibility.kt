package app.secretlock.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import app.secretlock.data.Store
import app.secretlock.domain.HidePolicy

/**
 * Blendet NUR den Launcher-Eintrag (activity-alias) aus. Die App bleibt installiert, sichtbar in
 * Einstellungen > Apps und in der Liste der Bedienungshilfen; Zeitpläne laufen unverändert weiter.
 */
object LauncherVisibility {
    private const val ALIAS = "app.secretlock.LauncherAlias"
    private const val MAIN = "app.secretlock.ui.MainActivity"

    /**
     * Messwerte für die Diagnose-Karte. Die Verwaltungs-Activity (MainActivity) wird von diesem Objekt
     * NIE deaktiviert; angefasst wird ausschließlich der Alias [ALIAS].
     */
    data class Diagnosis(
        val aliasState: String,
        val mainActivityState: String,
        val mainActivityResolvable: Boolean,
        val launcherEntries: Int,
    )

    fun diagnose(ctx: Context): Diagnosis {
        val pm = ctx.packageManager
        fun label(state: Int) = when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "deaktiviert"
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "aktiviert"
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> "aktiviert (Manifest-Standard)"
            else -> "sonstiger Zustand ($state)"
        }
        val main = ComponentName(ctx.packageName, MAIN)
        return Diagnosis(
            aliasState = label(pm.getComponentEnabledSetting(alias(ctx))),
            mainActivityState = label(pm.getComponentEnabledSetting(main)),
            mainActivityResolvable = pm.resolveActivity(Intent().setComponent(main), 0) != null,
            launcherEntries = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(ctx.packageName), 0,
            ).size,
        )
    }

    /**
     * Öffnet die Home-Einstellungen von One UI (dort: „Apps ausblenden“). UNGETESTET auf echtem Gerät:
     * Ob der Launcher diese Absicht annimmt, ist nicht belegt; Rückfall sind die allgemeinen Einstellungen.
     * Gibt true zurück, wenn die Launcher-Einstellungen direkt geöffnet wurden.
     */
    fun openHomeSettings(ctx: Context): Boolean {
        val direct = Intent(Intent.ACTION_APPLICATION_PREFERENCES)
            .setPackage("com.sec.android.app.launcher")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(direct); return true } catch (e: Exception) { }
        try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
        return false
    }

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
