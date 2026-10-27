package app.secretlock.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Zentrale, reine (Android-freie) Zeitplanberechnung. Wird von Service, UI und Tests genutzt. */
object ScheduleCalculator {

    /** Alle Sperrfenster einer Regel, deren Beginn in [-daysBack, +daysAhead] Tagen um [around] liegt. */
    fun windows(rule: Rule, around: Instant, zone: ZoneId, daysBack: Long = 1, daysAhead: Long = 8): List<Window> {
        if (rule.isOneShot) {
            val s = Instant.ofEpochMilli(rule.oneShotStartMs ?: 0L)
            val e = Instant.ofEpochMilli(rule.oneShotEndMs ?: 0L)
            return if (e.isAfter(s)) listOf(Window(s, e)) else emptyList()
        }
        val today = around.atZone(zone).toLocalDate()
        val start = LocalTime.of(rule.startMinute / 60, rule.startMinute % 60)
        val end = LocalTime.of(rule.endMinute / 60, rule.endMinute % 60)
        val out = ArrayList<Window>()
        for (offset in -daysBack..daysAhead) {
            val d = today.plusDays(offset)
            if (!rule.runsOn(d.dayOfWeek)) continue
            val s = ZonedDateTime.of(d, start, zone).toInstant()
            // Ende am selben Tag, wenn es nach dem Start liegt; sonst (über Mitternacht / 24 h) am Folgetag.
            val endDate = if (rule.endMinute > rule.startMinute) d else d.plusDays(1)
            val e = ZonedDateTime.of(endDate, end, zone).toInstant()
            if (e.isAfter(s)) out += Window(s, e)
        }
        return out
    }

    fun activeWindow(rule: Rule, now: Instant, zone: ZoneId): Window? {
        if (!rule.enabled) return null
        return windows(rule, now, zone, daysBack = 1, daysAhead = 0)
            .firstOrNull { it.contains(now) && it.end.toEpochMilli() > rule.suspendedUntilMs }
    }

    /**
     * Berechnet den Sperrzustand. Wurde die Uhr verstellt (wallNow != trustedNow), gilt eine Regel als
     * aktiv, wenn EINE der beiden Uhren sie aktiv sieht – die Sperre kann so nicht durch Verstellen
     * der Uhrzeit vorzeitig enden.
     */
    fun evaluate(
        rules: List<Rule>,
        wallNow: Instant,
        trustedNow: Instant = wallNow,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LockState {
        val active = LinkedHashMap<String, ActiveLock>()
        for (basis in listOf(wallNow, trustedNow).distinct()) {
            for (r in rules) {
                val w = activeWindow(r, basis, zone) ?: continue
                val cand = ActiveLock(r, w, w.end.toEpochMilli() - basis.toEpochMilli())
                val prev = active[r.id]
                if (prev == null || cand.remainingMs > prev.remainingMs) active[r.id] = cand
            }
        }
        val jump = wallNow.toEpochMilli() - trustedNow.toEpochMilli()
        return LockState(active.values.toList(), nextStart(rules, wallNow, zone), jump)
    }

    fun nextStart(rules: List<Rule>, now: Instant, zone: ZoneId): NextStart? {
        var best: NextStart? = null
        for (r in rules) {
            if (!r.enabled) continue
            for (w in windows(r, now, zone, daysBack = 0, daysAhead = 8)) {
                if (!w.start.isAfter(now)) continue
                if (w.end.toEpochMilli() <= r.suspendedUntilMs) continue
                if (best == null || w.start.isBefore(best.at)) best = NextStart(r.name, w.start, r.id)
            }
        }
        return best
    }

    /** Nächster Zeitpunkt (Wanduhr, epoch ms), an dem sich der Zustand ändern kann (Start oder Ende). */
    fun nextBoundaryMs(rules: List<Rule>, wallNow: Instant, trustedNow: Instant, zone: ZoneId): Long? {
        val shift = wallNow.toEpochMilli() - trustedNow.toEpochMilli()
        var best: Long? = null
        for ((basis, offset) in listOf(wallNow to 0L, trustedNow to shift)) {
            for (r in rules) {
                if (!r.enabled) continue
                for (w in windows(r, basis, zone, daysBack = 1, daysAhead = 8)) {
                    if (w.end.toEpochMilli() <= r.suspendedUntilMs) continue
                    for (t in listOf(w.start, w.end)) {
                        val ms = t.toEpochMilli()
                        if (ms <= basis.toEpochMilli()) continue
                        val wallMs = ms + offset
                        if (best == null || wallMs < best) best = wallMs
                    }
                }
            }
        }
        return best
    }
}
