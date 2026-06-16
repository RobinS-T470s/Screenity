package de.schanbro.screenity

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.StackedLineChart
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState
import de.schanbro.screenity.ui.theme.ScreenityTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.app.AppOpsManager
import androidx.compose.material.icons.filled.Block
import androidx.lifecycle.Lifecycle

sealed class Screen(val route: String) {
    object Today : Screen("today_screen")
    object Summary : Screen("summary_screen")

    object Local : Screen("local_screen")
    object Devices : Screen("devices_screen")
    object Settings : Screen("settings_screen")
    object DeviceDetail : Screen("device_detail/{deviceId}")
    object About : Screen("about_screen")
    object BlockApps : Screen("block_apps_screen")
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScreenityTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                val prefs = remember { context.getSharedPreferences("ScreenityPrefs", android.content.Context.MODE_PRIVATE) }
                var serverUrl by remember {
                    mutableStateOf(prefs.getString("server_url", "") ?: "")
                }

                val savedInterval = prefs.getInt("upload_interval_mins", 15)

                if (serverUrl.isBlank()) {
                    // Zeige Setup, wenn keine URL da ist
                    SetupScreen(onUrlSaved = { neueUrl ->
                        prefs.edit().putString("server_url", neueUrl).apply()
                        serverUrl = neueUrl // State-Update triggert Re-Composition
                    })
                } else {
                    LaunchedEffect(serverUrl) {
                        if (serverUrl.isNotBlank()) {
                            // 1. SOFORTIGER UPLOAD BEIM APP-START
                            val totalMs = getTotalScreenTime(context)
                            val usageList = getTodayUsageEvents(context)
                            val detailedEvents = getDetailedEvents(applicationContext)
                            sendDataToServer(context, serverUrl, totalMs, usageList, detailedEvents)

                            // 2. 15-MINUTEN-AUTOMATISIERUNG EINRICHTEN
                            val constraints = Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED) // Nur wenn Internet da ist
                                .build()

                            val uploadWorkRequest = PeriodicWorkRequestBuilder<UploadWorker>(
                                15, TimeUnit.MINUTES // Alle 15 Minuten (Minimum bei Android!)
                            )
                                .setConstraints(constraints)
                                .build()

                            // Dem System übergeben
                            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                                "ScreentimeUploadWork",
                                ExistingPeriodicWorkPolicy.KEEP, // Behält den alten Timer, falls die App neu startet
                                uploadWorkRequest
                            )
                        }
                    }

                    val navController = rememberNavController()
                    val currentBackStack by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStack?.destination?.route

                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed) // State für das Menü
                    val scope = rememberCoroutineScope() // Um das Menü per Code zu öffnen (Suspend Function)
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet {
                                Text(stringResource(R.string.app_name), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium)
                                //HorizontalDivider()

                                NavigationDrawerItem(
                                    label = { Text("Block Apps") },
                                    selected = currentRoute == Screen.BlockApps.route,
                                    icon = { Icon(Icons.Default.Block, contentDescription = null) },
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate(Screen.BlockApps.route)
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                NavigationDrawerItem(
                                    label = { Text(stringResource(R.string.about)) },
                                    selected = currentRoute == Screen.About.route,
                                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate(Screen.About.route)
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                NavigationDrawerItem(
                                    label = { Text(stringResource(R.string.Settings)) },
                                    selected = currentRoute == Screen.Settings.route,
                                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate(Screen.Settings.route)
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                // Hier kannst du weitere Menüpunkte hinzufügen
                            }
                        }
                    ) {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = {
                                        val title = when (currentRoute) {
                                            Screen.Today.route -> stringResource(R.string.Today)
                                            Screen.Summary.route -> stringResource(R.string.Summary)
                                            Screen.Devices.route -> stringResource(R.string.Devices)
                                            Screen.Local.route -> stringResource(R.string.Local)
                                            Screen.Settings.route -> stringResource(R.string.Settings)
                                            Screen.About.route -> stringResource(R.string.about)
                                            // Für Detail-Screens mit Argumenten:
                                            else -> if (currentRoute?.contains("device_detail") == true) stringResource(R.string.devices_overview) else stringResource(R.string.app_name)
                                        }
                                        Text(title)
                                    },
                                    navigationIcon = {
                                        // Das Hamburger-Icon zum Öffnen des Menüs
                                        IconButton(onClick = {
                                            scope.launch { drawerState.open() }
                                        }) {
                                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        titleContentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            },
                            bottomBar = {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = currentRoute == Screen.Today.route,
                                        onClick = { navController.navigate(Screen.Today.route) },
                                        icon = {
                                            Icon(
                                                Icons.Default.Today,
                                                contentDescription = null
                                            )
                                        },
                                        label = { Text(stringResource(R.string.Today)) }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == Screen.Summary.route,
                                        onClick = { navController.navigate(Screen.Summary.route) },
                                        icon = {
                                            Icon(
                                                Icons.Default.StackedLineChart,
                                                contentDescription = null
                                            )
                                        },
                                        label = { Text(stringResource(R.string.Summary)) }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == Screen.Local.route,
                                       onClick = { navController.navigate(Screen.Local.route) },
                                        icon = { Icon(Icons.Default.PinDrop, contentDescription = null) },
                                        label = { Text(stringResource(R.string.Local)) }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == Screen.Devices.route,
                                        onClick = { navController.navigate(Screen.Devices.route) },
                                        icon = {
                                            Icon(
                                                Icons.Default.Devices,
                                                contentDescription = null
                                            )
                                        },
                                        label = { Text(stringResource(R.string.Devices)) }
                                    )
                                    //NavigationBarItem(
                                    //    selected = currentRoute == Screen.Settings.route,
                                    //    onClick = { navController.navigate(Screen.Settings.route) },
                                    //    icon = {
                                    //        Icon(
                                    //            Icons.Default.Settings,
                                    //            contentDescription = null
                                    //        )
                                    //    },
                                    //    label = { Text(stringResource(R.string.Settings)) }
                                    //)
                                }
                            }
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = de.schanbro.screenity.Screen.Today.route,
                                modifier = Modifier.padding(innerPadding),
                                enterTransition = {
                                    fadeIn(animationSpec = tween(220)) + scaleIn(
                                        initialScale = 0.92f
                                    )
                                },
                                exitTransition = { fadeOut(animationSpec = tween(90)) },
                                popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                                popExitTransition = {
                                    fadeOut(animationSpec = tween(90)) + scaleOut(
                                        targetScale = 0.92f
                                    )
                                }
                            ) {
                                composable(de.schanbro.screenity.Screen.Today.route) {
                                    TodayScreen()
                                }
                                composable(de.schanbro.screenity.Screen.Summary.route) {
                                    SummaryScreen()
                                }
                                composable(de.schanbro.screenity.Screen.Local.route) {
                                    LocalScreen()
                                }
                                composable(de.schanbro.screenity.Screen.Devices.route) {
                                    DevicesScreen(onNavigateToDetail = { id ->
                                        navController.navigate("device_detail/$id")
                                    })
                                }
                                composable(de.schanbro.screenity.Screen.Settings.route) {
                                    SettingsScreen(
                                        onNavigateToVersion = { navController.navigate(Screen.About.route) }
                                    )
                                }
                                composable(Screen.DeviceDetail.route) { backStackEntry ->
                                    val deviceId =
                                        backStackEntry.arguments?.getString("deviceId") ?: ""
                                    DeviceDetailScreen(
                                        deviceId = deviceId,
                                        onBack = { navController.popBackStack() } // Geht zurück zum TodayScreen
                                    )
                                }
                                composable(de.schanbro.screenity.Screen.About.route) {
                                    AboutScreen(onBack = { navController.popBackStack() })
                                }
                                composable(de.schanbro.screenity.Screen.BlockApps.route) {
                                    BlockAppsScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 1. Prüft den Nutzungsdatenzugriff (Screentime)
fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

// 2. Prüft die Barrierefreiheit (Sperr-Dienst)
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = "${context.packageName}/${AppBlockService::class.java.canonicalName}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    if (enabledServices.isNullOrEmpty()) return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServices)
    while (colonSplitter.hasNext()) {
        val componentName = colonSplitter.next()
        if (componentName.equals(expectedComponentName, ignoreCase = true)) {
            return true
        }
    }
    return false
}
@Composable
fun SetupScreen(
    context: Context = LocalContext.current,
    onUrlSaved: (String) -> Unit
) {
    var urlInput by remember { mutableStateOf("") }

    // States für die UI
    var hasUsageAccess by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var hasAccessibility by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // Lifecycle Observer: Dies ist der "Magie"-Teil
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Sobald der Nutzer die Android-Einstellungen verlässt und zurückkommt:
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = checkUsageStatsPermission(context)
                hasAccessibility = isAccessibilityServiceEnabled(context)
                hasOverlay = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background) // Hintergrundfarbe für Kontrast
            .padding(24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = stringResource(R.string.welcome) + "!",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Konfiguriere Screenity, um dein digitales Wohlbefinden zu steigern.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        // SEKTION 1: Server-Konfiguration
        Text("1. Server-Verbindung", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("https://dein-server.de") },
                    singleLine = true
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // SEKTION 2: Berechtigungen
        Text("2. Berechtigungen", style = MaterialTheme.typography.titleMedium)
        Text("Tippe auf die Kacheln, um die Einstellungen zu öffnen.", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(12.dp))

        PermissionItem(
            title = "Nutzungsdaten",
            description = "Erlaubt die Messung der App-Nutzung.",
            isGranted = hasUsageAccess,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )

        PermissionItem(
            title = "Barrierefreiheit",
            description = "Ermöglicht das effektive Sperren von Apps.",
            isGranted = hasAccessibility,
            onClick = {
                // Ein kleiner Hinweis per Toast hilft dem Nutzer, den Dienst zu finden
                android.widget.Toast.makeText(context, "Suche 'Screenity' unter 'Installierte Apps'", android.widget.Toast.LENGTH_LONG).show()
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )

        PermissionItem(
            title = "Über anderen Apps",
            description = "Wird für den Sperrbildschirm benötigt.",
            isGranted = hasOverlay,
            onClick = {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }
        )

        Spacer(Modifier.height(40.dp))

        // START BUTTON
        Button(
            onClick = { if (urlInput.isNotBlank()) onUrlSaved(urlInput) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            // Der Button ist nur aktiv, wenn URL da ist UND die wichtigsten Rechte gewährt wurden
            enabled = urlInput.startsWith("http") && hasAccessibility && hasUsageAccess,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Jetzt starten", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        if (!(hasAccessibility && hasUsageAccess)) {
            Text(
                text = "Bitte erteile erst die nötigen Berechtigungen.",
                modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { if (!isGranted) onClick() }
            .background(if (isGranted) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (isGranted) Color(0xFF4CAF50) else Color.LightGray, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }

        if (isGranted) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Erteilt", tint = Color(0xFF4CAF50))
        } else {
            Icon(Icons.Default.ArrowForward, contentDescription = "Erteilen")
        }
    }
}

fun updateUploadWork(context: Context, intervalMinutes: Long) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val uploadWorkRequest = PeriodicWorkRequestBuilder<UploadWorker>(
        intervalMinutes, TimeUnit.MINUTES
    )
        .setConstraints(constraints)
        .build()

    // UPDATE steht hier für "REPLACE"
    // Wenn sich das Intervall ändert, bricht Android den alten Timer ab
    // und fängt mit der neuen Zeit von vorne an zu zählen!
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "ScreentimeUploadWork",
        ExistingPeriodicWorkPolicy.REPLACE,
        uploadWorkRequest
    )
}