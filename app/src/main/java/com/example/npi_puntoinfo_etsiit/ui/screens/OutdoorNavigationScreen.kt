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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
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

    Column(Modifier.fillMaxSize().testTag("outdoor-screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { destinationMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(state.destination?.name ?: "Seleccionar destino UGR")
                }
                DropdownMenu(expanded = destinationMenu, onDismissRequest = { destinationMenu = false }) {
                    OutdoorDestinations.all.forEach { destination ->
                        DropdownMenuItem(text = { Text(destination.name) }, onClick = {
                            selectDestination(destination.id)
                        })
                    }
                }
            }
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.Default.Info, contentDescription = "Información y privacidad")
            }
        }
        if (indoor != null) {
            Text("${indoor.target.name} · ${indoor.target.location}", Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall)
            if (!trip.showCompactEntry) TextButton(onClick = { showManualEntry = true },
                modifier = Modifier.padding(horizontal = 12.dp).testTag("manual-indoor-entry")) {
                Text("Continuar dentro manualmente")
            }
        } else if (finalDestination != null) {
            Text("No hay una ruta interior disponible para $finalDestination.", Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.error)
        }
        Box(Modifier.fillMaxWidth().weight(1.35f)) {
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
            if (mapStatus != OutdoorMapStatus.READY) {
                Surface(Modifier.align(Alignment.BottomCenter).padding(8.dp), tonalElevation = 4.dp) {
                    Column(Modifier.padding(8.dp)) {
                        Text(if (mapStatus == OutdoorMapStatus.LOADING) "Cargando mapa…" else
                            "No se ha podido cargar el mapa. Comprueba Internet o reintenta.")
                        if (mapStatus == OutdoorMapStatus.ERROR) TextButton(onClick = {
                            checkNetwork()
                            mapStatus = OutdoorMapStatus.LOADING
                            mapAttempt++
                        }) { Text("Reintentar mapa") }
                    }
                }
            }
            if (indoor != null && trip.showCompactEntry) {
                CompactIndoorEntryButton(onClick = {
                    onTripChanged(trip.enterIndoors())
                    onContinueIndoors(indoor)
                }, modifier = Modifier.align(Alignment.TopCenter).padding(8.dp))
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(0.85f, fill = false).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.destination == null) Text("Selecciona un destino")
            if (showArrival && indoor == null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Estás cerca de ${state.destination?.name}. Comprueba el acceso.", Modifier.weight(1f))
                        TextButton(onClick = { showArrival = false }) { Text("Cerrar") }
                    }
                }
            }
            if (!online) Text("Sin conexión a Internet. El mapa y las rutas necesitan red.")
            when (val location = state.location) {
                GpsLocationState.Idle -> Unit
                GpsLocationState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                GpsLocationState.PermissionDenied -> {
                    Text("Sin permiso de ubicación. Se admite ubicación precisa o aproximada.")
                    TextButton(onClick = { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) }) {
                        Text("Abrir permisos en Ajustes")
                    }
                }
                GpsLocationState.LocationDisabled -> {
                    Text("La ubicación del teléfono está desactivada.")
                    TextButton(onClick = { openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) }) {
                        Text("Activar ubicación")
                    }
                }
                GpsLocationState.Unavailable -> Text("Ubicación temporalmente no disponible. Reintenta al aire libre.")
                is GpsLocationState.Available -> {
                    Text("Ubicación ${if (location.permission == LocationPermission.APPROXIMATE) "aproximada" else "precisa"}" +
                        " · precisión ±${location.coordinates.accuracyMeters.roundToInt()} m")
                }
            }
            if (settingsError) Text("No se han podido abrir los ajustes del teléfono.")
            if (route == null && position != null && state.destination != null) {
                val destination = state.destination!!
                Text("En línea recta: " + formatOutdoorDistance(OutdoorDistances.distanceMeters(
                    position.latitude, position.longitude, destination.point.latitude, destination.point.longitude)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    if (state.location == GpsLocationState.PermissionDenied) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION))
                    } else navigator.refreshLocation(scope)
                }, modifier = Modifier.weight(1f)) { Text("Mi ubicación") }
                OutlinedButton(onClick = { recenter++ }, modifier = Modifier.weight(1f)) { Text("Centrar mapa") }
            }
            if (BuildConfig.ORS_API_KEY.isBlank()) Text(RouteError.MISSING_KEY.message,
                color = MaterialTheme.colorScheme.error)
            Button(enabled = !busy && state.destination != null && BuildConfig.ORS_API_KEY.isNotBlank(),
                modifier = Modifier.fillMaxWidth(), onClick = {
                checkNetwork()
                if (routeConsent) {
                    if (online) startRoute() else launchPending = true
                } else showConsent = true
            }) { Text(if (state.routing) "Calculando…" else if (route != null) "Actualizar ruta" else "Calcular ruta a pie") }
            if (state.routing && state.location != GpsLocationState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            (state.route as? WalkingRouteResult.Failure)?.let {
                Text(it.error.message, color = MaterialTheme.colorScheme.error)
            }
            route?.let {
                Text("Ruta a pie: ${formatOutdoorDistance(it.distanceMeters)}" +
                    (it.durationSeconds?.let { seconds -> " · ${formatWalkingDuration(seconds)} estimados" } ?: ""),
                    style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showSteps = !showSteps }) {
                    Text(if (showSteps) "Ocultar pasos" else "Ver instrucciones (${it.steps.size})")
                }
                if (showSteps) it.steps.forEachIndexed { index, step -> WalkingStepRow(step, index + 1) }
            }
        }
        Text("Rutas: © openrouteservice / HeiGIT", Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall)
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
