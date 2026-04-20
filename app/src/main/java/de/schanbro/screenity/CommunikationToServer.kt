package de.schanbro.screenity

import android.content.Context
import android.provider.Settings
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

data class DeviceFullData(
    val device_id: String,
    val device_name: String,
    val reports: Map<String, DayReport>
)

// --- NETZWERK LOGIK ---
suspend fun sendDataToServer(
    context: Context,
    url: String,
    totalMs: Long,
    usageList: List<AppUsageInfo>,
    detailedEvents: List<ScreenEvent>
): Boolean = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext false
    val client = OkHttpClient()
    val reportData = mapOf(
        "device_id" to Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID),
        "device_name" to android.os.Build.MODEL,
        "report_date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
        "total_screen_time_ms" to totalMs,
        "apps" to usageList.map { mapOf("app_name" to it.appName, "usage_ms" to it.usageMs) },
        "detailedEvents" to detailedEvents
    )
    try {
        val body = Gson().toJson(reportData).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("${url.trim().removeSuffix("/")}/report").post(body).build()
        client.newCall(request).execute().isSuccessful
    } catch (e: Exception) { false }
}

val okHttpClient = OkHttpClient()

suspend fun getSummaryFromServer(
    url: String,
    userID: String,
    password: String
): ServerSummary = withContext(Dispatchers.IO) {

    // Credentials für Basic Auth (falls benötigt)
    val credential = Credentials.basic(userID, password)

    val request = Request.Builder()
        .url("${url.trim().removeSuffix("/")}/summary")
        .header("Authorization", credential) // Authentifizierung hinzufügen
        .build()

    // execute() in einem try-with-resources ähnlichen Block (use)
    okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception("Server Fehler: ${response.code}")

        val body = response.body?.charStream()
            ?: throw Exception("Response Body ist leer")

        // Direkt vom Stream lesen statt erst alles in einen String zu laden
        Gson().fromJson(body, ServerSummary::class.java)
    }
}