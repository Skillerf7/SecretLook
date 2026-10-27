package app.secretlock.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleCalculatorTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(date: String, time: String, zone: ZoneId = berlin): Instant =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant()

    private fun rule(
        id: String = "r1", pk: Set<String> = setOf("yt", "tt", "ig"),
        s: Int = 22 * 60, e: Int = 7 * 60, days: Int = Rule.ALL_DAYS, enabled: Boolean = true,
    ) = Rule(id, id, pk, s, e, days, enabled)

    private fun active(r: List<Rule>, now: Instant, zone: ZoneId = berlin) =
        ScheduleCalculator.evaluate(r, now, now, zone)

    // 2026-06-10 ist ein Mittwoch.

    @Test fun lockBeginsAt2200() {
        val r = listOf(rule())
        assertFalse(active(r, at("2026-06-10", "21:59:59")).isActive)
        assertTrue(active(r, at("2026-06-10", "22:00:00")).isActive)
    }

    @Test fun lockEndsAt0700AndReleasesAutomatically() {
        val r = listOf(rule())
        assertTrue(active(r, at("2026-06-11", "06:59:59")).isActive)
        val after = active(r, at("2026-06-11", "07:00:00"))
        assertFalse(after.isActive)
        assertFalse(after.isLocked("yt"))
    }

    @Test fun remainingTimeIsCorrect() {
        val st = active(listOf(rule()), at("2026-06-10", "23:00"))
        assertEquals(Duration.ofHours(8).toMillis(), st.remainingMsFor("yt"))
        assertNull(st.remainingMsFor("not-selected-app"))
    }

    @Test fun overMidnightOnlyOnStartDay() {
        val monday = listOf(rule(days = 0b0000001))          // nur Montag
        assertTrue(active(monday, at("2026-06-15", "23:00")).isActive)    // Mo Abend
        assertTrue(active(monday, at("2026-06-16", "03:00")).isActive)    // Di früh gehört zum Montags-Fenster
        assertFalse(active(monday, at("2026-06-16", "23:00")).isActive)   // Di Abend: kein Start
        assertFalse(active(monday, at("2026-06-14", "23:00")).isActive)   // So Abend: kein Start
    }

    @Test fun recurringWeekdaysSameDayWindow() {
        val workdays = listOf(rule(s = 9 * 60, e = 17 * 60, days = 0b0011111))   // Mo–Fr 09–17
        assertTrue(active(workdays, at("2026-06-12", "10:00")).isActive)    // Freitag
        assertFalse(active(workdays, at("2026-06-13", "10:00")).isActive)   // Samstag
        assertFalse(active(workdays, at("2026-06-12", "17:00")).isActive)   // Ende exklusiv
    }

    @Test fun equalStartAndEndMeans24Hours() {
        val r = listOf(rule(s = 8 * 60, e = 8 * 60))
        val st = active(r, at("2026-06-10", "20:00"))
        assertEquals(Duration.ofHours(12).toMillis(), st.remainingMsFor("yt"))
    }

    @Test fun disabledRuleNeverLocks() {
        assertFalse(active(listOf(rule(enabled = false)), at("2026-06-10", "23:00")).isActive)
    }

    @Test fun springForwardDstWindowEndsAt0700LocalTime() {
        val sat = listOf(rule(days = 0b0100000))   // Samstag 22:00 -> Sonntag 07:00, Umstellung 29.03.2026
        val w = ScheduleCalculator.activeWindow(sat[0], at("2026-03-28", "23:00"), berlin)!!
        assertEquals(Instant.parse("2026-03-28T21:00:00Z"), w.start)   // 22:00 MEZ
        assertEquals(Instant.parse("2026-03-29T05:00:00Z"), w.end)     // 07:00 MESZ
        assertTrue(active(sat, Instant.parse("2026-03-29T04:59:59Z")).isActive)
        assertFalse(active(sat, Instant.parse("2026-03-29T05:00:00Z")).isActive)
    }

    @Test fun fallBackDstWindowEndsAt0700LocalTime() {
        val sat = listOf(rule(days = 0b0100000))   // Umstellung 25.10.2026
        val w = ScheduleCalculator.activeWindow(sat[0], at("2026-10-24", "23:00"), berlin)!!
        assertEquals(Instant.parse("2026-10-24T20:00:00Z"), w.start)   // 22:00 MESZ
        assertEquals(Instant.parse("2026-10-25T06:00:00Z"), w.end)     // 07:00 MEZ
    }

    @Test fun timeZoneChangeRecomputesFromWallClock() {
        val r = listOf(rule())
        val instant = Instant.parse("2026-06-10T20:30:00Z")
        assertTrue(active(r, instant, berlin).isActive)                          // 22:30
        assertFalse(active(r, instant, ZoneId.of("Europe/London")).isActive)     // 21:30
        assertFalse(active(r, instant, ZoneId.of("America/New_York")).isActive)  // 16:30
        assertTrue(active(r, instant, ZoneId.of("Asia/Tokyo")).isActive)         // 05:30
    }

    @Test fun multipleSimultaneousRulesTakeLongestRemaining() {
        val a = rule("a", setOf("a", "b"), 22 * 60, 7 * 60)
        val b = rule("b", setOf("b", "c"), 6 * 60, 8 * 60)
        val s1 = active(listOf(a, b), at("2026-06-11", "06:30"))
        assertEquals(setOf("a", "b", "c"), s1.lockedPackages)
        assertEquals(Duration.ofMinutes(90).toMillis(), s1.remainingMsFor("b"))   // max(30, 90)
        assertEquals(Duration.ofMinutes(30).toMillis(), s1.remainingMsFor("a"))
        val s2 = active(listOf(a, b), at("2026-06-11", "07:30"))
        assertEquals(setOf("b", "c"), s2.lockedPackages)
        assertFalse(active(listOf(a, b), at("2026-06-11", "08:00")).isActive)
    }

    @Test fun oneShotManualLock() {
        val start = at("2026-06-10", "12:00").toEpochMilli()
        val r = Rule("m", "Sofort", setOf("yt"), oneShotStartMs = start, oneShotEndMs = start + 3_600_000)
        assertEquals(1_800_000L, active(listOf(r), at("2026-06-10", "12:30")).remainingMsFor("yt"))
        assertFalse(active(listOf(r), at("2026-06-10", "13:00")).isActive)
    }

    @Test fun emergencyReleaseSuspendsOnlyTheCurrentWindow() {
        val base = rule()
        val windowEnd = at("2026-06-11", "07:00").toEpochMilli()
        val released = listOf(base.copy(suspendedUntilMs = windowEnd))
        assertFalse(active(released, at("2026-06-10", "23:00")).isActive)
        assertTrue(active(released, at("2026-06-11", "22:30")).isActive)   // nächste Nacht gilt wieder
    }

    @Test fun nextStartAndBoundary() {
        val r = listOf(rule())
        val now = at("2026-06-10", "12:00")
        assertEquals(at("2026-06-10", "22:00"), ScheduleCalculator.nextStart(r, now, berlin)!!.at)
        assertEquals(at("2026-06-10", "22:00").toEpochMilli(), ScheduleCalculator.nextBoundaryMs(r, now, now, berlin))
        val night = at("2026-06-10", "23:00")
        assertEquals(at("2026-06-11", "07:00").toEpochMilli(), ScheduleCalculator.nextBoundaryMs(r, night, night, berlin))
        assertNull(ScheduleCalculator.nextBoundaryMs(emptyList(), now, now, berlin))
    }
}
