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
import androidx.compose.material.icons.filled.Restaurant
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
import com.example.npi_puntoinfo_etsiit.sensors.CampusLocationManager
import com.example.npi_puntoinfo_etsiit.sensors.DeviceCoordinates
import com.example.npi_puntoinfo_etsiit.sensors.DeviceLocationProvider
import com.example.npi_puntoinfo_etsiit.sensors.EtsiitBuilding
import com.example.npi_puntoinfo_etsiit.sensors.LocationPermission
import com.example.npi_puntoinfo_etsiit.ui.theme.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest

/**
 * Estados del flujo de verificación del nodo de origen inicial
 */
sealed class VerificationStep {
    // Paso 0: Caso especial ruta demo Aula 1.1 (inicio desde Comedor Aynadamar)
    data class DemoComedorStep(val node: Nodo) : VerificationStep()

    // Paso 1: Confirmación del aula estimada por el Horario
    data class ScheduleStep(val estimated: EstimatedClassroom, val node: Nodo) : VerificationStep()

    // Paso 2: Estimación de la planta mediante el Barómetro y del edificio mediante GPS
    data class BarometerStep(val estimate: BarometerFloorEstimate) : VerificationStep()

    // Paso 2.1: Selección del nodo más cercano dentro de la planta y edificio estimados
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
    val nLower = n.lowercase()

    return when {
        // Aulas y laboratorios
        n.equals("A_Menos1", ignoreCase = true) -> if (isEnglish) "Classroom -1" else "Aula -1"
        n.equals("A_Menos2", ignoreCase = true) -> if (isEnglish) "Classroom -2" else "Aula -2"
        n.startsWith("A_", ignoreCase = true) -> {
            val num = n.removePrefix("A_").removePrefix("a_")
            val p = if (num.length >= 2) num.substring(0, 1) else "0"
            val rest = if (num.length >= 2) num.substring(1) else num
            if (isEnglish) "Classroom $p.$rest" else "Aula $p.$rest"
        }
        n.startsWith("L_", ignoreCase = true) -> {
            val num = n.removePrefix("L_").removePrefix("l_")
            val p = if (num.length >= 2) num.substring(0, 1) else "0"
            val rest = if (num.length >= 2) num.substring(1) else num
            if (isEnglish) "Laboratory $p.$rest" else "Laboratorio $p.$rest"
        }
        n.startsWith("D_", ignoreCase = true) -> {
            val num = n.removePrefix("D_").removePrefix("d_")
            if (isEnglish) "Office $num" else "Despacho $num"
        }

        // Aseos y baños (limpiando "desp", "PB", etc.)
        nLower.contains("bath") || nLower.contains("bano") || nLower.contains("baño") -> {
            when {
                nLower.contains("minus") ->
                    if (isEnglish) "Accessible Restroom (PRM)" else "Aseos adaptados (PMR)"
                nLower.contains("chicas") ->
                    if (nLower.contains("bib")) {
                        if (isEnglish) "Women's Restroom (Library)" else "Aseos femeninos (Biblioteca)"
                    } else {
                        if (isEnglish) "Women's Restroom" else "Aseos femeninos"
                    }
                nLower.contains("chicos") ->
                    if (nLower.contains("bib")) {
                        if (isEnglish) "Men's Restroom (Library)" else "Aseos masculinos (Biblioteca)"
                    } else {
                        if (isEnglish) "Men's Restroom" else "Aseos masculinos"
                    }
                else ->
                    if (isEnglish) "Restrooms" else "Aseos / Baños"
            }
        }

        // Ascensores (sin "PB", "P1", etc.)
        nLower.contains("ascensor") -> {
            when {
                nLower.contains("despacho") ->
                    if (isEnglish) "Offices Elevator" else "Ascensor de despachos"
                nLower.contains("aula") || nLower.contains("lab") ->
                    if (isEnglish) "Classrooms Elevator" else "Ascensor de aulas y laboratorios"
                else ->
                    if (isEnglish) "Elevator" else "Ascensor"
            }
        }

        // Escaleras (sin códigos raros)
        nLower.startsWith("esc") -> {
            when {
                nLower.contains("exterior") ->
                    if (isEnglish) "Exterior Stairs" else "Escaleras exteriores"
                nLower.contains("interior") ->
                    if (isEnglish) "Interior Stairs" else "Escaleras interiores"
                nLower.contains("bib") ->
                    if (isEnglish) "Library Stairs" else "Escaleras de biblioteca"
                nLower.contains("mesas") ->
                    if (isEnglish) "Stairs to Outside Tables" else "Escaleras hacia mesas"
                nLower.contains("pss") ->
                    if (isEnglish) "Stairs to Floor -1" else "Escaleras a planta -1"
                else ->
                    if (isEnglish) "Stairs" else "Escaleras"
            }
        }

        // Entradas y accesos
        n.equals("Entrada_Exterior", ignoreCase = true) ->
            if (isEnglish) "Main Outside Entrance" else "Entrada principal exterior"
        n.equals("Vestibulo", ignoreCase = true) || n.equals("Vestibulo_PSS", ignoreCase = true) ->
            if (isEnglish) "Lobby" else "Vestíbulo"
        n.equals("Entrada_Aulas_1", ignoreCase = true) ->
            if (isEnglish) "Classrooms Entrance (Gate 1)" else "Entrada a las aulas (Acceso 1)"
        n.equals("Entrada_Aulas_2", ignoreCase = true) ->
            if (isEnglish) "Classrooms Entrance (Gate 2)" else "Entrada a las aulas (Acceso 2)"
        nLower.contains("e_desp_principal") ->
            if (isEnglish) "Offices Main Entrance" else "Entrada principal de despachos"
        nLower.contains("e_desp_secundaria") ->
            if (isEnglish) "Offices Side Entrance" else "Entrada secundaria de despachos"
        nLower.contains("acceso_diresec") ->
            if (isEnglish) "Dean's & Secretary Access" else "Acceso a Dirección y Secretaría"
        nLower.contains("entrada_biblioteca") || n.equals("Biblioteca_2_entrada", ignoreCase = true) ->
            if (isEnglish) "Library Entrance" else "Entrada a la biblioteca"
        nLower.contains("entrada_lab") || nLower.contains("entrada_p3_labs") ->
            if (isEnglish) "Laboratories Entrance" else "Entrada a laboratorios"
        nLower.contains("entrada_pss") ->
            if (isEnglish) "Floor -1 Entrance" else "Entrada planta -1"
        nLower.contains("entrada_exterior_p1") ->
            if (isEnglish) "Floor 1 Outside Entrance" else "Entrada exterior planta 1"

        // Servicios y dependencias
        nLower.contains("conserjeria") -> {
            if (nLower.contains("aulas")) {
                if (isEnglish) "Classrooms Janitor's Office" else "Conserjería de aulas"
            } else {
                if (isEnglish) "Janitor's Office / Reception" else "Conserjería"
            }
        }
        n.startsWith("Secretaria_", ignoreCase = true) || n.equals("Secretaria", ignoreCase = true) ->
            if (isEnglish) "Secretary's Office" else "Secretaría"
        n.startsWith("Secretario_", ignoreCase = true) || n.equals("Secretario", ignoreCase = true) ->
            if (isEnglish) "Dean's Secretary" else "Secretaría de dirección"
        n.startsWith("Director_", ignoreCase = true) || n.equals("Director", ignoreCase = true) ->
            if (isEnglish) "ETSIIT Dean's Office" else "Dirección de la ETSIIT"
        nLower.contains("comedor") ->
            if (isEnglish) "Aynadamar Dining Hall" else "Comedor Aynadamar"
        nLower.contains("cafeteria") || nLower.contains("cafetería") ->
            if (isEnglish) "Cafeteria" else "Cafetería"
        nLower.contains("salon") || nLower.contains("actos") ->
            if (isEnglish) "Auditorium" else "Salón de Actos"
        n.equals("Biblioteca", ignoreCase = true) ->
            if (isEnglish) "Library" else "Biblioteca"
        nLower.contains("bibliotecario") ->
            if (isEnglish) "Library Help Desk" else "Mostrador de biblioteca"
        nLower.contains("ordenadores") ->
            if (isEnglish) "Computer Lab" else "Sala de ordenadores"
        nLower.contains("salas_estudio") || nLower.contains("sala_estudio") ->
            if (isEnglish) "Study Rooms" else "Salas de estudio"
        nLower.contains("equipo_tecnico") ->
            if (isEnglish) "Technical Support" else "Equipo técnico"
        nLower.contains("delegacion_estudiantes") ->
            if (isEnglish) "Student Council" else "Delegación de estudiantes"
        n.equals("Mesas_Exteriores", ignoreCase = true) ->
            if (isEnglish) "Outside Tables" else "Mesas exteriores"

        else -> n.replace("_", " ")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OriginVerificationDialog(
    grafo: GrafoETSIIT,
    estimatedClassroom: EstimatedClassroom?,
    destinationTitle: String = "",
    onNodeConfirmed: (Nodo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val isDemoRouteToAula11 = remember(destinationTitle) {
        destinationTitle.contains("1.1", ignoreCase = true) ||
            destinationTitle.contains("A_11", ignoreCase = true)
    }

    val nodoComedor = remember(grafo) {
        grafo.buscarNodoPorDestino("Comedor")
            ?: grafo.obtenerNodoPorId("Comedor_PSS")
            ?: grafo.obtenerNodoInicioPredeterminado()
    }

    // Coordenadas GPS del usuario para distinguir entre el edificio de las aulas y el de zonas comunes
    var userCoordinates by remember {
        mutableStateOf<DeviceCoordinates?>(
            DeviceLocationProvider.getInstantLocation(context) ?: CampusLocationManager.lastKnownLocation
        )
    }

    LaunchedEffect(context) {
        val provider = DeviceLocationProvider(context)
        val perm = provider.permission()
        if (perm != LocationPermission.DENIED) {
            DeviceLocationProvider.getInstantLocation(context)?.let {
                userCoordinates = it
                CampusLocationManager.lastKnownLocation = it
            }
            provider.lastKnownLocation()?.let {
                userCoordinates = it
                CampusLocationManager.lastKnownLocation = it
            }
            if (provider.isLocationEnabled()) {
                provider.updates(perm, 2_000L)
                    .catch { }
                    .collectLatest { coords ->
                        if (coords != null) {
                            userCoordinates = coords
                            CampusLocationManager.lastKnownLocation = coords
                        }
                    }
            }
        }
    }

    // Determinar paso inicial:
    // 1. Si es la ruta demo hacia Aula 1.1, iniciamos con la pregunta del Comedor
    // 2. Si hay aula estimada por horario, empezamos en Paso 1 (ScheduleStep)
    // 3. Si no hay horario activo, pasamos directamente a Paso 2 (BarometerStep)
    val initialStep = remember(isDemoRouteToAula11, estimatedClassroom) {
        if (isDemoRouteToAula11) {
            VerificationStep.DemoComedorStep(nodoComedor)
        } else if (estimatedClassroom != null) {
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
                        // PASO 0: CONFIRMACIÓN DE ORIGEN COMEDOR PARA RUTA DEMO AULA 1.1
                        // =========================================================================
                        is VerificationStep.DemoComedorStep -> {
                            Surface(
                                shape = CircleShape,
                                color = CampusTeal.copy(alpha = 0.12f),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Restaurant,
                                        contentDescription = null,
                                        tint = CampusTeal,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.verification_prompt_comedor),
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
                                        currentStep = VerificationStep.QRScanStep
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
                                        onNodeConfirmed(step.node)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CampusTeal)
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
                        // PASO 2: ESTIMACIÓN DE PLANTA (BARÓMETRO) Y EDIFICIO (GPS)
                        // =========================================================================
                        is VerificationStep.BarometerStep -> {
                            val detectedBuilding = CampusLocationManager.estimateEtsiitBuilding(
                                userLat = userCoordinates?.latitude,
                                userLon = userCoordinates?.longitude,
                                floorCode = step.estimate.floorCode
                            )
                            val buildingLabel = when (detectedBuilding) {
                                EtsiitBuilding.AULAS -> stringResource(R.string.building_classrooms)
                                EtsiitBuilding.ZONAS_COMUNES -> stringResource(R.string.building_common_areas)
                            }

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
                                text = stringResource(
                                    R.string.verification_barometer_prompt,
                                    step.estimate.floorName.lowercase(),
                                    buildingLabel
                                ),
                                fontSize = 17.sp,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                lineHeight = 23.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        // Si la estimación también es errónea, pasamos directamente al Paso 3 (Cámara QR)
                                        currentStep = VerificationStep.QRScanStep
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
                                        // Si dice que SÍ, filtramos los nodos solo por la planta y el edificio detectado
                                        val floorNodes = grafo.obtenerNodosPorPlanta(
                                            planta = step.estimate.floorCode,
                                            edificio = detectedBuilding.graphCode
                                        )
                                        val candidateNodes = if (floorNodes.isNotEmpty()) floorNodes else listOf(grafo.obtenerNodoInicioPredeterminado())
                                        val isEnglish = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
                                        val sortedNodes = candidateNodes.sortedBy { formatearNombreNodoAmigable(it, isEnglish) }
                                        currentStep = VerificationStep.FloorNodeSelectionStep(
                                            floorCode = step.estimate.floorCode,
                                            floorName = step.estimate.floorName,
                                            nodes = sortedNodes
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CampusTeal)
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
                                    modifier = Modifier.weight(0.85f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.cancel),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Button(
                                    onClick = {
                                        // Simular escaneo de QR: si es la ruta demo al Aula 1.1, se simula el QR del comedor; si no, entrada principal
                                        val confirmedNode = if (isDemoRouteToAula11) nodoComedor else grafo.obtenerNodoInicioPredeterminado()
                                        onNodeConfirmed(confirmedNode)
                                    },
                                    modifier = Modifier.weight(1.35f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
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
                                        Text(
                                            text = stringResource(R.string.open_camera),
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false
                                        )
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
