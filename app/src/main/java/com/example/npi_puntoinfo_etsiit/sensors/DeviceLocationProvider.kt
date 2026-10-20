package com.example.npi_puntoinfo_etsiit.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Fijaciones puntuales y suscripción cancelable solo durante una ruta exterior visible. */
class DeviceLocationProvider(context: Context) : DeviceLocationSource {
    private val appContext = context.applicationContext
    private val client by lazy { LocationServices.getFusedLocationProviderClient(appContext) }

    override fun permission(): LocationPermission {
        fun granted(permission: String) = ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
        return when {
            granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationPermission.PRECISE
            granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationPermission.APPROXIMATE
            else -> LocationPermission.DENIED
        }
    }

    override fun isLocationEnabled(): Boolean {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    @SuppressLint("MissingPermission") // Se comprueba también justo antes de solicitar la fijación.
    override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? {
        if (permission == LocationPermission.DENIED || permission() == LocationPermission.DENIED) {
            throw SecurityException("Permiso de ubicación no concedido")
        }
        val request = CurrentLocationRequest.Builder()
            .setPriority(
                if (permission == LocationPermission.PRECISE) Priority.PRIORITY_HIGH_ACCURACY
                else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            )
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setMaxUpdateAgeMillis(0) // La proximidad requiere fijaciones nuevas, no repetir la caché.
            .setDurationMillis(15_000)
            .build()
        return withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationTokenSource()
                continuation.invokeOnCancellation { cancellation.cancel() }
                client.getCurrentLocation(request, cancellation.token)
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) {
                            continuation.resume(location?.takeIf { it.hasAccuracy() }?.let {
                                DeviceCoordinates(it.latitude, it.longitude, it.accuracy, it.elapsedRealtimeNanos / 1_000_000)
                            })
                        }
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun updates(permission: LocationPermission, intervalMillis: Long) = callbackFlow {
        if (permission == LocationPermission.DENIED || permission() == LocationPermission.DENIED) {
            close(SecurityException("Permiso de ubicación no concedido"))
            return@callbackFlow
        }
        val request = LocationRequest.Builder(
            if (permission == LocationPermission.PRECISE) Priority.PRIORITY_HIGH_ACCURACY
            else Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis)
            .setMaxUpdateAgeMillis(0)
            .setMaxUpdateDelayMillis(0)
            .setWaitForAccurateLocation(false) // El detector, no Fused, valida ±25 m y dos fijaciones.
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .build()
        var previousFix: Long? = null
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    val time = location.elapsedRealtimeNanos / 1_000_000
                    Log.d("NavigationGps", "fix intervalMs=${previousFix?.let { time - it }} requestedMs=$intervalMillis " +
                        "ageMs=${android.os.SystemClock.elapsedRealtime() - time} accuracyM=${location.accuracy}")
                    previousFix = time
                    trySend(location.takeIf { it.hasAccuracy() }?.let {
                        DeviceCoordinates(it.latitude, it.longitude, it.accuracy, time)
                    })
                }
            }
        }
        Log.d("NavigationGps", "subscribe requestedMs=$intervalMillis")
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { close(it) }
        awaitClose {
            client.removeLocationUpdates(callback)
            Log.d("NavigationGps", "unsubscribe requestedMs=$intervalMillis")
        }
    }
}
