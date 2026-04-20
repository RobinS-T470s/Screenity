package de.schanbro.screenity

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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
fun DevicesScreen(onNavigateToDetail: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ScreenityPrefs", Context.MODE_PRIVATE) }
    val serverUrl = prefs.getString("server_url", "") ?: ""
    val userID = prefs.getString("userid", "") ?: ""
    val password = prefs.getString("password", "") ?: ""

    var devicesList by remember { mutableStateOf(emptyList<DeviceSummary>()) }
    var isLoading by remember { mutableStateOf(false) }

    fun fetch() {
        if (serverUrl.isBlank()) return
        scope.launch {
            isLoading = true
            try {
                val result = getSummaryFromServer(serverUrl, userID, password)
                // Hier greifen wir auf die Liste der Geräte zu:
                val fetchedDevices = result.devices
                devicesList = fetchedDevices

                // --- AB HIER NEU: Echte Geräte für das Widget sichern ---
                // Wir ziehen uns alle Gerätenamen aus der Liste heraus
                val deviceNames = fetchedDevices.map { it.device_name }.toSet()

                // Wir speichern das Set in den SharedPreferences ab
                prefs.edit().putStringSet("known_device_ids", deviceNames).apply()
                // --- ENDE NEU ---

            } catch (e: Exception) {
                // Fehlerbehandlung (z.B. Loggen oder Toast)
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { fetch() }

    ScreenWrapper(
        title = stringResource(R.string.devices_overview),
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = devicesList,
                    key = { it.device_id }
                ) { device ->
                    DeviceUsageRow(
                        device = device,
                        onClick = { onNavigateToDetail(device.device_id) }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceUsageRow(device: DeviceSummary, onClick: () -> Unit) {
    val h = device.today_ms / 3600000
    val m = (device.today_ms % 3600000) / 60000

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.device_name, style = MaterialTheme.typography.titleMedium)
                Text("ID: ${device.device_id.take(8)}...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = String.format("%dh %02dm", h, m),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}