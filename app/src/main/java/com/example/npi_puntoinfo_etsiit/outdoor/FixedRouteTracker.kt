package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.DeviceCoordinates

data class TrackingPolicy(
    /** Umbral de aviso de precisión, NO bloqueo de la selección del paso. */
    val maxAccuracyMeters: Double = 60.0,
    val maxFixAgeMillis: Long = 30_000,
    val minReadingIntervalMillis: Long = 1_500,
    val maxReadingGapMillis: Long = 20_000,
    val requiredReadings: Int = 3,
    val arrivalRadiusMeters: Double = 8.0,
    val arrivalMaxAccuracyMeters: Double = 8.0,
    val arrivalMaxFixAgeMillis: Long = 10_000,
    val corridorMeters: Double = 18.0,
    val offRouteMeters: Double = 35.0,
    val rejoinMeters: Double = 20.0,
    val maxSpeedMetersPerSecond: Double = 4.0,
    val updateIntervalMillis: Long = 2_000,
    val firstFixTimeoutMillis: Long = 15_000,
    val maneuverClearanceMeters: Double = 18.0,
    val nodeSwitchMarginMeters: Double = 5.0,
    val nodeSwitchReadings: Int = 2
) {
    init {
        require(listOf(maxAccuracyMeters, arrivalRadiusMeters, arrivalMaxAccuracyMeters,
            corridorMeters, offRouteMeters, rejoinMeters, maxSpeedMetersPerSecond,
            maneuverClearanceMeters, nodeSwitchMarginMeters).all { it.isFinite() && it > 0 })
        require(offRouteMeters > rejoinMeters && arrivalMaxAccuracyMeters <= maxAccuracyMeters)
        require(requiredReadings >= 2 && nodeSwitchReadings >= 2 && maxFixAgeMillis > 0 && minReadingIntervalMillis > 0)
        require(arrivalMaxFixAgeMillis > 0)
        require(maxReadingGapMillis >= minReadingIntervalMillis && updateIntervalMillis > 0 && firstFixTimeoutMillis > 0)
    }
}

enum class ProgressSource { NONE, MANUAL, GPS }

data class GuidanceState(
    val started: Boolean = false,
    val stepIndex: Int = 0,
    val arrived: Boolean = false,
    val source: ProgressSource = ProgressSource.NONE,
    val offRoute: Boolean = false,
    val segment: SegmentMatch? = null,
    val remainingMeters: Double? = null,
    val gpsMessage: String = "Esperando una posición del móvil. Puedes avanzar manualmente.",
    val positionConfirmed: Boolean = false,
    val remainingInStepMeters: Double? = null,
    val passedManeuver: Boolean = false,
    val nearestNodeId: String? = null,
    val nearestNodeDistanceMeters: Double? = null,
    val gpsProblem: Boolean = false,
    /** El GPS propone este paso, pero stepIndex solo cambia tras confirmación del usuario. */
    val suggestedStepIndex: Int? = null
)

/** Selección del paso por nodo más cercano. La geometría solo estima distancias/desviación. */
class FixedRouteTracker(val route: FixedOutdoorRoute, val policy: TrackingPolicy = TrackingPolicy()) {
    private val locator = RouteNodeLocator(route)
    var state = GuidanceState()
        private set
    private var lastFix: DeviceCoordinates? = null
    private var lastSeenTime: Long? = null
    private var candidateStep: Int? = null
    private var candidateReadings = 0
    private var arrivalReadings = 0
    private var deviationReadings = 0
    private var rejoinReadings = 0
    private var manualOverride = false
    private var returnedToCurrentReadings = 0
    private val dismissedSuggestions = mutableSetOf<Int>()

    fun start() { if (!state.started) state = state.copy(started = true) }
    fun restart() {
        state = GuidanceState(); clearEvidence(); lastSeenTime = null
        manualOverride = false; dismissedSuggestions.clear()
    }
    fun finish() { restart() }

    fun restoreSource(source: ProgressSource) {
        manualOverride = source == ProgressSource.MANUAL
        state = state.copy(source = source, positionConfirmed = source != ProgressSource.NONE)
    }

    fun selectManualMode() {
        manualOverride = true
        state = state.copy(source = ProgressSource.MANUAL, positionConfirmed = true)
        pauseGps("Modo manual · puedes seguir las instrucciones sin GPS.")
    }

    fun resumeGps() {
        manualOverride = false; clearEvidence(); lastSeenTime = null
        state = state.copy(suggestedStepIndex = null)
    }

    fun dismissedSuggestionSteps(): Set<Int> = dismissedSuggestions.toSet()

    fun restoreDismissedSuggestions(steps: Collection<Int>) {
        dismissedSuggestions.clear()
        dismissedSuggestions.addAll(steps.filter { it in route.waypoints.indices && it != state.stepIndex })
    }

    fun confirmSuggestedStep() {
        val suggested = state.suggestedStepIndex ?: return
        manualOverride = false
        dismissedSuggestions.clear()
        candidateStep = null; candidateReadings = 0; returnedToCurrentReadings = 0; arrivalReadings = 0
        state = state.copy(stepIndex = suggested, arrived = suggested == route.waypoints.lastIndex,
            source = ProgressSource.GPS, positionConfirmed = true, suggestedStepIndex = null,
            remainingMeters = if (suggested == route.waypoints.lastIndex) 0.0 else null,
            remainingInStepMeters = null, passedManeuver = false,
            gpsMessage = "Cambio de paso confirmado. El GPS seguirá proponiendo el siguiente avance.")
    }

    fun dismissSuggestedStep() {
        state.suggestedStepIndex?.let { dismissedSuggestions += it }
        candidateStep = null; candidateReadings = 0; returnedToCurrentReadings = 0; arrivalReadings = 0
        state = state.copy(suggestedStepIndex = null,
            gpsMessage = "Se mantiene el paso actual. Puedes avanzar manualmente cuando quieras.")
    }

    private fun clearEvidence() {
        lastFix = null; candidateStep = null; candidateReadings = 0
        arrivalReadings = 0; deviationReadings = 0; rejoinReadings = 0; returnedToCurrentReadings = 0
    }

    /** No mantener estimaciones obsoletas ni bloquear la próxima relocalización. */
    fun pauseGps(message: String = "Seguimiento GPS pausado. Puedes avanzar manualmente.") {
        clearEvidence(); lastSeenTime = null
        state = state.copy(segment = null, remainingMeters = if (state.arrived) 0.0 else null,
            remainingInStepMeters = null, passedManeuver = false, nearestNodeId = null,
            nearestNodeDistanceMeters = null, gpsMessage = message, gpsProblem = true, suggestedStepIndex = null)
    }

    fun next() {
        if (!state.started || state.arrived) return
        val index = (state.stepIndex + 1).coerceAtMost(route.waypoints.lastIndex)
        selectManualMode()
        dismissedSuggestions.clear()
        state = state.copy(stepIndex = index, arrived = index == route.waypoints.lastIndex,
            offRoute = false, remainingMeters = null)
    }

    fun previous() {
        if (!state.started) return
        selectManualMode()
        dismissedSuggestions.clear()
        state = state.copy(stepIndex = (state.stepIndex - 1).coerceAtLeast(0), arrived = false,
            offRoute = false, remainingMeters = null)
    }

    fun observe(fix: DeviceCoordinates, nowMillis: Long): GuidanceState {
        if (!state.started || state.arrived) return state
        if (!route.gpsReady) {
            pauseGps("No hay coordenadas disponibles para localizar el paso. Usa el avance manual.")
            return state
        }
        val time = fix.elapsedRealtimeMillis
        if (!fix.isValid || time == null || time < 0) {
            pauseGps("Posición GPS inválida. Se mantiene el último paso; puedes avanzar manualmente.")
            return state
        }
        val age = nowMillis - time
        if (age !in 0..policy.maxFixAgeMillis) {
            pauseGps("La posición GPS es antigua o su tiempo no es válido. Esperando una posición reciente.")
            return state
        }
        val elapsed = lastSeenTime?.let { time - it }
        if (elapsed != null && elapsed < policy.minReadingIntervalMillis) return state
        lastSeenTime = time
        if (elapsed != null && elapsed > policy.maxReadingGapMillis) {
            clearEvidence()
            state = state.copy(suggestedStepIndex = null)
        }
        val point = GeoPoint(fix.latitude, fix.longitude)
        val previous = lastFix
        if (previous != null) {
            val dt = (time - previous.elapsedRealtimeMillis!!) / 1000.0
            if (RouteGeometry.distance(point, GeoPoint(previous.latitude, previous.longitude)) >
                policy.maxSpeedMetersPerSecond * dt + fix.accuracyMeters + previous.accuracyMeters) {
                candidateStep = null; candidateReadings = 0; arrivalReadings = 0; returnedToCurrentReadings = 0
                state = state.copy(segment = null, remainingMeters = null, remainingInStepMeters = null,
                    passedManeuver = false, gpsProblem = true, suggestedStepIndex = null,
                    gpsMessage = "Salto GPS descartado; se mantiene el paso.")
                return state
            }
        }
        lastFix = fix
        val nearest = locator.nearest(point)!!
        // El nodo final identifica el último tramo, pero NO confirma por sí solo la llegada.
        val closestStep = nearest.node.stepIndex.coerceAtMost(route.waypoints.lastIndex - 1)
        var index = state.stepIndex
        if (!manualOverride) {
            if (!state.positionConfirmed) {
                index = closestStep // Primera posición reciente: selección inmediata, sin corredor ni anclaje.
            } else if (closestStep != index) {
                returnedToCurrentReadings = 0
                val currentDistance = locator.nearest(point, index)?.distanceMeters ?: Double.POSITIVE_INFINITY
                if (closestStep !in dismissedSuggestions &&
                    nearest.distanceMeters + policy.nodeSwitchMarginMeters < currentDistance) {
                    if (candidateStep != closestStep) { candidateStep = closestStep; candidateReadings = 0 }
                    candidateReadings++
                    if (candidateReadings >= policy.nodeSwitchReadings) state = state.copy(suggestedStepIndex = closestStep)
                } else { candidateStep = null; candidateReadings = 0 }
            } else {
                candidateStep = null; candidateReadings = 0
                val suggested = state.suggestedStepIndex
                if (suggested != null && suggested != route.waypoints.lastIndex) {
                    val suggestedDistance = locator.nearest(point, suggested)?.distanceMeters ?: Double.POSITIVE_INFINITY
                    if (nearest.distanceMeters + policy.nodeSwitchMarginMeters < suggestedDistance) returnedToCurrentReadings++
                    else returnedToCurrentReadings = 0
                    if (returnedToCurrentReadings >= policy.nodeSwitchReadings) state = state.copy(suggestedStepIndex = null)
                }
            }
        }
        if (index != state.stepIndex) { candidateStep = null; candidateReadings = 0; arrivalReadings = 0 }

        // Estas proyecciones no intervienen en elegir el paso.
        val match = RouteGeometry.nearest(point, route)!!
        val accuracy = fix.accuracyMeters.toDouble()
        if (match.distanceMeters - accuracy > policy.offRouteMeters) {
            deviationReadings++; rejoinReadings = 0
            if (deviationReadings >= policy.requiredReadings) state = state.copy(offRoute = true)
        } else if (match.distanceMeters + accuracy < policy.rejoinMeters) {
            deviationReadings = 0; rejoinReadings++
            if (rejoinReadings >= policy.requiredReadings) state = state.copy(offRoute = false)
        } else { deviationReadings = 0; rejoinReadings = 0 }
        if (state.offRoute) state = state.copy(suggestedStepIndex = null)
        val segment = RouteGeometry.projectOnStep(point, route, index)
        val remainingInStep = if (!state.offRoute && accuracy <= policy.maxAccuracyMeters &&
            segment.distanceMeters + accuracy <= policy.corridorMeters)
            RouteGeometry.stepLength(route, index) * (1 - segment.fraction) else null
        val remaining = remainingInStep?.let { meters -> meters +
            (index + 1 until route.waypoints.lastIndex).sumOf { RouteGeometry.stepLength(route, it) } }
        val message = when {
            manualOverride -> "Paso manual mantenido. Activa de nuevo el GPS para elegir el punto más cercano."
            state.suggestedStepIndex != null -> "El GPS propone otro paso. Confirma el cambio para continuar."
            closestStep != index && closestStep in dismissedSuggestions -> "Se mantiene el paso actual por tu elección."
            accuracy > policy.maxAccuracyMeters -> "Ubicación poco precisa (±${accuracy.toInt()} m): paso más cercano orientativo."
            else -> "Paso elegido por el nodo más cercano · GPS ±${accuracy.toInt()} m."
        }
        state = state.copy(stepIndex = index, positionConfirmed = true,
            source = if (manualOverride) ProgressSource.MANUAL else ProgressSource.GPS,
            segment = match, remainingMeters = remaining, remainingInStepMeters = remainingInStep,
            passedManeuver = nearest.node.stepIndex == index &&
                RouteGeometry.stepLength(route, index) * segment.fraction - accuracy >= policy.maneuverClearanceMeters,
            nearestNodeId = nearest.node.id, nearestNodeDistanceMeters = nearest.distanceMeters,
            gpsMessage = message, gpsProblem = false)

        val distanceToArrival = RouteGeometry.distance(point, route.waypoints.last().point!!)
        if (!manualOverride && !state.offRoute && index == route.waypoints.lastIndex - 1 &&
            accuracy <= policy.arrivalMaxAccuracyMeters && age <= policy.arrivalMaxFixAgeMillis &&
            distanceToArrival + accuracy * 0.5 <= policy.arrivalRadiusMeters) arrivalReadings++
        else arrivalReadings = 0
        if (arrivalReadings >= policy.requiredReadings && route.waypoints.lastIndex !in dismissedSuggestions)
            state = state.copy(suggestedStepIndex = route.waypoints.lastIndex)
        // Si se pierde la proximidad fiable al destino, retirar una confirmación de llegada ya obsoleta.
        if (state.suggestedStepIndex == route.waypoints.lastIndex && arrivalReadings == 0)
            state = state.copy(suggestedStepIndex = null)
        return state
    }
}
