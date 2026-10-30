package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.GrafoETSIIT

sealed interface OutdoorEntry {
    data object FixedRoute : OutdoorEntry
    data class EtsiitTarget(val indoor: NavigationJourney.Indoor) : OutdoorEntry
    data object Unavailable : OutdoorEntry
}

/** Solo los destinos ETSIIT pueden continuar desde la ruta fija; nunca otro campus. */
object OutdoorEntryPolicy {
    fun resolve(graph: GrafoETSIIT, destinationId: String?, finalName: String? = null,
                finalLocation: String = "", finalNodeId: String? = null): OutdoorEntry {
        if (destinationId == EtsiitAuxiliaryRoute.destinationId) {
            return if (finalName == null && finalNodeId == null) OutdoorEntry.FixedRoute else OutdoorEntry.Unavailable
        }
        val combined = if (finalName != null && destinationId == EtsiitAccess.outdoorId)
            EtsiitAccess.journey(graph, finalName, finalLocation, finalNodeId)
        else if (finalName == null) destinationId?.let { EtsiitAccess.diningJourney(graph, it) } else null
        return combined?.let { OutdoorEntry.EtsiitTarget(it.indoor) } ?: OutdoorEntry.Unavailable
    }
}
