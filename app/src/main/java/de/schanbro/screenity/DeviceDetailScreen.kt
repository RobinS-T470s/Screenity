package de.schanbro.screenity

import androidx.compose.ui.tooling.preview.Preview

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(deviceId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ScreenityPrefs", Context.MODE_PRIVATE) }
    val serverUrl = prefs.getString("server_url", "") ?: ""

    var isLoading by remember { mutableStateOf(true) }
    var appsFromToday by remember { mutableStateOf<List<AppUsageDetail>>(emptyList()) }
    var eventsFromToday by remember { mutableStateOf<List<ScreenEvent>>(emptyList()) } // NEU
    var deviceName by remember { mutableStateOf("...") }

    // Dialog-Steuerung
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editNameText by remember { mutableStateOf(deviceName) }

    // Hilfs-Variable für UI-Feedback
    var isProcessing by remember { mutableStateOf(false) }

    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun deleteDevice() {
        scope.launch {
            isProcessing = true
            try {
                val client = OkHttpClient()
                val url = "${serverUrl.trim().removeSuffix("/")}/device/$deviceId"
                val request = Request.Builder().url(url).delete().build()

                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                if (response.isSuccessful) {
                    onBack() // Nach dem Löschen zurück zur Liste
                }
            } catch (e: Exception) {
            }
            isProcessing = false
        }
    }

    fun updateDeviceName(newName: String) {
        scope.launch {
            isProcessing = true
            try {
                val client = OkHttpClient()
                val url = "${serverUrl.trim().removeSuffix("/")}/device/$deviceId"

                // JSON Body erstellen
                val json = Gson().toJson(mapOf("device_name" to newName))
                val body = okhttp3.RequestBody.create("application/json".toMediaTypeOrNull(), json)
                val request = Request.Builder().url(url).put(body).build()

                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                if (response.isSuccessful) {
                    deviceName = newName // UI lokal aktualisieren
                    showEditDialog = false
                }
            } catch (e: Exception) {
            }
            isProcessing = false
        }
    }

    fun fetch() {
        if (serverUrl.isBlank()) return

        scope.launch {
            isLoading = true
            try {
                val userID = prefs.getString("userid", "") ?: ""
                val password = prefs.getString("password", "") ?: ""

                val client = OkHttpClient()
                val fullUrl = "${serverUrl.trim().removeSuffix("/")}/summary"

                val credential = okhttp3.Credentials.basic(userID, password)

                val request = Request.Builder()
                    .url(fullUrl)
                    .header("Authorization", credential)
                    .build()

                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

                if (response.isSuccessful) {
                    // WICHTIG: Body nur einmal auslesen und in Variable speichern
                    val responseBodyString = withContext(Dispatchers.IO) { response.body?.string() } ?: ""

                    if (responseBodyString.isNotBlank()) {
                        try {
                            val fullSummary = Gson().fromJson(responseBodyString, ServerSummary::class.java)

                            // Gerät in der Liste suchen
                            val device = fullSummary.devices.find { it.device_id.trim() == deviceId.trim() }

                            if (device != null) {
                                deviceName = device.device_name

                                // Report für heute heraussuchen
                                val report = device.reports?.get(todayStr)
                                if (report != null) {
                                    appsFromToday = report.apps ?: emptyList()
                                    eventsFromToday = report.detailed_events ?: emptyList() // NEU: Events speichern
                                } else {
                                }
                            } else {
                            }
                        } catch (e: Exception) {
                        }
                    }
                } else {
                }
            } catch (e: Exception) {
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { fetch() }

    ScreenWrapper(
        title = "Details: $deviceName",
        headerAction = {
            // Löschen Button
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
            }
            // Bearbeiten Button
            IconButton(onClick = {
                editNameText = deviceName // Aktuellen Namen ins Feld laden
                showEditDialog = true
            }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
            }
            // Aktualisieren Button
            IconButton(onClick = { fetch() }, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
            }
        }
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val hourlyData = remember(eventsFromToday) { calculateHourlyUsage(eventsFromToday) }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    //Text(
                    //    "Nutzung über den Tag",
                    //    style = MaterialTheme.typography.titleMedium,
                    //    color = MaterialTheme.colorScheme.primary,
                    //    modifier = Modifier.padding(vertical = 8.dp)
                    //)

                    if (hourlyData.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            // Hier wird das Diagramm aus deiner Charts.kt aufgerufen
                            //ScreenTimeChart(
                            //    dataPoints = hourlyData,
                            //    modifier = Modifier.padding(16.dp)
                            //)
                            HourlyLineChart(
                                dataPoints = hourlyData,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        Text(stringResource(R.string.no_history_data), modifier = Modifier.padding(bottom = 24.dp))
                    }
                }

                item {
                    Text(
                        text = "${stringResource(R.string.todays_app_usage)} ($todayStr):",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                if (appsFromToday.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.no_history_data),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    //LazyColumn(
                    //    modifier = Modifier.fillMaxSize(),
                    //    contentPadding = PaddingValues(bottom = 24.dp)
                    //) {
                    //    items(appsFromToday.sortedByDescending { it.usage_ms }) { app ->
                    //        AppUsageRow(app)
                    //        HorizontalDivider(
                    //            modifier = Modifier.padding(vertical = 4.dp),
                    //            thickness = 0.5.dp
                    //        )
                    //    }
                    //}
                    items(appsFromToday.sortedByDescending { it.usage_ms }) { app ->
                        AppUsageRow(app)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }

            //Text("Nutzung über den Tag", style = MaterialTheme.typography.titleMedium)

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                ScreenTimeChart(
                    dataPoints = hourlyData,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
    // --- DELETE CONFIRMATION DIALOG ---
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_device)) },
            text = { Text(stringResource(R.string.want_to_delete_device, deviceName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deleteDevice()
                    },
                    enabled = !isProcessing, // Button deaktivieren wenn aktiv
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    else Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

// --- EDIT NAME DIALOG ---
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.edit_name)) },
            text = {
                TextField(
                    value = editNameText,
                    onValueChange = { editNameText = it },
                    label = { Text(stringResource(R.string.device_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = { updateDeviceName(editNameText) },
                    enabled = !isProcessing && editNameText.isNotBlank() // Verhindert leere Namen
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun AppUsageRow(app: AppUsageDetail) {
    val h = app.usage_ms / 3600000
    val m = (app.usage_ms % 3600000) / 60000
    val s = (app.usage_ms % 60000) / 1000

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = app.app_name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = if (h > 0) String.format("%dh %02dm", h, m) else String.format("%dm %02ds", m, s),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}