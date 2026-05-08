package de.schanbro.screenity

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import android.util.Log
import kotlinx.coroutines.*

class AppBlockService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var lastToastTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Wir reagieren auf App-Wechsel
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // Eigene App und System-Launcher ignorieren
            if (packageName == this.packageName || packageName.contains("launcher")) return

            checkAndNotify(packageName)
        }
    }

    private fun checkAndNotify(pkg: String) {
        val prefs = getSharedPreferences("ScreenityPrefs", Context.MODE_PRIVATE)
        val limitMinutes = prefs.getInt("limit_$pkg", 0)

        serviceScope.launch {
            // Hol die echten Daten von deiner Funktion
            val usageList = getTodayUsageEvents(applicationContext, pkg)
            val usedMs = usageList.firstOrNull()?.usageMs ?: 0L
            val usedMinutes = (usedMs / 60000).toInt()

            // 1. BENACHRICHTIGUNG (Toast) zur Kontrolle
            // Wir begrenzen Toasts auf alle 3 Sekunden, damit es nicht nervt
            if (System.currentTimeMillis() - lastToastTime > 3000) {
                val limitText = if (limitMinutes == 0) "Kein Limit" else "$limitMinutes Min"
                Toast.makeText(
                    applicationContext,
                    "App: $pkg\nHeute: $usedMinutes Min | Limit: $limitText",
                    Toast.LENGTH_SHORT
                ).show()
                lastToastTime = System.currentTimeMillis()
            }

            // 2. SPERR-LOGIK
            if (limitMinutes in 1..usedMinutes) {
                Log.d("Screenity", "SPERRE: $pkg ($usedMinutes >= $limitMinutes)")
                blockApp()
            }
        }
    }

    private fun blockApp() {
        val startMain = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(startMain)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}