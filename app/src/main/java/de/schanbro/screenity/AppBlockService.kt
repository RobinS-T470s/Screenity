package de.schanbro.screenity

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

class AppBlockService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentForegroundPackage: String? = null

    // Der Handler ermöglicht zeitgesteuerte Aufgaben
    private val checkHandler = Handler(Looper.getMainLooper())

    // Das Runnable ist die Aufgabe, die wiederholt wird
    private val checkRunnable = object : Runnable {
        override fun run() {
            currentForegroundPackage?.let { pkg ->
                checkAndBlock(pkg)
            }
            // In 30 Sekunden erneut prüfen (30.000 Millisekunden)
            checkHandler.postDelayed(this, 30000)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // Ignoriere System-Apps und den Launcher
            if (packageName == this.packageName || packageName.contains("launcher")) {
                currentForegroundPackage = null
                return
            }

            currentForegroundPackage = packageName

            // Sofortige Prüfung beim App-Wechsel
            checkAndBlock(packageName)
        }
    }

    private fun checkAndBlock(pkg: String) {
        val prefs = getSharedPreferences("ScreenityPrefs", Context.MODE_PRIVATE)
        val limitMinutes = prefs.getInt("limit_$pkg", 0)

        if (limitMinutes <= 0) return

        serviceScope.launch {
            val usageList = getTodayUsageEvents(applicationContext, pkg)
            val usedMinutes = (usageList.firstOrNull()?.usageMs ?: 0L) / 60000

            if (usedMinutes >= limitMinutes) {
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Starte die regelmäßige Überprüfung, sobald der Dienst verbunden ist
        checkHandler.post(checkRunnable)
    }

    override fun onInterrupt() {
        checkHandler.removeCallbacks(checkRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        checkHandler.removeCallbacks(checkRunnable)
        serviceScope.cancel()
    }
}