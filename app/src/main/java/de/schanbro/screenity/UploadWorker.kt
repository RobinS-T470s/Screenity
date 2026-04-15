package de.schanbro.screenity

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("ScreenityPrefs", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""

        if (serverUrl.isBlank()) {
            return@withContext Result.failure()
        }

        try {
            // 1. Daten sammeln (Nutzt deine optimierten Funktionen!)
            val totalMs = getTotalScreenTime(applicationContext)
            val usageList = getTodayUsageEvents(applicationContext)
            val detailedEvents = getDetailedEvents(applicationContext)

            // 2. An den Server senden (deine bestehende Funktion)
            val success = sendDataToServer(
                applicationContext,
                serverUrl,
                totalMs,
                usageList,
                detailedEvents
            )

            if (success) {
                Result.success()
            } else {
                Result.retry() // Android versucht es später nochmal, falls der Server offline war
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}