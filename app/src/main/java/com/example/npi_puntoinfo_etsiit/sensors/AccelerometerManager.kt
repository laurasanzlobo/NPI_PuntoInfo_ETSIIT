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
 * 1. Gesto de Clase Actual (3 inclinaciones rápidas hacia adelante y volver):
 *    - 3 cabeceos consecutivos y enérgicos hacia adelante en < 1.6 s (máximo 600 ms entre cabeceos).
 *    - Si se tarda más, el contador se resetea a cero de inmediato para evitar acumulaciones involuntarias.
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
    private val singleTiltActionDetector = SingleTiltActionDetector()

    val isAvailable: Boolean
        get() = accelerometer != null

    // Callbacks a la interfaz Compose
    var onSensorValuesChanged: ((x: Float, y: Float, z: Float) -> Unit)? = null
    var onShakeProgress: ((count: Int, total: Int) -> Unit)? = null
    var onShakeDetected: (() -> Unit)? = null       // 3 inclinaciones hacia adelante + volver
    var onTakeMeThereGesture: (() -> Unit)? = null  // Inclinar deliberadamente a la izquierda + volver
    var onNodStarted: (() -> Unit)? = null          // Fase 1 de llevar a vertical iniciada
    var onForwardTiltGesture: (() -> Unit)? = null  // Inclinar hacia ti + volver a posición de lectura
    var onDeviceLifted: (() -> Unit)? = null        // Levantamiento a vertical estricto
    var onPostureModeChanged: ((isVertical: Boolean) -> Unit)? = null
    var onOrientationChanged: ((DeviceOrientation) -> Unit)? = null

    private val gestureTag = "NPI_GESTOS"

    // =========================================================================
    // Parámetros Gesto 1 («¡Llévame!»): Pantalla inclinada a la izquierda y volver
    // Margen de tiempo estricto: si se mantiene inclinado más de 850 ms sin volver, se cancela
    // =========================================================================
    private val TILT_LEFT_STRONG_THRESHOLD = 6.8f     // Inclinación amplia y deliberada (~44º hacia la izquierda)
    private val RETURN_NEUTRAL_MAX_X = 2.5f           // Retorno a posición frontal
    private val MAX_TAKE_ME_WINDOW_MS = 850L          // Margen de tiempo máximo para ida y vuelta
    private val FLICK_COOLDOWN = 1500L                // Evitar disparos dobles

    private enum class FlickState {
        NEUTRAL,
        TILTED,
        TIMED_OUT
    }

    private var takeMeThereState = FlickState.NEUTRAL
    private var takeMeThereStartTime = 0L
    private var lastFlickTriggeredTime = 0L

    // =========================================================================
    // Parámetros Gesto 3 (Acción Rápida: Correo / Reserva Comedor):
    // Cabeceo dinámico hacia ti y retorno (detectado por giroscopio con fallback de acelerómetro dinámico)
    // =========================================================================
    private var lastActionTriggeredTime = 0L

    // Respaldo dinámico por acelerómetro (solo si el móvil no cuenta con giroscopio)
    private var accelActionState = 0
    private var accelActionDirection = 0
    private var accelActionStartZ = 0f
    private var accelActionStartTime = 0L
    private var accelActionReturnStartTime = 0L
    private var lastSampleZ = 0f
    private var lastAccelActionTime = 0L

    // Respaldo con acelerómetro para las 3 inclinaciones hacia adelante (si no hubiera giroscopio)
    private var accelForwardTiltCount = 0
    private var accelFirstForwardTiltTime = 0L
    private var accelInForwardPush = false
    private var accelWaitingFinalReturn = false
    private var lastPitchGestureTime = 0L

    // --- Parámetros Gesto 4: Levantar el móvil (Guiado paso a paso en Mapa) ---
    // Histéresis amplia: se activa al subir el móvil vertical (y >= 7.8, |z| <= 4.5)
    // y NO se desactiva con el movimiento normal al andar o girar, sino únicamente cuando
    // el usuario baja claramente el móvil hacia posición horizontal (|z| >= 7.5 e y <= 4.8).
    var verticalMaxZThreshold: Float = 4.5f
    var verticalMinYThreshold: Float = 7.8f
    var flatMinZThreshold: Float = 7.5f
    var flatMaxYThreshold: Float = 4.8f

    private var isCurrentlyVertical = false
    private var postureInitialized = false
    private var postureSmoothX = 0f
    private var postureSmoothY = 0f
    private var postureSmoothZ = 0f
    private var loweredStartTimeMs = 0L
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
        singleTiltActionDetector.reset()
        takeMeThereState = FlickState.NEUTRAL
        accelActionState = 0
        accelForwardTiltCount = 0
        accelInForwardPush = false
        accelWaitingFinalReturn = false
        postureInitialized = false
        loweredStartTimeMs = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        // =========================================================================
        // 1. GIROSCOPIO: Gestos dinámicos de cabeceo (Clase Actual y Acción Rápida)
        // =========================================================================
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val gx = event.values[0]
            val gy = event.values[1]
            val gz = event.values[2]

            // 1A. Gesto de 3 inclinaciones consecutivas hacia adelante (Clase Actual)
            if (onShakeDetected != null) {
                val prevCount = tripleForwardTiltDetector.currentCount
                val triggered = tripleForwardTiltDetector.onRotation(gx, gy, gz, event.timestamp)
                val newCount = tripleForwardTiltDetector.currentCount
                if (newCount != prevCount) {
                    onShakeProgress?.invoke(newCount, 3)
                }
                if (triggered) {
                    Log.i(gestureTag, "3 inclinaciones consecutivas hacia adelante completadas")
                    lastPitchGestureTime = System.currentTimeMillis()
                    takeMeThereState = FlickState.NEUTRAL
                    singleTiltActionDetector.reset()
                    onShakeDetected?.invoke()
                }
            }

            // 1B. Gesto de Acción Rápida (Correo / Reserva Comedor): Inclinar hacia ti y volver
            if (onForwardTiltGesture != null && tripleForwardTiltDetector.currentCount == 0 && takeMeThereState == FlickState.NEUTRAL) {
                if (singleTiltActionDetector.onRotation(gx, gy, gz, event.timestamp)) {
                    Log.i(gestureTag, "Gesto Acción Rápida completado (inclinación hacia ti y retorno)")
                    lastActionTriggeredTime = System.currentTimeMillis()
                    lastPitchGestureTime = System.currentTimeMillis()
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

        onSensorValuesChanged?.invoke(x, y, z)

        // =========================================================================
        // 2. RESPALDO ACELERÓMETRO PARA 3 INCLINACIONES HACIA ADELANTE (sin giroscopio)
        // =========================================================================
        if (gyroscope == null && onShakeDetected != null) {
            // Ventana máxima estricta de 1.6 s y máximo 650 ms entre inclinaciones
            if (accelForwardTiltCount > 0 && now - accelFirstForwardTiltTime > 1600L) {
                accelForwardTiltCount = 0
                accelInForwardPush = false
                accelWaitingFinalReturn = false
                onShakeProgress?.invoke(0, 3)
            }
            if (!accelInForwardPush && !accelWaitingFinalReturn && z > 8.5f && abs(x) < 3.5f) {
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

            if (accelWaitingFinalReturn && z in 3.0f..7.0f && abs(x) < 3.2f) {
                accelForwardTiltCount = 0
                accelInForwardPush = false
                accelWaitingFinalReturn = false
                lastPitchGestureTime = now
                onShakeDetected?.invoke()
            }
        }

        // =========================================================================
        // 3. GESTO DE ACCIÓN (Correo / Reserva Comedor) — Respaldo dinámico solo si NO hay giroscopio
        // =========================================================================
        if (gyroscope == null && onForwardTiltGesture != null) {
            val dt = if (lastAccelActionTime > 0L) (now - lastAccelActionTime) / 1000f else 0.02f
            lastAccelActionTime = now

            if (dt in 0.005f..0.25f) {
                val dz = z - lastSampleZ
                val zVelocity = abs(dz / dt) // m/s^3 velocidad dinámica de cambio

                when (accelActionState) {
                    0 -> { // IDLE: esperar movimiento dinámico
                        if (zVelocity >= 3.0f && abs(x) < 4.5f) {
                            accelActionDirection = if (dz > 0) 1 else -1
                            accelActionStartZ = lastSampleZ
                            accelActionStartTime = now
                            accelActionState = 1
                            onNodStarted?.invoke()
                        }
                    }
                    1 -> { // EN INCLINACIÓN
                        val elapsed = now - accelActionStartTime
                        if (elapsed > 1200L) {
                            accelActionState = 3 // TIMED_OUT
                        } else {
                            val deltaZ = (z - accelActionStartZ) * accelActionDirection
                            if (deltaZ >= 1.8f) {
                                accelActionState = 2
                                accelActionReturnStartTime = now
                            }
                        }
                    }
                    2 -> { // RETORNANDO A POSICIÓN BASE
                        val totalElapsed = now - accelActionStartTime
                        if (totalElapsed > 1500L) {
                            accelActionState = 3 // TIMED_OUT
                        } else {
                            val returnedDelta = abs(z - accelActionStartZ)
                            if (returnedDelta <= 1.5f && zVelocity < 3.0f) {
                                accelActionState = 0
                                lastActionTriggeredTime = now
                                lastPitchGestureTime = now
                                Log.i(gestureTag, "Acción rápida por acelerómetro completada")
                                onForwardTiltGesture?.invoke()
                            }
                        }
                    }
                    3 -> { // TIMED_OUT: esperar a que el usuario regrese a reposo sin disparar
                        val returnedDelta = abs(z - accelActionStartZ)
                        if (returnedDelta <= 1.3f && zVelocity < 2.0f) {
                            accelActionState = 0
                        }
                    }
                }
            }
            lastSampleZ = z
        }

        // =========================================================================
        // 4. GESTO «¡LLÉVAME!»:
        //    Inclinar el móvil deliberadamente a la izquierda (x >= 5.8 m/s²) y volver al frente (|x| <= 2.6 m/s²)
        //    Margen de tiempo: si se mantiene inclinado más de 850 ms sin volver, se cancela y no se activa al regresar.
        // =========================================================================
        if (onTakeMeThereGesture != null) {
            when (takeMeThereState) {
                FlickState.NEUTRAL -> {
                    // Fase 1: Pantalla inclinada deliberadamente hacia la izquierda (x >= 5.8 m/s², ~36º)
                    if (x >= TILT_LEFT_STRONG_THRESHOLD && abs(z) < 7.5f) {
                        if (now - lastFlickTriggeredTime > FLICK_COOLDOWN && now - lastPitchGestureTime > 600L) {
                            takeMeThereState = FlickState.TILTED
                            takeMeThereStartTime = now
                            Log.d(gestureTag, "Llévame: iniciado giro a la izquierda (x=$x)")
                        }
                    }
                }
                FlickState.TILTED -> {
                    val elapsed = now - takeMeThereStartTime
                    if (elapsed > MAX_TAKE_ME_WINDOW_MS) {
                        // Se mantuvo de lado más tiempo del margen permitido: CANCELAR
                        takeMeThereState = FlickState.TIMED_OUT
                        Log.d(gestureTag, "Llévame: superado margen de tiempo (${elapsed}ms). Gesto cancelado.")
                    } else if (x < -3.5f) {
                        takeMeThereState = FlickState.NEUTRAL
                    } else if (abs(x) <= RETURN_NEUTRAL_MAX_X && (y >= 2.0f || z >= 2.0f)) {
                        // De vuelta al frente dentro del margen de tiempo: ¡ACCIÓN!
                        takeMeThereState = FlickState.NEUTRAL
                        lastFlickTriggeredTime = now
                        Log.i(gestureTag, "Llévame: completado con éxito en ${elapsed}ms")
                        onTakeMeThereGesture?.invoke()
                    }
                }
                FlickState.TIMED_OUT -> {
                    // El gesto caducó. Esperar a que el usuario devuelva el móvil al centro para volver a NEUTRAL
                    if (abs(x) <= RETURN_NEUTRAL_MAX_X) {
                        takeMeThereState = FlickState.NEUTRAL
                        Log.d(gestureTag, "Llévame: móvil devuelto al centro tras cancelación. Listo.")
                    }
                }
            }
        }

        // =========================================================================
        // 5. GESTO LEVANTAR EL MÓVIL (Guiado paso a paso en navegación) Y BAJARLO (Ver mapa)
        // =========================================================================
        if (!postureInitialized) {
            postureSmoothX = x
            postureSmoothY = y
            postureSmoothZ = z
            postureInitialized = true
        } else {
            val alpha = 0.15f
            postureSmoothX += alpha * (x - postureSmoothX)
            postureSmoothY += alpha * (y - postureSmoothY)
            postureSmoothZ += alpha * (z - postureSmoothZ)
        }

        val isVertical = abs(postureSmoothZ) <= verticalMaxZThreshold &&
            postureSmoothY >= verticalMinYThreshold &&
            abs(postureSmoothX) < 4.5f

        // Solo se considera bajado cuando el usuario inclina el móvil claramente hacia abajo (hacia horizontal)
        val isClearlyLowered = abs(postureSmoothZ) >= flatMinZThreshold && postureSmoothY <= flatMaxYThreshold

        if (isVertical) {
            loweredStartTimeMs = 0L
            if (!isCurrentlyVertical) {
                isCurrentlyVertical = true
                onPostureModeChanged?.invoke(true)
                onDeviceLifted?.invoke()
            }
        } else if (isClearlyLowered) {
            if (isCurrentlyVertical) {
                if (loweredStartTimeMs == 0L) {
                    loweredStartTimeMs = now
                } else if (now - loweredStartTimeMs >= 250L) {
                    isCurrentlyVertical = false
                    loweredStartTimeMs = 0L
                    onPostureModeChanged?.invoke(false)
                }
            }
        } else {
            loweredStartTimeMs = 0L
        }

        val newOrientation = when {
            x >= TILT_LEFT_STRONG_THRESHOLD -> DeviceOrientation.TILTED_LEFT
            x <= -TILT_LEFT_STRONG_THRESHOLD -> DeviceOrientation.TILTED_RIGHT
            isCurrentlyVertical -> DeviceOrientation.VERTICAL
            isClearlyLowered -> DeviceOrientation.FLAT
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
 * Exige realizar 3 inclinaciones consecutivas hacia adelante (1, 2, 3) en una ventana rápida de tiempo.
 *
 * Temporización estricta:
 * - Ventana máxima total: 1.6 segundos para las 3 repeticiones.
 * - Pausa máxima entre cabeceos: 600 ms. Si pasa más de 600 ms sin hacer el siguiente, se resetea a 0.
 * - Exige movimiento de cabeceo deliberado (gx < -0.80 rad/s) y dominante en el eje X para no confundirse con giros o pasos al andar.
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
    private var lastTiltCompletedNs = 0L
    private var enteredReturnNs = 0L
    private var lastTriggeredNs = -3_000_000_000L

    private var currentForwardAngle = 0f
    private var finalReturnAngle = 0f

    // Velocidad angular mínima: movimiento intencionado y rápido de cabeceo
    private val minPushSpeed = 0.80f          // rad/s (gx < -0.80)
    private val minForwardAngle = 0.16f       // radianes (~9.5º)
    // Límites de tiempo estrictos: 600 ms de pausa máxima entre cabeceos y 1.6 s en total
    private val maxPauseBetweenTiltsNs = 600_000_000L
    private val maxTotalSequenceNs = 1_600_000_000L
    private val cooldownNs = 1_500_000_000L

    fun reset() {
        state = State.WAITING_FORWARD_PUSH
        currentCount = 0
        previousTimestampNs = 0L
        firstTiltNs = 0L
        lastTiltCompletedNs = 0L
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

        // Control de tiempo estricto: si pasa demasiado tiempo entre cabeceos o en total, resetear a 0
        if (currentCount > 0 || state != State.WAITING_FORWARD_PUSH) {
            val timeSinceFirst = if (firstTiltNs > 0L) timestampNs - firstTiltNs else 0L
            val timeSinceLast = if (lastTiltCompletedNs > 0L) timestampNs - lastTiltCompletedNs else 0L

            if (timeSinceFirst > maxTotalSequenceNs || (currentCount in 1..2 && timeSinceLast > maxPauseBetweenTiltsNs)) {
                reset()
                previousTimestampNs = timestampNs
                return false
            }
        }

        // Exige que el cabeceo (eje X) sea claramente dominante respecto a giros laterales de muñeca
        val xIsPrimaryAxis = abs(x) >= 0.75f * abs(y) && abs(x) >= 0.75f * abs(z)

        when (state) {
            State.WAITING_FORWARD_PUSH -> {
                // Empuje decidido hacia adelante (gx < -0.80)
                if (x < -minPushSpeed && xIsPrimaryAxis) {
                    state = State.IN_FORWARD_PUSH
                    currentForwardAngle = abs(x) * dt
                    if (currentCount == 0) {
                        firstTiltNs = timestampNs
                    }
                }
            }
            State.IN_FORWARD_PUSH -> {
                if (x < -0.20f) {
                    currentForwardAngle += abs(x) * dt
                } else if (x >= 0.15f || (currentForwardAngle >= minForwardAngle && x >= -0.05f)) {
                    // Terminó el empuje hacia adelante y empieza a retroceder a la posición
                    if (currentForwardAngle >= minForwardAngle) {
                        currentCount++
                        lastTiltCompletedNs = timestampNs
                        if (currentCount >= 3) {
                            state = State.WAITING_FINAL_RETURN
                            enteredReturnNs = timestampNs
                            finalReturnAngle = if (x > 0f) x * dt else 0f
                        } else {
                            state = State.WAITING_FORWARD_PUSH
                            currentForwardAngle = 0f
                        }
                    } else if (x > 0.40f) {
                        // Movimiento insuficiente, descartar este intento
                        state = State.WAITING_FORWARD_PUSH
                        currentForwardAngle = 0f
                    }
                }
            }
            State.WAITING_FINAL_RETURN -> {
                // Acumular la rotación de regreso a la posición base (gx > 0)
                if (x > 0.08f) {
                    finalReturnAngle += x * dt
                }
                val returnElapsedNs = timestampNs - enteredReturnNs

                // Exige un retorno real hacia el usuario (gx > 0) y que se detenga en la posición base
                val hasReturnedToPosition = finalReturnAngle >= (currentForwardAngle * 0.70f).coerceAtLeast(minForwardAngle * 0.85f)
                val settledAtRest = abs(x) < 0.40f

                if (hasReturnedToPosition && settledAtRest) {
                    lastTriggeredNs = timestampNs
                    reset()
                    previousTimestampNs = timestampNs
                    return true
                } else if (returnElapsedNs > 900_000_000L) {
                    // Si pasaron 900ms sin volver a la posición base, cancelar
                    reset()
                    previousTimestampNs = timestampNs
                }
            }
        }
        return false
    }
}

/**
 * Detector de precisión para el gesto de Acción Rápida (Correo / Reserva Comedor):
 * «Inclinar el móvil hacia ti y volver a la posición de lectura».
 *
 * Utiliza el giroscopio para medir la velocidad angular del cabeceo (eje X, pitch)
 * e integrarla en un ángulo de rotación:
 * 1. Detección de rotación intencionada de cabeceo (|gx| >= 0.55 rad/s) alcanzando >= 0.09 rad (~5.2º).
 * 2. Inmediato movimiento de retorno en sentido contrario hacia la posición de partida.
 * 3. Reposo en la posición inicial.
 *
 * Ventajas fundamentales:
 * - Es IMPOSIBLE que se active por sostener el móvil en vertical o en reposo (gx = 0).
 * - Exige un cabeceo amplio y claro hacia ti (~23º) para no dispararse con gestos pequeños o temblores.
 */
internal class SingleTiltActionDetector {
    private enum class State {
        IDLE,
        IN_TILT,
        RETURNING,
        TIMED_OUT
    }

    private var state = State.IDLE
    private var previousTimestampNs = 0L
    private var tiltStartNs = 0L
    private var returnStartNs = 0L
    private var accumulatedTiltAngle = 0f
    private var accumulatedReturnAngle = 0f
    private var lastTriggeredNs = 0L

    // Parámetros calibrados para cabeceo natural hacia ti y retorno
    private val minAngularSpeed = 0.70f            // rad/s (~40º/s, cabeceo fluido)
    private val minTiltAngle = 0.18f                // radianes (~10.3º de cabeceo)
    private val maxTiltHoldNs = 900_000_000L       // 900 ms máx para completar la ida hacia ti
    private val maxTotalWindowNs = 1_500_000_000L  // 1.5 s máx para todo el ciclo (ida + vuelta)
    private val cooldownNs = 1_200_000_000L        // Cooldown tras disparo

    fun reset() {
        state = State.IDLE
        previousTimestampNs = 0L
        tiltStartNs = 0L
        returnStartNs = 0L
        accumulatedTiltAngle = 0f
        accumulatedReturnAngle = 0f
    }

    fun onRotation(gx: Float, gy: Float, gz: Float, timestampNs: Long): Boolean {
        if (previousTimestampNs == 0L) {
            previousTimestampNs = timestampNs
            return false
        }
        val dt = (timestampNs - previousTimestampNs) / 1_000_000_000f
        previousTimestampNs = timestampNs

        if (dt <= 0f || dt > 0.25f) {
            reset()
            return false
        }

        if (timestampNs - lastTriggeredNs < cooldownNs) {
            return false
        }

        // El cabeceo hacia el usuario (gx > 0) debe ser dominante sobre giros laterales o desviaciones
        val isPitchDominant = gx >= 0.70f * abs(gy) && gx >= 0.70f * abs(gz)

        when (state) {
            State.IDLE -> {
                // Solo se inicia con un cabeceo cómodo hacia ti (gx > 0)
                if (gx >= minAngularSpeed && isPitchDominant) {
                    state = State.IN_TILT
                    tiltStartNs = timestampNs
                    accumulatedTiltAngle = gx * dt
                    accumulatedReturnAngle = 0f
                }
            }

            State.IN_TILT -> {
                val tiltElapsed = timestampNs - tiltStartNs
                if (tiltElapsed > maxTiltHoldNs) {
                    state = State.TIMED_OUT
                    return false
                }

                if (gx > 0.10f) {
                    accumulatedTiltAngle += gx * dt
                } else if (-gx >= 0.20f || (accumulatedTiltAngle >= minTiltAngle && -gx >= 0.06f)) {
                    // Ha terminado la inclinación hacia ti y empieza el retroceso hacia la posición de lectura
                    if (accumulatedTiltAngle >= minTiltAngle) {
                        state = State.RETURNING
                        returnStartNs = timestampNs
                        accumulatedReturnAngle = if (-gx > 0f) (-gx) * dt else 0f
                    } else if (-gx >= 0.35f) {
                        reset()
                    }
                }
            }

            State.RETURNING -> {
                val totalElapsed = timestampNs - tiltStartNs

                if (totalElapsed > maxTotalWindowNs) {
                    state = State.TIMED_OUT
                    return false
                }

                if (-gx > 0.06f) {
                    accumulatedReturnAngle += (-gx) * dt
                }

                // Ha recuperado al menos el 55% de la inclinación y se ha detenido en reposo en la posición base
                val hasReturnedEnough = accumulatedReturnAngle >= (accumulatedTiltAngle * 0.55f).coerceAtLeast(minTiltAngle * 0.70f)
                val settledAtRest = abs(gx) < 0.60f && abs(gy) < 0.60f

                if (hasReturnedEnough && settledAtRest) {
                    lastTriggeredNs = timestampNs
                    reset()
                    return true
                }
            }

            State.TIMED_OUT -> {
                // El gesto caducó porque se mantuvo inclinado más de 850 ms.
                // Esperar a que el usuario termine el movimiento y regrese al reposo para resetear a IDLE sin disparar.
                if (abs(gx) < 0.35f && abs(gy) < 0.35f && abs(gz) < 0.35f) {
                    val timeSinceTimeout = timestampNs - tiltStartNs
                    if (timeSinceTimeout > maxTotalWindowNs + 150_000_000L) {
                        reset()
                    }
                }
            }
        }
        return false
    }
}
