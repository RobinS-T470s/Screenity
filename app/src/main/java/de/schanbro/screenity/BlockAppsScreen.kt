package de.schanbro.screenity

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun BlockAppsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ScreenityPrefs", Context.MODE_PRIVATE) }

    var usageList by remember { mutableStateOf<List<AppUsageInfo>>(emptyList()) }

    // DIESE ZEILE FEHLT WAHRSCHEINLICH:
    // Eine SnapshotStateMap, die Compose sagt: "Wenn sich hier was ändert, zeichne neu!"
    val limitsMap = remember { mutableStateMapOf<String, Int>() }

    fun load() {
        scope.launch {
            val list = getTodayUsageEvents(context)
            // Limits initial aus den SharedPreferences in die Map laden
            list.forEach { app ->
                val savedLimit = prefs.getInt("limit_${app.packageName}", 0)
                limitsMap[app.packageName] = savedLimit
            }
            usageList = list
        }
    }

    LaunchedEffect(Unit) {
        load()
    }

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ScreenWrapper(title = stringResource(R.string.this_device)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // WICHTIG: Nutze 'key', damit die Map-Zuweisung stabil bleibt
                    items(usageList, key = { it.packageName }) { app ->

                        // Hier wird auf die limitsMap zugegriffen
                        val currentLimit = limitsMap[app.packageName] ?: 0

                        AppRow(
                            app = app,
                            initialLimit = currentLimit,
                            onLimitChange = { newLimit ->
                                // 1. UI sofort aktualisieren
                                limitsMap[app.packageName] = newLimit
                                // 2. Permanent speichern für den Hintergrund-Service
                                prefs.edit().putInt("limit_${app.packageName}", newLimit).apply()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppRow(
    app: AppUsageInfo,
    initialLimit: Int,
    onLimitChange: (Int) -> Unit
) {
    val usedMin = (app.usageMs / 60000).toInt()
    // Sperre visuell anzeigen, wenn Limit > 0 und verbraucht >= limit
    val isOverLimit = initialLimit in 1..usedMin

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverLimit) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Genutzt: $usedMin Min", style = MaterialTheme.typography.labelSmall)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (initialLimit >= 5) onLimitChange(initialLimit - 5)
                    else if (initialLimit > 0) onLimitChange(0)
                }) {
                    Text("-", style = MaterialTheme.typography.headlineSmall)
                }

                Text(
                    text = if (initialLimit == 0) "Aus" else "$initialLimit",
                    modifier = Modifier.widthIn(min = 45.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                IconButton(onClick = {
                    onLimitChange(initialLimit + 5)
                }) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}