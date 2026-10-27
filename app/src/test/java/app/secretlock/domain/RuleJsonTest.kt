package app.secretlock.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class RuleJsonTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private val sample = listOf(
        Rule("1", "Nacht", setOf("com.google.android.youtube", "com.zhiliaoapp.musically"), 22 * 60, 7 * 60, 0b1111111, true),
        Rule("2", "Sofort", setOf("x"), oneShotStartMs = 10L, oneShotEndMs = 20L, suspendedUntilMs = 15L),
    )

    @Test fun roundTrip() {
        assertEquals(sample, RuleJson.decodeList(RuleJson.encodeList(sample)))
    }

    @Test fun corruptEntriesAreSkippedAndInvalidJsonIsEmpty() {
        val json = """[{"id":"ok","name":"A","packages":["p"]},{"name":"no id"},"junk"]"""
        val list = RuleJson.decodeList(json)
        assertEquals(listOf("ok"), list.map { it.id })
        assertTrue(RuleJson.decodeList("not json").isEmpty())
    }

    @Test fun missingFieldsGetDefaults() {
        val r = RuleJson.decodeList("""[{"id":"x","packages":["p"]}]""").single()
        assertEquals(22 * 60, r.startMinute); assertEquals(7 * 60, r.endMinute)
        assertEquals(Rule.ALL_DAYS, r.daysMask); assertTrue(r.enabled); assertNull(r.oneShotEndMs)
    }

    @Test fun outOfRangeValuesAreClamped() {
        val r = RuleJson.decodeList("""[{"id":"x","startMinute":99999,"endMinute":-5,"daysMask":255}]""").single()
        assertEquals(1439, r.startMinute); assertEquals(0, r.endMinute); assertEquals(Rule.ALL_DAYS, r.daysMask)
    }

    /** "Neustart": Regeln aus dem Speicher laden und denselben Zustand wie vorher berechnen. */
    @Test fun stateAfterRestartMatchesStateBefore() {
        val now = LocalDateTime.parse("2026-06-10T23:30:00").atZone(berlin).toInstant()
        val before = ScheduleCalculator.evaluate(sample, now, now, berlin)
        val restored = RuleJson.decodeList(RuleJson.encodeList(sample))
        val after = ScheduleCalculator.evaluate(restored, now, now, berlin)
        assertEquals(before.lockedPackages, after.lockedPackages)
        assertEquals(before.maxRemainingMs, after.maxRemainingMs)
        assertTrue(after.isLocked("com.google.android.youtube"))
    }
}
