package app.secretlock

import android.app.Application
import app.secretlock.engine.RefreshWorker

class SecretLockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshWorker.ensureScheduled(this)   // Sicherheitsnetz, kein Echtzeit-Timer
    }
}
