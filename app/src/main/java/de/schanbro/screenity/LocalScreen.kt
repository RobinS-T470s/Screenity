package de.schanbro.screenity

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

// --- DATENMODELL ---
data class AppUsageInfo(
    val appName: String,
    val usageMs: Long
)

@Composable
@Preview
fun LocalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val prefs = remember { context.getSharedPreferences("ScreenityPrefs", Context.MODE_PRIVATE) }
    val serverUrl = prefs.getString("server_url", "") ?: ""

    var usageList by remember { mutableStateOf<List<AppUsageInfo>>(emptyList()) }
    var totalMs by remember { mutableLongStateOf(0L) }
    val detailedEvents = getDetailedEvents(context)
    var isSending by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            // getTodayUsageEvents solltest du am besten auch auf suspend umstellen!
            usageList = getTodayUsageEvents(context)

            // Hier rufen wir unsere neue asynchrone Funktion auf
            totalMs = getTotalScreenTime(context)
        }
    }

    // Beim ersten Start des Screens laden
    LaunchedEffect(Unit) {
        load()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        ScreenWrapper(
            title = stringResource(R.string.this_device),
            headerAction = {
                IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, null) }
            }
        ) {
            TotalTimeCard(totalMs)
            Spacer(Modifier.height(12.dp))

            val successMessage = stringResource(R.string.sent)
            val errorMessage = stringResource(R.string.error)

            Button(
                onClick = {
                    scope.launch {
                        isSending = true
                        val ok = sendDataToServer(context, serverUrl, totalMs, usageList, detailedEvents)
                        snackbarHostState.showSnackbar(if(ok) successMessage else errorMessage)
                        isSending = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending && serverUrl.isNotBlank()
            ) {
                if (isSending) CircularProgressIndicator(Modifier.size(20.dp))
                else Text(stringResource(R.string.upload_data))
            }

            Spacer(Modifier.height(12.dp)) // Kleiner Abstand zur Liste

            LazyColumn(Modifier.weight(1f)) {
                items(usageList) { app -> AppUsageRow(app) }
            }
        }
    }
}

// --- UI KOMPONENTEN ---

@Composable
fun TotalTimeCard(totalMs: Long) {
    val h = totalMs / 3600000
    val m = (totalMs % 3600000) / 60000
    val s = (totalMs % 60000) / 1000

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.all_time), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                text = String.format("%02dh %02dm %02ds", h, m, s),
                style = MaterialTheme.typography.displayMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
fun AppUsageRow(app: AppUsageInfo) {
    val hours = app.usageMs / 3600000
    val minutes = (app.usageMs % 3600000) / 60000
    val seconds = (app.usageMs % 60000) / 1000

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(app.appName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(
                text = String.format("%02dh %02dm %02ds", hours, minutes, seconds),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// --- SYSTEM LOGIK (PROFI-METHODEN) ---

suspend fun getTodayUsageEvents(context: Context): List<AppUsageInfo> = withContext(Dispatchers.IO) {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    val events = usm.queryEvents(calendar.timeInMillis, System.currentTimeMillis())
    val event = UsageEvents.Event()

    val appUsageMap = mutableMapOf<String, Long>()
    val openApps = mutableMapOf<String, Long>()

    var lastActivePackageName: String? = null
    var screenOnTime: Long? = null

    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        val packageName = event.packageName

        when (event.eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                // Wenn der Bildschirm gerade erst angegangen ist UND direkt eine NEUE App startet,
                // löschen wir den Verdacht, dass die alte App weiterlief!
                if (screenOnTime != null && packageName != lastActivePackageName) {
                    screenOnTime = null
                }

                // Falls der Verdacht stimmte und die ALTE App weiterlief:
                if (screenOnTime != null && lastActivePackageName != null) {
                    openApps[lastActivePackageName] = screenOnTime!!
                    screenOnTime = null
                }

                openApps[packageName] = event.timeStamp
                lastActivePackageName = packageName
            }

            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                val start = openApps.remove(packageName)
                if (start != null) {
                    val duration = event.timeStamp - start
                    appUsageMap[packageName] = appUsageMap.getOrDefault(packageName, 0L) + duration
                }
            }

            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                val now = event.timeStamp
                openApps.forEach { (pkg, start) ->
                    appUsageMap[pkg] = appUsageMap.getOrDefault(pkg, 0L) + (now - start)
                }
                openApps.clear()
                screenOnTime = null
            }

            UsageEvents.Event.SCREEN_INTERACTIVE -> {
                // Wir merken uns NUR den Zeitpunkt. Wir tragen die App noch nicht als "laufend" ein!
                // Wir warten ab, was das nächste Event sagt.
                screenOnTime = event.timeStamp
            }
        }
    }

    // Am Ende der Schleife: Falls der Bildschirm an ging und DANACH kein Event mehr kam,
    // lief die letzte App tatsächlich weiter.
    if (screenOnTime != null && lastActivePackageName != null) {
        openApps[lastActivePackageName] = screenOnTime!!
    }

    // Abrechnung bis JETZT
    val now = System.currentTimeMillis()
    openApps.forEach { (pkg, start) ->
        appUsageMap[pkg] = appUsageMap.getOrDefault(pkg, 0L) + (now - start)
    }

    val pm = context.packageManager
    appUsageMap.map { (pkg, ms) ->
        val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
        AppUsageInfo(name, ms)
    }.sortedByDescending { it.usageMs }
}

suspend fun getTotalScreenTime(context: Context): Long = withContext(Dispatchers.IO) {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val events = usm.queryEvents(calendar.timeInMillis, System.currentTimeMillis())
    val event = UsageEvents.Event()

    var totalTime = 0L
    var sessionStartTime = 0L
    val openApps = mutableSetOf<String>()

    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        val packageName = event.packageName

        when (event.eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                if (openApps.isEmpty()) {
                    sessionStartTime = event.timeStamp
                }
                openApps.add(packageName)
            }

            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                openApps.remove(packageName)
                if (openApps.isEmpty() && sessionStartTime != 0L) {
                    totalTime += (event.timeStamp - sessionStartTime)
                    sessionStartTime = 0L
                }
            }

            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                if (sessionStartTime != 0L) {
                    totalTime += (event.timeStamp - sessionStartTime)
                    sessionStartTime = 0L
                }
                openApps.clear()
            }
        }
    }

    if (sessionStartTime != 0L) {
        totalTime += (System.currentTimeMillis() - sessionStartTime)
    }

    // Das Ergebnis wird automatisch zurückgegeben
    return@withContext totalTime
}