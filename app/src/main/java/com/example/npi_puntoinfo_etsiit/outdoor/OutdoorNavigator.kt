package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.GpsLocationReader
import com.example.npi_puntoinfo_etsiit.sensors.GpsLocationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class OutdoorUiState(
    val destination: OutdoorDestination?,
    val location: GpsLocationState,
    val route: WalkingRouteResult? = null,
    val routing: Boolean = false
)

/** Estado de una sola pantalla; las comprobaciones activas dependen del ciclo de vida de su UI. */
class OutdoorNavigator(
    private val gps: GpsLocationReader,
    private val routes: WalkingRouteSource,
    initialDestinationId: String?
) {
    private val mutableState = MutableStateFlow(OutdoorUiState(
        OutdoorDestinations.find(initialDestinationId), gps.accessState()))
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var requestVersion = 0

    fun selectDestination(id: String) {
        requestVersion++
        job?.cancel()
        mutableState.value = mutableState.value.copy(
            destination = OutdoorDestinations.find(id), route = null, routing = false,
            location = mutableState.value.location.takeUnless { it == GpsLocationState.Loading }
                ?: gps.accessState())
    }

    fun refreshLocation(scope: CoroutineScope) {
        if (job?.isActive == true) return
        requestVersion++
        mutableState.value = mutableState.value.copy(location = GpsLocationState.Loading, route = null)
        job = scope.launch {
            val location = gps.read()
            coroutineContext.ensureActive()
            mutableState.value = mutableState.value.copy(location = location)
        }
    }

    /** Tras consentimiento explícito. Obtiene una fijación nueva en cada cálculo. */
    fun requestRoute(scope: CoroutineScope) {
        if (job?.isActive == true || mutableState.value.destination == null) return
        val destination = mutableState.value.destination!!
        val version = ++requestVersion
        mutableState.value = mutableState.value.copy(location = GpsLocationState.Loading, route = null, routing = true)
        job = scope.launch {
            try {
                val location = gps.read()
                coroutineContext.ensureActive()
                mutableState.value = mutableState.value.copy(location = location)
                if (location is GpsLocationState.Available) {
                    val point = location.coordinates.let { GeoPoint(it.latitude, it.longitude) }
                    val result = routes.route(point, destination.point)
                    coroutineContext.ensureActive()
                    mutableState.value = mutableState.value.copy(route = result)
                }
            } finally {
                if (requestVersion == version) mutableState.value = mutableState.value.copy(routing = false)
            }
        }
    }

    fun onStart() {
        val access = gps.accessState()
        if (access != GpsLocationState.Idle) {
            requestVersion++
            job?.cancel()
            mutableState.value = mutableState.value.copy(location = access, route = null, routing = false)
        }
    }

    /** Refresca solo el marcador/proximidad, sin recalcular ni reenviar posiciones a ORS. */
    suspend fun updateActivePosition() {
        if (job?.isActive == true || mutableState.value.route !is WalkingRouteResult.Success) return
        val version = requestVersion
        val location = gps.read()
        coroutineContext.ensureActive()
        if (requestVersion == version && mutableState.value.route is WalkingRouteResult.Success) {
            mutableState.value = mutableState.value.copy(location = location)
        }
    }

    /** La cancelación del scope STARTED retira la suscripción, sin consultar ORS. */
    suspend fun trackActivePosition(
        intervalMillis: Long,
        requireRoute: Boolean = true,
        onLocation: (GpsLocationState) -> Unit
    ) {
        fun canTrack() = mutableState.value.destination != null &&
            (!requireRoute || mutableState.value.route is WalkingRouteResult.Success)
        if (!canTrack()) return
        val version = requestVersion
        val destinationId = mutableState.value.destination?.id
        gps.updates(intervalMillis).collect { location ->
            coroutineContext.ensureActive()
            // Refrescar/calcular la ruta no debe silenciar la proximidad independiente de ORS.
            val sameRequest = if (requireRoute) requestVersion == version
                else mutableState.value.destination?.id == destinationId
            if (sameRequest && canTrack()) {
                mutableState.value = mutableState.value.copy(location = location)
                onLocation(location)
            }
        }
    }

    fun onStop() {
        requestVersion++
        job?.cancel()
        mutableState.value = mutableState.value.copy(location = gps.accessState(), route = null, routing = false)
    }
}
