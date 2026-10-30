package com.example.npi_puntoinfo_etsiit.ui.navigation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.example.npi_puntoinfo_etsiit.GrafoRepository
import com.example.npi_puntoinfo_etsiit.ResultadoVoz
import com.example.npi_puntoinfo_etsiit.sensors.ProximityManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import com.example.npi_puntoinfo_etsiit.data.ClassItem
import com.example.npi_puntoinfo_etsiit.data.ScheduleRepository
import com.example.npi_puntoinfo_etsiit.ui.components.GestureBadge
import com.example.npi_puntoinfo_etsiit.ui.components.GestureGuideButton
import com.example.npi_puntoinfo_etsiit.ui.components.AccelerometerGesturesGuideDialog
import com.example.npi_puntoinfo_etsiit.ui.components.GestureType
import com.example.npi_puntoinfo_etsiit.ui.components.AutomaticOutdoorEntry
import java.time.LocalTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.npi_puntoinfo_etsiit.sensors.AccelerometerManager
import com.example.npi_puntoinfo_etsiit.sensors.CampusLocationManager
import com.example.npi_puntoinfo_etsiit.sensors.DeviceLocationProvider
import com.example.npi_puntoinfo_etsiit.ui.screens.*
import com.example.npi_puntoinfo_etsiit.ui.theme.*
import com.example.npi_puntoinfo_etsiit.outdoor.NavigationJourney
import com.example.npi_puntoinfo_etsiit.outdoor.EtsiitAccess
import com.example.npi_puntoinfo_etsiit.outdoor.OutdoorNavigationRoute
import com.example.npi_puntoinfo_etsiit.outdoor.NavigationTrip
import com.example.npi_puntoinfo_etsiit.outdoor.TripPhase
import com.example.npi_puntoinfo_etsiit.outdoor.OutdoorDestinations
import com.example.npi_puntoinfo_etsiit.outdoor.EtsiitAuxiliaryRoute
import com.example.npi_puntoinfo_etsiit.outdoor.OutdoorEntry
import com.example.npi_puntoinfo_etsiit.outdoor.OutdoorEntryPolicy
import com.example.npi_puntoinfo_etsiit.sensors.GpsLocationReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    guidanceGpsReader: GpsLocationReader? = null,
    guidanceNowMillis: () -> Long = { android.os.SystemClock.elapsedRealtime() }
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var showUnavailableOutdoor by remember { mutableStateOf(false) }
    var manualIndoorOverride by rememberSaveable { mutableStateOf(false) }

    // Estado persistente del destino de guiado activo en toda la sesión de la app
    var activeDestination by rememberSaveable { mutableStateOf<String?>(null) }
    var activeLocation by rememberSaveable { mutableStateOf<String?>(null) }
    var isVerticalGuidanceActive by rememberSaveable { mutableStateOf(false) }
    var activeEntryNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeTargetNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var canReturnOutdoors by rememberSaveable { mutableStateOf(false) }
    var activeTrip by rememberSaveable(stateSaver = listSaver<NavigationTrip?, String>(
        save = { it?.savedValues().orEmpty() },
        restore = { if (it.isEmpty()) null else NavigationTrip.restore(it) }
    )) { mutableStateOf<NavigationTrip?>(null) }

    fun clearHandoff() {
        activeEntryNodeId = null
        activeTargetNodeId = null
        canReturnOutdoors = false
    }

    fun returnToOutdoor() {
        manualIndoorOverride = false
        activeTrip = (activeTrip ?: NavigationTrip.startFixedOutdoor()).selectOutdoorMode()
        clearHandoff()
        isVerticalGuidanceActive = false
        if (!navController.popBackStack(Screen.Outdoor.routePattern, inclusive = false)) {
            activeTrip?.let { trip ->
                navController.navigate(OutdoorNavigationRoute.create(trip.destinationId)) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = false
                    }
                    launchSingleTop = true
                }
            }
        }
    }

    // Diálogo emergente global de Clase Actual (Gesto de agitar 3 veces el terminal)
    var showCurrentClassDialog by remember { mutableStateOf(false) }
    var activeClassForDialog by remember { mutableStateOf<ClassItem?>(null) }

    // Diálogo emergente de Guía de Gestos del Acelerómetro (botón 'i' en cabecera)
    var showGesturesGuideDialog by remember { mutableStateOf(false) }

    // Contadores para forzar el reinicio a 'Hoy' al entrar o pulsar en Horario y Comedores
    var diningNavCounter by remember { mutableIntStateOf(0) }
    var scheduleNavCounter by remember { mutableIntStateOf(0) }

    fun navigateToTab(route: String) {
        if (!route.startsWith("navigation")) {
            isVerticalGuidanceActive = false
        }
        if (route == Screen.Dining.route) {
            diningNavCounter++
        }
        if (route == Screen.Schedule.route) {
            scheduleNavCounter++
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
    }

    fun handleNavigation(route: String) {
        if (route == Screen.Outdoor.route || route.startsWith("${Screen.Outdoor.route}?")) {
            isVerticalGuidanceActive = false
            val uri = android.net.Uri.parse("app://$route")
            val graph = GrafoRepository.getGrafo(context)
            val name = uri.getQueryParameter("finalDestination")
            val destinationId = uri.getQueryParameter("destinationId")
            val resolved = OutdoorEntryPolicy.resolve(graph, destinationId, name,
                uri.getQueryParameter("finalLocation").orEmpty(), uri.getQueryParameter("finalNodeId"))
            // Toda petición a la ETSIIT entra en Guiado: allí se comprueba el GPS.
            // El objetivo interior se conserva también durante el tramo auxiliar → ETSIIT.
            if (resolved is OutdoorEntry.EtsiitTarget) {
                activeTrip = NavigationTrip.startFixedOutdoor(resolved.indoor).selectIndoorMode()
                manualIndoorOverride = false
                clearHandoff()
                activeDestination = resolved.indoor.target.name
                activeLocation = resolved.indoor.target.location
                activeEntryNodeId = resolved.indoor.entryNodeId
                activeTargetNodeId = resolved.indoor.target.nodeId
                navigateToTab(Screen.Navigation.route)
                return
            }
            if (resolved != OutdoorEntry.FixedRoute) {
                showUnavailableOutdoor = true
                return
            }
            activeTrip = NavigationTrip.startFixedOutdoor()
            manualIndoorOverride = false
            clearHandoff()
            activeDestination = null
            activeLocation = null
            navController.navigate(OutdoorNavigationRoute.create(EtsiitAuxiliaryRoute.destinationId)) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = false
                }
                launchSingleTop = true
            }
        } else if (route.startsWith("navigation?")) {
            manualIndoorOverride = false
            val uri = android.net.Uri.parse("app://$route")
            val dest = uri.getQueryParameter("destination")
            val loc = uri.getQueryParameter("location")
            val graph = GrafoRepository.getGrafo(context)
            if (dest != null && EtsiitAccess.journey(graph, dest, loc.orEmpty()) != null) {
                handleNavigation(OutdoorNavigationRoute.forIndoorTarget(dest, loc.orEmpty(),
                    graph.buscarNodoPorDestino(dest)?.id))
                return
            }
            if (dest?.contains("comedor", ignoreCase = true) == true) {
                val dining = OutdoorDestinations.diningNamedIn(dest)
                if (dining != null) {
                    handleNavigation(OutdoorNavigationRoute.create(dining.id))
                } else {
                    val provider = DeviceLocationProvider(context)
                    val last = runCatching { kotlinx.coroutines.runBlocking { provider.lastKnownLocation() } }.getOrNull()
                    val nearest = CampusLocationManager.findNearestDiningHall(last?.latitude, last?.longitude)
                    handleNavigation(OutdoorNavigationRoute.create("dining-${nearest.id}"))
                }
                return
            }
            clearHandoff()
            if (!dest.isNullOrBlank()) {
                activeTrip = null
                activeDestination = dest
                activeLocation = loc
            }
            navigateToTab(Screen.Navigation.route)
        } else if (route == Screen.Profile.route) {
            navController.navigate(Screen.Profile.route)
        } else {
            navigateToTab(route)
        }
    }

    // Asistente de Voz Global (búsqueda de destinos en todo el campus)
    var isListeningSpeechGlobal by remember { mutableStateOf(false) }

    val globalSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListeningSpeechGlobal = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val query = spokenList?.firstOrNull()?.trim()
            if (!query.isNullOrBlank()) {

                // --- 1. BUSCAR PRIMERO EN EL DIRECTORIO DE PROFESORES ---
                val jsonString = context.assets.open("docentes.json").bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONObject(jsonString).getJSONArray("profesores")
                var foundProfessor: String? = null

                for (i in 0 until jsonArray.length()) {
                    val nombre = jsonArray.getJSONObject(i).getString("nombre")
                    // Comprobamos si lo que ha dicho el usuario coincide con algún nombre
                    if (nombre.contains(query, ignoreCase = true)) {
                        foundProfessor = nombre
                        break
                    }
                }

                if (foundProfessor != null) {
                    triggerVibration(context, durationMs = 120)
                    // Viajamos a la pestaña de profesores pasándole el nombre por la URL interna
                    navController.navigate("${Screen.Professors.route}?nombre=$foundProfessor")
                    return@rememberLauncherForActivityResult
                }
                // --- FIN DE LA BÚSQUEDA DE PROFESORES ---

                val namedDining = OutdoorDestinations.diningNamedIn(query)
                if (namedDining != null) {
                    triggerVibration(context, durationMs = 120)
                    handleNavigation(OutdoorNavigationRoute.create(namedDining.id))
                    return@rememberLauncherForActivityResult
                }

                val asksNearestDining = query.contains("más cercano", ignoreCase = true) &&
                    (query.contains("comedor", ignoreCase = true) || query.contains("comer", ignoreCase = true))
                if (asksNearestDining) {
                    val provider = DeviceLocationProvider(context)
                    val last = runCatching { kotlinx.coroutines.runBlocking { provider.lastKnownLocation() } }.getOrNull()
                    val nearest = CampusLocationManager.findNearestDiningHall(last?.latitude, last?.longitude)
                    triggerVibration(context, durationMs = 120)
                    handleNavigation(OutdoorNavigationRoute.create("dining-${nearest.id}"))
                    return@rememberLauncherForActivityResult
                }
                val grafo = GrafoRepository.getGrafo(context)
                when (val res = grafo.resolverDestinoPorVoz(query)) {
                    is ResultadoVoz.Exito -> {
                        if (EtsiitAccess.journey(grafo, res.destino.titulo,
                                res.destino.ubicacion, res.destino.nodo.id) != null) {
                            triggerVibration(context, durationMs = 120)
                            handleNavigation(OutdoorNavigationRoute.forIndoorTarget(res.destino.titulo,
                                res.destino.ubicacion, res.destino.nodo.id))
                        } else {
                            activeTrip = null
                            clearHandoff()
                            triggerVibration(context, durationMs = 120)
                            activeDestination = res.destino.titulo
                            activeLocation = res.destino.ubicacion
                            navigateToTab(Screen.Navigation.route)
                        }
                    }
                    is ResultadoVoz.Error -> {
                        Toast.makeText(
                            context,
                            res.mensaje,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    fun launchGlobalVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "¿A dónde quieres ir?")
        }
        try {
            isListeningSpeechGlobal = true
            globalSpeechLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            isListeningSpeechGlobal = false
            Toast.makeText(context, "El reconocimiento por voz no está disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    val currentLaunchGlobalVoiceSearch by rememberUpdatedState(::launchGlobalVoiceSearch)

    // Sensor de Proximidad GLOBAL: activo en todas las pantallas de la app.
    // Al pasar la mano por la cámara frontal / parte superior del móvil o acercarlo a la oreja,
    // se activa el asistente de voz y se calcula la ruta guiada en el mapa.
    DisposableEffect(context) {
        val proximityManager = ProximityManager(context)

        proximityManager.onNear = {
            if (!isListeningSpeechGlobal) {
                triggerVibration(context, durationMs = 80)
                currentLaunchGlobalVoiceSearch()
            }
        }

        proximityManager.startListening()

        onDispose {
            proximityManager.stopListening()
        }
    }

    // Gesto de agitación (Triple Shake): activo GLOBALMENTE en toda la app y en cualquier pestaña.
    // Detecta dinámicamente si hay una clase en curso o próxima (con 30 minutos de antelación).
    // Si hay clase, el gesto de «¡Llévame!» (giro de muñeca) guía directamente a su aula.
    DisposableEffect(context, showCurrentClassDialog, activeClassForDialog) {
        val accelerometerManager = AccelerometerManager(context)

        accelerometerManager.onShakeDetected = {
            triggerVibration(context, durationMs = 150)
            activeClassForDialog = ScheduleRepository.getCurrentOrUpcomingClass()
            showCurrentClassDialog = true
        }

        if (showCurrentClassDialog && activeClassForDialog != null) {
            val targetClass = activeClassForDialog!!
            accelerometerManager.onTakeMeThereGesture = {
                triggerVibration(context, durationMs = 150)
                val roomName = targetClass.room.split("·")[0].trim()
                val floorName = targetClass.room.split("·").getOrElse(1) { "ETSIIT" }.trim()
                showCurrentClassDialog = false
                activeClassForDialog = null
                handleNavigation(Screen.Navigation.createRoute(roomName, floorName))
            }
        }

        accelerometerManager.startListening()

        onDispose {
            accelerometerManager.stopListening()
        }
    }

    // Título superior dinámico según la pantalla activa
    val currentTitle = when {
        currentRoute == Screen.Home.route -> "ETSIIT"
        currentRoute == Screen.Schedule.route -> "Mi Horario"
        currentRoute?.startsWith("navigation") == true -> {
            if (isVerticalGuidanceActive && !activeDestination.isNullOrBlank()) "Guiado Paso a Paso" else "Mapa ETSIIT"
        }
        currentRoute == Screen.Dining.route -> "Comedores"
        currentRoute == Screen.Outdoor.routePattern -> "Guiado exterior"
        currentRoute == Screen.Professors.route -> "Profesores"
        currentRoute == Screen.Profile.route -> "Tarjeta Universitaria (TUI)"
        else -> "ETSIIT UGR"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    val showBack = currentRoute == Screen.Profile.route || currentRoute == Screen.Outdoor.routePattern
                    if (showBack) {
                        IconButton(onClick = {
                            if (currentRoute == Screen.Outdoor.routePattern) {
                                activeTrip = null
                                clearHandoff()
                            }
                            navController.popBackStack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Atrás",
                                tint = TextPrimary
                            )
                        }
                    }
                },
                actions = {
                    // Loguito del gesto global de micrófono / sensor de proximidad (morado)
                    GestureBadge(
                        type = GestureType.PROXIMITY_VOICE,
                        size = 26.dp,
                        iconSize = 14.dp,
                        onClick = { launchGlobalVoiceSearch() }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Loguito del gesto global de triple agitación para ver la clase actual (rojo)
                    GestureBadge(
                        type = GestureType.TRIPLE_SHAKE,
                        size = 26.dp,
                        iconSize = 14.dp,
                        onClick = {
                            activeClassForDialog = ScheduleRepository.getCurrentOrUpcomingClass()
                            showCurrentClassDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Botón con la 'i' de información sobre gestos (circulito gris sutil, se mantiene igual)
                    GestureGuideButton(
                        size = 32.dp,
                        iconSize = 18.dp,
                        onClick = { showGesturesGuideDialog = true }
                    )

                    // Icono de perfil de usuario a la derecha
                    if (currentRoute != Screen.Profile.route) {
                        IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Perfil",
                                tint = UgrRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceWhite,
                    scrolledContainerColor = SurfaceWhite,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary,
                    actionIconContentColor = UgrRed
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceWhite,
                tonalElevation = NavigationBarDefaults.Elevation
            ) {
                bottomNavItems.forEach { screen ->
                    val isNavigationSelected = screen == Screen.Navigation &&
                        (currentRoute == Screen.Navigation.route ||
                         currentRoute?.startsWith("navigation") == true ||
                         currentRoute == Screen.Outdoor.routePattern ||
                         currentRoute?.startsWith("outdoor") == true)
                    val selected = (currentRoute == screen.route && !isNavigationSelected) || (screen == Screen.Navigation && isNavigationSelected)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (screen == Screen.Navigation) {
                                if (activeTrip != null && activeTrip?.phase == TripPhase.OUTDOOR) {
                                    returnToOutdoor()
                                } else {
                                    manualIndoorOverride = false // Pulsar Guiado es una nueva comprobación explícita.
                                    navigateToTab(Screen.Navigation.route)
                                }
                            } else {
                                if (!selected) {
                                    navigateToTab(screen.route)
                                } else {
                                    if (screen == Screen.Dining) {
                                        diningNavCounter++
                                    } else if (screen == Screen.Schedule) {
                                        scheduleNavCounter++
                                    }
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = UgrRed,
                            selectedTextColor = UgrRed,
                            indicatorColor = UgrRed.copy(alpha = 0.12f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(onNavigate = { route -> handleNavigation(route) })
            }
            composable(Screen.Schedule.route) {
                ScheduleScreen(
                    onNavigate = { route -> handleNavigation(route) },
                    navKey = scheduleNavCounter
                )
            }
            composable(
                route = Screen.Navigation.routePattern,
                arguments = listOf(
                    navArgument("destination") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("location") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                BackHandler(enabled = canReturnOutdoors) { returnToOutdoor() }
                val destinationArg = backStackEntry.arguments?.getString("destination")
                val locationArg = backStackEntry.arguments?.getString("location")

                // Si se recibe un nuevo destino explícito desde otra pantalla, se actualiza la ruta activa
                LaunchedEffect(destinationArg, locationArg) {
                    if (!destinationArg.isNullOrBlank()) {
                        activeDestination = destinationArg
                        activeLocation = locationArg
                    }
                }

                Column(Modifier.fillMaxSize()) {
                    AutomaticOutdoorEntry(
                        manualIndoor = manualIndoorOverride,
                        onAutomatic = { manualIndoorOverride = false },
                        onOutside = { returnToOutdoor() },
                        onOpenOutdoor = { returnToOutdoor() },
                        onOpenIndoor = { manualIndoorOverride = true },
                        gpsReader = guidanceGpsReader,
                        nowMillis = guidanceNowMillis
                    )
                    Box(Modifier.weight(1f).testTag("indoor-screen")) {
                        NavigationScreen(
                            destinationName = activeDestination,
                            destinationLocation = activeLocation,
                            isVerticalGuidanceActive = isVerticalGuidanceActive,
                            onPostureChanged = { isVertical ->
                                isVerticalGuidanceActive = isVertical
                            },
                            onSelectDestination = { dest, loc ->
                                if (EtsiitAccess.journey(GrafoRepository.getGrafo(context), dest, loc) != null) {
                                    handleNavigation(Screen.Navigation.createRoute(dest, loc))
                                } else {
                                    activeTrip = null
                                    clearHandoff()
                                    activeDestination = dest
                                    activeLocation = loc
                                }
                            },
                            onClearRoute = {
                                manualIndoorOverride = true
                                val hadTrip = activeTrip != null
                                activeTrip = activeTrip?.finish()
                                clearHandoff()
                                activeDestination = null
                                activeLocation = null
                                isVerticalGuidanceActive = false
                                if (hadTrip) {
                                    navController.popBackStack(Screen.Outdoor.routePattern, inclusive = true)
                                    activeTrip = null
                                    navigateToTab(Screen.Navigation.route)
                                }
                            },
                            entryNodeId = activeEntryNodeId,
                            destinationNodeId = activeTargetNodeId,
                            onReturnOutdoors = if (canReturnOutdoors) ({
                                returnToOutdoor()
                            }) else null
                        )
                    }
                }
            }
            composable(Screen.Dining.route) {
                DiningScreen(
                    onNavigate = { route -> handleNavigation(route) },
                    isCurrentClassDialogVisible = showCurrentClassDialog,
                    navKey = diningNavCounter
                )
            }
            composable(
                route = Screen.Outdoor.routePattern,
                arguments = listOf("destinationId", "finalDestination", "finalLocation", "finalNodeId").map { name ->
                    navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
                }
            ) { entry ->
                BackHandler {
                    activeTrip = null
                    clearHandoff()
                    activeDestination = null
                    activeLocation = null
                    navController.popBackStack()
                }
                val trip = activeTrip
                if (trip != null && trip.destinationId == EtsiitAuxiliaryRoute.destinationId) {
                    OutdoorNavigationScreen(
                        tripId = trip.id,
                        indoorDestinationLabel = trip.indoor?.target?.let { "${it.name} · ${it.location}" },
                        gpsReader = guidanceGpsReader,
                        nowMillis = guidanceNowMillis,
                        onOpenIndoors = {
                            // Selección de modo, NO confirmación GPS de haber cruzado una puerta.
                            manualIndoorOverride = true
                            activeTrip = trip.selectIndoorMode()
                            clearHandoff()
                            trip.indoor?.let { indoor ->
                                activeDestination = indoor.target.name
                                activeLocation = indoor.target.location
                                activeEntryNodeId = indoor.entryNodeId
                                activeTargetNodeId = indoor.target.nodeId
                            }
                            canReturnOutdoors = true
                            navController.navigate(Screen.Navigation.route) { launchSingleTop = true }
                        },
                        onFinish = {
                            activeTrip = null
                            clearHandoff()
                            activeDestination = null
                            activeLocation = null
                            navController.popBackStack()
                        }
                    )
                } else {
                    Column(Modifier.padding(16.dp)) {
                        Text(EtsiitAuxiliaryRoute.unavailableMessage)
                        TextButton(onClick = { activeTrip = null; clearHandoff(); navController.popBackStack() }) { Text("Volver") }
                    }
                }
            }
            composable(
                route = "${Screen.Professors.route}?nombre={nombre}",
                arguments = listOf(navArgument("nombre") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val nombreProfesor = backStackEntry.arguments?.getString("nombre")
                ProfessorsScreen(
                    onNavigate = { route -> handleNavigation(route) },
                    searchedName = nombreProfesor // Le pasamos el nombre a tu pantalla
                )
            }
            
            composable(Screen.Profile.route) {
                ProfileScreen()
            }
        }

        if (showUnavailableOutdoor) AlertDialog(
            onDismissRequest = { showUnavailableOutdoor = false },
            title = { Text("Recorrido no disponible") },
            text = { Text(EtsiitAuxiliaryRoute.unavailableMessage) },
            confirmButton = { TextButton(onClick = { showUnavailableOutdoor = false }) { Text("Cerrar") } })

        // Diálogo informativo global: Horario de clase actual o próxima (disparado por Triple Shake)
        if (showCurrentClassDialog) {
            val currentClass = activeClassForDialog
            val nowTime = remember(showCurrentClassDialog) { LocalTime.now() }

            AlertDialog(
                onDismissRequest = {
                    showCurrentClassDialog = false
                    activeClassForDialog = null
                },
                containerColor = SurfaceWhite,
                titleContentColor = TextPrimary,
                textContentColor = TextPrimary,
                iconContentColor = UgrRed,
                icon = {
                    Icon(
                        imageVector = if (currentClass != null) Icons.Default.School else Icons.Default.EventBusy,
                        contentDescription = null,
                        tint = UgrRed,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = if (currentClass != null) {
                            val parts = currentClass.time.split("-").map { it.trim() }
                            val start = try { LocalTime.parse(parts[0]) } catch (_: Exception) { null }
                            if (start != null && nowTime.isBefore(start)) "Próxima Clase" else "Clase en Curso"
                        } else {
                            "Próxima Clase"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                text = {
                    Column {
                        if (currentClass != null) {
                            val parts = currentClass.time.split("-").map { it.trim() }
                            val start = try { LocalTime.parse(parts[0]) } catch (_: Exception) { null }
                            val isUpcoming = start != null && nowTime.isBefore(start)

                            Text(
                                text = if (isUpcoming) "Tu próxima clase comienza a las ${parts[0]}:" else "Información de tu clase en curso:",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${currentClass.subject} (${currentClass.acronym})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        GestureBadge(
                                            type = GestureType.TAKE_ME_THERE,
                                            onClick = {
                                                val roomName = currentClass.room.split("·")[0].trim()
                                                val floorName = currentClass.room.split("·").getOrElse(1) { "ETSIIT" }.trim()
                                                showCurrentClassDialog = false
                                                activeClassForDialog = null
                                                handleNavigation(Screen.Navigation.createRoute(roomName, floorName))
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${currentClass.time} · ${currentClass.room}",
                                        fontSize = 13.sp,
                                        color = UgrRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Profesor/a: ${currentClass.professor}",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No tienes clase próximamente.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No tienes ninguna clase programada en tu horario en este momento ni en los próximos 30 minutos.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showCurrentClassDialog = false
                        activeClassForDialog = null
                    }) {
                        Text("Cerrar", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Diálogo informativo: Guía completa de gestos del acelerómetro
        if (showGesturesGuideDialog) {
            AccelerometerGesturesGuideDialog(
                onDismiss = { showGesturesGuideDialog = false }
            )
        }
    }
}

/**
 * Función auxiliar para emitir una vibración háptica al disparar un gesto
 */
private fun triggerVibration(context: Context, durationMs: Long = 150) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        }
    } catch (_: Exception) {
    }
}
