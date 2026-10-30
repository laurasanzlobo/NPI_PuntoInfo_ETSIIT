package com.example.npi_puntoinfo_etsiit.ui.components

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.npi_puntoinfo_etsiit.outdoor.*
import com.example.npi_puntoinfo_etsiit.sensors.*

/** Solo se monta al solicitar Guiado, también con destino a un aula, profesor o comedor ETSIIT. */
@Composable
fun AutomaticOutdoorEntry(
    manualIndoor: Boolean,
    onAutomatic: () -> Unit,
    onOutside: () -> Unit,
    onOpenOutdoor: () -> Unit,
    onOpenIndoor: () -> Unit,
    gpsReader: GpsLocationReader? = null,
    nowMillis: () -> Long = { SystemClock.elapsedRealtime() }
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val gps = remember(context, gpsReader) { gpsReader ?: GpsLocationReader(DeviceLocationProvider(context)) }
    val detector = remember { EtsiitPresenceDetector() }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Comprobando ubicación…") }
    val outside by rememberUpdatedState(onOutside)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { attempt++ }

    LaunchedEffect(gps, lifecycle, manualIndoor, attempt) {
        if (manualIndoor) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            detector.reset()
            if (gps.accessState() == GpsLocationState.PermissionDenied && !permissionRequested) {
                permissionRequested = true
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            } else {
                var opened = false
                gps.updates(detector.policy.updateIntervalMillis).collect { location ->
                    when (location) {
                        is GpsLocationState.Available -> {
                            when (detector.observe(location.coordinates, nowMillis())) {
                                EtsiitPresence.OUTSIDE -> if (!opened) { opened = true; outside() }
                                EtsiitPresence.NEAR_BUILDING -> message = "Cerca de la ETSIIT · guiado interior"
                                EtsiitPresence.UNKNOWN -> message = "Confirmando ubicación · puedes elegir el guiado"
                            }
                        }
                        else -> {
                            detector.clearEvidence()
                            message = when (location) {
                                GpsLocationState.PermissionDenied -> "Sin permiso de ubicación · elige el guiado manualmente"
                                GpsLocationState.LocationDisabled -> "Ubicación desactivada · elige el guiado manualmente"
                                else -> "Ubicación no disponible · elige el guiado manualmente"
                            }
                        }
                    }
                }
            }
        }
    }
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            GuidanceModeButtons(outdoor = false, onIndoor = onOpenIndoor, onOutdoor = onOpenOutdoor)
            Text(if (manualIndoor) "Guiado interior seleccionado" else message, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (manualIndoor) TextButton(onClick = onAutomatic) { Text("Detectar ubicación") }
                else if (gps.accessState() == GpsLocationState.PermissionDenied) TextButton(onClick = {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text("Permitir ubicación") }
            }
        }
    }
}
