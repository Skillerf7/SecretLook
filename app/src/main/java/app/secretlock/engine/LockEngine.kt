package app.secretlock.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import app.secretlock.data.Store
import app.secretlock.domain.Anchor
import app.secretlock.domain.ClockGuard
import app.secretlock.domain.LockState
import app.secretlock.domain.Rule
import app.secretlock.domain.ScheduleCalculator
import app.secretlock.service.LockAccessibilityService
import app.secretlock.system.Permissions
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

/** Zentraler Sperrzustand + Planung des nächsten Zustandswechsels. */
object LockEngine {
    const val ACTION_BOUNDARY = "app.secretlock.action.BOUNDARY"

    /** Berechnet den Zustand JETZT neu (nie aus einem gemerkten Timer) und pflegt den Uhr-Schutz. */
    @Synchronized
    fun currentState(ctx: Context): LockState {
        val app = ctx.applicationContext
        val store = Store.get(app)
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val boot = Permissions.bootCount(app)
        val prev = store.anchor()
        val reading = ClockGuard.read(prev, wall, elapsed, boot)
        val state = ScheduleCalculator.evaluate(
            store.rules(), Instant.ofEpochMilli(wall), Instant.ofEpochMilli(reading.trustedMs), ZoneId.systemDefault(),
        )

        if (reading.suspicious && abs(reading.jumpMs - store.lastClockJumpMs()) > ClockGuard.TOLERANCE_MS) {
            store.recordClockEvent(wall, reading.jumpMs)
        }
        val stale = prev == null || elapsed < prev.elapsedMs || elapsed - prev.elapsedMs > 60_000L
        when {
            // Keine Sperre aktiv: neue Uhrzeit wird akzeptiert (z. B. berechtigte Korrektur).
            reading.suspicious && !state.isActive -> store.saveAnchor(Anchor(wall, elapsed, boot))
            stale -> store.saveAnchor(reading.anchor)
        }
        return state
    }

    /** Neu berechnen, nächsten Alarm setzen, laufende Vordergrund-App ggf. sofort sperren. */
    fun refresh(ctx: Context, reason: String) {
        val app = ctx.applicationContext
        cleanupExpiredOneShots(app)
        currentState(app)
        scheduleNext(app)
        LockAccessibilityService.instance?.enforceNow()
    }

    private fun pendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_BOUNDARY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun scheduleNext(ctx: Context) {
        val store = Store.get(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(ctx)
        am.cancel(pi)
        val wall = System.currentTimeMillis()
        val reading = ClockGuard.read(store.anchor(), wall, SystemClock.elapsedRealtime(), Permissions.bootCount(ctx))
        val at = ScheduleCalculator.nextBoundaryMs(
            store.rules(), Instant.ofEpochMilli(wall), Instant.ofEpochMilli(reading.trustedMs), ZoneId.systemDefault(),
        ) ?: return
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)   // ungenau – Durchsetzung läuft trotzdem über den Service
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    // ---- Änderungen mit Schutz vor versehentlichem Abbruch ----

    /** Neue Regeln sind immer erlaubt (strenger). Bestehende Regeln sind während einer aktiven Sperre schreibgeschützt. */
    fun saveRule(ctx: Context, rule: Rule): Boolean {
        val store = Store.get(ctx)
        val exists = store.rules().any { it.id == rule.id }
        if (exists && currentState(ctx).isActive) return false
        store.upsert(rule)
        refresh(ctx, "rule_saved")
        return true
    }

    fun deleteRule(ctx: Context, id: String): Boolean {
        if (currentState(ctx).isActive) return false
        Store.get(ctx).delete(id)
        refresh(ctx, "rule_deleted")
        return true
    }

    fun setRuleEnabled(ctx: Context, id: String, enabled: Boolean): Boolean {
        val store = Store.get(ctx)
        val r = store.rules().firstOrNull { it.id == id } ?: return false
        if (currentState(ctx).isActive) return false
        store.upsert(r.copy(enabled = enabled))
        refresh(ctx, "rule_toggled")
        return true
    }

    fun startManual(ctx: Context, packages: Set<String>, minutes: Int) {
        val now = System.currentTimeMillis()
        Store.get(ctx).upsert(
            Rule(
                id = UUID.randomUUID().toString(), name = "Sofortsperre", packages = packages,
                oneShotStartMs = now, oneShotEndMs = now + minutes * 60_000L,
            )
        )
        refresh(ctx, "manual_start")
    }

    /** Dokumentierter Verwaltungsweg: beendet die aktuell laufenden Sperrfenster (spätere Fenster bleiben bestehen). */
    fun emergencyRelease(ctx: Context) {
        val store = Store.get(ctx)
        currentState(ctx).activeLocks.forEach { store.suspendRule(it.rule.id, it.window.end.toEpochMilli()) }
        refresh(ctx, "emergency_release")
    }

    private fun cleanupExpiredOneShots(ctx: Context) {
        val store = Store.get(ctx)
        val limit = System.currentTimeMillis() - 24 * 3_600_000L
        val cur = store.rules()
        val kept = cur.filterNot { it.isOneShot && (it.oneShotEndMs ?: 0L) < limit }
        if (kept.size != cur.size) store.saveRules(kept)
    }
}
