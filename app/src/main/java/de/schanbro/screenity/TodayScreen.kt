package de.schanbro.screenity

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TodayScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ScreenityPrefs", Context.MODE_PRIVATE) }
    val serverUrl = prefs.getString("server_url", "") ?: ""
    val userID = prefs.getString("userid", "") ?: ""
    val password = prefs.getString("password", "") ?: ""

    var summary by remember { mutableStateOf<ServerSummary?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    fun fetch() {
        if (serverUrl.isBlank()) return
        scope.launch {
            isLoading = true
            try {
                summary = getSummaryFromServer(serverUrl, userID, password)
            } catch (e: Exception) {
                // Fehlerbehandlung
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
            summary?.let { s ->
                val startOfDay = remember {
                    Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TotalAllDevicesCard(s.today_total_all_devices_ms)

                    // 1. DATEN FÜR DIE TIMELINE VORBEREITEN
                    val timelineEvents = remember(s) {
                        val events = mutableListOf<Pair<Long, Long>>()

                        s.devices.forEach { device ->
                            val report = device.reports?.get(todayStr)
                            // Wichtig: Die Events MÜSSEN zeitlich sortiert sein für die Logik
                            val rawEvents = report?.detailed_events?.sortedBy { it.timestamp } ?: emptyList()

                            var lastOnTimestamp: Long? = null

                            rawEvents.forEach { ev ->
                                when (ev.type) {
                                    "SCREEN_ON" -> {
                                        // Falls wir schon ein ON hatten (ohne OFF), ignorieren wir das doppelte ON
                                        if (lastOnTimestamp == null) {
                                            lastOnTimestamp = ev.timestamp
                                        }
                                    }
                                    "SCREEN_OFF" -> {
                                        if (lastOnTimestamp != null) {
                                            // Ein vollständiges Paar gefunden
                                            events.add(lastOnTimestamp!! to ev.timestamp)
                                            lastOnTimestamp = null
                                        } else {
                                            /* Sonderfall: Ein OFF ohne vorheriges ON heute.
                                               Das passiert, wenn das Handy seit gestern Abend an war.
                                               Wir starten den Balken dann um 00:00 Uhr heute.
                                            */
                                            events.add(startOfDay to ev.timestamp)
                                        }
                                    }
                                }
                            }

                            /* Sonderfall: Das Handy ist aktuell noch an.
                               Wenn das letzte Event ein SCREEN_ON war, ziehen wir den Balken bis 'jetzt'.
                            */
                            if (lastOnTimestamp != null) {
                                events.add(lastOnTimestamp!! to System.currentTimeMillis())
                            }
                        }

                        // Am Ende alle Balken sortieren, damit der Cursor im Chart nicht springt
                        events.sortBy { it.first }
                        events
                    }

                    // 2. DIE INTERAKTIVE TIMELINE
                    if (timelineEvents.isNotEmpty()) {
                        ActivityTimelineChart(
                            events = timelineEvents,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    // 3. HOURLY LINE CHART
                    val hourlyData = remember(s) {
                        val allTodayEvents = mutableListOf<ScreenEvent>()
                        s.devices.forEach { device ->
                            val report = device.reports?.get(todayStr)
                            report?.detailed_events?.let { allTodayEvents.addAll(it) }
                        }
                        calculateHourlyUsage(allTodayEvents)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        if (hourlyData.isNotEmpty()) {
                            HourlyLineChart(
                                dataPoints = hourlyData,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    // 4. HISTORIE
                    Text(
                        text = stringResource(R.string.history_last_days),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )

                    val chartData = remember(s) {
                        val dailyTotals = mutableMapOf<String, Long>()
                        s.devices.forEach { device ->
                            device.reports?.forEach { (date, report) ->
                                dailyTotals[date] = (dailyTotals[date] ?: 0L) + report.total_screen_time_ms
                            }
                        }
                        dailyTotals.toList().sortedBy { it.first }.takeLast(7)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (chartData.isNotEmpty()) {
                            ScreenTimeChart(dataPoints = chartData, modifier = Modifier.padding(16.dp))
                        }
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
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.all_time),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = String.format("%02dh %02dm %02ds", h, m, s),
                style = MaterialTheme.typography.displayMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}