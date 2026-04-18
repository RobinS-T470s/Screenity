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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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


@Composable
fun TodayScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ScreenityPrefs", Context.MODE_PRIVATE) }
    val serverUrl = prefs.getString("server_url", "") ?: ""

    var summary by remember { mutableStateOf<ServerSummary?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun fetch() {
        if (serverUrl.isBlank()) return
        scope.launch {
            isLoading = true
            try {
                summary = getSummaryFromServer(serverUrl)
            } catch (e: Exception) {
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { fetch() }

    ScreenWrapper(
        title = stringResource(R.string.all_time),
        headerAction = {
            IconButton(onClick = { fetch() }) {
                Icon(Icons.Default.Refresh, contentDescription = null)
            }
        }
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            summary?.let {
                // Nutzt die bereits definierte Card für die große Anzeige
                TotalAllDevicesCard(it.today_total_all_devices_ms)
            }
        }
        summary?.let { s ->
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TotalAllDevicesCard(s.today_total_all_devices_ms)

                Text(
                    text = "Nutzung letzte Tage",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )

                // Daten für das Diagramm vorbereiten
                val chartData = remember(s) {
                    // Wir sammeln alle Reports aller Geräte
                    val dailyTotals = mutableMapOf<String, Long>()
                    s.devices.forEach { device ->
                        device.reports?.forEach { (date, report) ->
                            dailyTotals[date] = (dailyTotals[date] ?: 0L) + report.total_screen_time_ms
                        }
                    }
                    // Sortieren nach Datum und die letzten 7 Tage nehmen
                    dailyTotals.toList()
                        .sortedBy { it.first }
                        .takeLast(7)
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (chartData.isNotEmpty()) {
                        ScreenTimeChart(
                            dataPoints = chartData,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Text("Keine Verlaufsdaten vorhanden", Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TotalAllDevicesCard(totalMs: Long) {
    val h = totalMs / 3600000
    val m = (totalMs % 3600000) / 60000
    val s = (totalMs % 60000) / 1000

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.all_time),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiary
            )
            Text(
                text = String.format("%02dh %02dm %02ds", h, m, s),
                style = MaterialTheme.typography.displayMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}