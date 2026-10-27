package app.secretlock.domain

import org.json.JSONArray
import org.json.JSONObject

/** (De-)Serialisierung der Regeln. Tolerant: fehlende Felder bekommen Standardwerte, defekte Einträge werden übersprungen. */
object RuleJson {
    fun encode(r: Rule): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("name", r.name)
        put("packages", JSONArray(r.packages.sorted()))
        put("startMinute", r.startMinute)
        put("endMinute", r.endMinute)
        put("daysMask", r.daysMask)
        put("enabled", r.enabled)
        put("oneShotStartMs", r.oneShotStartMs ?: JSONObject.NULL)
        put("oneShotEndMs", r.oneShotEndMs ?: JSONObject.NULL)
        put("suspendedUntilMs", r.suspendedUntilMs)
    }

    fun decode(o: JSONObject): Rule {
        val pk = o.optJSONArray("packages") ?: JSONArray()
        val pkgs = (0 until pk.length()).map { pk.getString(it) }.toSet()
        val id = o.getString("id")
        require(id.isNotBlank())
        return Rule(
            id = id,
            name = o.optString("name", "Regel"),
            packages = pkgs,
            startMinute = o.optInt("startMinute", 22 * 60).coerceIn(0, 1439),
            endMinute = o.optInt("endMinute", 7 * 60).coerceIn(0, 1439),
            daysMask = o.optInt("daysMask", Rule.ALL_DAYS) and Rule.ALL_DAYS,
            enabled = o.optBoolean("enabled", true),
            oneShotStartMs = if (o.isNull("oneShotStartMs")) null else o.getLong("oneShotStartMs"),
            oneShotEndMs = if (o.isNull("oneShotEndMs")) null else o.getLong("oneShotEndMs"),
            suspendedUntilMs = o.optLong("suspendedUntilMs", 0L),
        )
    }

    fun encodeList(list: List<Rule>): String = JSONArray().also { a -> list.forEach { a.put(encode(it)) } }.toString()

    fun decodeList(json: String): List<Rule> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> runCatching { decode(arr.getJSONObject(i)) }.getOrNull() }
    }
}
