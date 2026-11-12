package com.example.npi_puntoinfo_etsiit.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.example.npi_puntoinfo_etsiit.indoor.IndoorStep
import com.example.npi_puntoinfo_etsiit.indoor.IndoorTripState
import com.example.npi_puntoinfo_etsiit.outdoor.Maneuver
import com.example.npi_puntoinfo_etsiit.ui.theme.*

/**
 * Contenedor UI de Guiado Paso a Paso Interior basado en Pedómetro (Indoor Step Guidance Card).
 * Muestra las instrucciones secuenciales, la maniobra direccional, el contenedor de imagen de guiado,
 * la progresión de pasos por sensor y los controles de anulación manual de respaldo.
 */
@Composable
fun IndoorStepGuidanceCard(
    tripState: IndoorTripState,
    onStepSimulated: () -> Unit,
    onNextManual: () -> Unit,
    onPreviousManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    val step = tripState.currentStep

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (tripState.isCompleted || step == null) {
                // Estado de Llegada a Destino
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "¡Llegada a Destino!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Has alcanzado ${tripState.route.destino.nombre.replace('_', ' ')} tras ${tripState.totalStepsTaken} pasos.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                // Header del Paso Actual
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val maneuverIcon = resolveManeuverIcon(step)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(EtsiitBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = maneuverIcon,
                                contentDescription = null,
                                tint = EtsiitBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            val isFinalStep = tripState.currentStepIndex >= tripState.steps.size - 1 ||
                                step.accion?.contains("FIN", ignoreCase = true) == true ||
                                step.accion?.contains("LLEGADA", ignoreCase = true) == true

                            val remainingSteps = (step.targetSteps - tripState.currentStepProgress).coerceAtLeast(0)
                            val titleLabel = if (isFinalStep) {
                                "¡Has llegado a tu destino!"
                            } else if (remainingSteps <= 0) {
                                "¡Paso alcanzado!"
                            } else {
                                "$remainingSteps ${if (remainingSteps == 1) "paso" else "pasos"}"
                            }
                            Text(
                                text = titleLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = EtsiitBlue
                            )
                            Text(
                                text = "Planta ${step.fromNode.planta} → ${step.toNode.planta}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Contador de Pasos restantes del Tramo Actual
                    val isFinalStepCard = tripState.currentStepIndex >= tripState.steps.size - 1 ||
                        step.accion?.contains("FIN", ignoreCase = true) == true ||
                        step.accion?.contains("LLEGADA", ignoreCase = true) == true

                    if (!isFinalStepCard) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CampusTeal.copy(alpha = 0.12f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                    contentDescription = null,
                                    tint = CampusTeal,
                                    modifier = Modifier.size(14.dp)
                                )
                                val remainingSteps = (step.targetSteps - tripState.currentStepProgress).coerceAtLeast(0)
                                Text(
                                    text = "$remainingSteps ${if (remainingSteps == 1) "paso" else "pasos"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CampusTeal
                                )
                            }
                        }
                    }
                }

                // Instrucción de Guiado Secuencial
                Text(
                    text = step.instructionText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    lineHeight = 19.sp
                )

                // Contenedor de Imagen de Guiado
                StepAssetImage(
                    photoPath = step.photoAssetPath,
                    fallbackText = "Vista de pasillo y entorno hacia ${step.toNode.nombre.replace('_', ' ')}",
                    modifier = Modifier.fillMaxWidth()
                )

                // Barra de Progresión de Pasos por Sensor
                val progressFraction = (tripState.currentStepProgress.toFloat() / step.targetSteps.toFloat()).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = CampusTeal,
                        trackColor = BackgroundLight
                    )
                }

                // Controles de Anulación Manual de Respaldo (Fallback Controls)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousManual,
                        enabled = tripState.currentStepIndex > 0,
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = if (tripState.currentStepIndex > 0) Color.Black.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Anterior",
                            tint = if (tripState.currentStepIndex > 0) TextSecondary else TextSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onNextManual,
                        enabled = tripState.currentStepIndex < tripState.steps.size - 1,
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = if (tripState.currentStepIndex < tripState.steps.size - 1) Color.Black.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Siguiente",
                            tint = if (tripState.currentStepIndex < tripState.steps.size - 1) TextSecondary else TextSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Carga de forma eficiente la imagen de guiado fotográfico desde los assets de la app.
 */
@Composable
private fun StepAssetImage(
    photoPath: String?,
    fallbackText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmapState = remember(photoPath) {
        if (!photoPath.isNullOrBlank()) {
            try {
                val inputStream = context.assets.open(photoPath)
                val bm = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                bm
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(14.dp),
        color = BackgroundLight,
        border = BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.4f))
    ) {
        if (bitmapState != null) {
            Image(
                bitmap = bitmapState.asImageBitmap(),
                contentDescription = fallbackText,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = EtsiitBlue.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = fallbackText,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Vista de guiado fotográfico paso a paso interior a pantalla completa (Fullscreen con acelerómetro y brújula),
 * idéntica en formato e interfaz a la navegación exterior.
 */
@Composable
fun IndoorFullScreenStepGuidanceView(
    tripState: IndoorTripState,
    onStepSimulated: () -> Unit,
    onNextManual: () -> Unit,
    onPreviousManual: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val step = tripState.currentStep
    val currentAzimuth = rememberVerticalCompassAzimuth(context)

    LaunchedEffect(tripState.isCompleted) {
        if (tripState.isCompleted) {
            onClose()
        }
    }

    if (tripState.isCompleted || step == null) {
        return
    }

    val deltaTheta: Float = if (step.heading != null && currentAzimuth != null) {
        ((currentAzimuth - step.heading + 540f) % 360f) - 180f
    } else if (currentAzimuth != null) {
        ((currentAzimuth - 180f + 540f) % 360f) - 180f
    } else {
        0f
    }

    val isFacingCorrectDirection = currentAzimuth == null || abs(deltaTheta) <= 35f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. IMAGEN PANORÁMICA A PANTALLA COMPLETA RECORTADA SEGÚN BRÚJULA Y ROTACIÓN
        val photoPath = step.photoAssetPath
        if (!photoPath.isNullOrBlank()) {
            val bitmap: ImageBitmap? = remember(photoPath) {
                loadSampledBitmapFromAssets(context, photoPath, 2)
            }
            if (bitmap != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val screenW = size.width
                    val screenH = size.height
                    if (screenW > 0f && screenH > 0f) {
                        val sliceWidth = (bitmap.height.toFloat() * (screenW / screenH))
                            .roundToInt()
                            .coerceIn(1, bitmap.width)

                        val targetBmpX = bitmap.width.toFloat() * step.ejeXCamino
                        val fovHorizontal = 65f
                        val shiftBmpX = (deltaTheta / fovHorizontal) * sliceWidth.toFloat()
                        val visualCenterX = targetBmpX + shiftBmpX

                        val maxLeft = (bitmap.width - sliceWidth).coerceAtLeast(0)
                        val srcLeft = (visualCenterX - sliceWidth / 2f)
                            .roundToInt()
                            .coerceIn(0, maxLeft)

                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset(srcLeft, 0),
                            srcSize = IntSize(sliceWidth, bitmap.height),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(screenW.roundToInt(), screenH.roundToInt())
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1D24))
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1D24))
            )
        }

        // 2. INDICADOR DIRECCIONAL Y TARJETA
        val maneuverIcon = resolveManeuverIcon(step)

        if (isFacingCorrectDirection) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp, bottom = 82.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = UgrRed,
                    border = BorderStroke(2.5.dp, Color.White),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = maneuverIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(18.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), shape = RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isFinalStep = tripState.currentStepIndex >= tripState.steps.size - 1 ||
                            step.accion?.contains("FIN", ignoreCase = true) == true ||
                            step.accion?.contains("LLEGADA", ignoreCase = true) == true

                        val remainingSteps = (step.targetSteps - tripState.currentStepProgress).coerceAtLeast(0)
                        val stepsLabel = if (isFinalStep) {
                            "¡Has llegado a tu destino!"
                        } else if (remainingSteps <= 0) {
                            "¡Paso alcanzado!"
                        } else {
                            "$remainingSteps ${if (remainingSteps == 1) "paso" else "pasos"}"
                        }
                        Text(
                            text = stepsLabel,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = step.instructionText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 21.sp
                        )
                    }
                }
            }
        } else {
            val turnLeft = deltaTheta > 0f
            val isEnglish = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 82.dp)
                    .background(Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(18.dp))
                    .border(BorderStroke(1.5.dp, UgrRed), shape = RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (turnLeft) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = UgrRed,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = if (turnLeft) {
                            if (isEnglish) "Turn left" else "Gira a la izquierda"
                        } else {
                            if (isEnglish) "Turn right" else "Gira a la derecha"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 3. BOTÓN CERRAR SUPERIOR DERECHO
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.size(44.dp),
                onClick = onClose
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar guiado",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 4. CONTROLES INFERIORES FLOTANTES (Anterior / Siguiente cuadrado discreto en extremos)
        val isLastStep = tripState.currentStepIndex >= tripState.steps.size - 1

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousManual,
                enabled = tripState.currentStepIndex > 0,
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Anterior",
                    tint = if (tripState.currentStepIndex > 0) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = {
                    if (isLastStep) {
                        onClose()
                    } else {
                        onNextManual()
                    }
                },
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                Icon(
                    imageVector = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = if (isLastStep) "Finalizar" else "Siguiente",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Obtiene el rumbo magnético (0°..360°) hacia el que apunta la cámara trasera
 * cuando el usuario sostiene el móvil en vertical frente a él.
 */
@Composable
private fun rememberVerticalCompassAzimuth(context: Context): Float? {
    var azimuth by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasMag = false
        var smoothSin = 0f
        var smoothCos = 0f
        var initialized = false

        fun updateFromRotationMatrix() {
            if (SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Z,
                    remappedMatrix
                )
            ) {
                SensorManager.getOrientation(remappedMatrix, orientationAngles)
                val rad = orientationAngles[0]
                if (!initialized) {
                    smoothSin = kotlin.math.sin(rad)
                    smoothCos = kotlin.math.cos(rad)
                    initialized = true
                } else {
                    val alpha = 0.18f
                    smoothSin += alpha * (kotlin.math.sin(rad) - smoothSin)
                    smoothCos += alpha * (kotlin.math.cos(rad) - smoothCos)
                }
                val deg = ((Math.toDegrees(kotlin.math.atan2(smoothSin.toDouble(), smoothCos.toDouble())).toFloat() + 360f) % 360f)
                azimuth = deg
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        updateFromRotationMatrix()
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                        if (hasMag && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                            updateFromRotationMatrix()
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasMag = true
                        if (hasGravity && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                            updateFromRotationMatrix()
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotationSensor != null) {
            sensorManager?.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else if (accelSensor != null && magSensor != null) {
            sensorManager?.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager?.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return azimuth
}

/**
 * Determina el icono de maniobra adecuado según la acción indicada en el JSON (o maneuver del nodo).
 */
private fun resolveManeuverIcon(step: IndoorStep): ImageVector {
    val accionUpper = step.accion?.uppercase()?.trim() ?: ""
    val tipoUpper = step.tipoTramo?.uppercase()?.trim() ?: ""
    val maneuver = step.maneuver

    return when {
        // 1. Acciones explícitas de giro o llegada
        accionUpper.contains("LIGERO_IZQ") || accionUpper.contains("SLIGHT_LEFT") -> Icons.Default.TurnSlightLeft
        accionUpper.contains("LIGERO_DER") || accionUpper.contains("SLIGHT_RIGHT") -> Icons.Default.TurnSlightRight
        accionUpper.contains("IZQ") || accionUpper.contains("LEFT") -> Icons.Default.TurnLeft
        accionUpper.contains("DER") || accionUpper.contains("RIGHT") -> Icons.Default.TurnRight
        accionUpper.contains("FIN") || accionUpper.contains("LLEGADA") || accionUpper.contains("ARRIV") -> Icons.Default.Flag

        // 2. Acciones explícitas de subir/bajar o usar ascensor o escaleras
        accionUpper.contains("SUBIR") || accionUpper.contains("SUBE") || accionUpper.contains("BAJAR") -> {
            if (accionUpper.contains("ASCENSOR") || tipoUpper.contains("ASCENSOR") || accionUpper.contains("ELEVATOR")) {
                Icons.Default.Elevator
            } else {
                Icons.Default.Stairs
            }
        }

        // 3. Acción explícita de ir recto (prevalece sobre el tipo de tramo si no hay subida/bajada activa)
        accionUpper.contains("RECTO") || accionUpper.contains("STRAIGHT") || accionUpper.contains("PASILLO") -> Icons.Default.ArrowUpward

        // 4. Palabras clave explícitas de ascensor o escaleras en la acción
        accionUpper.contains("ASCENSOR") || accionUpper.contains("ELEVATOR") || accionUpper.contains("LIFT") -> Icons.Default.Elevator
        accionUpper.contains("ESCALERA") || accionUpper.contains("STAIR") -> Icons.Default.Stairs

        // 5. Maniobras direccionales secundarias
        maneuver == Maneuver.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
        maneuver == Maneuver.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
        maneuver == Maneuver.LEFT -> Icons.Default.TurnLeft
        maneuver == Maneuver.RIGHT -> Icons.Default.TurnRight
        maneuver == Maneuver.ARRIVAL -> Icons.Default.Flag

        else -> Icons.Default.ArrowUpward
    }
}
