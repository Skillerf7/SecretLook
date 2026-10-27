package app.secretlock.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class EnforcementTest {
    private val rule = Rule("r", "r", setOf("yt"), 0, 0)   // 24 h, immer aktiv
    private val now = Instant.parse("2026-06-10T10:00:00Z")
    private val zone = ZoneId.of("UTC")
    private fun perms(a11y: Boolean) = PermissionStatus(a11y, exactAlarmsAllowed = true, autoTimeEnabled = true)

    @Test fun activeWithoutAccessibilityIsNeverReportedAsEnforced() {
        val st = ScheduleCalculator.evaluate(listOf(rule), now, now, zone)
        assertEquals(EnforcementStatus.NOT_ENFORCED, Enforcement.status(st, 1, perms(false)))
        assertEquals(EnforcementStatus.ENFORCING, Enforcement.status(st, 1, perms(true)))
    }

    @Test fun idleStates() {
        val idle = ScheduleCalculator.evaluate(listOf(rule.copy(enabled = false)), now, now, zone)
        assertEquals(EnforcementStatus.NO_RULES, Enforcement.status(idle, 0, perms(true)))
        val waiting = ScheduleCalculator.evaluate(listOf(rule.copy(daysMask = 0)), now, now, zone)
        assertEquals(EnforcementStatus.SETUP_INCOMPLETE, Enforcement.status(waiting, 1, perms(false)))
        assertEquals(EnforcementStatus.WAITING, Enforcement.status(waiting, 1, perms(true)))
    }

    @Test fun hidingRequiresAtLeastOneValidRecoveryWay() {
        assertEquals(3, HidePolicy.blockers(false, 0, emptySet()).size)
        assertTrue(HidePolicy.blockers(true, 1, setOf(HidePolicy.WAY_CODE)).isEmpty())
        assertTrue(HidePolicy.blockers(true, 1, emptySet()).isNotEmpty())
    }

    @Test fun accessibilityWayOnlyCountsWhileServiceIsEnabled() {
        assertTrue(HidePolicy.validWays(setOf(HidePolicy.WAY_A11Y), accessibilityEnabled = false).isEmpty())
        assertEquals(setOf(HidePolicy.WAY_A11Y), HidePolicy.validWays(setOf(HidePolicy.WAY_A11Y), accessibilityEnabled = true))
        assertTrue(HidePolicy.validWays(setOf("UNKNOWN"), true).isEmpty())
    }
}
