package de.schanbro.screenity

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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