package de.schanbro.screenity

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WidgetConfigScreen(appWidgetId = appWidgetId) {
                        val resultValue = Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        setResult(Activity.RESULT_OK, resultValue)
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
fun WidgetConfigScreen(appWidgetId: Int, onFinished: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ScreenityPrefs", Context.MODE_PRIVATE) }

    // ECHTE DATEN: Wir lesen die bekannten Geräte-IDs aus den SharedPreferences
    // (Diese Liste füllst du am besten immer auf, wenn du Daten vom Server erfolgreich abrufst)
    val deviceIds = remember {
        prefs.getStringSet("known_device_ids", setOf("Dieses Gerät")) ?: setOf("Dieses Gerät")
    }.toList()

    val selectedDevices = remember { mutableStateListOf<String>() }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Widget konfigurieren", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Wähle die Geräte aus, deren Zeit addiert werden soll:", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(16.dp))

        // LazyColumn für den Fall, dass du sehr viele Geräte hast
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(deviceIds) { deviceId ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(deviceId, style = MaterialTheme.typography.bodyLarge)
                    Checkbox(
                        checked = selectedDevices.contains(deviceId),
                        onCheckedChange = { checked ->
                            if (checked) selectedDevices.add(deviceId) else selectedDevices.remove(deviceId)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                // Konfiguration für dieses spezifische Widget speichern
                prefs.edit().putStringSet("widget_${appWidgetId}_devices", selectedDevices.toSet()).apply()
                onFinished()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedDevices.isNotEmpty()
        ) {
            Text("Widget erstellen")
        }
    }
}