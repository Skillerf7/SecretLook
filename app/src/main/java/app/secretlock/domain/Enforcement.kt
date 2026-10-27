package app.secretlock.domain

data class PermissionStatus(
    val accessibilityEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val autoTimeEnabled: Boolean,
)

enum class EnforcementStatus { NO_RULES, SETUP_INCOMPLETE, WAITING, ENFORCING, NOT_ENFORCED }

object Enforcement {
    /** Behauptet nie eine Sperre, die ohne Bedienungshilfe gar nicht durchgesetzt werden kann. */
    fun status(state: LockState, enabledRuleCount: Int, perms: PermissionStatus): EnforcementStatus = when {
        state.isActive && perms.accessibilityEnabled -> EnforcementStatus.ENFORCING
        state.isActive -> EnforcementStatus.NOT_ENFORCED
        enabledRuleCount == 0 -> EnforcementStatus.NO_RULES
        !perms.accessibilityEnabled -> EnforcementStatus.SETUP_INCOMPLETE
        else -> EnforcementStatus.WAITING
    }
}

/** Voraussetzungen für das Ausblenden des Launcher-Symbols. */
object HidePolicy {
    const val WAY_A11Y = "A11Y"   // Einstellungen > Bedienungshilfe > Secret Lock
    const val WAY_CODE = "CODE"   // Telefon-Code *#*#75625#*#*
    const val WAY_TILE = "TILE"   // Schnelleinstellungs-Kachel
    val ALL_WAYS = setOf(WAY_A11Y, WAY_CODE, WAY_TILE)

    fun validWays(confirmed: Set<String>, accessibilityEnabled: Boolean): Set<String> =
        confirmed.filter { it in ALL_WAYS && (it != WAY_A11Y || accessibilityEnabled) }.toSet()

    fun blockers(accessibilityEnabled: Boolean, enabledRuleCount: Int, confirmed: Set<String>): List<String> {
        val out = ArrayList<String>()
        if (!accessibilityEnabled) out += "Die Bedienungshilfe „Secret Lock Sperre“ ist nicht aktiv."
        if (enabledRuleCount == 0) out += "Es gibt keine aktive Sperrregel."
        if (validWays(confirmed, accessibilityEnabled).isEmpty()) out += "Es ist noch kein Wiederherstellungsweg bestätigt."
        return out
    }
}
