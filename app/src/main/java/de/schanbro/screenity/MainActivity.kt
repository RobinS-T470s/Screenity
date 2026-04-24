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

sealed class Screen(val route: String) {
    object Today : Screen("today_screen")
    object Summary : Screen("summary_screen")

    object Local : Screen("local_screen")
    object Devices : Screen("devices_screen")
    object Settings : Screen("settings_screen")
    object DeviceDetail : Screen("device_detail/{deviceId}")
    object About : Screen("about_screen")
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
                                HorizontalDivider()

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
                            modifier = Modifier.fillMaxSize(),
                            topBar = {
                                TopAppBar(
                                    title = {
                                        val title = when (currentRoute) {
                                            Screen.Today.route -> stringResource(R.string.Today)
                                            Screen.Summary.route -> "Summary"
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
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        titleContentColor = MaterialTheme.colorScheme.primary
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
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupScreen(onUrlSaved: (String) -> Unit) {
    var urlInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.welcome)+"!", style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.pls_enter_url))

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text(stringResource(R.string.ex_server_url)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("http://...") }
        )
        Text(
            text = stringResource(R.string.pls_enter_url)
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { if (urlInput.isNotBlank()) onUrlSaved(urlInput) },
            modifier = Modifier.fillMaxWidth(),
            enabled = urlInput.startsWith("http") // Kleine Sicherheitssperre
        ) {
            Text(stringResource(R.string.connect_and_start))
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