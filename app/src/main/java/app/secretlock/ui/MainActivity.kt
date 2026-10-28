package app.secretlock.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.secretlock.data.Store
import app.secretlock.domain.Rule
import app.secretlock.engine.LockEngine
import app.secretlock.system.AppEntry
import app.secretlock.system.AppRepository
import app.secretlock.system.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecretLockTheme {
                Surface(Modifier.fillMaxSize(), color = Pal.Bg) { AppRoot() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LockEngine.refresh(this, "resume")   // Zustand beim Öffnen immer neu berechnen
    }
}

sealed interface Screen {
    data object Home : Screen
    data object Rules : Screen
    data class Editor(val ruleId: String?) : Screen
    data object Manual : Screen
    data object Activate : Screen
    data object Help : Screen
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var rules by remember { mutableStateOf(store.rules()) }
    var tick by remember { mutableIntStateOf(0) }

    // Sekundentakt nur, solange die Oberfläche sichtbar ist (RESUMED).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                rules = store.rules()
                tick++
                delay(1000)
            }
        }
    }
    val state = remember(tick) { LockEngine.currentState(ctx) }
    val perms = remember(tick) { Permissions.status(ctx) }
    val catalog by produceState(emptyList<AppEntry>()) {
        value = withContext(Dispatchers.IO) { AppRepository.load(ctx) }
    }

    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen is Screen.Editor) Screen.Rules else Screen.Home
    }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            state, perms, rules, catalog,
            onRules = { screen = Screen.Rules }, onActivate = { screen = Screen.Activate },
            onManual = { screen = Screen.Manual }, onHelp = { screen = Screen.Help },
        )
        Screen.Rules -> RulesScreen(
            state, rules, onBack = { screen = Screen.Home },
            onEdit = { screen = Screen.Editor(it) }, onManual = { screen = Screen.Manual },
        )
        is Screen.Editor -> RuleEditorScreen(
            initial = rules.firstOrNull { it.id == s.ruleId }, catalog = catalog,
            locked = state.isActive && s.ruleId != null,
            onBack = { screen = Screen.Rules }, onSaved = { screen = Screen.Rules },
        )
        Screen.Manual -> ManualLockScreen(catalog, onBack = { screen = Screen.Home }, onStarted = { screen = Screen.Home })
        Screen.Activate -> ActivateScreen(perms, rules, tick, onBack = { screen = Screen.Home })
        Screen.Help -> HelpScreen(onBack = { screen = Screen.Home })
    }
}
