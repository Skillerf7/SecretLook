package app.secretlock.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.secretlock.ui.MainActivity
import java.util.TimeZone

/** Alarm zu Sperrbeginn/-ende. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LockEngine.refresh(context, "alarm")
    }
}

/** Neustart, App-Update, Uhr-/Zeitzonenänderung, geänderte Alarm-Berechtigung: Zustand neu berechnen, Alarm neu setzen. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) TimeZone.setDefault(null)
        LockEngine.refresh(context, intent.action ?: "system")
        RefreshWorker.ensureScheduled(context)
    }
}

/** Wiederherstellungsweg: Telefon-Code *#*#75625#*#* öffnet die Verwaltung. */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
