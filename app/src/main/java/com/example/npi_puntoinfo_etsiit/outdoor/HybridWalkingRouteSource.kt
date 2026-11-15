package com.example.npi_puntoinfo_etsiit.outdoor

import android.content.Context
import com.example.npi_puntoinfo_etsiit.sensors.OutdoorDistances

/**
 * Proveedor de rutas híbrido:
 * - Para la ruta exterior entre ETSIIT Principal y Edificio Auxiliar (o Comedor Aynadamar),
 *   utiliza los nodos locales calibrados con fotos reales de guidance_exterior.json.
 * - Para otros destinos lejanos (ej. Comedor PTS, Fuentenueva, Cartuja), delega en el
 *   proveedor de red (OpenRouteService).
 */
class HybridWalkingRouteSource(
    private val context: Context,
    private val fallback: WalkingRouteSource,
    private val destinationIdProvider: () -> String? = { null }
) : WalkingRouteSource {

    override suspend fun route(origin: GeoPoint, destination: GeoPoint): WalkingRouteResult {
        val destId = destinationIdProvider()

        // 1. Comprobar si el ID de destino corresponde a la ruta local
        val isLocalById = GuidanceExteriorRepository.isLocalGuidanceRoute(destId)

        // 2. Comprobar por proximidad geográfica a los extremos de nuestra ruta
        val distToAux = OutdoorDistances.distanceMeters(destination.latitude, destination.longitude, 37.197846, -3.630084)
        val distToEtsiit = OutdoorDistances.distanceMeters(destination.latitude, destination.longitude, 37.196873, -3.624742)
        val isLocalByCoords = distToAux < 75.0 || distToEtsiit < 75.0

        if (isLocalById || isLocalByCoords) {
            val localRoute = GuidanceExteriorRepository.getRouteForDestination(
                context = context,
                destinationId = destId,
                origin = origin
            )
            if (localRoute != null) {
                return WalkingRouteResult.Success(localRoute)
            }
        }

        // Si no es la ruta local del campus, utilizar el fallback remoto
        return fallback.route(origin, destination)
    }
}
