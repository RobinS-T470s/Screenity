package de.schanbro.screenity

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScreentimeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val prefs = context.getSharedPreferences("ScreenityPrefs", Context.MODE_PRIVATE)

        // 1. Welche Geräte hat der Nutzer in der Config gewählt?
        val selectedDevices = prefs.getStringSet("widget_${appWidgetId}_devices", emptySet()) ?: emptySet()
        val serverUrl = prefs.getString("server_url", "") ?: ""

        var totalMs = 0L

        // 2. ECHTE DATEN VOM SERVER HOLEN
        if (serverUrl.isNotBlank()) {
            try {
                val result = withContext(Dispatchers.IO) {
                    getSummaryFromServer(serverUrl)
                }

                // HIER IST DIE KORREKTUR: Wir greifen erst auf .devices zu!
                val allDevices = result.devices

                // Jetzt können wir filtern
                val filteredDevices = allDevices.filter { it.device_id in selectedDevices || it.device_name in selectedDevices }
                totalMs = filteredDevices.sumOf { it.today_ms }

            } catch (e: Exception) {
                // Bei Netzwerkfehlern bleibt totalMs einfach auf 0 oder du nimmst einen Cache
            }
        }

        // Zeit in Stunden und Minuten umrechnen
        val hours = totalMs / 3600000
        val minutes = (totalMs % 3600000) / 60000
        val totalTimeText = String.format("%02dh %02dm", hours, minutes)

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Gesamtzeit",
                    style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(Color.Black))
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = totalTimeText,
                    style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(Color.Blue))
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = "${selectedDevices.size} Geräte",
                    style = TextStyle(color = ColorProvider(Color.Gray))
                )
            }
        }
    }
}

class ScreentimeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScreentimeWidget()
}