package com.example.npi_puntoinfo_etsiit.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
 * Gestor avanzado del Acelerómetro y Giroscopio para Android / Jetpack Compose.
 *
 * Todos los gestos parten de cómo el usuario sujeta normalmente el móvil (un poco inclinado)
 * y ejecutan la acción únicamente cuando el móvil regresa a esa posición inicial de lectura:
 *
 * 1. Gesto de Clase Actual (3 inclinaciones hacia adelante y volver):
 *    - Sujetando el móvil normalmente (un poco inclinado), echar la punta superior hacia adelante 3 veces (1, 2, 3) y volver a la posición.
 * 2. Gesto «¡Llévame!» (Inclinar totalmente a la izquierda y volver):
 *    - Girar el móvil hasta que la pantalla mire claramente hacia la izquierda (x >= 7.5 m/s²) y volver a ponerlo recto frente al usuario.
 * 3. Gesto de Acción Rápida (Desde inclinado, llevarlo a vertical hacia ti y volver):
 *    - Desde la postura normal inclinada de lectura, levantar el móvil hasta ponerlo totalmente vertical frente a ti y devolverlo a la posición inclinada inicial.
 * 4. Gesto de Levantar el móvil (Guiado paso a paso en navegación):
 *    - Mantener el móvil en vertical frente a los ojos para guiado paso a paso con fotos o bajarlo para ver el plano 2D.
 */
class AccelerometerManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val tripleForwardTiltDetector = TripleForwardTiltDetector()

    val isAvailable: Boolean
        get() = accelerometer != null

    // Callbacks a la interfaz Compose
    var onSensorValuesChanged: ((x: Float, y: Float, z: Float) -> Unit)? = null
    var onShakeProgress: ((count: Int, total: Int) -> Unit)? = null
    var onShakeDetected: (() -> Unit)? = null       // 3 inclinaciones hacia adelante + volver
    var onTakeMeThereGesture: (() -> Unit)? = null  // Inclinar totalmente a la izquierda + volver
    var onNodStarted: (() -> Unit)? = null          // Fase 1 de llevar a vertical iniciada
    var onForwardTiltGesture: (() -> Unit)? = null  // Llevar de inclinado a vertical + volver a inclinado
    var onDeviceLifted: (() -> Unit)? = null        // Levantamiento a vertical estricto
    var onPostureModeChanged: ((isVertical: Boolean) -> Unit)? = null
    var onOrientationChanged: ((DeviceOrientation) -> Unit)? = null

    private val gestureTag = "NPI_GESTOS"

    // =========================================================================
    // Parámetros Gesto 1 («¡Llévame!»): Pantalla totalmente hacia la izquierda y volver
    // =========================================================================
    // Exige ~60º-85º de inclinación lateral izquierda (pantalla mirando a la izquierda)
    private val TILT_LEFT_STRONG_THRESHOLD = 7.5f
    private val RETURN_NEUTRAL_MAX_X = 2.4f
    private val MIN_TILT_LEFT_DURATION_MS = 80L
    private val MAX_TILT_LEFT_WINDOW_MS = 2400L
    private val FLICK_COOLDOWN = 1600L

    private var isTiltedLeftForTakeMe = false
    private var tiltedLeftStartTime = 0L
    private var lastFlickTriggeredTime = 0L

    // =========================================================================
    // Parámetros Gesto 3 (Acción Rápida: Correo / Reserva Comedor):
    // Partir de móvil inclinado (Z >= 3.8) -> Llevarlo a vertical (Z <= 2.1, Y >= 8.1) -> Volver a inclinado (Z >= 3.8)
    // =========================================================================
    private val INCLINED_MIN_Z = 3.8f
    private val VERTICAL_MAX_Z_FOR_ACTION = 2.1f
    private val VERTICAL_MIN_Y_FOR_ACTION = 8.1f
    private val MIN_Z_SWING_FOR_ACTION = 2.6f
    private val MAX_VERTICAL_ACTION_WINDOW_MS = 2200L
    private val MIN_VERTICAL_HOLD_MS = 60L
    private val ACTION_COOLDOWN_MS = 1600L

    private var hasInclinedBaseline = false
    private var baselineInclinedZ = 6.0f
    private var isAtVerticalForAction = false
    private var reachedVerticalTime = 0L
    private var minVerticalZReached = 2.1f
    private var lastActionTriggeredTime = 0L

    // Respaldo con acelerómetro para las 3 inclinaciones hacia adelante (si no hubiera giroscopio)
    private var accelForwardTiltCount = 0
    private var accelFirstForwardTiltTime = 0L
    private var accelInForwardPush = false
    private var accelWaitingFinalReturn = false
    private var lastPitchGestureTime = 0L

    // --- Parámetros Gesto 4: Levantar el móvil (Guiado paso a paso en Mapa) ---
    var verticalMaxZThreshold: Float = 3.0f
    var verticalMinYThreshold: Float = 7.8f
    var flatMinZThreshold: Float = 3.8f

    private var isCurrentlyVertical = false
    private var currentOrientation: DeviceOrientation = DeviceOrientation.UNKNOWN

    fun startListening() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        if (onShakeDetected != null || onForwardTiltGesture != null || onTakeMeThereGesture != null) {
            gyroscope?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
        tripleForwardTiltDetector.reset()
        isTiltedLeftForTakeMe = false
        hasInclinedBaseline = false
        isAtVerticalForAction = false
        accelForwardTiltCount = 0
        accelInForwardPush = false
        accelWaitingFinalReturn = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        // =========================================================================
        // 1. GIROSCOPIO: Gesto de 3 inclinaciones hacia adelante y volver (Clase Actual)
        // =========================================================================
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            if (onShakeDetected != null) {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]

                val prevCount = tripleForwardTiltDetector.currentCount
                val triggered = tripleForwardTiltDetector.onRotation(gx, gy, gz, event.timestamp)
                val newCount = tripleForwardTiltDetector.currentCount
                if (newCount != prevCount) {
                    onShakeProgress?.invoke(newCount, 3)
                }
                if (triggered) {
                    Log.i(gestureTag, "3 inclinaciones hacia adelante y retorno completadas")
                    lastPitchGestureTime = System.currentTimeMillis()
                    isTiltedLeftForTakeMe = false
                    isAtVerticalForAction = false
                    onShakeDetected?.invoke()
                }
            }
            return
        }

        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val now = System.currentTimeMillis()

        onSensorValuesChanged?.invoke(x, y, z)

        // =========================================================================
        // 2. RESPALDO ACELERÓMETRO PARA 3 INCLINACIONES HACIA ADELANTE (sin giroscopio)
        // =========================================================================
        if (gyroscope == null && onShakeDetected != null) {
            if (accelForwardTiltCount > 0 && now - accelFirstForwardTiltTime > 3500L) {
                accelForwardTiltCount = 0
                accelInForwardPush = false
                accelWaitingFinalReturn = false
            }
            if (!accelInForwardPush && !accelWaitingFinalReturn && z > 8.2f && abs(x) < 4.0f) {
                accelInForwardPush = true
                if (accelForwardTiltCount == 0) accelFirstForwardTiltTime = now
                accelForwardTiltCount++
                onShakeProgress?.invoke(accelForwardTiltCount, 3)
                if (accelForwardTiltCount >= 3) {
                    accelWaitingFinalReturn = true
                }
            } else if (accelInForwardPush && z < 6.8f) {
                accelInForwardPush = false
            }

            if (accelWaitingFinalReturn && z in 3.0f..7.2f && abs(x) < 3.5f) {
                accelForwardTiltCount = 0
                accelInForwardPush = false
                accelWaitingFinalReturn = false
                lastPitchGestureTime = now
                onShakeDetected?.invoke()
            }
        }

        // =========================================================================
        // 3. GESTO DE ACCIÓN (Correo / Reserva Comedor):
        //    De postura normal inclinada (Z >= 3.8) -> Llevar a vertical (Z <= 2.1, Y >= 8.1) -> Volver a inclinado (Z >= 3.8)
        // =========================================================================
        if (onForwardTiltGesture != null) {
            if (tripleForwardTiltDetector.currentCount >= 2 || isTiltedLeftForTakeMe) {
                isAtVerticalForAction = false
            } else if (!isAtVerticalForAction) {
                // Registrar la postura inclinada normal con la que el usuario sujeta el móvil
                if (z >= INCLINED_MIN_Z && y >= 2.0f && abs(x) < 4.2f) {
                    hasInclinedBaseline = true
                    baselineInclinedZ = max(INCLINED_MIN_Z, min(9.0f, baselineInclinedZ * 0.8f + z * 0.2f))
                }

                // Fase 1: El usuario levanta/inclina el móvil hacia sí hasta ponerlo en VERTICAL
                if (hasInclinedBaseline &&
                    z <= VERTICAL_MAX_Z_FOR_ACTION &&
                    y >= VERTICAL_MIN_Y_FOR_ACTION &&
                    abs(x) < 4.2f &&
                    (baselineInclinedZ - z) >= MIN_Z_SWING_FOR_ACTION &&
                    (now - lastActionTriggeredTime > ACTION_COOLDOWN_MS)
                ) {
                    isAtVerticalForAction = true
                    reachedVerticalTime = now
                    minVerticalZReached = z
                    Log.d(gestureTag, "Acción Fase 1: móvil llevado a vertical (z=$z desde baseline=$baselineInclinedZ)")
                    onNodStarted?.invoke()
                }
            } else {
                // Fase 2: El usuario devuelve el móvil desde vertical a su posición inclinada inicial
                minVerticalZReached = min(minVerticalZReached, z)
                val elapsed = now - reachedVerticalTime

                if (elapsed > MAX_VERTICAL_ACTION_WINDOW_MS || abs(x) >= 5.5f) {
                    isAtVerticalForAction = false
                } else if (elapsed >= MIN_VERTICAL_HOLD_MS &&
                    z >= INCLINED_MIN_Z &&
                    (z - minVerticalZReached) >= MIN_Z_SWING_FOR_ACTION &&
                    y >= 2.5f &&
                    abs(x) < 4.2f
                ) {
                    isAtVerticalForAction = false
                    hasInclinedBaseline = true
                    baselineInclinedZ = z
                    lastActionTriggeredTime = now
                    lastPitchGestureTime = now
                    Log.i(gestureTag, "Acción Fase 2 completada: móvil devuelto a posición inclinada (z=$z)")
                    onForwardTiltGesture?.invoke()
                }
            }
        }

        // =========================================================================
        // 4. GESTO «¡LLÉVAME!»:
        //    Poner el móvil totalmente inclinado hacia la izquierda (X >= 7.5) y volver a posición recta (abs(X) <= 2.4)
        // =========================================================================
        if (onTakeMeThereGesture != null) {
            if (!isTiltedLeftForTakeMe) {
                // Fase 1: Pantalla mirando claramente hacia la izquierda (gesto amplio y exagerado)
                if (x >= TILT_LEFT_STRONG_THRESHOLD && abs(z) < 7.5f) {
                    if (now - lastFlickTriggeredTime > FLICK_COOLDOWN && now - lastPitchGestureTime > 600L) {
                        isTiltedLeftForTakeMe = true
                        tiltedLeftStartTime = now
                        isAtVerticalForAction = false
                        Log.d(gestureTag, "Llévame Fase 1: móvil totalmente inclinado a la izquierda (x=$x)")
                    }
                }
            } else {
                // Fase 2: Volver a la posición horizontalmente recta frente al usuario
                val elapsed = now - tiltedLeftStartTime
                if (elapsed > MAX_TILT_LEFT_WINDOW_MS || x < -4.5f) {
                    isTiltedLeftForTakeMe = false
                } else if (elapsed >= MIN_TILT_LEFT_DURATION_MS &&
                    abs(x) <= RETURN_NEUTRAL_MAX_X &&
                    (y >= 2.5f || z >= 2.5f)
                ) {
                    isTiltedLeftForTakeMe = false
                    lastFlickTriggeredTime = now
                    Log.i(gestureTag, "Llévame Fase 2 completada: móvil de vuelta en posición inicial (x=$x)")
                    onTakeMeThereGesture?.invoke()
                }
            }
        }

        // =========================================================================
        // 5. GESTO LEVANTAR EL MÓVIL (Guiado paso a paso en navegación) Y BAJARLO (Ver mapa)
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

        val newOrientation = when {
            x >= TILT_LEFT_STRONG_THRESHOLD -> DeviceOrientation.TILTED_LEFT
            x <= -TILT_LEFT_STRONG_THRESHOLD -> DeviceOrientation.TILTED_RIGHT
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
 * Detector del gesto de Consulta de Clase Actual:
 * Funciona desde cualquier ángulo cómodo en el que el usuario sujete el móvil (normalmente un poco inclinado).
 * Detecta 3 inclinaciones de la punta superior hacia adelante (1, 2, 3) y dispara la acción
 * cuando el usuario devuelve el móvil a su posición tras la 3ª inclinación.
 */
internal class TripleForwardTiltDetector {
    private enum class State {
        WAITING_FORWARD_PUSH,
        IN_FORWARD_PUSH,
        WAITING_FINAL_RETURN
    }

    private var state = State.WAITING_FORWARD_PUSH
    var currentCount: Int = 0
        private set

    private var previousTimestampNs = 0L
    private var firstTiltNs = 0L
    private var lastActivityNs = 0L
    private var enteredReturnNs = 0L
    private var lastTriggeredNs = -3_000_000_000L

    private var currentForwardAngle = 0f
    private var finalReturnAngle = 0f

    private val minPushSpeed = 0.45f          // rad/s hacia adelante (gx < -0.45)
    private val minForwardAngle = 0.13f       // radianes (~7.5º) por cada inclinación hacia adelante
    private val maxPauseBetweenTiltsNs = 1_500_000_000L
    private val maxTotalSequenceNs = 4_200_000_000L
    private val cooldownNs = 1_500_000_000L

    fun reset() {
        state = State.WAITING_FORWARD_PUSH
        currentCount = 0
        previousTimestampNs = 0L
        firstTiltNs = 0L
        lastActivityNs = 0L
        enteredReturnNs = 0L
        currentForwardAngle = 0f
        finalReturnAngle = 0f
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
        if (timestampNs - lastTriggeredNs < cooldownNs) return false

        if (currentCount > 0 || state != State.WAITING_FORWARD_PUSH) {
            if (timestampNs - lastActivityNs > maxPauseBetweenTiltsNs ||
                (firstTiltNs > 0L && timestampNs - firstTiltNs > maxTotalSequenceNs)
            ) {
                reset()
                previousTimestampNs = timestampNs
            }
        }

        // Permitimos cierta holgura lateral de la muñeca al sujetar el móvil inclinado
        val xIsPrimaryAxis = abs(x) >= 0.55f * abs(y) && abs(x) >= 0.55f * abs(z)

        when (state) {
            State.WAITING_FORWARD_PUSH -> {
                // Empuje de la punta superior hacia adelante (gx negativo)
                if (x < -minPushSpeed && xIsPrimaryAxis) {
                    state = State.IN_FORWARD_PUSH
                    currentForwardAngle = abs(x) * dt
                    if (currentCount == 0) firstTiltNs = timestampNs
                    lastActivityNs = timestampNs
                }
            }
            State.IN_FORWARD_PUSH -> {
                if (x < -0.12f) {
                    currentForwardAngle += abs(x) * dt
                    lastActivityNs = timestampNs
                } else if (x >= 0.10f || (currentForwardAngle >= minForwardAngle && x >= -0.05f)) {
                    // Terminó el empuje hacia adelante y empieza a regresar (o pausa tras completar el ángulo)
                    if (currentForwardAngle >= minForwardAngle) {
                        currentCount++
                        lastActivityNs = timestampNs
                        if (currentCount >= 3) {
                            state = State.WAITING_FINAL_RETURN
                            enteredReturnNs = timestampNs
                            finalReturnAngle = if (x > 0f) x * dt else 0f
                        } else {
                            state = State.WAITING_FORWARD_PUSH
                            currentForwardAngle = 0f
                        }
                    } else if (x > 0.35f) {
                        // Movimiento muy pequeño descartado
                        state = State.WAITING_FORWARD_PUSH
                        currentForwardAngle = 0f
                    }
                }
            }
            State.WAITING_FINAL_RETURN -> {
                if (x > 0.08f) {
                    finalReturnAngle += x * dt
                    lastActivityNs = timestampNs
                }
                val returnElapsedNs = timestampNs - enteredReturnNs
                // Se dispara en cuanto el móvil vuelve hacia el usuario y se detiene en su posición
                val returnedAndSettled = (finalReturnAngle >= 0.10f && x < 0.55f) ||
                        (returnElapsedNs >= 160_000_000L && abs(x) < 0.65f)

                if (returnedAndSettled) {
                    lastTriggeredNs = timestampNs
                    reset()
                    previousTimestampNs = timestampNs
                    return true
                }
            }
        }
        return false
    }
}
