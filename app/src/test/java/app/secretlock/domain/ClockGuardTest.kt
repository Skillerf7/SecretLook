package app.secretlock.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ClockGuardTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun ms(date: String, time: String) = LocalDateTime.parse("${date}T$time").atZone(berlin).toInstant().toEpochMilli()
    private val night = Rule("n", "Nacht", setOf("yt"), 22 * 60, 7 * 60)

    @Test fun firstReadingTrustsWallClock() {
        val r = ClockGuard.read(null, 1_000_000L, 500L, 7)
        assertFalse(r.suspicious)
        assertEquals(1_000_000L, r.trustedMs)
    }

    @Test fun smallDriftIsTolerated() {
        val prev = Anchor(1_000_000L, 0L, 7)
        val r = ClockGuard.read(prev, 1_000_000L + 60_000 + 5_000, 60_000L, 7)
        assertFalse(r.suspicious)
    }

    @Test fun clockJumpInsideSameBootIsDetected() {
        val prev = Anchor(1_000_000L, 0L, 7)
        val r = ClockGuard.read(prev, 1_000_000L + 8 * 3_600_000L, 60_000L, 7)
        assertTrue(r.suspicious)
        assertEquals(1_000_000L + 60_000L, r.trustedMs)
    }

    @Test fun settingClockForwardDoesNotEndLockEarly() {
        val real = ms("2026-06-10", "23:00")
        val prev = Anchor(real, 0L, 7)
        val wall = ms("2026-06-11", "08:00")            // Benutzer stellt die Uhr auf 08:00 vor
        val reading = ClockGuard.read(prev, wall, 60_000L, 7)
        val st = ScheduleCalculator.evaluate(listOf(night), Instant.ofEpochMilli(wall), Instant.ofEpochMilli(reading.trustedMs), berlin)
        assertTrue(st.isLocked("yt"))
        assertEquals(8 * 3_600_000L - 60_000L, st.remainingMsFor("yt"))
    }

    @Test fun settingClockBackwardDoesNotDisableLock() {
        val real = ms("2026-06-10", "23:00")
        val prev = Anchor(real, 0L, 7)
        val wall = ms("2026-06-10", "20:00")            // zurückgestellt auf 20:00
        val reading = ClockGuard.read(prev, wall, 60_000L, 7)
        val st = ScheduleCalculator.evaluate(listOf(night), Instant.ofEpochMilli(wall), Instant.ofEpochMilli(reading.trustedMs), berlin)
        assertTrue(st.isLocked("yt"))
    }

    @Test fun afterRebootClockCannotGoBelowLastKnownTime() {
        val prev = Anchor(ms("2026-06-10", "23:00"), 100_000L, 7)
        val back = ClockGuard.read(prev, ms("2026-06-10", "20:00"), 5_000L, 8)   // anderer Boot
        assertTrue(back.suspicious)
        assertEquals(prev.trustedMs, back.trustedMs)
        val normal = ClockGuard.read(prev, ms("2026-06-10", "23:10"), 5_000L, 8)
        assertFalse(normal.suspicious)
    }

    @Test fun elapsedGoingBackwardsCountsAsReboot() {
        val prev = Anchor(1_000_000L, 900_000L, -1)       // Boot-ID unbekannt
        val r = ClockGuard.read(prev, 1_100_000L, 1_000L, -1)
        assertEquals(1_100_000L, r.trustedMs)
    }
}
