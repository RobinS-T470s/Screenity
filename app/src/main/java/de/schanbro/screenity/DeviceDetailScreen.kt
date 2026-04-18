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
    var deviceName by remember { mutableStateOf("Gerät") }

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
                android.util.Log.e("SCREENITY", "Löschfehler: ${e.message}")
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
                android.util.Log.e("SCREENITY", "Updatefehler: ${e.message}")
            }
            isProcessing = false
        }
    }

    fun fetch() {
        android.util.Log.d("SCREENITY_CHECK", "1. fetch() gestartet für ID: $deviceId")
        if (serverUrl.isBlank()) return

        scope.launch {
            isLoading = true
            try {
                val client = OkHttpClient()
                val fullUrl = "${serverUrl.trim().removeSuffix("/")}/summary"
                val request = Request.Builder().url(fullUrl).build()

                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

                if (response.isSuccessful) {
                    // WICHTIG: Body nur einmal auslesen und in Variable speichern
                    val responseBodyString = withContext(Dispatchers.IO) { response.body?.string() } ?: ""
                    android.util.Log.d("SCREENITY_CHECK", "2. JSON geladen, Länge: ${responseBodyString.length}")

                    if (responseBodyString.isNotBlank()) {
                        try {
                            val fullSummary = Gson().fromJson(responseBodyString, ServerSummary::class.java)

                            // Gerät in der Liste suchen
                            val device = fullSummary.devices.find { it.device_id.trim() == deviceId.trim() }

                            if (device != null) {
                                deviceName = device.device_name
                                android.util.Log.d("SCREENITY_CHECK", "3. Gerät gefunden: ${device.device_name}")

                                // Report für heute heraussuchen
                                val report = device.reports?.get(todayStr)
                                if (report != null) {
                                    appsFromToday = report.apps ?: emptyList()
                                    android.util.Log.d("SCREENITY_CHECK", "4. Apps für heute gefunden: ${appsFromToday.size}")
                                } else {
                                    android.util.Log.w("SCREENITY_CHECK", "Hinweis: Kein Report für $todayStr vorhanden.")
                                    // Debug: Zeige welche Tage da sind
                                    android.util.Log.d("SCREENITY_CHECK", "Verfügbare Tage: ${device.reports?.keys}")
                                }
                            } else {
                                android.util.Log.e("SCREENITY_CHECK", "Fehler: Gerät mit ID $deviceId nicht in Liste.")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SCREENITY_CHECK", "Parsing-Fehler: ${e.message}")
                            android.util.Log.d("SCREENITY_CHECK", "Fehlerhaftes JSON: $responseBodyString")
                        }
                    }
                } else {
                    android.util.Log.e("SCREENITY_CHECK", "Server Fehler-Code: ${response.code}")
                }
            } catch (e: Exception) {
                android.util.Log.e("SCREENITY_CHECK", "Netzwerk-Fehler: ${e.message}")
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
                Icon(Icons.Default.Remove, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
            }
            // Bearbeiten Button
            IconButton(onClick = {
                editNameText = deviceName // Aktuellen Namen ins Feld laden
                showEditDialog = true
            }) {
                Icon(Icons.Default.Edit, contentDescription = "Bearbeiten")
            }
            // Aktualisieren Button
            IconButton(onClick = { fetch() }, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
            }
        }
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Text(
                    text = "App-Nutzung heute ($todayStr):",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (appsFromToday.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Keine detaillierten Daten für heute gefunden.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(appsFromToday.sortedByDescending { it.usage_ms }) { app ->
                            AppUsageRow(app)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
    // --- DELETE CONFIRMATION DIALOG ---
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Gerät löschen?") },
            text = { Text("Möchtest du '$deviceName' und alle zugehörigen Daten wirklich unwiderruflich löschen?") },
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
                    else Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            }
        )
    }

// --- EDIT NAME DIALOG ---
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Name bearbeiten") },
            text = {
                TextField(
                    value = editNameText,
                    onValueChange = { editNameText = it },
                    label = { Text("Gerätename") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = { updateDeviceName(editNameText) },
                    enabled = !isProcessing && editNameText.isNotBlank() // Verhindert leere Namen
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Abbrechen") }
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

// Erstelle eine extra Funktion ohne Parameter für die Vorschau
@Preview(showBackground = true, name = "Geräte Details Vorschau")
@Composable
fun DeviceDetailPreview() {
    // Hier rufst du deinen Screen mit Test-Daten auf
    DeviceDetailScreen(
        deviceId = "d1be2cb6cbd8ef59",
        onBack = { /* Macht in der Preview nichts */ }
    )
}