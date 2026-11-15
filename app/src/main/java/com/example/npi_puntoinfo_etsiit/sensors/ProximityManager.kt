package com.example.npi_puntoinfo_etsiit.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

/**
 * Gestor del Sensor de Proximidad para Android / Jetpack Compose.
 *
 * Permite activar acciones físicas sin contacto (como el asistente de voz en Guiado):
 * - Acercar la mano a poca distancia de la parte superior del móvil.
 * - Incluye filtro de distancia reducida y tiempo mínimo de permanencia (debounce)
 *   para evitar activaciones accidentales al pulsar la flecha superior izquierda de retroceso.
 */
class ProximityManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingNearRunnable: Runnable? = null

    val isAvailable: Boolean
        get() = proximitySensor != null

    // Callbacks a Compose
    var onNear: (() -> Unit)? = null
    var onFar: (() -> Unit)? = null
    var onStateChanged: ((isNear: Boolean, distance: Float) -> Unit)? = null

    private var isCurrentlyNear = false
    private var lastNearTriggerTime = 0L
    private val COOLDOWN_MS = 2500L

    // Tiempo mínimo (ms) que la mano debe permanecer cerca del sensor para confirmar el gesto
    // y no dispararse por error al rozar cerca de la esquina superior izquierda (botón Atrás).
    private val NEAR_CONFIRMATION_DELAY_MS = 320L

    // Umbral reducido en sensores analógicos (en cm) para exigir acercar más la mano.
    private val ANALOG_NEAR_THRESHOLD_CM = 1.5f

    fun startListening() {
        if (!isAvailable) return
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        pendingNearRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingNearRunnable = null
        sensorManager?.unregisterListener(this)
        isCurrentlyNear = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_PROXIMITY) return

        val distance = event.values[0]
        val maxRange = proximitySensor?.maximumRange ?: 5.0f

        val isNear = if (maxRange <= 1.0f) {
            // Sensor puramente binario (0 = Cerca, 1 = Lejos)
            distance < 0.5f
        } else {
            // Sensor analógico en centímetros: rango reducido para no detectar desde tan lejos
            distance < maxRange && distance <= ANALOG_NEAR_THRESHOLD_CM
        }

        onStateChanged?.invoke(isNear, distance)

        if (isNear) {
            if (!isCurrentlyNear) {
                isCurrentlyNear = true
                pendingNearRunnable?.let { mainHandler.removeCallbacks(it) }
                val runnable = Runnable {
                    val now = System.currentTimeMillis()
                    if (isCurrentlyNear && (now - lastNearTriggerTime > COOLDOWN_MS)) {
                        lastNearTriggerTime = now
                        onNear?.invoke()
                    }
                }
                pendingNearRunnable = runnable
                mainHandler.postDelayed(runnable, NEAR_CONFIRMATION_DELAY_MS)
            }
        } else {
            if (isCurrentlyNear) {
                isCurrentlyNear = false
                pendingNearRunnable?.let { mainHandler.removeCallbacks(it) }
                pendingNearRunnable = null
                onFar?.invoke()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No aplicable
    }
}
