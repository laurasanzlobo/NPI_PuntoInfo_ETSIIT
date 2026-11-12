package com.example.npi_puntoinfo_etsiit.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Network
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.os.SystemClock
import android.util.Log
import com.example.npi_puntoinfo_etsiit.BuildConfig
import com.example.npi_puntoinfo_etsiit.outdoor.*
import com.example.npi_puntoinfo_etsiit.sensors.*
import com.example.npi_puntoinfo_etsiit.ui.components.OutdoorMap
import com.example.npi_puntoinfo_etsiit.ui.components.OutdoorMapStatus
import com.example.npi_puntoinfo_etsiit.ui.components.WalkingStepRow
import com.example.npi_puntoinfo_etsiit.ui.components.IndoorArrivalDialog
import com.example.npi_puntoinfo_etsiit.ui.components.CompactIndoorEntryButton
import com.example.npi_puntoinfo_etsiit.ui.components.GestureBadge
import com.example.npi_puntoinfo_etsiit.ui.components.GestureType
import com.example.npi_puntoinfo_etsiit.ui.components.loadSampledBitmapFromAssets
import com.example.npi_puntoinfo_etsiit.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun OutdoorNavigationScreen(
    destinationId: String?,
    finalDestination: String? = null,
    finalLocation: String? = null,
    finalNodeId: String? = null,
    trip: NavigationTrip,
    onTripChanged: (NavigationTrip) -> Unit,
    onSelectOutdoorDestination: (String) -> Unit,
    onContinueIndoors: (NavigationJourney.Indoor) -> Unit = {},
    onVerticalGuidanceActiveChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val selectedId = trip.destinationId
    val indoor = trip.indoor
    val currentTrip by rememberUpdatedState(trip)
    val updateTrip by rememberUpdatedState(onTripChanged)
    var currentSelectedDestinationId by remember(selectedId) { mutableStateOf(selectedId) }
    val navigator = remember(trip.id, context) {
        val ors = OpenRouteService(BuildConfig.ORS_API_KEY)
        val hybrid = HybridWalkingRouteSource(
            context = context,
            fallback = ors,
            destinationIdProvider = { currentSelectedDestinationId }
        )
        OutdoorNavigator(
            gps = GpsLocationReader(DeviceLocationProvider(context)),
            routes = hybrid,
            initialDestinationId = selectedId
        )
    }
    val state by navigator.state.collectAsState()
    var destinationMenu by remember { mutableStateOf(false) }
    var mapAttempt by remember { mutableIntStateOf(0) }
    var mapStatus by remember { mutableStateOf(OutdoorMapStatus.LOADING) }
    var recenter by remember { mutableIntStateOf(0) }
    var settingsError by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(true) }
    var showInfo by remember(destinationId, finalDestination, finalNodeId) { mutableStateOf(false) }
    var showSteps by rememberSaveable { mutableStateOf(false) }
    var launchPending by rememberSaveable(destinationId, finalDestination, finalNodeId) { mutableStateOf(true) }
    val consentStore = remember(context) { RouteConsentStore(context) }
    var routeConsent by remember { mutableStateOf(consentStore.isGranted) }
    var showConsent by rememberSaveable(destinationId, finalDestination, finalNodeId) { mutableStateOf(false) }
    var routeAfterPermission by rememberSaveable(destinationId, finalDestination, finalNodeId) { mutableStateOf(false) }
    var showArrival by rememberSaveable(trip.id) { mutableStateOf(false) }
    var showManualEntry by rememberSaveable(trip.id) { mutableStateOf(false) }
    var automaticPromptVisible by remember(trip.id) { mutableStateOf(false) }
    val detector = remember(trip.id, navigator) { ArrivalDetector(alreadyNotified = trip.arrivalNotified) }

    fun selectDestination(id: String) {
        currentSelectedDestinationId = id
        navigator.selectDestination(id)
        if (id != selectedId) {
            onSelectOutdoorDestination(id)
        }
        destinationMenu = false
    }

    fun checkNetwork() {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        online = manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    DisposableEffect(navigator, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    navigator.onStart()
                    checkNetwork()
                    if (routeConsent && navigator.state.value.location == GpsLocationState.Idle) launchPending = true
                }
                Lifecycle.Event.ON_STOP -> navigator.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            navigator.onStart()
            checkNetwork()
            if (routeConsent && navigator.state.value.location == GpsLocationState.Idle) launchPending = true
        }
        onDispose { lifecycle.removeObserver(observer); navigator.onStop() }
    }
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = checkNetwork()
            override fun onLost(network: Network) = checkNetwork()
        }
        manager.registerDefaultNetworkCallback(callback)
        onDispose { manager.unregisterNetworkCallback(callback) }
    }
    LaunchedEffect(mapAttempt, mapStatus) {
        if (mapStatus == OutdoorMapStatus.LOADING) {
            delay(20_000)
            mapStatus = OutdoorMapStatus.ERROR
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (routeAfterPermission && routeConsent) {
            if (online) navigator.requestRoute(scope) else launchPending = true
        } else navigator.refreshLocation(scope)
        routeAfterPermission = false
    }
    fun startRoute() {
        val isLocal = GuidanceExteriorRepository.isLocalGuidanceRoute(state.destination?.id)
        if (!isLocal && (!routeConsent || !online)) return
        if (state.location == GpsLocationState.PermissionDenied) {
            routeAfterPermission = true
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else navigator.requestRoute(scope)
    }
    LaunchedEffect(navigator, selectedId, online, launchPending) {
        val isLocal = GuidanceExteriorRepository.isLocalGuidanceRoute(selectedId)
        if (launchPending && state.destination != null && (isLocal || (online && BuildConfig.ORS_API_KEY.isNotBlank()))) {
            launchPending = false
            if (isLocal || routeConsent) startRoute() else showConsent = true
        }
    }
    fun openSettings(action: String) {
        try {
            context.startActivity(Intent(action).apply {
                if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                    data = "package:${context.packageName}".toUri()
                }
            })
        } catch (_: ActivityNotFoundException) { settingsError = true }
    }

    val position = (state.location as? GpsLocationState.Available)?.coordinates
    LaunchedEffect(position) {
        if (position != null) {
            CampusLocationManager.lastKnownLocation = position
        }
    }
    val localFallbackRoute = remember(context, state.destination?.id, position) {
        if (GuidanceExteriorRepository.isLocalGuidanceRoute(state.destination?.id)) {
            GuidanceExteriorRepository.getRouteForDestination(
                context = context,
                destinationId = state.destination?.id,
                origin = position?.let { GeoPoint(it.latitude, it.longitude) }
            )
        } else null
    }
    val route = (state.route as? WalkingRouteResult.Success)?.route ?: localFallbackRoute
    val guidanceRoute = route ?: remember(context, state.destination?.id, position) {
        if (GuidanceExteriorRepository.isLocalGuidanceRoute(state.destination?.id)) {
            GuidanceExteriorRepository.getRouteForDestination(
                context = context,
                destinationId = state.destination?.id,
                origin = position?.let { GeoPoint(it.latitude, it.longitude) }
            )
        } else null
    }
    var isVerticalGuidanceActive by rememberSaveable { mutableStateOf(false) }
    var isManualGuidanceLocked by rememberSaveable { mutableStateOf(false) }
    var currentStepIndex by rememberSaveable(trip.id, guidanceRoute) { mutableIntStateOf(0) }
    var awaitingGuidanceLocation by rememberSaveable(trip.id, guidanceRoute) { mutableStateOf(true) }

    // El callback del acelerómetro debe usar la ruta y ubicación más recientes sin reiniciar el sensor.
    val startGuidance by rememberUpdatedState { manual: Boolean ->
        if (!isVerticalGuidanceActive) {
            val nearestIndex = guidanceRoute?.nearestStepIndex(
                position?.let { GeoPoint(it.latitude, it.longitude) }
            )
            currentStepIndex = nearestIndex ?: 0
            awaitingGuidanceLocation = nearestIndex == null
        }
        isManualGuidanceLocked = manual
        isVerticalGuidanceActive = true
    }

    LaunchedEffect(isVerticalGuidanceActive) {
        onVerticalGuidanceActiveChanged(isVerticalGuidanceActive)
    }

    LaunchedEffect(guidanceRoute) {
        if (guidanceRoute == null) {
            isManualGuidanceLocked = false
            isVerticalGuidanceActive = false
        }
    }

    BackHandler(enabled = isVerticalGuidanceActive) {
        isManualGuidanceLocked = false
        isVerticalGuidanceActive = false
    }

    // Detección del gesto de levantar el móvil (Acelerómetro) para ver el guiado paso a paso con imágenes
    // Si el usuario pulsó manualmente el botón "Iniciar guiado paso a paso", el acelerómetro se desactiva
    // para fijar la pantalla hasta que pulse la 'X' de salir.
    DisposableEffect(context, guidanceRoute != null, isManualGuidanceLocked) {
        val manager = AccelerometerManager(context)
        if (guidanceRoute != null && !isManualGuidanceLocked) {
            manager.onPostureModeChanged = { isVertical ->
                triggerVibration(context, durationMs = 70)
                if (isVertical) startGuidance(false) else isVerticalGuidanceActive = false
            }
            manager.startListening()
        }
        onDispose {
            manager.stopListening()
            if (!isManualGuidanceLocked) {
                isVerticalGuidanceActive = false
                onVerticalGuidanceActiveChanged(false)
            }
        }
    }

    // Geofencing GPS: avanzar automáticamente de paso si el usuario camina hacia el siguiente nodo
    LaunchedEffect(position, guidanceRoute, currentStepIndex) {
        if (position != null && guidanceRoute != null && guidanceRoute.steps.isNotEmpty()) {
            // El visor puede abrirse antes de que termine la primera lectura GPS.
            if (awaitingGuidanceLocation) {
                val nearestIndex = guidanceRoute.nearestStepIndex(GeoPoint(position.latitude, position.longitude))
                if (nearestIndex != null) {
                    currentStepIndex = nearestIndex
                    awaitingGuidanceLocation = false
                }
                return@LaunchedEffect
            }
            val nextIndex = currentStepIndex + 1
            if (nextIndex < guidanceRoute.steps.size) {
                val nextStep = guidanceRoute.steps[nextIndex]
                val nextPoint = nextStep.point
                if (nextPoint != null) {
                    val d = OutdoorDistances.distanceMeters(
                        position.latitude, position.longitude,
                        nextPoint.latitude, nextPoint.longitude
                    )
                    // Si el usuario está a 12 metros o menos del siguiente nodo, avanzamos automáticamente de foto
                    if (d <= 12.0) {
                        currentStepIndex = nextIndex
                        triggerVibration(context, durationMs = 120)
                    }
                }
            }
        }
    }
    val busy = state.location == GpsLocationState.Loading || state.routing
    val distanceToAccess = if (position != null && state.destination != null) OutdoorDistances.distanceMeters(
        position.latitude, position.longitude, state.destination!!.point.latitude, state.destination!!.point.longitude)
        else Double.NaN
    val updateInterval = ActiveLocationPolicy.interval(distanceToAccess, position?.accuracyMeters?.toDouble() ?: Double.NaN)

    // La propuesta interior no depende de Internet, consentimiento ORS ni de calcular una ruta a pie.
    LaunchedEffect(navigator, route != null, state.location == GpsLocationState.Loading,
        indoor, updateInterval, lifecycle) {
        if (route == null && indoor == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            fun observe(location: GpsLocationState) {
                if (currentTrip.arrivalNotified) return
                val fix = (location as? GpsLocationState.Available)?.coordinates
                val destination = navigator.state.value.destination
                val distance = if (fix != null && destination != null) OutdoorDistances.distanceMeters(
                    fix.latitude, fix.longitude, destination.point.latitude, destination.point.longitude) else Double.NaN
                val now = SystemClock.elapsedRealtime()
                val firstBefore = detector.firstValidFixMillis
                val confirmed = detector.observe(distance, fix?.accuracyMeters?.toDouble() ?: Double.NaN,
                    fix?.elapsedRealtimeMillis ?: -1L, now)
                if (firstBefore != detector.firstValidFixMillis && detector.firstValidFixMillis != null) {
                    Log.d("NavigationArrival", "firstValid trip=${currentTrip.id} fixMs=${detector.firstValidFixMillis} observedMs=$now")
                }
                if (confirmed) {
                    Log.d("NavigationArrival", "confirmed trip=${currentTrip.id} atMs=$now " +
                        "sinceFirstFixMs=${now - detector.firstValidFixMillis!!}")
                    updateTrip(currentTrip.onArrival())
                    if (currentTrip.indoor == null) showArrival = true
                }
            }
            observe(navigator.state.value.location)
            navigator.trackActivePosition(updateInterval, requireRoute = indoor == null, onLocation = ::observe)
        }
    }
    LaunchedEffect(trip.presentation) {
        if (trip.presentation == ArrivalPresentation.PROMPT) {
            automaticPromptVisible = true
            onTripChanged(trip.markPromptShown())
        }
    }
    LaunchedEffect(automaticPromptVisible) {
        if (automaticPromptVisible) {
            withFrameNanos { } // Medir tras componer el diálogo, no al solicitar su estado.
            Log.d("NavigationArrival", "promptVisible trip=${trip.id} atMs=${SystemClock.elapsedRealtime()} " +
                "afterConfirmationMs=${detector.confirmedAtMillis?.let { SystemClock.elapsedRealtime() - it }}")
        }
    }

    if (showConsent) {
        AlertDialog(onDismissRequest = { showConsent = false },
            title = { Text("Calcular ruta a pie") },
            text = { Text("Para calcular la ruta se enviarán tu ubicación actual y el destino a " +
                "openrouteservice (HeiGIT). ¿Lo autorizas para esta y futuras rutas? " +
                "Puedes consultar los detalles en el icono de información.") },
            confirmButton = { TextButton(onClick = {
                consentStore.grant()
                routeConsent = true
                showConsent = false
                if (online) startRoute() else launchPending = true
            }) { Text("Aceptar e iniciar ruta") } },
            dismissButton = { TextButton(onClick = { showConsent = false }) { Text("Ahora no") } })
    }
    if (showInfo) {
        AlertDialog(onDismissRequest = { showInfo = false },
            title = { Text("Información de navegación") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Para calcular la ruta se envían tu ubicación actual y el destino a openrouteservice (HeiGIT). " +
                    "OpenFreeMap descarga la zona visible. Durante una ruta activa se comprueba tu ubicación " +
                    "cada 12 s, o cada 2 s cerca del acceso (según señal), solo con esta pantalla visible; " +
                    "sin historial ni seguimiento en segundo plano.")
                if (indoor != null) TextButton(onClick = {
                    showInfo = false
                    showManualEntry = true
                }) { Text("Continuar dentro manualmente") }
                Text("Ruta estática: usa Actualizar ruta al moverte. Los extremos se ajustan a caminos próximos " +
                    "(hasta 100 m). Comprueba los accesos y la señalización.")
                Text("El punto ETSIIT es la referencia del catálogo para el acceso PB; su posición exacta en la puerta " +
                    "está pendiente de validación sobre el terreno. El GPS no confirma que estés dentro.")
                OutdoorAttribution()
            } },
            confirmButton = {
                Button(
                    onClick = { showInfo = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UgrRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Text(
                        text = "Cerrar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            })
    }
    if ((automaticPromptVisible || showManualEntry) && indoor != null) {
        IndoorArrivalDialog(manual = showManualEntry,
            onContinue = {
                onTripChanged(trip.enterIndoors())
                automaticPromptVisible = false
                showManualEntry = false
                onContinueIndoors(indoor)
            },
            onStayOutside = {
                onTripChanged(trip.stayOutside())
                automaticPromptVisible = false
                showManualEntry = false
            })
    }

    Box(Modifier.fillMaxSize().testTag("outdoor-screen")) {
        // 1. EL MAPA: Ocupa todo el alto y ancho (fullscreen real)
        key(mapAttempt) {
            OutdoorMap(
                destinations = OutdoorDestinations.all,
                selected = state.destination,
                position = position?.let { GeoPoint(it.latitude, it.longitude) },
                route = route,
                recenter = recenter,
                onSelect = ::selectDestination,
                onStatus = { mapStatus = it },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Estado del mapa (cargando o error)
        if (mapStatus != OutdoorMapStatus.READY) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(20.dp),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite.copy(alpha = 0.95f),
                shadowElevation = 6.dp
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (mapStatus == OutdoorMapStatus.LOADING) "Cargando mapa…" else
                        "No se ha podido cargar el mapa. Comprueba Internet o reintenta.")
                    if (mapStatus == OutdoorMapStatus.ERROR) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            checkNetwork()
                            mapStatus = OutdoorMapStatus.LOADING
                            mapAttempt++
                        }) { Text("Reintentar mapa") }
                    }
                }
            }
        }

        // 2. CABECERA FLOTANTE SUPERIOR: Selector de destino y entrada al tramo interior
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Selector de destino en píldora con fondo blanco semitransparente sobre el mapa
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                color = SurfaceWhite.copy(alpha = 0.95f),
                shadowElevation = 3.dp
            ) {
                Box {
                    TextButton(
                        onClick = { destinationMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val isEnglish = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
                        Text(
                            text = state.destination?.getLocalizedName(isEnglish) ?: if (isEnglish) "Select UGR destination" else "Seleccionar destino UGR",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                    }
                    DropdownMenu(expanded = destinationMenu, onDismissRequest = { destinationMenu = false }) {
                        val isEnglish = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
                        OutdoorDestinations.mapDestinations.forEach { destination ->
                            DropdownMenuItem(text = { Text(destination.getLocalizedName(isEnglish)) }, onClick = {
                                selectDestination(destination.id)
                            })
                        }
                    }
                }
            }

            // Reservar espacio al botón aunque el nombre del destino interior sea largo.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (indoor != null) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceWhite.copy(alpha = 0.95f),
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.5f))
                    ) {
                        val isEnglish = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
                        val localizedTarget = if (isEnglish && indoor.target.name == "Comedor Aynadamar") "Aynadamar Dining Hall" else indoor.target.name
                        Text(
                            text = "$localizedTarget · ${indoor.target.location}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                } else if (finalDestination != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceWhite.copy(alpha = 0.95f),
                        shadowElevation = 2.dp
                    ) {
                        val isEnglish = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
                        Text(
                            text = if (isEnglish) "No indoor route for $finalDestination" else "Sin ruta interior para $finalDestination",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                // Disponible también lejos del edificio, sin esperar a la detección de llegada.
                if (trip.showCompactEntry && indoor != null) {
                    CompactIndoorEntryButton(
                        onClick = {
                            onTripChanged(trip.enterIndoors())
                            onContinueIndoors(indoor)
                        }
                    )
                }
            }
        }

        // 3. BOTÓN FLOTANTE CIRCULAR DE CENTRAR MAPA (estilo Google Maps)
        Surface(
            shape = CircleShape,
            color = SurfaceWhite,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.6f)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (route != null) 140.dp else 100.dp)
                .size(46.dp),
            onClick = { recenter++ }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.NearMe,
                    contentDescription = "Centrar mapa",
                    tint = UgrRed,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 4. TARJETA INFERIOR FLOTANTE ELEGANTE
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite.copy(alpha = 0.96f),
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Aviso de llegada
                if (showArrival && indoor == null) {
                    val isEnglish = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
                    val destName = state.destination?.getLocalizedName(isEnglish) ?: ""
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isEnglish) "You are near $destName. Check entry." else "Estás cerca de $destName. Comprueba el acceso.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { showArrival = false }) { Text(if (isEnglish) "Close" else "Cerrar", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }

                // Avisos de red / GPS
                if (!online) {
                    Text("Sin conexión a Internet. El mapa necesita red.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                when (val location = state.location) {
                    GpsLocationState.Idle -> Unit
                    GpsLocationState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    GpsLocationState.PermissionDenied -> {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sin permiso de ubicación", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) }) {
                                Text("Ajustes", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    GpsLocationState.LocationDisabled -> {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ubicación desactivada en el móvil", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) }) {
                                Text("Activar", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    GpsLocationState.Unavailable -> Text("Ubicación temporalmente no disponible", style = MaterialTheme.typography.bodySmall)
                    is GpsLocationState.Available -> Unit
                }

                if (route != null) {
                    // Resumen de la ruta exterior
                    val isEnglish = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${formatOutdoorDistance(route.distanceMeters)}" +
                                    (route.durationSeconds?.let { " · ${formatWalkingDuration(it)}" } ?: ""),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = state.destination?.getLocalizedName(isEnglish) ?: if (isEnglish) "Outdoor walking route" else "Ruta peatonal exterior",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        GestureBadge(
                            type = GestureType.LIFT_PHONE,
                            size = 38.dp,
                            iconSize = 20.dp,
                            onClick = {
                                startGuidance(true)
                            }
                        )
                    }

                    // Botón para iniciar el guiado paso a paso a pantalla completa con acelerómetro
                    Button(
                        onClick = {
                            startGuidance(true)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = UgrRed)
                    ) {
                        Text(
                            text = "Iniciar guiado paso a paso",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    // Estado 2: Sin ruta calculada todavía
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (position != null && state.destination != null) {
                                val dMeters = OutdoorDistances.distanceMeters(
                                    position.latitude, position.longitude,
                                    state.destination!!.point.latitude, state.destination!!.point.longitude
                                )
                                Text("Línea recta: ${formatOutdoorDistance(dMeters)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            } else {
                                Text(state.destination?.name ?: "Selecciona un destino", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        val isLocalDest = GuidanceExteriorRepository.isLocalGuidanceRoute(state.destination?.id)
                        Button(
                            enabled = !busy && state.destination != null && (isLocalDest || BuildConfig.ORS_API_KEY.isNotBlank()),
                            onClick = {
                                if (isLocalDest) {
                                    startRoute()
                                } else {
                                    checkNetwork()
                                    if (routeConsent) {
                                        if (online) startRoute() else launchPending = true
                                    } else showConsent = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = UgrRed),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (state.routing) "Calculando…" else "Calcular ruta",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (state.routing && state.location != GpsLocationState.Loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                (state.route as? WalkingRouteResult.Failure)?.let {
                    Text(it.error.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 5. MODO GUIADO VERTICAL A PANTALLA COMPLETA CON ORIENTACIÓN Y EJE X (Activado al levantar el móvil)
        if (isVerticalGuidanceActive && guidanceRoute != null && guidanceRoute.steps.isNotEmpty()) {
            val step = guidanceRoute.steps.getOrElse(currentStepIndex) { guidanceRoute.steps.first() }
            val currentAzimuth = rememberVerticalCompassAzimuth(context)

            // Desviación angular [-180°, 180°) entre hacia dónde apunta el móvil y el rumbo del nodo
            val deltaTheta: Float = if (step.heading != null && currentAzimuth != null) {
                ((currentAzimuth - step.heading.toFloat() + 540f) % 360f) - 180f
            } else {
                0f
            }

            // Margen de orientación clara (±28°) para mostrar la flecha e instrucción en el centro
            val orientationMarginDeg = 28f
            val isFacingCorrectDirection = step.heading == null || currentAzimuth == null || abs(deltaTheta) <= orientationMarginDeg

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // 1. IMAGEN PANORÁMICA A PANTALLA COMPLETA RECORTADA SEGÚN ORIENTACIÓN Y EJE_X_CAMINO
                if (step.photo != null) {
                    val bitmap = remember(step.photo) {
                        loadSampledBitmapFromAssets(context, step.photo, 2)
                    }
                    if (bitmap != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val screenW = size.width
                            val screenH = size.height
                            if (screenW > 0f && screenH > 0f) {
                                // Ancho del recorte en píxeles del bitmap para mantener proporción vertical a pantalla completa
                                val sliceWidth = (bitmap.height.toFloat() * (screenW / screenH))
                                    .roundToInt()
                                    .coerceIn(1, bitmap.width)

                                // Píxel objetivo calibrado en el HTML (eje_x_camino)
                                val targetBmpX = bitmap.width.toFloat() * step.ejeXCamino.toFloat()

                                // Desplazamiento horizontal según desviación de brújula (FOV horizontal ~65°)
                                val fovHorizontal = 65f
                                val shiftBmpX = (deltaTheta / fovHorizontal) * sliceWidth.toFloat()
                                val visualCenterX = targetBmpX + shiftBmpX

                                val maxLeft = (bitmap.width - sliceWidth).coerceAtLeast(0)
                                val srcLeft = (visualCenterX - sliceWidth / 2f)
                                    .roundToInt()
                                    .coerceIn(0, maxLeft)

                                drawImage(
                                    image = bitmap,
                                    srcOffset = IntOffset(srcLeft, 0),
                                    srcSize = IntSize(sliceWidth, bitmap.height),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(screenW.roundToInt(), screenH.roundToInt())
                                )
                            }
                        }
                    }
                } else {
                    // Fondo para nodos sin fotografía (interior / en obras)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A1D24))
                    )
                }

                // 2. INDICACIÓN Y FLECHA: situada en la parte inferior sobre los controles para no tapar la foto
                val maneuverIcon = when (step.maneuver) {
                    Maneuver.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
                    Maneuver.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
                    Maneuver.LEFT, Maneuver.KEEP_LEFT, Maneuver.SHARP_LEFT -> Icons.Default.TurnLeft
                    Maneuver.RIGHT, Maneuver.KEEP_RIGHT, Maneuver.SHARP_RIGHT -> Icons.Default.TurnRight
                    Maneuver.ARRIVAL -> Icons.Default.Flag
                    else -> Icons.Default.ArrowUpward
                }

                // Distancia GPS en tiempo real hacia el siguiente nodo (o destino final)
                val nextStepIdx = currentStepIndex + 1
                val targetStepPoint = if (nextStepIdx < guidanceRoute.steps.size) {
                    guidanceRoute.steps[nextStepIdx].point
                } else {
                    step.point
                }
                val liveGpsDistance = if (position != null && targetStepPoint != null) {
                    OutdoorDistances.distanceMeters(
                        position.latitude, position.longitude,
                        targetStepPoint.latitude, targetStepPoint.longitude
                    )
                } else null

                val distanceLabel = when {
                    currentStepIndex >= guidanceRoute.steps.size - 1 -> {
                        if (liveGpsDistance != null && liveGpsDistance > 10.0) {
                            formatOutdoorDistance(liveGpsDistance)
                        } else {
                            "¡Destino alcanzado!"
                        }
                    }
                    liveGpsDistance != null -> formatOutdoorDistance(liveGpsDistance)
                    step.distanceMeters > 0 -> formatOutdoorDistance(step.distanceMeters)
                    else -> "¡Destino alcanzado!"
                }

                if (isFacingCorrectDirection) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 20.dp, end = 20.dp, bottom = 82.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Flecha direccional sobre el camino
                        Surface(
                            shape = CircleShape,
                            color = UgrRed,
                            border = BorderStroke(2.5.dp, Color.White),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = maneuverIcon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        // Tarjeta con metros e instrucción concreta: fondo oscuro uniforme sin sombras escalonadas
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)), shape = RoundedCornerShape(16.dp))
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = distanceLabel,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = step.instruction.trim(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                    }
                } else {
                    // Indicador cuando el usuario debe girar: indicación limpia sin mostrar grados
                    val turnLeft = deltaTheta > 0f
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 24.dp, end = 24.dp, bottom = 82.dp)
                            .background(Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.5.dp, UgrRed), shape = RoundedCornerShape(16.dp))
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (turnLeft) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = UgrRed,
                                modifier = Modifier.size(42.dp)
                            )
                            val isEnglish = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
                            Text(
                                text = if (turnLeft) {
                                    if (isEnglish) "Turn left" else "Gira a la izquierda"
                                } else {
                                    if (isEnglish) "Turn right" else "Gira a la derecha"
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // 3. BOTÓN CERRAR SUPERIOR DERECHO (Flotante)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.size(40.dp),
                        onClick = {
                            isManualGuidanceLocked = false
                            isVerticalGuidanceActive = false
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Volver al mapa",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // 4. CONTROLES INFERIORES FLOTANTES (Anterior / Siguiente paso)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { if (currentStepIndex > 0) currentStepIndex-- },
                        enabled = currentStepIndex > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.72f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.Black.copy(alpha = 0.35f),
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("← Anterior", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { if (currentStepIndex < guidanceRoute.steps.size - 1) currentStepIndex++ },
                        enabled = currentStepIndex < guidanceRoute.steps.size - 1,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UgrRed,
                            contentColor = Color.White,
                            disabledContainerColor = Color.Black.copy(alpha = 0.35f),
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Siguiente →", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Obtiene el rumbo magnético (0°..360°) hacia el que apunta la cámara trasera
 * cuando el usuario sostiene el móvil en vertical frente a él.
 */
@Composable
private fun rememberVerticalCompassAzimuth(context: Context): Float? {
    var azimuth by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasMag = false
        var smoothSin = 0f
        var smoothCos = 0f
        var initialized = false

        fun updateFromRotationMatrix() {
            // Al sujetar el móvil en vertical, remapeamos X -> X y Y -> Z para medir el rumbo horizontal hacia el que mira la cámara
            if (SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Z,
                    remappedMatrix
                )
            ) {
                SensorManager.getOrientation(remappedMatrix, orientationAngles)
                val rad = orientationAngles[0]
                if (!initialized) {
                    smoothSin = sin(rad)
                    smoothCos = cos(rad)
                    initialized = true
                } else {
                    val alpha = 0.18f
                    smoothSin += alpha * (sin(rad) - smoothSin)
                    smoothCos += alpha * (cos(rad) - smoothCos)
                }
                val deg = ((Math.toDegrees(atan2(smoothSin.toDouble(), smoothCos.toDouble())).toFloat() + 360f) % 360f)
                azimuth = deg
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        updateFromRotationMatrix()
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                        if (hasMag && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                            updateFromRotationMatrix()
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasMag = true
                        if (hasGravity && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                            updateFromRotationMatrix()
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotationSensor != null) {
            sensorManager?.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else if (accelSensor != null && magSensor != null) {
            sensorManager?.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager?.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return azimuth
}

@Composable
private fun OutdoorAttribution() {
    val uriHandler = LocalUriHandler.current
    val links = listOf("OpenFreeMap" to "https://openfreemap.org/",
        "© OpenMapTiles" to "https://openmaptiles.org/",
        "© OpenStreetMap" to "https://www.openstreetmap.org/copyright",
        "Rutas: © openrouteservice / HeiGIT" to "https://openrouteservice.org/",
        "Privacidad de HeiGIT" to "https://heigit.org/privacy-policy/")
    links.forEach { (label, url) ->
        TextButton(onClick = { try { uriHandler.openUri(url) } catch (_: ActivityNotFoundException) { } }) {
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun triggerVibration(context: Context, durationMs: Long = 80) {
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
