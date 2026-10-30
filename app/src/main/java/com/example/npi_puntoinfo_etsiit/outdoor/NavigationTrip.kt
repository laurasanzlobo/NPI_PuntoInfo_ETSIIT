package com.example.npi_puntoinfo_etsiit.outdoor

import java.util.UUID

enum class TripPhase { OUTDOOR, INDOOR, FINISHED }

/** Estado del recorrido, propiedad de AppNavigation, no de las pantallas ni del receptor GPS. */
data class NavigationTrip(
    val id: String,
    val destinationId: String,
    val indoor: NavigationJourney.Indoor?,
    val arrivalNotified: Boolean = false,
    val presentation: ArrivalPresentation = ArrivalPresentation.WAITING,
    val phase: TripPhase = TripPhase.OUTDOOR
) {
    val showCompactEntry: Boolean get() = indoor != null && phase == TripPhase.OUTDOOR &&
        presentation in setOf(ArrivalPresentation.SHOWN, ArrivalPresentation.DEFERRED,
            ArrivalPresentation.CONTINUED, ArrivalPresentation.RETURNED)

    fun onArrival(): NavigationTrip = if (arrivalNotified || phase != TripPhase.OUTDOOR) this else
        copy(arrivalNotified = true, presentation = presentation.onArrival(indoor != null))

    fun markPromptShown(): NavigationTrip = if (presentation == ArrivalPresentation.PROMPT)
        copy(presentation = ArrivalPresentation.SHOWN) else this

    fun stayOutside(): NavigationTrip = if (phase != TripPhase.OUTDOOR || indoor == null) this else
        copy(arrivalNotified = true, presentation = presentation.defer())

    fun enterIndoors(): NavigationTrip = if (indoor == null || phase == TripPhase.FINISHED) this else
        copy(arrivalNotified = true, presentation = presentation.continueIndoors(), phase = TripPhase.INDOOR)

    fun returnOutdoors(): NavigationTrip = if (phase != TripPhase.INDOOR) this else
        copy(presentation = ArrivalPresentation.RETURNED, phase = TripPhase.OUTDOOR)

    fun finish(): NavigationTrip = copy(phase = TripPhase.FINISHED)

    /** Cambiar de modo es explícito y no certifica una llegada ni haber cruzado una puerta. */
    fun selectIndoorMode(): NavigationTrip = copy(phase = TripPhase.INDOOR)
    fun selectOutdoorMode(): NavigationTrip = copy(phase = TripPhase.OUTDOOR)

    // Solo identificadores y estado de UI: nunca posiciones GPS, rutas exteriores ni historial.
    fun savedValues(): List<String> = listOf(id, destinationId, indoor?.target?.name.orEmpty(),
        indoor?.target?.location.orEmpty(), indoor?.target?.nodeId.orEmpty(), indoor?.entryNodeId.orEmpty(),
        arrivalNotified.toString(), presentation.name, phase.name)

    companion object {
        /** El destino interior se conserva mientras se recorre auxiliar → ETSIIT. */
        fun startFixedOutdoor(indoor: NavigationJourney.Indoor? = null): NavigationTrip = NavigationTrip(UUID.randomUUID().toString(),
            EtsiitAuxiliaryRoute.destinationId, indoor = indoor)

        fun start(journey: NavigationJourney): NavigationTrip = when (journey) {
            is NavigationJourney.Combined -> NavigationTrip(UUID.randomUUID().toString(), journey.destination.id, journey.indoor)
            is NavigationJourney.Outdoor -> NavigationTrip(UUID.randomUUID().toString(), journey.destination.id, null)
            is NavigationJourney.Indoor -> error("El recorrido debe comenzar en el exterior")
        }

        fun restore(values: List<String>): NavigationTrip = NavigationTrip(values[0], values[1],
            if (values[4].isBlank()) null else NavigationJourney.Indoor(
                IndoorTarget(values[2], values[3], values[4]), values[5]),
            values[6].toBoolean(), ArrivalPresentation.valueOf(values[7]), TripPhase.valueOf(values[8]))
    }
}
