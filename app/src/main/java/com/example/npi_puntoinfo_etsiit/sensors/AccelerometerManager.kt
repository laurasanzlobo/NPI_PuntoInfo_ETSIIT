package com.example.npi_puntoinfo_etsiit.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt
import android.util.Log
import kotlin.math.sign

/**
 * Representa la postura física del terminal.
 */
enum class DeviceOrientation(val label: String) {
    VERTICAL("En la mano (Vertical)"),
    FLAT("Horizontal / Lectura en mano"),
    TILTED_LEFT("Inclinado a la izquierda (Gesto Llévame)"),
    TILTED_RIGHT("Inclinado a la derecha"),
    UNKNOWN("En movimiento")
}

/**
 * Gestor avanzado del Acelerómetro para Android / Jetpack Compose.
 *
 * Detección de aceleración lineal, orientación y 3 gestos físicos del móvil (NPI - UGR):
 * 1. Gesto de 3 Sacudidas (Triple Shake):
 *    - Vaivén controlado de 3 repeticiones en < 1.3 s.
 *    - Acción: Consulta de clase actual ("¿Dónde me toca ir ahora?").
 * 2. Gesto de Giro de muñeca a la izquierda (Wrist Flick / Twist):
 *    - Giro rápido de la muñeca hacia el lado izquierdo mientras se sostiene el móvil.
 *    - Acción: Atajo de «¡Llévame!» para iniciar de inmediato la ruta hacia el aula/destino.
 * 3. Gesto de Levantar el móvil (Lift-to-Orient):
 *    - Transición de postura relajada de lectura/mesa (FLAT, Z >= 4.5) a vertical estricto (VERTICAL, Z <= 2.0).
 *    - Acción: En navegación, alterna automáticamente del plano 2D al guiado paso a paso interactivo.
 */
class AccelerometerManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val forwardNodDetector = ForwardNodDetector()

    val isAvailable: Boolean
        get() = accelerometer != null

    // Callbacks a la interfaz Compose
    var onSensorValuesChanged: ((x: Float, y: Float, z: Float) -> Unit)? = null
    var onShakeProgress: ((count: Int, total: Int) -> Unit)? = null
    var onShakeDetected: (() -> Unit)? = null
    var onTakeMeThereGesture: (() -> Unit)? = null // Giro de muñeca hacia la izquierda
    var onNodStarted: (() -> Unit)? = null         // Inclinación hacia adelante iniciada (Fase 1: salir del campo de visión)
    var onForwardTiltGesture: (() -> Unit)? = null // Inclinación y retorno completado (Fase 2: reentrada al campo de visión)
    var onDeviceLifted: (() -> Unit)? = null       // Levantamiento a vertical estricto
    var onPostureModeChanged: ((isVertical: Boolean) -> Unit)? = null
    var onOrientationChanged: ((DeviceOrientation) -> Unit)? = null

    // --- Parámetros Gesto 1: Triple Shake ---
    private val REQUIRED_SHAKES = 3
    private val SHAKE_G_FORCE_THRESHOLD = 2.05f
    private val MIN_TIME_BETWEEN_SHAKES = 160L
    private val MAX_WINDOW_FOR_SHAKES = 1300L
    private val SHAKE_COOLDOWN = 1500L
    private var shakeCount = 0
    private var firstShakeTime = 0L
    private var lastShakeTime = 0L
    private var lastShakeTriggeredTime = 0L

    // --- Parámetros Gesto 2: Giro de muñeca a la izquierda («¡Llévame!») ---
    private val FLICK_LEFT_THRESHOLD = 5.8f // El eje X se dispara al rotar hacia la izquierda
    private val FLICK_COOLDOWN = 1800L
    private var lastFlickTriggeredTime = 0L

    // La inclinación de comedores se detecta con el GIROSCOPIO (rotación sobre eje X),
    // más apropiado que exigir posturas absolutas con el acelerómetro.
    // Solo se escucha si la pantalla ha instalado onForwardTiltGesture.
    private val gestureTag = "NPI_COMEDOR_GESTO"
    private var lastGyroLogNs = 0L

    // --- Parámetros Gesto 3: Levantar el móvil (Guiado paso a paso) y bajarlo (Ver mapa) ---
    var verticalMaxZThreshold: Float = 3.0f // Z <= 3.0 m/s² y Y >= 7.8 m/s² indican vertical (levantado frente a los ojos)
    var verticalMinYThreshold: Float = 7.8f
    var flatMinZThreshold: Float = 3.8f     // Z >= 3.8 m/s² o Y < 7.2 m/s² indican que el móvil se ha bajado a posición de lectura del mapa

    private var isCurrentlyVertical = false

    private var currentOrientation: DeviceOrientation = DeviceOrientation.UNKNOWN

    fun startListening() {
        // Conservamos el acelerómetro para los gestos originales sin modificarlos.
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // Comedores: registrar el giroscopio solamente cuando se requiere el nuevo gesto.
        if (onForwardTiltGesture != null) {
            if (gyroscope == null) {
                Log.e(gestureTag, "No hay giroscopio disponible en este dispositivo")
            } else {
                val registered = sensorManager?.registerListener(
                    this, gyroscope, SensorManager.SENSOR_DELAY_GAME
                ) ?: false
                Log.i(gestureTag, "Giroscopio registrado: $registered")
            }
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
        forwardNodDetector.reset()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            if (onForwardTiltGesture != null) {
                // Trazas breves para medir el movimiento REAL del S22 en Logcat.
                if (kotlin.math.abs(event.values[0]) > 0.25f &&
                    event.timestamp - lastGyroLogNs > 120_000_000L
                ) {
                    lastGyroLogNs = event.timestamp
                    Log.d(gestureTag, "Giro medido x=${event.values[0]}, " +
                            "y=${event.values[1]}, z=${event.values[2]}")
                }
                val previous = forwardNodDetector.phaseName
                val detected = forwardNodDetector.onRotation(
                    event.values[0], event.values[1], event.values[2], event.timestamp
                )
                if (previous != forwardNodDetector.phaseName) {
                    Log.d(gestureTag, "Gesto: $previous -> ${forwardNodDetector.phaseName}")
                    if (previous == "IDLE" && forwardNodDetector.phaseName == "DOWN") {
                        onNodStarted?.invoke()
                    }
                }
                if (detected) {
                    Log.i(gestureTag, "Cabeceo completo detectado: bajar y volver")
                    onForwardTiltGesture?.invoke()
                }
            }
            return
        }
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val now = System.currentTimeMillis()

        // 1. Notificar valores brutos
        onSensorValuesChanged?.invoke(x, y, z)

        // 2. Fuerzas G normalizadas
        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH
        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        // =========================================================================
        // GESTO 1: TRIPLE SHAKE (3 Sacudidas de vaivén para "¿Qué tengo ahora?")
        // =========================================================================
        if (gForce > SHAKE_G_FORCE_THRESHOLD) {
            if (now - lastShakeTriggeredTime > SHAKE_COOLDOWN) {
                if (shakeCount > 0 && (now - firstShakeTime > MAX_WINDOW_FOR_SHAKES)) {
                    shakeCount = 0
                }

                if (now - lastShakeTime > MIN_TIME_BETWEEN_SHAKES) {
                    if (shakeCount == 0) firstShakeTime = now
                    shakeCount++
                    lastShakeTime = now
                    onShakeProgress?.invoke(shakeCount, REQUIRED_SHAKES)

                    if (shakeCount >= REQUIRED_SHAKES) {
                        shakeCount = 0
                        lastShakeTriggeredTime = now
                        onShakeDetected?.invoke()
                    }
                }
            }
        } else {
            if (shakeCount > 0 && (now - firstShakeTime > MAX_WINDOW_FOR_SHAKES)) {
                shakeCount = 0
                onShakeProgress?.invoke(0, REQUIRED_SHAKES)
            }
        }

        // =========================================================================
        // GESTO 2: GIRO DEL MÓVIL / MUÑECA («¡Llévame!»)
        // =========================================================================
        if (abs(x) > FLICK_LEFT_THRESHOLD && y > 2.0f && abs(z) < 7.5f) {
            if (now - lastFlickTriggeredTime > FLICK_COOLDOWN && now - lastShakeTriggeredTime > 1000L) {
                lastFlickTriggeredTime = now
                onTakeMeThereGesture?.invoke()
            }
        }

        // =========================================================================
        // GESTO 3: LEVANTAR EL MÓVIL (Guiado paso a paso) Y BAJARLO (Ver mapa)
        // =========================================================================
        val isVertical = abs(z) <= verticalMaxZThreshold && y >= verticalMinYThreshold && abs(x) < 4.0f
        val isLowered = abs(z) >= flatMinZThreshold || y < 7.2f || abs(x) >= 4.8f

        if (isVertical) {
            if (!isCurrentlyVertical) {
                isCurrentlyVertical = true
                onPostureModeChanged?.invoke(true)
                onDeviceLifted?.invoke()
            }
        } else if (isLowered) {
            if (isCurrentlyVertical) {
                isCurrentlyVertical = false
                onPostureModeChanged?.invoke(false)
            }
        }

        // Estimación de orientación textual para la UI
        val newOrientation = when {
            x > 5.5f -> DeviceOrientation.TILTED_LEFT
            x < -5.5f -> DeviceOrientation.TILTED_RIGHT
            isVertical -> DeviceOrientation.VERTICAL
            isLowered -> DeviceOrientation.FLAT
            else -> DeviceOrientation.UNKNOWN
        }

        if (newOrientation != currentOrientation) {
            currentOrientation = newOrientation
            onOrientationChanged?.invoke(newOrientation)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No aplicable
    }
}

/**
Gesto de inclinar la parte delantera del movil y volver
 a la posicion original
 */
internal class ForwardNodDetector {
    private enum class Phase { IDLE, DOWN, BACK }
    private var phase = Phase.IDLE
    val phaseName: String get() = phase.name
    private var previousTimestampNs = 0L
    private var gestureStartedNs = 0L
    private var lastMotionNs = 0L
    private var lastDetectionNs = -3_000_000_000L
    private var direction = 0f
    private var forwardAngle = 0f
    private var returnAngle = 0f

    private val minimumSpeed = 0.42f       // rad/s, movimiento intencional
    private val minimumDipAngle = 0.20f    // radianes (~11,5º)
    private val maxDipAngle = 1.1f         // evita movimientos descontrolados
    private val maxGestureNs = 1_700_000_000L
    private val maxPauseNs = 500_000_000L
    private val cooldownNs = 1_500_000_000L

    fun reset() {
        phase = Phase.IDLE
        previousTimestampNs = 0L
        gestureStartedNs = 0L
        lastMotionNs = 0L
        direction = 0f
        forwardAngle = 0f
        returnAngle = 0f
    }

    fun onRotation(x: Float, y: Float, z: Float, timestampNs: Long): Boolean {
        if (previousTimestampNs == 0L) {
            previousTimestampNs = timestampNs
            return false
        }
        val dt = (timestampNs - previousTimestampNs) / 1_000_000_000f
        previousTimestampNs = timestampNs
        if (dt <= 0f || dt > 0.25f) {
            reset()
            previousTimestampNs = timestampNs
            return false
        }
        if (timestampNs - lastDetectionNs < cooldownNs) return false

        // Durante el gesto, la rotación predominante ha de ser el cabeceo (eje X).
        val xDominant = kotlin.math.abs(x) >= minimumSpeed &&
                kotlin.math.abs(x) >= 0.90f * kotlin.math.abs(y) &&
                kotlin.math.abs(x) >= 0.90f * kotlin.math.abs(z)

        if (phase != Phase.IDLE &&
            (timestampNs - gestureStartedNs > maxGestureNs ||
                    timestampNs - lastMotionNs > maxPauseNs)
        ) {
            reset()
            previousTimestampNs = timestampNs
        }

        when (phase) {
            Phase.IDLE -> {
                if (xDominant) {
                    phase = Phase.DOWN
                    direction = sign(x)
                    forwardAngle = kotlin.math.abs(x) * dt
                    gestureStartedNs = timestampNs
                    lastMotionNs = timestampNs
                }
            }
            Phase.DOWN -> {
                when {
                    xDominant && x * direction > minimumSpeed -> {
                        forwardAngle += kotlin.math.abs(x) * dt
                        lastMotionNs = timestampNs
                        if (forwardAngle > maxDipAngle) {
                            reset()
                            previousTimestampNs = timestampNs
                        }
                    }
                    xDominant && x * direction < -minimumSpeed -> {
                        if (forwardAngle >= minimumDipAngle) {
                            phase = Phase.BACK
                            returnAngle = kotlin.math.abs(x) * dt
                            lastMotionNs = timestampNs
                        } else {
                            reset()
                            previousTimestampNs = timestampNs
                        }
                    }
                }
            }
            Phase.BACK -> {
                if (xDominant && x * direction < -minimumSpeed) {
                    returnAngle += kotlin.math.abs(x) * dt
                    lastMotionNs = timestampNs
                    if (returnAngle >= minimumDipAngle &&
                        returnAngle >= forwardAngle * 0.72f
                    ) {
                        lastDetectionNs = timestampNs
                        reset()
                        previousTimestampNs = timestampNs
                        return true
                    }
                } else if (xDominant && x * direction > minimumSpeed) {
                    // La persona volvió a inclinarlo en vez de recuperarlo.
                    reset()
                    previousTimestampNs = timestampNs
                }
            }
        }
        return false
    }
}
