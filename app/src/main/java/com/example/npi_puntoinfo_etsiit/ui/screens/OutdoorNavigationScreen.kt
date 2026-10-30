package com.example.npi_puntoinfo_etsiit.ui.screens

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.npi_puntoinfo_etsiit.outdoor.*
import com.example.npi_puntoinfo_etsiit.sensors.DeviceLocationProvider
import com.example.npi_puntoinfo_etsiit.sensors.GpsLocationReader
import com.example.npi_puntoinfo_etsiit.sensors.GpsLocationState
import com.example.npi_puntoinfo_etsiit.ui.components.WalkingStepRow
import com.example.npi_puntoinfo_etsiit.ui.components.GuidanceModeButtons
import com.example.npi_puntoinfo_etsiit.ui.components.OutdoorStepConfirmationDialog

@Composable
fun OutdoorNavigationScreen(
    tripId: String,
    onFinish: () -> Unit,
    onOpenIndoors: () -> Unit,
    route: FixedOutdoorRoute = EtsiitAuxiliaryRoute.route,
    indoorDestinationLabel: String? = null,
    gpsReader: GpsLocationReader? = null,
    nowMillis: () -> Long = { SystemClock.elapsedRealtime() }
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val gps = remember(context, gpsReader) { gpsReader ?: GpsLocationReader(DeviceLocationProvider(context)) }
    // Al cambiar de pantalla/recrear se conserva el índice, nunca fijaciones ni evidencia GPS.
    val tracker = rememberSaveable(tripId, route.id, saver = listSaver<FixedRouteTracker, String>(
        save = { listOf(it.state.started.toString(), it.state.stepIndex.toString(), it.state.source.name,
            it.dismissedSuggestionSteps().joinToString(",")) },
        restore = { saved -> FixedRouteTracker(route).apply {
            if (saved[0].toBoolean()) {
                start()
                repeat(saved[1].toInt().coerceIn(0, route.waypoints.lastIndex)) { next() }
                restoreSource(ProgressSource.valueOf(saved[2]))
                restoreDismissedSuggestions(saved.getOrNull(3).orEmpty().split(",").mapNotNull { it.toIntOrNull() })
            }
        } }
    )) { FixedRouteTracker(route).apply { start() } }
    val navigator = remember(tracker, gps) { OutdoorNavigator(tracker, gps) }
    val state by navigator.state.collectAsState()
    var useGps by rememberSaveable(tripId) { mutableStateOf(true) }
    var permissionRequested by rememberSaveable(tripId) { mutableStateOf(false) }
    var permissionAttempt by remember { mutableIntStateOf(0) }
    var permissionDenied by rememberSaveable(tripId) { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionAttempt++
        useGps = it.values.any { granted -> granted }
        permissionDenied = !useGps
        if (permissionDenied) navigator.manual()
        else navigator.resumeGps()
    }
    LaunchedEffect(navigator, lifecycle, useGps, permissionAttempt, state.started, state.arrived) {
        if (useGps && state.started && !state.arrived && route.gpsReady) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (gps.accessState() == GpsLocationState.PermissionDenied && !permissionRequested) {
                    permissionRequested = true
                    permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else navigator.track(nowMillis)
            }
        } else navigator.pause()
    }
    DisposableEffect(navigator) { onDispose { navigator.pause() } }

    OutdoorGuidanceContent(route,
        if (permissionDenied) state.copy(gpsMessage = "Sin permiso de ubicación. Puedes avanzar manualmente.") else state,
        useGps, onStart = navigator::start, onNext = navigator::next, onPrevious = navigator::previous,
        onRestart = { navigator.restart(); navigator.start() },
        onGps = {
            if (useGps) { useGps = false; navigator.manual() }
            else { permissionDenied = false; permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
        },
        onFinish = { navigator.finish(); onFinish() }, onOpenIndoors = onOpenIndoors,
        onConfirmSuggestedStep = navigator::confirmSuggestedStep,
        onDismissSuggestedStep = navigator::dismissSuggestedStep,
        indoorDestinationLabel = indoorDestinationLabel)
}

/** Lista completa: no elimina pasos realizados ni oculta los futuros. Cabecera/controles fijos. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OutdoorGuidanceContent(
    route: FixedOutdoorRoute,
    state: GuidanceState,
    useGps: Boolean,
    onStart: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRestart: () -> Unit,
    onGps: () -> Unit,
    onFinish: () -> Unit,
    onOpenIndoors: () -> Unit,
    onConfirmSuggestedStep: () -> Unit,
    onDismissSuggestedStep: () -> Unit,
    indoorDestinationLabel: String? = null
) {
    state.suggestedStepIndex?.takeIf { it in route.waypoints.indices }?.let { suggested ->
        OutdoorStepConfirmationDialog(route, state.stepIndex, suggested,
            onConfirm = onConfirmSuggestedStep, onDismiss = onDismissSuggestedStep)
    }
    val requesters = remember(route.waypoints) { List(route.waypoints.size) { BringIntoViewRequester() } }
    val locating = useGps && !state.positionConfirmed
    LaunchedEffect(state.stepIndex, state.started, locating) {
        if (state.started && !locating) {
            withFrameNanos { }
            requesters[state.stepIndex].bringIntoView()
        }
    }
    Column(Modifier.fillMaxSize().testTag("outdoor-screen").padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GuidanceModeButtons(outdoor = true, onIndoor = onOpenIndoors, onOutdoor = {})
        Text(route.name, style = MaterialTheme.typography.titleLarge)
        indoorDestinationLabel?.let { Text("Destino interior: $it", style = MaterialTheme.typography.bodySmall) }
        if (!route.gpsReady) Text("No están disponibles los puntos verificados del itinerario. El avance GPS no está activo.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (state.started) {
            Text(if (locating) {
                if (state.gpsProblem) "Ubicación no disponible · usa el avance manual" else "Esperando la posición del móvil…"
            } else "Paso ${state.stepIndex + 1} de ${route.waypoints.size}",
                style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { if (locating) 0f else (state.stepIndex + 1).toFloat() / route.waypoints.size }, modifier = Modifier.fillMaxWidth())
            Text(when (state.source) {
                ProgressSource.NONE -> if (locating) "El primer dato reciente seleccionará el paso más cercano" else "Instrucciones disponibles sin GPS"
                ProgressSource.MANUAL -> "Avance manual · no confirmado por GPS"
                ProgressSource.GPS -> "Paso estimado por el nodo GPS más cercano"
            }, style = MaterialTheme.typography.bodySmall)
        }
        if (state.arrived) Text(when {
            !route.gpsReady -> "Recorrido marcado como completado; llegada sin verificar."
            state.source == ProgressSource.MANUAL -> "Llegada a la ETSIIT marcada manualmente."
            else -> "Has llegado a la ETSIIT (estimación GPS). Puedes continuar con el guiado interior."
        }, style = MaterialTheme.typography.titleMedium)
        if (state.offRoute) Text("Parece que te has alejado del recorrido previsto. Vuelve a la ruta señalada para continuar.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Text(state.remainingMeters?.let { "Distancia restante estimada: ${formatOutdoorDistance(it)}" }
            ?: "Distancia restante: esperando una posición fiable", style = MaterialTheme.typography.bodySmall)
        state.remainingInStepMeters?.takeIf { !state.arrived }?.let {
            Text("Hasta el siguiente punto: ${formatOutdoorDistance(it)}", style = MaterialTheme.typography.bodySmall)
        }

        Column(Modifier.weight(1f).fillMaxWidth().testTag("outdoor-instructions").verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            route.waypoints.forEachIndexed { index, waypoint ->
                Column(Modifier.bringIntoViewRequester(requesters[index])) {
                    val active = state.started && !locating && index == state.stepIndex
                    val continuing = active && state.source == ProgressSource.GPS && state.passedManeuver && waypoint.continuationInstruction != null
                    WalkingStepRow(WalkingStep(if (continuing) waypoint.continuationInstruction!! else waypoint.instruction,
                        if (active && state.source == ProgressSource.GPS) state.remainingInStepMeters else waypoint.distanceToNextMeters,
                        if (continuing) Maneuver.STRAIGHT else waypoint.maneuver), index + 1,
                        modifier = Modifier.testTag("waypoint-${waypoint.id}"),
                        active = active, completed = state.started && !locating && index < state.stepIndex)
                    waypoint.landmark?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Text(state.gpsMessage, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        if (!state.started) Button(onClick = onStart) { Text("Iniciar recorrido") }
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPrevious, enabled = state.stepIndex > 0, modifier = Modifier.weight(1f)) { Text("Paso anterior") }
            Button(onClick = onNext, enabled = !state.arrived, modifier = Modifier.weight(1f)) { Text("Siguiente paso") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (route.gpsReady) TextButton(onClick = onGps) { Text(if (useGps) "Pausar GPS" else "Activar GPS") }
            TextButton(onClick = onRestart) { Text("Reiniciar recorrido") }
            TextButton(onClick = onFinish) { Text("Finalizar") }
        }
    }
}
