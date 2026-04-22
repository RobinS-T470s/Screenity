package de.schanbro.screenity

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "0.0.x-preview"
        }
    }

    ScreenWrapper(title = "About") {
        // Box mit fillMaxSize sorgt dafür, dass wir auf Tablets zentrieren können
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState), // Scrollbar falls der Content auf kleinen Handys zu lang ist
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 600.dp), // WICHTIG: Begrenzt die Breite auf Tablets für bessere Lesbarkeit
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sektion 1: Logo und Name in einer Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.screenity_edited_foreground),
                            contentDescription = "App Logo",
                            modifier = Modifier.size(240.dp), // Feste Größe für das Icon in der Card
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        )

                        Text(
                            text = "Screenity",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Version $versionName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sektion 2: Informationen
                Text(
                    text = "Developed with ❤️ by",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )

                Text(
                    text = "Robin Schanbacher",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Sektion 3: GitHub Link als "Action"
                Surface(
                    onClick = { uriHandler.openUri("https://github.com/RobinS-T470s/Screenity") },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "View on GitHub",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Text(
                    text = "github.com/RobinS-T470s/Screenity",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                    textDecoration = TextDecoration.Underline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Footer
                Text(
                    text = "© 2026 Screenity Project",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "About Screen Vorschau")
@Composable
fun AboutScreenPreview() {
    AboutScreen(onBack = {})
}