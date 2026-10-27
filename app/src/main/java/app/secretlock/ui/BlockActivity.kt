package app.secretlock.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.secretlock.engine.LockEngine
import kotlinx.coroutines.delay

/** Sperrseite: zeigt die gesperrte App und die verbleibende Zeit. Schließt sich selbst, sobald die Sperre endet. */
class BlockActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_PKG = "pkg"
        fun intent(ctx: Context, pkg: String): Intent =
            Intent(ctx, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_PKG, pkg)
    }

    private var pkg by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        setContent {
            SecretLockTheme {
                Surface(Modifier.fillMaxSize(), color = Pal.Bg) {
                    BackHandler { goHome() }
                    BlockScreen(pkg, onHome = { goHome() }, onExpired = { finish() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: pkg
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
private fun BlockScreen(pkg: String, onHome: () -> Unit, onExpired: () -> Unit) {
    val ctx = LocalContext.current
    val label = remember(pkg) {
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg)
    }
    var remaining by remember { mutableLongStateOf(-1L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, pkg) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {                       // tickt nur, solange die Sperrseite sichtbar ist
                val r = LockEngine.currentState(ctx).remainingMsFor(pkg)
                if (r == null) { onExpired(); break }
                remaining = r
                delay(1000)
            }
        }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Gesperrt", color = Pal.Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if (remaining >= 0) {
            Text(Fmt.countdown(remaining), fontSize = 46.sp, fontWeight = FontWeight.Light)
            Text("bis ${Fmt.clock(System.currentTimeMillis() + remaining)} Uhr", color = Pal.Dim)
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "Diese App ist durch deine eigene Secret-Lock-Regel gesperrt und wird automatisch wieder freigegeben.",
            color = Pal.Dim, textAlign = TextAlign.Center, fontSize = 14.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onHome) { Text("Zur Startseite") }
    }
}
