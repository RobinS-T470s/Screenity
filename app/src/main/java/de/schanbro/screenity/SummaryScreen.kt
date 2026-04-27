package de.schanbro.screenity


import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
        var showDatePicker by remember { mutableStateOf(false) }
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
                    Icon(Icons.Default.ChevronLeft, stringResource(R.string.previous_day))
                }

                // --- DATEPICKER DIALOG ---
                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = selectedCalendar.timeInMillis
                    )

                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val newCal = Calendar.getInstance().apply {
                                        timeInMillis = millis
                                    }
                                    selectedCalendar = newCal // Aktualisiert den gesamten Screen
                                }
                                showDatePicker = false
                            }) {
                                Text(stringResource(id = android.R.string.ok))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text(stringResource(id = android.R.string.cancel))
                            }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                // --- KLICKBARE DATUMSANZEIGE ---
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { showDatePicker = true } // Öffnet den Dialog
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (dateFormatter.format(Date()) == selectedDateStr)
                            stringResource(R.string.Today)
                        else
                            displayFormatter.format(selectedCalendar.time),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = selectedDateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { changeDate(1) }) {
                    Icon(Icons.Default.ChevronRight, stringResource(R.string.next_day))
                }
            }
        }

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            configuration.screenWidthDp.dp.toPx()
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            summary?.let { s ->
                val startOfDay = remember(selectedCalendar) {
                    (selectedCalendar.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }

// Ende des Tages für den "Handy noch an" Fall
                val endOfSelectedDay = remember(startOfDay) {
                    startOfDay + (24 * 60 * 60 * 1000L) - 1
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .pointerInput(selectedCalendar) { // State-Reset bei Datumswechsel
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDrag += dragAmount
                                },
                                onDragEnd = {
                                    if (totalDrag > 300) changeDate(-1)
                                    else if (totalDrag < -300) changeDate(1)
                                    totalDrag = 0f
                                }
                            )
                        }
                ) {
                    // 2. TIMELINE DATEN VORBEREITEN
                    val timelineEvents = remember(s, selectedDateStr) {
                        val events = mutableListOf<Pair<Long, Long>>()

                        s.devices.forEach { device ->
                            val report = device.reports?.get(selectedDateStr)
                            val rawEvents = report?.detailed_events?.sortedBy { it.timestamp } ?: emptyList()

                            var lastOnTimestamp: Long? = null

                            rawEvents.forEach { ev ->
                                when (ev.type) {
                                    "SCREEN_ON" -> {
                                        if (lastOnTimestamp == null) lastOnTimestamp = ev.timestamp
                                    }
                                    "SCREEN_OFF" -> {
                                        if (lastOnTimestamp != null) {
                                            events.add(lastOnTimestamp!! to ev.timestamp)
                                            lastOnTimestamp = null
                                        } else {
                                            // Handy war seit Mitternacht AN
                                            events.add(startOfDay to ev.timestamp)
                                        }
                                    }
                                }
                            }

                            // Sonderfall: Handy ist am Ende des gewählten Tages noch an
                            if (lastOnTimestamp != null) {
                                // Wenn heute ausgewählt ist -> bis jetzt. Wenn Vergangenheit -> bis 23:59.
                                val isToday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) == selectedDateStr
                                val endTime = if (isToday) System.currentTimeMillis() else endOfSelectedDay
                                events.add(lastOnTimestamp!! to endTime)
                            }
                        }
                        events.sortBy { it.first }
                        events
                    }

                    // 3. ANZEIGE DER TIMELINE
                    if (timelineEvents.isNotEmpty()) {
                        ActivityTimelineChart(
                            events = timelineEvents,
                            // Wichtig: Die Chart braucht auch das startOfDay des gewählten Tages!
                            // Falls du ActivityTimelineChart angepasst hast, übergib es hier.
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        // Optional: Ein Platzhalter, damit das UI nicht springt
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp).height(100.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = 0.5f
                                )
                            )
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Keine Aktivitäts-Details",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}