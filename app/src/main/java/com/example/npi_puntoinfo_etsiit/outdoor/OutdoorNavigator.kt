package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.GpsLocationReader
import com.example.npi_puntoinfo_etsiit.sensors.GpsLocationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Adaptador GPS del motor puro. No conoce servicios de rutas ni proveedores cartográficos. */
class OutdoorNavigator(val tracker: FixedRouteTracker, private val gps: GpsLocationReader) {
    private val mutableState = MutableStateFlow(tracker.state)
    val state = mutableState.asStateFlow()

    fun start() { tracker.start(); publish() }
    fun next() { tracker.next(); publish() }
    fun previous() { tracker.previous(); publish() }
    fun restart() { tracker.restart(); publish() }
    fun finish() { tracker.finish(); publish() }
    fun manual() { tracker.selectManualMode(); publish() }
    fun resumeGps() { tracker.resumeGps(); publish() }
    fun confirmSuggestedStep() { tracker.confirmSuggestedStep(); publish() }
    fun dismissSuggestedStep() { tracker.dismissSuggestedStep(); publish() }
    fun pause() { tracker.pauseGps(); publish() }
    private fun publish() { mutableState.value = tracker.state }

    /** La UI cancela esta función al dejar RESUMED, desactivar GPS o terminar el recorrido. */
    suspend fun track(nowMillis: () -> Long) = coroutineScope {
        if (!tracker.state.started || tracker.state.arrived || !tracker.route.gpsReady) return@coroutineScope
        // También informar si el proveedor no emite ni siquiera una primera posición.
        // El flujo sigue vivo: una fijación posterior selecciona el nodo inmediatamente.
        var expiry: Job? = launch {
            delay(tracker.policy.firstFixTimeoutMillis)
            tracker.pauseGps("No se recibe ubicación del móvil. Activa la ubicación o utiliza el avance manual.")
            publish()
        }
        try {
            gps.updates(tracker.policy.updateIntervalMillis).collect { location ->
                expiry?.cancel()
                val observedAt = nowMillis()
                when (location) {
                    is GpsLocationState.Available -> tracker.observe(location.coordinates, observedAt)
                    else -> tracker.pauseGps(when (location) {
                        GpsLocationState.PermissionDenied -> "Sin permiso de ubicación. Continúa manualmente."
                        GpsLocationState.LocationDisabled -> "Ubicación desactivada. Continúa manualmente."
                        else -> "GPS no disponible. Continúa manualmente."
                    })
                }
                publish()
                // Si el proveedor queda silencioso, no mantener distancia/precisión obsoletas.
                val age = (location as? GpsLocationState.Available)?.coordinates?.elapsedRealtimeMillis
                    ?.let { (observedAt - it).coerceAtLeast(0) } ?: 0
                expiry = launch {
                    delay((tracker.policy.maxFixAgeMillis - age).coerceAtLeast(0))
                    tracker.pauseGps("Sin posición GPS reciente. Continúa manualmente.")
                    publish()
                }
            }
        } finally {
            expiry?.cancel()
            tracker.pauseGps(if (tracker.state.segment != null) "Seguimiento GPS pausado. Puedes avanzar manualmente."
                else tracker.state.gpsMessage)
            publish()
        }
    }
}
