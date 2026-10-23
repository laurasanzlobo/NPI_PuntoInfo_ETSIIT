package com.example.npi_puntoinfo_etsiit.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Network
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
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
import com.example.npi_puntoinfo_etsiit.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun OutdoorNavigationScreen(
    destinationId: String?,
    finalDestination: String? = null,
    finalLocation: String? = null,
    finalNodeId: String? = null,
    trip: NavigationTrip,
    onTripChanged: (NavigationTrip) -> Unit,
    onSelectOutdoorDestination: (String) -> Unit,
    onContinueIndoors: (NavigationJourney.Indoor) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val selectedId = trip.destinationId
    val indoor = trip.indoor
    val currentTrip by rememberUpdatedState(trip)
    val updateTrip by rememberUpdatedState(onTripChanged)
    val navigator = remember(trip.id, context) {
        OutdoorNavigator(GpsLocationReader(DeviceLocationProvider(context)),
            OpenRouteService(BuildConfig.ORS_API_KEY), selectedId)
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
        if (!routeConsent || !online) return
        if (state.location == GpsLocationState.PermissionDenied) {
            routeAfterPermission = true
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else navigator.requestRoute(scope)
    }
    LaunchedEffect(navigator, selectedId, online, launchPending) {
        // La acción «¡Llévame!» prepara la ruta. El primer envío exige consentimiento explícito.
        if (launchPending && online && state.destination != null && BuildConfig.ORS_API_KEY.isNotBlank()) {
            launchPending = false
            if (routeConsent) startRoute() else showConsent = true
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
    val route = (state.route as? WalkingRouteResult.Success)?.route
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
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Cerrar") } })
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

        // 2. CABECERA FLOTANTE SUPERIOR: Selector de destino + Info + "Ya estoy dentro"
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
                        Text(
                            text = state.destination?.name ?: "Seleccionar destino UGR",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                    }
                    DropdownMenu(expanded = destinationMenu, onDismissRequest = { destinationMenu = false }) {
                        OutdoorDestinations.all.forEach { destination ->
                            DropdownMenuItem(text = { Text(destination.name) }, onClick = {
                                selectDestination(destination.id)
                            })
                        }
                    }
                }
            }

            // Subtítulo con el despacho / profesor y botón "Ya estoy dentro"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (indoor != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceWhite.copy(alpha = 0.95f),
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${indoor.target.name} · ${indoor.target.location}",
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
                        Text(
                            text = "Sin ruta interior para $finalDestination",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                // Botón "Ya estoy dentro" en la esquina superior derecha
                if (indoor != null) {
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
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Estás cerca de ${state.destination?.name}. Comprueba el acceso.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { showArrival = false }) { Text("Cerrar", style = MaterialTheme.typography.labelMedium) }
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
                    // Estado 1: Ruta calculada
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
                            Text("Ruta a pie recomendada", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }

                        Button(
                            enabled = !busy,
                            onClick = {
                                checkNetwork()
                                if (routeConsent) {
                                    if (online) startRoute() else launchPending = true
                                } else showConsent = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = UgrRed),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (state.routing) "Calculando…" else "Actualizar",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Botón centrado para desplegar / ocultar pasos
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(
                            onClick = { showSteps = !showSteps },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (showSteps) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (showSteps) "Ocultar pasos" else "Ver instrucciones (${route.steps.size})",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    if (showSteps) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            route.steps.forEachIndexed { index, step -> WalkingStepRow(step, index + 1) }
                        }
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

                        Button(
                            enabled = !busy && state.destination != null && BuildConfig.ORS_API_KEY.isNotBlank(),
                            onClick = {
                                checkNetwork()
                                if (routeConsent) {
                                    if (online) startRoute() else launchPending = true
                                } else showConsent = true
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
    }
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
