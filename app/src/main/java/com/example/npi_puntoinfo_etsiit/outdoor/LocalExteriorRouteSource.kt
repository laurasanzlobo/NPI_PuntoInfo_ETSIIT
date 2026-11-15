package com.example.npi_puntoinfo_etsiit.outdoor

import android.content.Context

/**
 * Proveedor local de rutas exteriores calibradas (ETSIIT <-> Edificio Auxiliar).
 * Cualquier guiado exterior utiliza directamente los nodos y fotografías reales
 * de guidance_exterior.json sin depender de APIs externas de enrutamiento.
 */
class LocalExteriorRouteSource(
    private val context: Context,
    private val destinationIdProvider: () -> String? = { null }
) : WalkingRouteSource {

    override suspend fun route(origin: GeoPoint, destination: GeoPoint): WalkingRouteResult {
        if (!origin.isValid || !destination.isValid) {
            return WalkingRouteResult.Failure(RouteError.INVALID_POSITION)
        }
        val localRoute = GuidanceExteriorRepository.getRouteForDestination(
            context = context,
            destinationId = destinationIdProvider(),
            origin = origin
        )
        return if (localRoute != null) {
            WalkingRouteResult.Success(localRoute)
        } else {
            WalkingRouteResult.Failure(RouteError.NO_ROUTE)
        }
    }
}
