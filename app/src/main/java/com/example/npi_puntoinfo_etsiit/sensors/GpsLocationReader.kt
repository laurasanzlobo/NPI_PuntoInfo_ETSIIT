package com.example.npi_puntoinfo_etsiit.sensors

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch

enum class LocationPermission { DENIED, APPROXIMATE, PRECISE }

data class DeviceCoordinates(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val elapsedRealtimeMillis: Long? = null
) {
    val isValid: Boolean
        get() = OutdoorDistances.validCoordinates(latitude, longitude) &&
            accuracyMeters.isFinite() && accuracyMeters >= 0
}

sealed interface GpsLocationState {
    data object Idle : GpsLocationState
    data object Loading : GpsLocationState
    data object PermissionDenied : GpsLocationState
    data object LocationDisabled : GpsLocationState
    data object Unavailable : GpsLocationState
    data class Available(
        val coordinates: DeviceCoordinates,
        val permission: LocationPermission
    ) : GpsLocationState
}

/** Adaptador inyectable para probar los estados sin depender del hardware Android. */
interface DeviceLocationSource {
    fun permission(): LocationPermission
    fun isLocationEnabled(): Boolean
    suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates?
    /** Alternativa para fuentes puntuales inyectadas; el dispositivo utiliza una suscripción Fused. */
    fun updates(permission: LocationPermission, intervalMillis: Long): Flow<DeviceCoordinates?> = flow {
        while (true) {
            emit(currentLocation(permission))
            delay(intervalMillis)
        }
    }
}

class GpsLocationReader(private val source: DeviceLocationSource) {
    fun updates(intervalMillis: Long): Flow<GpsLocationState> = flow {
        val access = accessState()
        if (access != GpsLocationState.Idle) {
            emit(access)
            return@flow
        }
        val permission = source.permission()
        source.updates(permission, intervalMillis).collect { coordinates ->
            val access = accessState()
            emit(when {
                access != GpsLocationState.Idle -> access
                source.permission() != permission -> GpsLocationState.Unavailable
                coordinates == null || !coordinates.isValid -> GpsLocationState.Unavailable
                else -> GpsLocationState.Available(coordinates, permission)
            })
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(if (error is SecurityException) GpsLocationState.PermissionDenied else GpsLocationState.Unavailable)
    }
    fun accessState(): GpsLocationState = when {
        source.permission() == LocationPermission.DENIED -> GpsLocationState.PermissionDenied
        !source.isLocationEnabled() -> GpsLocationState.LocationDisabled
        else -> GpsLocationState.Idle
    }

    suspend fun read(): GpsLocationState {
        return try {
            val access = accessState()
            if (access != GpsLocationState.Idle) return access
            val permission = source.permission()
            val coordinates = source.currentLocation(permission)
            // No publicar una posición si los permisos o ajustes cambiaron durante la petición.
            val finalAccess = accessState()
            if (finalAccess != GpsLocationState.Idle) return finalAccess
            if (source.permission() != permission) return GpsLocationState.Unavailable
            if (coordinates == null || !coordinates.isValid) GpsLocationState.Unavailable
            else GpsLocationState.Available(coordinates, permission)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            GpsLocationState.PermissionDenied
        } catch (_: Exception) {
            GpsLocationState.Unavailable
        }
    }
}
