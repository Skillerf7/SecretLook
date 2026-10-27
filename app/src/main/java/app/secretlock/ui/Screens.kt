@file:OptIn(ExperimentalMaterial3Api::class)

package app.secretlock.ui

import android.app.StatusBarManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.secretlock.R
import app.secretlock.data.Store
import app.secretlock.domain.*
import app.secretlock.engine.LockEngine
import app.secretlock.service.RecoveryTileService
import app.secretlock.system.AppEntry
import app.secretlock.system.AppRepository
import app.secretlock.system.LauncherVisibility
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.abs

// ---------------------------------------------------------------- gemeinsame Bausteine

private fun launch(ctx: Context, intent: Intent) {
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = Pal.Bg,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("‹ Zurück") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Pal.Bg, titleContentColor = Pal.Text),
            )
        },
    ) { pad -> Box(Modifier.padding(pad).fillMaxSize()) { content() } }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Pal.Surface),
        shape = RoundedCornerShape(20.dp),
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

@Composable
private fun PermRow(ok: Boolean, title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "●" else "○", color = if (ok) Pal.Ok else Pal.Bad, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, color = Pal.Dim, fontSize = 13.sp)
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

// ---------------------------------------------------------------- Startseite

@Composable
fun HomeScreen(
    state: LockState, perms: PermissionStatus, rules: List<Rule>, catalog: List<AppEntry>,
    onRules: () -> Unit, onActivate: () -> Unit, onManual: () -> Unit, onHelp: () -> Unit,
) {
    val ctx = LocalContext.current
    val status = Enforcement.status(state, rules.count { it.enabled }, perms)
    ScreenScaffold("Secret Lock", null, actions = { TextButton(onClick = onHelp) { Text("Info") } }) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusCard(status, state) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onActivate, modifier = Modifier.weight(1f)) { Text("Aktivieren") }
                        OutlinedButton(onClick = onRules, modifier = Modifier.weight(1f)) { Text("Verwalten") }
                    }
                    FilledTonalButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("Sofort sperren …") }
                }
            }
            if (abs(state.clockJumpMs) > ClockGuard.TOLERANCE_MS) item {
                SectionCard {
                    Text("Uhrzeit wurde verstellt", color = Pal.Warn, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Die Systemuhr weicht um etwa ${abs(state.clockJumpMs) / 60_000} Min. von der Laufzeit seit dem letzten Abgleich ab. " +
                            "Laufende Sperren enden nach der unverstellten Zeit.",
                        color = Pal.Dim, fontSize = 13.sp,
                    )
                }
            }
            if (state.isActive) item {
                SectionCard {
                    Text("Aktive Sperren", fontWeight = FontWeight.SemiBold)
                    state.activeLocks.forEach {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${it.rule.name} · ${it.rule.packages.size} Apps")
                            Text(Fmt.countdown(it.remainingMs), color = Pal.Accent)
                        }
                    }
                }
            }
            item { PermissionCard(perms) }
            item { RulesSummaryCard(rules) }
            item { AppsCard(rules, catalog) }
        }
    }
}

@Composable
private fun StatusCard(status: EnforcementStatus, state: LockState) {
    val (title, color, sub) = when (status) {
        EnforcementStatus.ENFORCING -> Triple("Sperre aktiv", Pal.Ok, "Endet um ${Fmt.clock(System.currentTimeMillis() + state.maxRemainingMs)} Uhr")
        EnforcementStatus.NOT_ENFORCED -> Triple(
            "Sperre fällig – NICHT durchgesetzt", Pal.Bad,
            "Die Bedienungshilfe ist aus. Apps sind im Moment nicht gesperrt.",
        )
        EnforcementStatus.SETUP_INCOMPLETE -> Triple(
            "Einrichtung unvollständig", Pal.Warn, "Ohne Bedienungshilfe kann keine App gesperrt werden.",
        )
        EnforcementStatus.WAITING -> Triple("Inaktiv – bereit", Pal.Accent, "Keine Sperre läuft gerade.")
        EnforcementStatus.NO_RULES -> Triple("Inaktiv", Pal.Dim, "Es ist noch keine Regel angelegt.")
    }
    SectionCard {
        Text(title, color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (state.isActive) Text(Fmt.countdown(state.maxRemainingMs), fontSize = 40.sp, fontWeight = FontWeight.Light)
        Text(sub, color = Pal.Dim)
        state.nextStart?.let { Text("Nächste Sperre: ${Fmt.dayTime(it.at)} · ${it.ruleName}", color = Pal.Dim, fontSize = 13.sp) }
    }
}

@Composable
private fun PermissionCard(perms: PermissionStatus) {
    val ctx = LocalContext.current
    val pkgUri = Uri.parse("package:${ctx.packageName}")
    SectionCard {
        Text("Berechtigungen", fontWeight = FontWeight.SemiBold)
        PermRow(
            perms.accessibilityEnabled, "Bedienungshilfe „Secret Lock Sperre“",
            if (perms.accessibilityEnabled) "Aktiv – Sperren werden durchgesetzt."
            else "Erforderlich. Ausgegraut? App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“.",
            "Öffnen",
        ) { launch(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        if (!perms.accessibilityEnabled) TextButton(onClick = { launch(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)) }) {
            Text("App-Info öffnen")
        }
        PermRow(
            perms.exactAlarmsAllowed, "Exakte Alarme",
            if (perms.exactAlarmsAllowed) "Sperrbeginn/-ende werden minutengenau ausgelöst." else "Aus – Wechsel kann sich verzögern; die Durchsetzung bleibt aktiv.",
            if (perms.exactAlarmsAllowed || Build.VERSION.SDK_INT < 31) null else "Erlauben",
        ) { launch(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri)) }
        PermRow(
            perms.autoTimeEnabled, "Automatische Uhrzeit",
            if (perms.autoTimeEnabled) "An – Uhrzeit kommt vom Netz." else "Aus – Uhr lässt sich von Hand verstellen (wird erkannt, aber nicht verhindert).",
            if (perms.autoTimeEnabled) null else "Ändern",
        ) { launch(ctx, Intent(Settings.ACTION_DATE_SETTINGS)) }
    }
}

@Composable
private fun RulesSummaryCard(rules: List<Rule>) {
    SectionCard {
        Text("Sperrregeln", fontWeight = FontWeight.SemiBold)
        if (rules.isEmpty()) Text("Noch keine Regel. Über „Verwalten“ anlegen.", color = Pal.Dim)
        rules.forEach {
            Column {
                Text(it.name + if (!it.enabled) " (aus)" else "", color = if (it.enabled) Pal.Text else Pal.Dim)
                Text(Fmt.rule(it), color = Pal.Dim, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AppsCard(rules: List<Rule>, catalog: List<AppEntry>) {
    val byPkg = remember(catalog) { catalog.associateBy { it.pkg } }
    val pkgs = rules.filter { it.enabled }.flatMap { it.packages }.distinct()
    SectionCard {
        Text("Ausgewählte Apps (${pkgs.size})", fontWeight = FontWeight.SemiBold)
        if (pkgs.isEmpty()) Text("Keine Apps ausgewählt.", color = Pal.Dim)
        else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pkgs, key = { it }) { p ->
                    val e = byPkg[p]
                    if (e?.icon != null) Image(e.icon, e.label, Modifier.size(38.dp))
                    else Box(Modifier.size(38.dp).clip(CircleShape).background(Pal.Surface2))
                }
            }
            Text(pkgs.take(6).joinToString(", ") { byPkg[it]?.label ?: it } + if (pkgs.size > 6) " …" else "", color = Pal.Dim, fontSize = 13.sp)
        }
    }
}

// ---------------------------------------------------------------- Regelverwaltung

@Composable
fun RulesScreen(state: LockState, rules: List<Rule>, onBack: () -> Unit, onEdit: (String?) -> Unit, onManual: () -> Unit) {
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf<Rule?>(null) }
    var emergency by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    ScreenScaffold("Verwalten", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.isActive) item {
                SectionCard {
                    Text("Änderungen gesperrt", color = Pal.Warn, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Bestehende Regeln können erst nach Ablauf der laufenden Sperre (noch ${Fmt.countdown(state.maxRemainingMs)}) geändert werden. " +
                            "Neue, zusätzliche Sperren sind jederzeit möglich.",
                        color = Pal.Dim, fontSize = 13.sp,
                    )
                }
            }
            info?.let { item { Text(it, color = Pal.Warn) } }
            items(rules, key = { it.id }) { r ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontWeight = FontWeight.SemiBold)
                            Text(Fmt.rule(r), color = Pal.Dim, fontSize = 13.sp)
                        }
                        Switch(
                            checked = r.enabled, enabled = !state.isActive,
                            onCheckedChange = { if (!LockEngine.setRuleEnabled(ctx, r.id, it)) info = "Während einer aktiven Sperre nicht änderbar." },
                        )
                    }
                    Row {
                        TextButton(onClick = { onEdit(r.id) }, enabled = !state.isActive) { Text("Bearbeiten") }
                        TextButton(onClick = { confirmDelete = r }, enabled = !state.isActive) { Text("Löschen") }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onEdit(null) }, modifier = Modifier.weight(1f)) { Text("Neue Regel") }
                    FilledTonalButton(onClick = onManual, modifier = Modifier.weight(1f)) { Text("Sofort sperren") }
                }
            }
            if (state.isActive) item {
                OutlinedButton(onClick = { emergency = true }, modifier = Modifier.fillMaxWidth()) { Text("Notfall-Aufhebung …") }
            }
        }
    }
    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Regel löschen?") },
            text = { Text("„${r.name}“ wird endgültig entfernt.") },
            confirmButton = { TextButton(onClick = { LockEngine.deleteRule(ctx, r.id); confirmDelete = null }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Abbrechen") } },
        )
    }
    if (emergency) EmergencyDialog(onDismiss = { emergency = false }, onConfirm = { LockEngine.emergencyRelease(ctx); emergency = false })
}

@Composable
private fun EmergencyDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var left by remember { mutableIntStateOf(60) }
    var phrase by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notfall-Aufhebung") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Beendet die gerade laufenden Sperren sofort. Spätere Sperrzeiten bleiben bestehen. Bewusste Reibung: 60 Sekunden warten und das Wort ENTSPERREN eingeben.")
                OutlinedTextField(phrase, { phrase = it }, singleLine = true, label = { Text("ENTSPERREN") })
                if (left > 0) Text("Noch $left s …", color = Pal.Warn)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = left == 0 && phrase.trim().uppercase() == "ENTSPERREN") { Text("Sperre beenden") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ---------------------------------------------------------------- Regel-Editor

private fun pickTime(ctx: Context, initial: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, true).show()
}

@Composable
private fun DayToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (selected) Pal.Accent else Pal.Surface2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) Pal.Bg else Pal.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun RuleEditorScreen(initial: Rule?, catalog: List<AppEntry>, locked: Boolean, onBack: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val excluded = remember { AppRepository.excluded(ctx) }
    var name by remember { mutableStateOf(initial?.name ?: "Nachtsperre") }
    var start by remember { mutableIntStateOf(initial?.startMinute ?: 22 * 60) }
    var end by remember { mutableIntStateOf(initial?.endMinute ?: 7 * 60) }
    var useDuration by remember { mutableStateOf(false) }
    var dur by remember { mutableIntStateOf(((end - start + 1440) % 1440).let { if (it == 0) 1440 else it }) }
    var days by remember { mutableIntStateOf(initial?.daysMask ?: Rule.ALL_DAYS) }
    var pkgs by remember { mutableStateOf(initial?.packages ?: emptySet()) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = picking) { picking = false }
    if (picking) {
        AppPickerScreen(catalog, pkgs, onDone = { pkgs = it; picking = false }, onCancel = { picking = false })
        return
    }
    val effEnd = if (useDuration) (start + dur) % 1440 else end

    ScreenScaffold(if (initial == null) "Neue Regel" else "Regel bearbeiten", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (locked) item { Text("Während einer aktiven Sperre ist diese Regel schreibgeschützt.", color = Pal.Warn) }
            item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !locked) }
            item {
                SectionCard {
                    Text("Zeitraum", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Start", modifier = Modifier.width(52.dp))
                        OutlinedButton(onClick = { pickTime(ctx, start) { start = it } }, enabled = !locked) { Text(Fmt.minute(start)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!useDuration, { useDuration = false }, label = { Text("Endzeit") }, enabled = !locked)
                        FilterChip(useDuration, { useDuration = true }, label = { Text("Dauer") }, enabled = !locked)
                    }
                    if (!useDuration) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Ende", modifier = Modifier.width(52.dp))
                            OutlinedButton(onClick = { pickTime(ctx, end) { end = it } }, enabled = !locked) { Text(Fmt.minute(end)) }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { dur = (dur - 15).coerceAtLeast(15) }, enabled = !locked) { Text("−") }
                            Text(Fmt.duration(dur))
                            OutlinedButton(onClick = { dur = (dur + 15).coerceAtMost(1440) }, enabled = !locked) { Text("+") }
                        }
                    }
                    Text(
                        if (start > effEnd || (start == effEnd)) "Endet am Folgetag um ${Fmt.minute(effEnd)} (über Mitternacht)." else "Endet am selben Tag um ${Fmt.minute(effEnd)}.",
                        color = Pal.Dim, fontSize = 13.sp,
                    )
                }
            }
            item {
                SectionCard {
                    Text("Wochentage (Tag des Sperrbeginns)", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Fmt.DAY_SHORT.forEachIndexed { i, d ->
                            DayToggle(d, (days and (1 shl i)) != 0) { if (!locked) days = days xor (1 shl i) }
                        }
                    }
                }
            }
            item {
                SectionCard {
                    Text("Apps", fontWeight = FontWeight.SemiBold)
                    Text("${pkgs.count { it !in excluded }} ausgewählt", color = Pal.Dim)
                    OutlinedButton(onClick = { picking = true }, enabled = !locked) { Text("Apps auswählen …") }
                }
            }
            error?.let { item { Text(it, color = Pal.Bad) } }
            item {
                Button(
                    enabled = !locked, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val clean = pkgs.filter { it !in excluded }.toSet()
                        error = when {
                            clean.isEmpty() -> "Bitte mindestens eine App auswählen."
                            days == 0 -> "Bitte mindestens einen Wochentag wählen."
                            !useDuration && start == end -> "Start und Ende dürfen nicht gleich sein (für 24 h die Dauer wählen)."
                            else -> null
                        }
                        if (error == null) {
                            val rule = Rule(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.ifBlank { "Regel" }, packages = clean,
                                startMinute = start, endMinute = effEnd, daysMask = days,
                                enabled = initial?.enabled ?: true, suspendedUntilMs = initial?.suspendedUntilMs ?: 0L,
                            )
                            if (LockEngine.saveRule(ctx, rule)) onSaved() else error = "Gerade aktive Sperre: bestehende Regeln sind schreibgeschützt."
                        }
                    },
                ) { Text("Speichern") }
            }
        }
    }
}

// ---------------------------------------------------------------- App-Auswahl

@Composable
fun AppPickerScreen(catalog: List<AppEntry>, selected: Set<String>, onDone: (Set<String>) -> Unit, onCancel: () -> Unit) {
    var sel by remember { mutableStateOf(selected) }
    var q by remember { mutableStateOf("") }
    val shown = remember(catalog, q) { catalog.filter { q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true) } }
    ScreenScaffold("Apps auswählen", onCancel, actions = { TextButton(onClick = { onDone(sel) }) { Text("Fertig (${sel.size})") } }) {
        Column {
            OutlinedTextField(
                q, { q = it }, singleLine = true, placeholder = { Text("Apps suchen") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (catalog.isEmpty()) Text("Apps werden geladen …", color = Pal.Dim, modifier = Modifier.padding(16.dp))
            LazyColumn {
                items(shown, key = { it.pkg }) { app ->
                    val on = app.pkg in sel
                    Row(
                        Modifier.fillMaxWidth().clickable { sel = if (on) sel - app.pkg else sel + app.pkg }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (app.icon != null) Image(app.icon, null, Modifier.size(40.dp)) else Box(Modifier.size(40.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(app.label)
                            Text(app.pkg, color = Pal.Dim, fontSize = 11.sp)
                        }
                        Checkbox(checked = on, onCheckedChange = { sel = if (on) sel - app.pkg else sel + app.pkg })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Sofortsperre

@Composable
fun ManualLockScreen(catalog: List<AppEntry>, onBack: () -> Unit, onStarted: () -> Unit) {
    val ctx = LocalContext.current
    var minutes by remember { mutableIntStateOf(60) }
    var pkgs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    BackHandler(enabled = picking) { picking = false }
    if (picking) { AppPickerScreen(catalog, pkgs, { pkgs = it; picking = false }, { picking = false }); return }

    ScreenScaffold("Sofort sperren", onBack) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard {
                Text("Dauer: ${Fmt.duration(minutes)}", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30 to "30 min", 60 to "1 h", 120 to "2 h", 240 to "4 h", 480 to "8 h").forEach { (m, l) ->
                        FilterChip(minutes == m, { minutes = m }, label = { Text(l) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { minutes = (minutes - 15).coerceAtLeast(15) }) { Text("−15") }
                    OutlinedButton(onClick = { minutes = (minutes + 15).coerceAtMost(1440) }) { Text("+15") }
                }
            }
            SectionCard {
                Text("${pkgs.size} Apps ausgewählt", color = Pal.Dim)
                OutlinedButton(onClick = { picking = true }) { Text("Apps auswählen …") }
            }
            Button(onClick = { confirm = true }, enabled = pkgs.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Jetzt sperren") }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Sperre starten?") },
        text = { Text("${pkgs.size} Apps werden für ${Fmt.duration(minutes)} gesperrt. Vorzeitiges Beenden geht nur über die Notfall-Aufhebung.") },
        confirmButton = { TextButton(onClick = { LockEngine.startManual(ctx, pkgs, minutes); confirm = false; onStarted() }) { Text("Sperren") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
    )
}

// ---------------------------------------------------------------- Aktivieren / Symbol ausblenden

@Composable
fun ActivateScreen(perms: PermissionStatus, rules: List<Rule>, tick: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var ways by remember { mutableStateOf(store.confirmedWays()) }
    var hidden by remember(tick) { mutableStateOf(LauncherVisibility.isHidden(ctx)) }
    var confirm by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf<List<String>>(emptyList()) }
    val enabledCount = rules.count { it.enabled }
    val blockers = HidePolicy.blockers(perms.accessibilityEnabled, enabledCount, ways)

    fun toggle(way: String, on: Boolean) { store.setWayConfirmed(way, on); ways = store.confirmedWays() }

    ScreenScaffold("Aktivieren", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    Text("Voraussetzungen", fontWeight = FontWeight.SemiBold)
                    PermRow(perms.accessibilityEnabled, "Bedienungshilfe aktiv", "Ohne sie wird keine App gesperrt.")
                    PermRow(enabledCount > 0, "Mindestens eine aktive Regel", "$enabledCount aktiv")
                }
            }
            item {
                SectionCard {
                    Text("Wiederherstellungswege", fontWeight = FontWeight.SemiBold)
                    Text("Bevor das Symbol verschwindet, muss mindestens ein Weg zurück in die Verwaltung bestätigt sein.", color = Pal.Dim, fontSize = 13.sp)
                    WayRow(
                        "Bedienungshilfen-Einstellungen",
                        "Einstellungen → Bedienungshilfe → Installierte Apps → Secret Lock Sperre (ggf. Zahnrad/Einstellungen). Gilt nur, solange die Bedienungshilfe an ist.",
                        HidePolicy.WAY_A11Y in ways,
                    ) { toggle(HidePolicy.WAY_A11Y, it) }
                    WayRow(
                        "Telefon-Code",
                        "In der Telefon-App *#*#75625#*#* wählen. Funktioniert nicht auf jedem Gerät/jeder One-UI-Version – bitte ausprobieren, bevor du bestätigst.",
                        HidePolicy.WAY_CODE in ways,
                    ) { toggle(HidePolicy.WAY_CODE, it) }
                    WayRow(
                        "Schnelleinstellungs-Kachel",
                        "Kachel „Secret Lock“ im Schnellzugriff-Panel; öffnet die Verwaltung direkt.",
                        HidePolicy.WAY_TILE in ways,
                    ) { toggle(HidePolicy.WAY_TILE, it) }
                    if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = {
                        val sbm = ctx.getSystemService(StatusBarManager::class.java)
                        sbm.requestAddTileService(
                            ComponentName(ctx, RecoveryTileService::class.java), "Secret Lock",
                            Icon.createWithResource(ctx, R.drawable.ic_tile), ctx.mainExecutor,
                        ) { result ->
                            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                            ) toggle(HidePolicy.WAY_TILE, true)   // vom System bestätigt
                        }
                    }) { Text("Kachel jetzt hinzufügen") }
                }
            }
            if (refused.isNotEmpty()) item { Text(refused.joinToString("\n"), color = Pal.Bad) }
            item {
                if (!hidden) {
                    Button(onClick = { confirm = true }, enabled = blockers.isEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text("Aktivieren und Symbol ausblenden")
                    }
                    if (blockers.isNotEmpty()) Text(blockers.joinToString("\n"), color = Pal.Warn, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                } else {
                    SectionCard {
                        Text("Symbol ist ausgeblendet", color = Pal.Ok, fontWeight = FontWeight.SemiBold)
                        Text("Sperrzeiten laufen weiter. Öffnen über die bestätigten Wege.", color = Pal.Dim, fontSize = 13.sp)
                        OutlinedButton(onClick = { LauncherVisibility.show(ctx); hidden = false }) { Text("Symbol wieder einblenden") }
                    }
                }
            }
            item {
                Text(
                    "Hinweis: Ausgeblendet wird nur der Launcher-Eintrag. Ob Secret Lock danach in der Suche deines Launchers " +
                        "(Samsung Finder) noch erscheint, hängt vom Launcher ab und ist nicht garantiert. Die App bleibt in " +
                        "Einstellungen → Apps sichtbar und lässt sich jederzeit deinstallieren.",
                    color = Pal.Dim, fontSize = 12.sp,
                )
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Symbol ausblenden?") },
        text = { Text("Secret Lock verschwindet aus dem App-Drawer. Zeitpläne laufen weiter. Zurück kommst du über die bestätigten Wiederherstellungswege.") },
        confirmButton = {
            TextButton(onClick = { refused = LauncherVisibility.hide(ctx); hidden = LauncherVisibility.isHidden(ctx); confirm = false }) { Text("Ausblenden") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
    )
}

@Composable
private fun WayRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Checkbox(checked, onChange)
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(text, color = Pal.Dim, fontSize = 12.sp)
            Text("Ich habe diesen Weg ausprobiert / eingerichtet", color = Pal.Dim, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- Info

@Composable
fun HelpScreen(onBack: () -> Unit) {
    ScreenScaffold("Info & Grenzen", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    Text("Was Secret Lock macht", fontWeight = FontWeight.SemiBold)
                    Text("Eine Bedienungshilfe meldet nur den Namen der Vordergrund-App. Ist sie gerade gesperrt, wird eine Sperrseite angezeigt. Alle Daten bleiben lokal auf dem Gerät.", color = Pal.Dim, fontSize = 13.sp)
                }
            }
            item {
                SectionCard {
                    Text("Bekannte Grenzen", fontWeight = FontWeight.SemiBold)
                    Text(
                        "• Die Sperre ist eine Annäherung: Wer die Bedienungshilfe abschaltet, Secret Lock deinstalliert oder das Gerät zurücksetzt, hebt sie auf.\n" +
                            "• Beim Schalten der Uhr nach einem Neustart kann die Sperre nicht sicher erkannt werden.\n" +
                            "• Eine schon geöffnete App wird erst beim nächsten Fenster-Wechsel erkannt, wenn der Dienst frisch gestartet wurde.\n" +
                            "• Auffindbarkeit in der Suche bei ausgeblendetem Symbol ist launcherabhängig.\n" +
                            "• Notfall-Aufhebung: Verwalten → „Notfall-Aufhebung“ (60 s Wartezeit + Bestätigungswort).",
                        color = Pal.Dim, fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
