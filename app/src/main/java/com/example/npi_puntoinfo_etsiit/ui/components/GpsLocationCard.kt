package com.example.npi_puntoinfo_etsiit.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.npi_puntoinfo_etsiit.sensors.DeviceLocationProvider
import com.example.npi_puntoinfo_etsiit.sensors.GpsLocationReader
import com.example.npi_puntoinfo_etsiit.sensors.GpsLocationState
import com.example.npi_puntoinfo_etsiit.sensors.LocationPermission
import com.example.npi_puntoinfo_etsiit.sensors.OutdoorDistances
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GpsLocationCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reader = remember(context) { GpsLocationReader(DeviceLocationProvider(context)) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<GpsLocationState>(reader.accessState()) }
    var requestJob by remember { mutableStateOf<Job?>(null) }

    fun refresh() {
        if (requestJob?.isActive == true) return
        state = GpsLocationState.Loading
        requestJob = scope.launch { state = reader.read() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val access = reader.accessState()
        if (access == GpsLocationState.Idle) refresh() else state = access
    }

    DisposableEffect(lifecycleOwner, reader) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    val access = reader.accessState()
                    if (state != GpsLocationState.Loading) state = access
                }
                Lifecycle.Event.ON_STOP -> {
                    requestJob?.cancel()
                    state = GpsLocationState.Idle
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            requestJob?.cancel()
        }
    }

    fun openSettings(action: String) {
        val intent = Intent(action).apply {
            if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                data = "package:${context.packageName}".toUri()
            }
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            state = GpsLocationState.Unavailable
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Ubicación exterior", style = MaterialTheme.typography.titleMedium)
            when (val current = state) {
                GpsLocationState.Idle -> Text("Obtén tu ubicación para consultar los comedores cercanos.")
                GpsLocationState.Loading -> {
                    CircularProgressIndicator()
                    Text("Buscando ubicación… Puede tardar unos segundos.")
                }
                GpsLocationState.PermissionDenied -> {
                    Text("Sin permiso de ubicación. Puedes permitir ubicación aproximada o precisa. " +
                        "Si Android ya no muestra la solicitud, activa el permiso en Ajustes.")
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
                GpsLocationState.Unavailable -> Text(
                    "No se ha podido obtener una ubicación actual. " +
                        "Prueba al aire libre y comprueba los servicios de ubicación."
                )
                is GpsLocationState.Available -> {
                    val position = current.coordinates
                    Text(if (current.permission == LocationPermission.PRECISE) {
                        "Permiso de ubicación precisa"
                    } else "Permiso de ubicación aproximada")
                    Text(String.format(Locale.getDefault(), "Latitud: %.6f\nLongitud: %.6f",
                        position.latitude, position.longitude))
                    Text(String.format(Locale.getDefault(), "Precisión estimada: ±%.0f m",
                        position.accuracyMeters))
                    val distances = remember(position) {
                        OutdoorDistances.distancesFrom(position.latitude, position.longitude)
                    }
                    distances.firstOrNull()?.let { nearest ->
                        Text("Más cercano por distancia geográfica: ${nearest.hall.name}",
                            fontWeight = FontWeight.Bold)
                    }
                    distances.forEach { distance ->
                        val formatted = if (distance.meters < 1000) {
                            "${distance.meters.roundToInt()} m"
                        } else String.format(Locale.getDefault(), "%.2f km", distance.meters / 1000)
                        Text("${distance.hall.name}: $formatted")
                    }
                    Text("Distancias en línea recta, no rutas peatonales. La precisión de la " +
                        "ubicación puede cambiar cuál es el comedor más cercano.")
                }
            }
            Button(
                enabled = state != GpsLocationState.Loading,
                onClick = {
                    if (reader.accessState() == GpsLocationState.PermissionDenied) {
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    } else refresh()
                }
            ) {
                Text(when (state) {
                    GpsLocationState.PermissionDenied -> "Permitir ubicación"
                    is GpsLocationState.Available -> "Actualizar ubicación"
                    else -> "Obtener ubicación"
                })
            }
            Text("Consulta puntual al pulsar; sin historial de ubicaciones.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
