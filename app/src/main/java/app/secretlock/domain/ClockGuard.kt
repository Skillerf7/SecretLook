package app.secretlock.domain

import kotlin.math.abs

/** Referenzpunkt: vertrauenswürdige Zeit zum Zeitpunkt [elapsedMs] (SystemClock.elapsedRealtime) im Boot [bootId]. */
data class Anchor(val trustedMs: Long, val elapsedMs: Long, val bootId: Long)

data class ClockReading(
    val wallMs: Long,
    /** Zeit, die für die Auswertung zusätzlich zur Wanduhr verwendet wird (== wallMs, wenn unauffällig). */
    val trustedMs: Long,
    /** Wanduhr minus extrapolierte Zeit; groß => Uhr wurde verstellt. */
    val jumpMs: Long,
    val suspicious: Boolean,
    val anchor: Anchor,
)

/**
 * Erkennt manuelle Uhrzeitänderungen: Innerhalb eines Boots läuft die vertrauenswürdige Zeit über die
 * monotone elapsedRealtime-Uhr weiter. Nach einem Neustart ist das nicht prüfbar; dort gilt nur,
 * dass die Uhr nicht hinter den letzten bekannten Stand zurückgesetzt werden kann.
 */
object ClockGuard {
    const val TOLERANCE_MS = 120_000L

    fun read(prev: Anchor?, wallMs: Long, elapsedMs: Long, bootId: Long): ClockReading {
        val trusted = when {
            prev == null -> wallMs
            prev.bootId == bootId && elapsedMs >= prev.elapsedMs -> prev.trustedMs + (elapsedMs - prev.elapsedMs)
            else -> maxOf(wallMs, prev.trustedMs)
        }
        val jump = wallMs - trusted
        val suspicious = abs(jump) > TOLERANCE_MS
        val effective = if (suspicious) trusted else wallMs
        return ClockReading(wallMs, effective, jump, suspicious, Anchor(effective, elapsedMs, bootId))
    }
}
