package de.schanbro.screenity

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas

// Das ist wie eine CSS-Klasse für die Screen-Struktur!
@Composable
fun ScreenWrapper(
    title: String,
    headerAction: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp) // Zentrales Padding für die gesamte App
    ) {
        // Einheitlicher Header für alle Screens
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically // Besser zentriert
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f), // NIMMT RESTLICHEN PLATZ EIN
                maxLines = 1, // Optional: Verhindert zweite Zeile
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis // Macht "..."
            )

            // Die Icons in eine eigene Row packen, damit sie beieinander bleiben
            Row(verticalAlignment = Alignment.CenterVertically) {
                headerAction?.invoke()
            }
        }

        Spacer(Modifier.height(16.dp))

        // Hier wird der eigentliche Inhalt des Screens eingesetzt
        content()
    }
}

@Composable
fun ScreenTimeChart(
    dataPoints: List<Pair<String, Long>>, // Label (z.B. Datum oder Stunde) zu Millisekunden
    modifier: Modifier = Modifier
) {
    val maxUsage = dataPoints.maxOfOrNull { it.second } ?: 1L
    val color = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 8.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val barWidth = canvasWidth / (dataPoints.size * 1.5f)
            val spacing = (canvasWidth - (barWidth * dataPoints.size)) / (dataPoints.size + 1)

            dataPoints.forEachIndexed { index, point ->
                val barHeight = (point.second.toFloat() / maxUsage) * (canvasHeight - 40f)
                val x = spacing + index * (barWidth + spacing)
                val y = canvasHeight - barHeight - 40f

                // Balken zeichnen
                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                )

                // Label (Tag/Stunde) unter dem Balken
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        this.color = labelColor.hashCode()
                        this.textSize = 30f
                        this.textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText(
                        point.first.takeLast(5), // Nur die letzten 5 Zeichen (z.B. 04-18)
                        x + barWidth / 2,
                        canvasHeight - 5f,
                        paint
                    )
                }
            }
        }
    }
}