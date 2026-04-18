package de.schanbro.screenity

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

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
fun WeeklyBarChart(
    dataPoints: List<Pair<String, Long>>,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) return

    // 1. FARBEN HIER OBEN EXTRAHIEREN (im Composable Context)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val maxUsage = dataPoints.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    val scrollState = rememberScrollState()
    val segmentWidth = 70.dp
    val totalWidth = segmentWidth * dataPoints.size

    Box(modifier = modifier.horizontalScroll(scrollState)) {
        Canvas(modifier = Modifier
            .width(totalWidth)
            .height(250.dp)
            .padding(top = 24.dp, bottom = 20.dp)
        ) {
            val canvasHeight = size.height
            val usableHeight = canvasHeight - 80f
            val barWidth = 40.dp.toPx()

            dataPoints.forEachIndexed { index, point ->
                val xCenter = (segmentWidth.toPx() * index) + (segmentWidth.toPx() / 2)
                val barHeight = (point.second.toFloat() / maxUsage) * usableHeight

                // Balken zeichnen (primaryColor nutzen)
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(xCenter - barWidth / 2, canvasHeight - barHeight - 40f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(10f, 10f)
                )

                drawContext.canvas.nativeCanvas.apply {
                    val h = point.second / 3600000
                    val m = (point.second % 3600000) / 60000

                    // 2. DIE VARIABLEN NUTZEN (statt MaterialTheme direkt)
                    drawText("${h}h ${m}m", xCenter, canvasHeight - barHeight - 55f,
                        android.graphics.Paint().apply {
                            color = secondaryColor.hashCode() // Variable nutzen
                            textSize = 26f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )

                    drawText(point.first.takeLast(5), xCenter, canvasHeight,
                        android.graphics.Paint().apply {
                            color = onSurfaceColor.hashCode() // Variable nutzen
                            textSize = 28f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HourlyLineChart(
    dataPoints: List<Pair<String, Long>>,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) return

    // 1. FARBEN HIER OBEN EXTRAHIEREN (im Composable Context)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val maxUsage = dataPoints.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    val scrollState = rememberScrollState()
    val segmentWidth = 60.dp
    val totalWidth = segmentWidth * dataPoints.size

    Box(modifier = modifier.horizontalScroll(scrollState)) {
        Canvas(modifier = Modifier
            .width(totalWidth)
            .height(250.dp)
            .padding(top = 30.dp, bottom = 20.dp, start = 30.dp, end = 30.dp)
        ) {
            val canvasHeight = size.height
            val usableHeight = canvasHeight - 80f

            val path = Path()
            val points = mutableListOf<Offset>()

            // 1. Koordinaten berechnen
            dataPoints.forEachIndexed { index, point ->
                val x = (segmentWidth.toPx() * index)
                val y = canvasHeight - ((point.second.toFloat() / maxUsage) * usableHeight) - 40f
                points.add(Offset(x, y))

                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            // 2. Linie zeichnen
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 6f, cap = StrokeCap.Round)
            )

            // 3. Punkte und Labels zeichnen
            points.forEachIndexed { index, offset ->
                drawCircle(color = primaryColor, radius = 8f, center = offset)

                drawContext.canvas.nativeCanvas.apply {
                    // Uhrzeit (z.B. 14:00)
                    drawText(dataPoints[index].first, offset.x, canvasHeight,
                        android.graphics.Paint().apply {
                            color = onSurfaceColor.hashCode()
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                    // Dauer (z.B. 45m)
                    val m = dataPoints[index].second / 60000
                    if (m > 0) {
                        drawText("${m}m", offset.x, offset.y - 20f,
                            android.graphics.Paint().apply {
                                color = secondaryColor.hashCode()
                                textSize = 22f
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenTimeChart(
    dataPoints: List<Pair<String, Long>>,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) return

    // 1. FARBEN HIER OBEN EXTRAHIEREN (im Composable Context)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val maxUsage = dataPoints.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    val scrollState = rememberScrollState()
    val segmentWidth = 70.dp
    val totalWidth = segmentWidth * dataPoints.size

    Box(modifier = modifier.horizontalScroll(scrollState)) {
        Canvas(modifier = Modifier
            .width(totalWidth)
            .height(250.dp)
            .padding(top = 24.dp, bottom = 20.dp)
        ) {
            val canvasHeight = size.height
            val usableHeight = canvasHeight - 80f
            val barWidth = 40.dp.toPx()

            dataPoints.forEachIndexed { index, point ->
                val xCenter = (segmentWidth.toPx() * index) + (segmentWidth.toPx() / 2)
                val barHeight = (point.second.toFloat() / maxUsage) * usableHeight

                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(xCenter - barWidth / 2, canvasHeight - barHeight - 40f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(12f, 12f)
                )

                drawContext.canvas.nativeCanvas.apply {
                    val h = point.second / 3600000
                    val m = (point.second % 3600000) / 60000
                    val timeText = if (h > 0) "${h}h ${m}m" else "${m}m"

                    // Zeit oben
                    drawText(timeText, xCenter, canvasHeight - barHeight - 55f,
                        android.graphics.Paint().apply {
                            color = secondaryColor.hashCode()
                            textSize = 26f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                    )
                    // Label unten
                    drawText(point.first.takeLast(5), xCenter, canvasHeight,
                        android.graphics.Paint().apply {
                            color = onSurfaceColor.hashCode()
                            textSize = 28f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChartsPreview() {
    MaterialTheme {
        Column(Modifier.padding(16.dp)) {
            Text("Wochenübersicht (Balken)", style = MaterialTheme.typography.titleMedium)
            WeeklyBarChart(
                dataPoints = listOf("Mo" to 5000000L, "Di" to 3000000L, "Mi" to 7000000L,
                    "Do" to 2000000L, "Fr" to 8000000L, "Sa" to 4000000L, "So" to 9000000L)
            )

            Spacer(Modifier.height(32.dp))

            Text("Tagesverlauf (Linie)", style = MaterialTheme.typography.titleMedium)
            HourlyLineChart(
                dataPoints = (0..23).map { String.format("%02d:00", it) to (0..3600000).random().toLong() }
            )
        }
    }
}