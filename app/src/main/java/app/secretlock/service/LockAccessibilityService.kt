package app.secretlock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import app.secretlock.engine.LockEngine
import app.secretlock.ui.BlockActivity

/**
 * Setzt die Sperre durch: bei jedem Fenster-Wechsel wird geprüft, ob das Vordergrund-Paket gerade gesperrt
 * ist. Es werden nur Paketnamen ausgewertet – keine Bildschirminhalte, keine Eingaben, keine Übertragung.
 * Kein Dauer-Loop: der Service arbeitet ausschließlich ereignisgesteuert.
 */
class LockAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: LockAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastForeground: String? = null
    private var ignored: Set<String> = emptySet()
    private var lastBlockAt = 0L

    override fun onServiceConnected() {
        instance = this
        ignored = setOf("android", "com.android.systemui") + runCatching {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        }.getOrDefault(emptyList())
        LockEngine.refresh(this, "service_connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in ignored) return
        lastForeground = pkg
        checkAndBlock(pkg)
    }

    /** Wird bei Sperrbeginn aufgerufen: eine bereits geöffnete Ziel-App im Vordergrund wird sofort gesperrt. */
    fun enforceNow() {
        lastForeground?.let { checkAndBlock(it) }
    }

    private fun checkAndBlock(pkg: String) {
        val state = LockEngine.currentState(this)
        if (!state.isLocked(pkg)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 500) return
        lastBlockAt = now
        performGlobalAction(GLOBAL_ACTION_HOME)           // Ziel-App aus dem Vordergrund nehmen
        handler.postDelayed({                              // einmalig, kein Loop
            startActivity(BlockActivity.intent(this, pkg))
        }, 200)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
