package app.secretlock.system

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

object AppRepository {
    /** Systemkritische Pakete, die nie als Ziel wählbar sind (zusätzlich zu Launcher, Tastaturen, Secret Lock selbst). */
    private val NEVER = setOf(
        "android", "com.android.settings", "com.android.systemui", "com.android.phone",
        "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
        "com.samsung.android.incallui", "com.samsung.android.app.telephonyui", "com.android.server.telecom",
        "com.android.emergency", "com.sec.android.app.launcher", "com.android.packageinstaller",
        "com.google.android.packageinstaller", "com.google.android.permissioncontroller",
    )

    fun excluded(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val home = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
        val ime = runCatching {
            ctx.getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }
        }.getOrDefault(emptyList())
        return NEVER + ctx.packageName + home + ime
    }

    @Volatile private var cache: List<AppEntry>? = null

    /** Installierte Apps mit Launcher-Eintrag, alphabetisch. Aufruf abseits des Main-Threads. */
    fun load(ctx: Context): List<AppEntry> {
        cache?.let { return it }
        val pm = ctx.packageManager
        val skip = excluded(ctx)
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName !in skip }
            .map { ri ->
                AppEntry(
                    ri.activityInfo.packageName,
                    ri.loadLabel(pm).toString(),
                    runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
        cache = list
        return list
    }
}
