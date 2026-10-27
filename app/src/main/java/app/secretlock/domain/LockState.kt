package app.secretlock.domain

import java.time.Instant

data class ActiveLock(val rule: Rule, val window: Window, val remainingMs: Long)

data class NextStart(val ruleName: String, val at: Instant, val ruleId: String)

/** Ergebnis der zentralen Zustandsberechnung. */
data class LockState(
    val activeLocks: List<ActiveLock>,
    val nextStart: NextStart?,
    /** Wanduhr minus vertrauenswürdige Uhr; != 0 nur, wenn die Uhr verstellt wurde. */
    val clockJumpMs: Long = 0L,
) {
    val isActive: Boolean get() = activeLocks.isNotEmpty()
    val lockedPackages: Set<String> by lazy { activeLocks.flatMap { it.rule.packages }.toSet() }
    fun isLocked(pkg: String): Boolean = pkg in lockedPackages
    fun remainingMsFor(pkg: String): Long? =
        activeLocks.filter { pkg in it.rule.packages }.maxOfOrNull { it.remainingMs }
    val maxRemainingMs: Long get() = activeLocks.maxOfOrNull { it.remainingMs } ?: 0L
}
