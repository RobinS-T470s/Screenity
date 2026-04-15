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
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            headerAction?.invoke()
        }

        Spacer(Modifier.height(16.dp))

        // Hier wird der eigentliche Inhalt des Screens eingesetzt
        content()
    }
}