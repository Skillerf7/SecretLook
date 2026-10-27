package app.secretlock.engine

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Sicherheitsnetz (mind. 15 Min., vom System gebündelt): berechnet den Zustand neu und setzt den Alarm erneut.
 * KEIN minutengenauer Timer – Beginn/Ende werden über AlarmManager ausgelöst, die Durchsetzung über den Service.
 */
class RefreshWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        LockEngine.refresh(applicationContext, "worker")
        return Result.success()
    }

    companion object {
        fun ensureScheduled(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniquePeriodicWork("secretlock_refresh", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
