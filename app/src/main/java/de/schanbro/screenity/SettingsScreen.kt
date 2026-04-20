package de.schanbro.screenity

//import de.schanbro.screenity.BuildConfig

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.schanbro.screenity.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// 1. ConnectionState erweitert, um Fehlermeldungen direkt mitzuführen
sealed class ConnectionState {
    object Idle : ConnectionState()
    object Loading : ConnectionState()
    object Success : ConnectionState()
    data class Error(val message: String) : ConnectionState() // Fehler trägt seine Nachricht selbst!
}

@Composable
@Preview
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ScreenityPrefs", Context.MODE_PRIVATE) }

    var urlInput by remember { mutableStateOf(prefs.getString("server_url", "") ?: "") }
    var userID by remember { mutableStateOf(prefs.getString("userid", "") ?: "")}
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }

    var passwordVisible by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<ConnectionState>(ConnectionState.Idle) }

    // Intervall auslesen (Standard: 15 Minuten)
    var selectedInterval by remember { mutableIntStateOf(prefs.getInt("upload_interval_mins", 15)) }

    ScreenWrapper(title = stringResource(R.string.Settings)) {

        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it; status = ConnectionState.Idle },
            label = { Text(stringResource(R.string.server_url)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        // --- USER ID ---
        OutlinedTextField(
            value = userID,
            onValueChange = { userID = it; status = ConnectionState.Idle },
            label = { Text("UserID") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // --- PASSWORT ---
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; status = ConnectionState.Idle },
            label = { Text("Passwort") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = "Passwort Sichtbarkeit")
                }
            }
        )

        Spacer(Modifier.height(24.dp))

        // --- MATERIAL 3 SLIDER ---
        Text(
            text = "Upload-Intervall: $selectedInterval Minuten",
            style = MaterialTheme.typography.titleSmall
        )

        Slider(
            value = selectedInterval.toFloat(),
            onValueChange = {
                selectedInterval = it.roundToInt()
            },
            valueRange = 15f..120f,
            steps = 6
        )

        // Kleine Beschriftung unter dem Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("15 min", style = MaterialTheme.typography.bodySmall)
            Text("120 min", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(32.dp))

        val errorMessage = stringResource(R.string.error)

        Button(
            onClick = {
                // 1. Einstellungen speichern
                prefs.edit()
                    .putString("server_url", urlInput)
                    .putInt("upload_interval_mins", selectedInterval)
                    .putString("userid", userID)
                    .putString("password", password)
                    .apply()

                // 2. WorkManager mit neuem Intervall updaten!
                updateUploadWork(context, selectedInterval.toLong())

                scope.launch {
                    status = ConnectionState.Loading
                    try {
                        getSummaryFromServer(urlInput, userID, password)
                        status = ConnectionState.Success
                    } catch (e: Exception) {
                        val detail = e.localizedMessage ?: errorMessage
                        status = ConnectionState.Error(detail)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.save_and_try))
        }

        Spacer(Modifier.height(24.dp))
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        Text(text = "Screenity by Robin Schanbacher")
        Text(text = "${packageInfo.versionName}")

        Spacer(Modifier.height(24.dp))

        ConnectionStatusWidget(state = status)
    }

}

@Composable
fun ConnectionStatusWidget(state: ConnectionState) {
    when (state) {
        is ConnectionState.Loading -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ConnectionState.Success -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, "OK", tint = SuccessGreen)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.connection_successful),
                    color = SuccessGreen,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        is ConnectionState.Error -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, stringResource(R.string.error), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.server_not_reachable),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        else -> {}
    }
}