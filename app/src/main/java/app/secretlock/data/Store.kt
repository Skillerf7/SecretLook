package app.secretlock.data

import android.content.Context
import android.content.SharedPreferences
import app.secretlock.domain.Anchor
import app.secretlock.domain.Rule
import app.secretlock.domain.RuleJson

/** Rein lokale Persistenz (SharedPreferences im app-privaten Speicher). Keine Cloud, kein Backup. */
class Store private constructor(app: Context) {
    private val prefs: SharedPreferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    @Volatile private var cache: List<Rule>? = null

    init { Migrations.run(prefs) }

    @Synchronized fun rules(): List<Rule> {
        cache?.let { return it }
        val r = RuleJson.decodeList(prefs.getString(KEY_RULES, "[]") ?: "[]")
        cache = r
        return r
    }

    @Synchronized fun saveRules(list: List<Rule>) {
        prefs.edit().putString(KEY_RULES, RuleJson.encodeList(list)).commit()
        cache = list
    }

    @Synchronized fun upsert(rule: Rule) {
        val cur = rules()
        saveRules(if (cur.any { it.id == rule.id }) cur.map { if (it.id == rule.id) rule else it } else cur + rule)
    }

    @Synchronized fun delete(id: String) = saveRules(rules().filterNot { it.id == id })

    @Synchronized fun suspendRule(id: String, untilMs: Long) =
        saveRules(rules().map { if (it.id == id) it.copy(suspendedUntilMs = maxOf(it.suspendedUntilMs, untilMs)) else it })

    // --- Uhr-Schutz ---
    @Synchronized fun anchor(): Anchor? =
        if (!prefs.contains(KEY_A_TRUSTED)) null
        else Anchor(prefs.getLong(KEY_A_TRUSTED, 0), prefs.getLong(KEY_A_ELAPSED, 0), prefs.getLong(KEY_A_BOOT, -1))

    @Synchronized fun saveAnchor(a: Anchor) {
        prefs.edit().putLong(KEY_A_TRUSTED, a.trustedMs).putLong(KEY_A_ELAPSED, a.elapsedMs).putLong(KEY_A_BOOT, a.bootId).apply()
    }

    @Synchronized fun lastClockJumpMs(): Long = prefs.getLong(KEY_JUMP_MS, 0L)
    @Synchronized fun lastClockEventAtMs(): Long = prefs.getLong(KEY_JUMP_AT, 0L)
    @Synchronized fun recordClockEvent(atWallMs: Long, jumpMs: Long) {
        prefs.edit().putLong(KEY_JUMP_AT, atWallMs).putLong(KEY_JUMP_MS, jumpMs).apply()
    }

    // --- Wiederherstellungswege ---
    @Synchronized fun confirmedWays(): Set<String> = prefs.getStringSet(KEY_WAYS, emptySet())?.toSet() ?: emptySet()
    @Synchronized fun setWayConfirmed(way: String, confirmed: Boolean) {
        val cur = confirmedWays()
        prefs.edit().putStringSet(KEY_WAYS, if (confirmed) cur + way else cur - way).apply()
    }

    companion object {
        private const val PREFS = "secret_lock"
        private const val KEY_RULES = "rules_json"
        private const val KEY_A_TRUSTED = "anchor_trusted"
        private const val KEY_A_ELAPSED = "anchor_elapsed"
        private const val KEY_A_BOOT = "anchor_boot"
        private const val KEY_JUMP_MS = "clock_jump_ms"
        private const val KEY_JUMP_AT = "clock_jump_at"
        private const val KEY_WAYS = "recovery_ways"
        @Volatile private var instance: Store? = null
        fun get(ctx: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(ctx.applicationContext).also { instance = it }
        }
    }
}

/** Schema-Versionierung der gespeicherten Daten. Version 1 = aktuelles Format. */
object Migrations {
    const val CURRENT = 1
    private const val KEY = "schema"

    fun run(prefs: SharedPreferences) {
        val v = prefs.getInt(KEY, 0)
        if (v >= CURRENT) return
        // v0 -> v1: Erstinstallation bzw. Format ohne Versionsfeld. RuleJson ergänzt fehlende Felder
        // mit Standardwerten, deshalb ist keine Umschreibung nötig. Künftige Schritte hier einhängen:
        // if (v < 2) { ...umschreiben... }
        prefs.edit().putInt(KEY, CURRENT).apply()
    }
}
