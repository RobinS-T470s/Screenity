package de.schanbro.screenity


import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ScreenityPrefs", Context.MODE_PRIVATE) }
    val serverUrl = prefs.getString("server_url", "") ?: ""
    val userID = prefs.getString("userid", "") ?: ""
    val password = prefs.getString("password", "") ?: ""

    var summary by remember { mutableStateOf<ServerSummary?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // 1. STATE FÜR DAS DATUM
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val displayFormatter = remember { SimpleDateFormat("EEE, dd. MMM", Locale.getDefault()) }

    val selectedDateStr = dateFormatter.format(selectedCalendar.time)

    // Hilfsfunktion zum Wechseln der Tage
    fun changeDate(amount: Int) {
        val newCal = selectedCalendar.clone() as Calendar
        newCal.add(Calendar.DAY_OF_YEAR, amount)
        selectedCalendar = newCal
    }

    fun fetch() {
        if (serverUrl.isBlank()) return
        scope.launch {
            isLoading = true
            try {
                summary = getSummaryFromServer(serverUrl, userID, password)
            } catch (e: Exception) {}
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
        // 2. DATUMS-NAVIGATION (Buttons & Text)
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { changeDate(-1) }) {
                    Icon(Icons.Default.ChevronLeft, "Vorheriger Tag")
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { /* Hier könnte man einen DatePicker öffnen */ }
                ) {
                    Text(
                        text = if (dateFormatter.format(Date()) == selectedDateStr) "Heute" else displayFormatter.format(selectedCalendar.time),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(selectedDateStr, style = MaterialTheme.typography.bodySmall)
                }

                IconButton(onClick = { changeDate(1) }) {
                    Icon(Icons.Default.ChevronRight, "Nächster Tag")
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            summary?.let { s ->
                // 3. HORIZONTALES WISCHEN ERMÖGLICHEN
                // Wir nutzen einen PointerInput für einfache Swipes
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                if (dragAmount > 50) { // Swipe nach Rechts -> Gestern
                                    changeDate(-1)
                                    change.consume()
                                } else if (dragAmount < -50) { // Swipe nach Links -> Morgen
                                    changeDate(1)
                                    change.consume()
                                }
                            }
                        }
                ) {
                    // Daten für das ausgewählte Datum berechnen
                    val dayTotalMs = remember(s, selectedDateStr) {
                        s.devices.sumOf { it.reports?.get(selectedDateStr)?.total_screen_time_ms ?: 0L }
                    }

                    TotalAllDevicesCard(dayTotalMs)

                    val hourlyData = remember(s, selectedDateStr) {
                        val events = mutableListOf<ScreenEvent>()
                        s.devices.forEach { device ->
                            device.reports?.get(selectedDateStr)?.detailed_events?.let { events.addAll(it) }
                        }
                        calculateHourlyUsage(events)
                    }

                    // --- CHART DISPLAY ---
                    Text(
                        text = "Stündlicher Verlauf",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        if (hourlyData.isNotEmpty()) {
                            HourlyLineChart(
                                dataPoints = hourlyData,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            Text(
                                "Keine Daten für diesen Tag",
                                modifier = Modifier.padding(32.dp).align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }
}