package com.example.npi_puntoinfo_etsiit.ui.components

import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.npi_puntoinfo_etsiit.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.GrafoETSIIT
import com.example.npi_puntoinfo_etsiit.Nodo
import com.example.npi_puntoinfo_etsiit.data.ClassroomStatus
import com.example.npi_puntoinfo_etsiit.data.EstimatedClassroom
import com.example.npi_puntoinfo_etsiit.formatearPlanta
import com.example.npi_puntoinfo_etsiit.sensors.BarometerFloorEstimate
import com.example.npi_puntoinfo_etsiit.sensors.BarometerManager
import com.example.npi_puntoinfo_etsiit.ui.theme.*

/**
 * Estados del flujo de verificación del nodo de origen inicial
 */
sealed class VerificationStep {
    // Paso 1: Confirmación del aula estimada por el Horario
    data class ScheduleStep(val estimated: EstimatedClassroom, val node: Nodo) : VerificationStep()

    // Paso 2: Estimación de la planta mediante el Barómetro
    data class BarometerStep(val estimate: BarometerFloorEstimate) : VerificationStep()

    // Paso 2.1: Selección del nodo más cercano dentro de la planta estimada por el Barómetro
    data class FloorNodeSelectionStep(val floorCode: String, val floorName: String, val nodes: List<Nodo>) : VerificationStep()

    // Paso 3: Ambas estimaciones fallaron -> Escanear código QR mediante la cámara
    object QRScanStep : VerificationStep()
}

/**
 * Formatea el nombre de un nodo para mostrarlo de forma amigable en la lista de selección.
 */
fun formatearNombreNodoAmigable(
    nodo: Nodo,
    isEnglish: Boolean = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
): String {
    val n = nodo.nombre
    return when {
        n.startsWith("A_") -> {
            val num = n.removePrefix("A_")
            val p = if (num.length >= 2) num.substring(0, 1) else "0"
            val rest = if (num.length >= 2) num.substring(1) else num
            if (isEnglish) "Classroom $p.$rest" else "Aula $p.$rest"
        }
        n.startsWith("L_") -> {
            val num = n.removePrefix("L_")
            val p = if (num.length >= 2) num.substring(0, 1) else "0"
            val rest = if (num.length >= 2) num.substring(1) else num
            if (isEnglish) "Laboratory $p.$rest" else "Laboratorio $p.$rest"
        }
        n.startsWith("D_") -> {
            val rest = n.replace("_", " ")
            if (isEnglish) "Office $rest" else "Despacho $rest"
        }
        n.equals("Entrada_Exterior", ignoreCase = true) || n.contains("Entrada", ignoreCase = true) ->
            if (isEnglish) "Main Outside Entrance" else "Entrada Principal Exterior"
        n.equals("Vestibulo", ignoreCase = true) ->
            if (isEnglish) "Entrance Lobby" else "Vestíbulo de Entrada"
        n.equals("Comedor_PSS", ignoreCase = true) || n.contains("Comedor", ignoreCase = true) ->
            if (isEnglish) "Aynadamar Dining Hall" else "Comedor Aynadamar"
        n.equals("Cafeteria_PSS", ignoreCase = true) || n.contains("Cafeteria", ignoreCase = true) ->
            if (isEnglish) "Cafeteria" else "Cafetería"
        n.contains("Biblioteca", ignoreCase = true) ->
            if (isEnglish) "Library" else "Biblioteca"
        n.contains("Salon", ignoreCase = true) || n.contains("Actos", ignoreCase = true) ->
            if (isEnglish) "Auditorium" else "Salón de Actos"
        n.contains("Secretaria", ignoreCase = true) ->
            if (isEnglish) "Secretary's Office" else "Secretaría"
        n.contains("Conserjeria", ignoreCase = true) ->
            if (isEnglish) "Reception / Janitor's Office" else "Conserjería"
        else -> n.replace("_", " ")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OriginVerificationDialog(
    grafo: GrafoETSIIT,
    estimatedClassroom: EstimatedClassroom?,
    onNodeConfirmed: (Nodo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Determinar paso inicial: si hay aula estimada por horario, empezamos en Paso 1 (ScheduleStep);
    // si no hay horario activo, pasamos directamente a Paso 2 (BarometerStep).
    val initialStep = remember(estimatedClassroom) {
        if (estimatedClassroom != null) {
            val node = grafo.buscarNodoPorDestino(estimatedClassroom.roomName)
            if (node != null) VerificationStep.ScheduleStep(estimatedClassroom, node) else null
        } else null
    }

    var currentStep by remember {
        mutableStateOf<VerificationStep>(
            initialStep ?: VerificationStep.BarometerStep(
                BarometerFloorEstimate("PB", formatearPlanta("PB"), 935.0f, 700.0f)
            )
        )
    }

    // Gestor del sensor de barómetro para el Paso 2
    var barometerEstimate by remember {
        mutableStateOf(BarometerFloorEstimate("PB", formatearPlanta("PB"), 935.0f, 700.0f))
    }

    DisposableEffect(context) {
        val manager = BarometerManager(context)
        manager.onPressureChanged = { est ->
            barometerEstimate = est
            if (currentStep is VerificationStep.BarometerStep) {
                currentStep = VerificationStep.BarometerStep(est)
            }
        }
        manager.startListening()
        onDispose { manager.stopListening() }
    }

    // Launcher de la cámara para el Paso 3 (Escaneo QR)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Al volver de la cámara, cerramos la ventana
        onDismiss()
    }

    fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            cameraLauncher.launch(intent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            runCatching { cameraLauncher.launch(fallbackIntent) }
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.5f))
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "VerificationStepAnimation"
            ) { step ->
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (step) {
                        // =========================================================================
                        // PASO 1: CONFIRMACIÓN DE ORIGEN SEGÚN HORARIO
                        // =========================================================================
                        is VerificationStep.ScheduleStep -> {
                            val isOngoing = step.estimated.status == ClassroomStatus.ONGOING
                            Surface(
                                shape = CircleShape,
                                color = (if (isOngoing) EtsiitBlue else WarningAmber).copy(alpha = 0.12f),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = if (isOngoing) EtsiitBlue else WarningAmber,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Text(
                                text = stringResource(
                                    R.string.verification_prompt_schedule,
                                    step.estimated.classItem.acronym,
                                    step.estimated.roomName,
                                    step.estimated.floorName.lowercase()
                                ),
                                fontSize = 17.sp,
                                lineHeight = 23.sp,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        // Si dice que NO, pasamos a estimación por barómetro
                                        currentStep = VerificationStep.BarometerStep(barometerEstimate)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.verification_no_not_here),
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                }

                                Button(
                                    onClick = {
                                        // Si dice que SÍ, utilizamos este nodo dado
                                        onNodeConfirmed(step.node)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EtsiitBlue)
                                ) {
                                    Text(
                                        text = stringResource(R.string.verification_yes_here),
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // =========================================================================
                        // PASO 2: ESTIMACIÓN DE PLANTA POR BARÓMETRO
                        // =========================================================================
                        is VerificationStep.BarometerStep -> {
                            Surface(
                                shape = CircleShape,
                                color = CampusTeal.copy(alpha = 0.12f),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = CampusTeal,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.verification_barometer_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Text(
                                text = stringResource(R.string.verification_barometer_prompt, step.estimate.floorName),
                                fontSize = 14.sp,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BackgroundLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.verification_barometer_pressure, step.estimate.pressureHpa),
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = stringResource(R.string.verification_barometer_floor, step.estimate.floorCode),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CampusTeal
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        // Si la estimación por barómetro también es errónea, pasamos al Paso 3 (Cámara QR)
                                        currentStep = VerificationStep.QRScanStep
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.verification_no_wrong), fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        // Si dice que SÍ a la planta del barómetro, mostramos la lista de opciones de esa planta
                                        val floorNodes = grafo.obtenerNodosPorPlanta(step.estimate.floorCode)
                                        currentStep = VerificationStep.FloorNodeSelectionStep(
                                            floorCode = step.estimate.floorCode,
                                            floorName = step.estimate.floorName,
                                            nodes = if (floorNodes.isNotEmpty()) floorNodes else listOf(grafo.obtenerNodoInicioPredeterminado())
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CampusTeal)
                                ) {
                                    Text(stringResource(R.string.verification_yes_here), fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }

                        // =========================================================================
                        // PASO 2.1: SELECCIÓN DEL NODO MÁS CERCANO EN LA PLANTA CONFIRMADA
                        // =========================================================================
                        is VerificationStep.FloorNodeSelectionStep -> {
                            var selectedNode by remember { mutableStateOf(step.nodes.firstOrNull()) }

                            Surface(
                                shape = CircleShape,
                                color = EtsiitBlue.copy(alpha = 0.12f),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = EtsiitBlue,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.verification_select_location_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Text(
                                text = stringResource(R.string.verification_select_location_prompt, step.floorName),
                                fontSize = 13.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(BackgroundLight)
                                    .padding(4.dp)
                            ) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(step.nodes) { node ->
                                        val isSelected = (selectedNode?.id == node.id)
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedNode = node },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) EtsiitBlue.copy(alpha = 0.12f) else SurfaceWhite,
                                            border = if (isSelected) BorderStroke(1.dp, EtsiitBlue) else null
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = formatearNombreNodoAmigable(node),
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) EtsiitBlue else TextPrimary
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = EtsiitBlue,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.cancel), fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        selectedNode?.let { onNodeConfirmed(it) }
                                    },
                                    enabled = selectedNode != null,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EtsiitBlue)
                                ) {
                                    Text(stringResource(R.string.confirm), fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }

                        // =========================================================================
                        // PASO 3: AMBAS ESTIMACIONES FALLAN -> ESCANEAR CÓDIGO QR CON LA CÁMARA
                        // =========================================================================
                        VerificationStep.QRScanStep -> {
                            Surface(
                                shape = CircleShape,
                                color = UgrRed.copy(alpha = 0.12f),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = UgrRed,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.verification_qr_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Text(
                                text = stringResource(R.string.verification_qr_prompt),
                                fontSize = 14.sp,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.cancel), fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        openCamera()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = UgrRed)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(stringResource(R.string.open_camera), fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
