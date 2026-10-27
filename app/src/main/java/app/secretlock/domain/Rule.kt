package app.secretlock.domain

import java.time.DayOfWeek
import java.time.Instant

/**
 * Eine Sperrregel. Zeiten sind lokale Wanduhrzeit (Minuten seit 00:00), damit Zeitzonen- und
 * Sommerzeitwechsel korrekt berücksichtigt werden. Eine Sofortsperre ist eine Regel mit
 * absoluten Zeitpunkten (oneShot*).
 *
 * daysMask: Bit0 = Montag … Bit6 = Sonntag; gemeint ist der Tag, an dem die Sperre BEGINNT.
 * startMinute == endMinute bedeutet 24 Stunden.
 */
data class Rule(
    val id: String,
    val name: String,
    val packages: Set<String>,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
    val daysMask: Int = ALL_DAYS,
    val enabled: Boolean = true,
    val oneShotStartMs: Long? = null,
    val oneShotEndMs: Long? = null,
    /** Notfall-Aufhebung: Fenster, die vor diesem Zeitpunkt enden, zählen nicht. */
    val suspendedUntilMs: Long = 0L,
) {
    val isOneShot: Boolean get() = oneShotEndMs != null

    fun runsOn(day: DayOfWeek): Boolean = (daysMask and dayBit(day)) != 0

    companion object {
        const val ALL_DAYS = 0b1111111
        fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    }
}

data class Window(val start: Instant, val end: Instant) {
    fun contains(t: Instant): Boolean = !t.isBefore(start) && t.isBefore(end)
}
