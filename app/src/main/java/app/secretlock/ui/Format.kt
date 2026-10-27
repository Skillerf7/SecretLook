package app.secretlock.ui

import app.secretlock.domain.Rule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    val DAY_SHORT = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    fun minute(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

    fun countdown(ms: Long): String {
        val s = (ms.coerceAtLeast(0L) + 999) / 1000
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    fun duration(min: Int): String = "${min / 60} h ${"%02d".format(min % 60)} min"

    fun days(mask: Int): String =
        if (mask == Rule.ALL_DAYS) "täglich"
        else DAY_SHORT.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }.joinToString(", ")

    fun dayTime(i: Instant): String =
        DateTimeFormatter.ofPattern("EEE HH:mm", Locale.GERMAN).format(i.atZone(ZoneId.systemDefault()))

    fun clock(ms: Long): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    fun rule(r: Rule): String =
        if (r.isOneShot) "Sofortsperre bis ${clock(r.oneShotEndMs ?: 0L)} · ${r.packages.size} Apps"
        else {
            val range = if (r.startMinute == r.endMinute) "${minute(r.startMinute)} (24 h)"
            else "${minute(r.startMinute)}–${minute(r.endMinute)}"
            "$range · ${days(r.daysMask)} · ${r.packages.size} Apps"
        }
}
