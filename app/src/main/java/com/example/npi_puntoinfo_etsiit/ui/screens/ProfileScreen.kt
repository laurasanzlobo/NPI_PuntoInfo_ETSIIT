package com.example.npi_puntoinfo_etsiit.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.data.ScheduleRepository
import com.example.npi_puntoinfo_etsiit.data.SubjectOption
import com.example.npi_puntoinfo_etsiit.data.UserProfile
import com.example.npi_puntoinfo_etsiit.data.UserSessionRepository
import com.example.npi_puntoinfo_etsiit.ocr.TuiOcrScanner
import com.example.npi_puntoinfo_etsiit.ocr.TuiScannedData
import com.example.npi_puntoinfo_etsiit.sensors.NfcCardReader
import com.example.npi_puntoinfo_etsiit.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val activity = context as? Activity

    var profile by remember { mutableStateOf(UserSessionRepository.getProfile(context)) }

    // Estados del flujo de Login TUI
    var isProcessingOcr by remember { mutableStateOf(false) }
    var scannedTuiData by remember { mutableStateOf<TuiScannedData?>(null) }
    var nfcVerifiedUid by remember { mutableStateOf<String?>(null) }
    var showSubjectEditorDialog by remember { mutableStateOf(false) }

    // Campos del formulario académico
    var selectedDegreeId by remember { mutableStateOf("gii") }
    var selectedDegreeName by remember { mutableStateOf("Grado en Ingeniería Informática") }
    var selectedCourse by remember { mutableIntStateOf(3) }
    var selectedGroup by remember { mutableStateOf("A") }
    var userMatricula by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Inicializar asignaturas cuando se selecciona grado o curso
    val catalogSubjects = remember(context, selectedDegreeId, selectedCourse) {
        ScheduleRepository.getCatalogSubjects(context, selectedDegreeId, selectedCourse)
    }

    // Auto-preseleccionar asignaturas del curso
    LaunchedEffect(catalogSubjects) {
        if (catalogSubjects.isNotEmpty() && userMatricula.isEmpty()) {
            val defaultMatricula = mutableMapOf<String, String>()
            catalogSubjects.forEach { subj ->
                val defaultSubgroup = subj.subgruposDisponibles.firstOrNull() ?: "A1"
                defaultMatricula[subj.acronym] = defaultSubgroup
            }
            userMatricula = defaultMatricula
        }
    }

    // Listener del sensor NFC
    DisposableEffect(activity, scannedTuiData, nfcVerifiedUid) {
        if (activity != null && scannedTuiData != null && nfcVerifiedUid == null) {
            val nfcReader = NfcCardReader(activity)
            nfcReader.onCardDiscovered = { uid ->
                nfcVerifiedUid = uid
                Toast.makeText(context, "Tarjeta NFC verificada con éxito ($uid)", Toast.LENGTH_SHORT).show()
            }
            nfcReader.startListening()

            onDispose {
                nfcReader.stopListening()
            }
        } else {
            onDispose {}
        }
    }

    // Launcher de cámara para foto a la TUI
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            isProcessingOcr = true
            TuiOcrScanner.processImage(
                bitmap = bitmap,
                onSuccess = { data ->
                    isProcessingOcr = false
                    scannedTuiData = data
                    Toast.makeText(context, "TUI leída: ${data.name}", Toast.LENGTH_SHORT).show()
                },
                onError = { e ->
                    isProcessingOcr = false
                    Toast.makeText(context, "Error leyendo imagen: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (profile.isLoggedIn) {
            // ==========================================
            // VISTA DE USUARIO CON SESIÓN INICIADA
            // ==========================================

            // Carné virtual TUI con datos reales
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(UgrRedDark, UgrRed, Color(0xFF6B111F))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "UNIVERSIDAD DE GRANADA",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Nfc,
                                    contentDescription = "NFC Verificado",
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "UID: ${profile.nfcUid.take(8)}...",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = profile.name,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = profile.degreeName,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "${profile.primaryCourse}º Curso · Grupo ${profile.primaryGroup} · DNI ${profile.dni}",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TUI: ${profile.tuiCardNumber.chunked(4).joinToString(" ")}",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = profile.colectivo,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Sección de Asignaturas Matriculadas y Subgrupos
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mis Asignaturas y Subgrupos",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        IconButton(onClick = { showSubjectEditorDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar matrícula",
                                tint = EtsiitBlue
                            )
                        }
                    }

                    if (profile.matricula.isEmpty()) {
                        Text(
                            text = "Asistiendo al horario general del Grupo ${profile.primaryGroup}.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    } else {
                        profile.matricula.forEach { (acronym, subgroup) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BackgroundLight)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = acronym,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SurfaceWhite
                                    ) {
                                        Text(
                                            text = "Teoría",
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = CampusTeal.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "Prácticas: $subgroup",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CampusTeal,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Botón de Cerrar Sesión
            OutlinedButton(
                onClick = {
                    UserSessionRepository.logout(context)
                    profile = UserProfile()
                    scannedTuiData = null
                    nfcVerifiedUid = null
                    Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = UgrRed)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrar Sesión")
            }

        } else {
            // ==========================================
            // VISTA DE INICIO DE SESIÓN CON TUI + NFC
            // ==========================================

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Inicio de Sesión con TUI",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Escanea tu Tarjeta Universitaria Inteligente de la UGR y acércala al lector NFC para validar tu identidad física y cargar automáticamente tu horario.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            // PASO 1: Captura Óptica de la TUI
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = (if (scannedTuiData != null) Color(0xFF10B981) else UgrRed).copy(alpha = 0.12f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "1",
                                    fontWeight = FontWeight.Bold,
                                    color = if (scannedTuiData != null) Color(0xFF10B981) else UgrRed
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Captura de la TUI (Foto / OCR)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                    }

                    if (scannedTuiData == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { takePhotoLauncher.launch(null) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = UgrRed)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hacer foto", fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    scannedTuiData = TuiOcrScanner.getDemoTui()
                                    Toast.makeText(context, "Cargada TUI demo", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Usar TUI Demo", fontSize = 13.sp)
                            }
                        }

                        if (isProcessingOcr) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Extrayendo datos de la tarjeta con ML Kit...", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    } else {
                        // Resumen de datos extraídos
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(BackgroundLight)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Estudiante: ${scannedTuiData?.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                            Text("DNI: ${scannedTuiData?.dni} · Tarjeta: ${scannedTuiData?.cardNumber?.take(8)}...", fontSize = 12.sp, color = TextSecondary)
                            Text("Colectivo: ${scannedTuiData?.colectivo}", fontSize = 12.sp, color = CampusTeal, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // PASO 2: Verificación de Seguridad por NFC
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = (if (nfcVerifiedUid != null) Color(0xFF10B981) else EtsiitBlue).copy(alpha = 0.12f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "2",
                                    fontWeight = FontWeight.Bold,
                                    color = if (nfcVerifiedUid != null) Color(0xFF10B981) else EtsiitBlue
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Verificación Física por NFC",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                    }

                    if (nfcVerifiedUid == null) {
                        Text(
                            text = if (scannedTuiData != null)
                                "Acerca tu tarjeta física al lector NFC de la parte trasera del teléfono para validar el chip anti-suplantación."
                            else
                                "Completa primero la foto de la TUI en el Paso 1.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        if (scannedTuiData != null) {
                            OutlinedButton(
                                onClick = {
                                    nfcVerifiedUid = NfcCardReader.generateMockUid()
                                    Toast.makeText(context, "Simulación NFC completada", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simular toque NFC (Prueba en emulador)", fontSize = 13.sp)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.1f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Tarjeta TUI física verificada", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF065F46))
                                Text("UID hardware: $nfcVerifiedUid", fontSize = 12.sp, color = Color(0xFF047857))
                            }
                        }
                    }
                }
            }

            // PASO 3: Selección de Grado, Curso y Configuración de Matrícula
            AnimatedVisibility(visible = scannedTuiData != null && nfcVerifiedUid != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = CampusTeal.copy(alpha = 0.12f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("3", fontWeight = FontWeight.Bold, color = CampusTeal)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Matrícula y Asignaturas",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                        }

                        // Selector de Grado
                        Text("Titulación universitaria:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        val degrees = remember { ScheduleRepository.getAvailableDegrees(context) }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            degrees.take(3).forEach { (id, name) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedDegreeId = id
                                            selectedDegreeName = name
                                        }
                                        .background(if (selectedDegreeId == id) EtsiitBlue.copy(alpha = 0.12f) else BackgroundLight)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedDegreeId == id,
                                        onClick = {
                                            selectedDegreeId = id
                                            selectedDegreeName = name
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(name, fontSize = 13.sp, color = TextPrimary)
                                }
                            }
                        }

                        // Selector de Curso y Grupo
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Curso:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(1, 2, 3, 4).forEach { c ->
                                        FilterChip(
                                            selected = selectedCourse == c,
                                            onClick = { selectedCourse = c },
                                            label = { Text("${c}º") }
                                        )
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Grupo:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("A", "B", "C", "D").forEach { g ->
                                        FilterChip(
                                            selected = selectedGroup == g,
                                            onClick = { selectedGroup = g },
                                            label = { Text(g) }
                                        )
                                    }
                                }
                            }
                        }

                        // Asignaturas y subgrupos
                        Text("Asignaturas y subgrupos de prácticas:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        catalogSubjects.forEach { subj ->
                            val currentSubg = userMatricula[subj.acronym] ?: "A1"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BackgroundLight)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(subj.acronym, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    Text(subj.name, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val subgrupos = if (subj.subgruposDisponibles.isNotEmpty()) subj.subgruposDisponibles else listOf("A1", "A2", "A3")
                                    subgrupos.take(3).forEach { sg ->
                                        FilterChip(
                                            selected = currentSubg == sg,
                                            onClick = {
                                                userMatricula = userMatricula.toMutableMap().apply { put(subj.acronym, sg) }
                                            },
                                            label = { Text(sg, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        // Botón Final de Iniciar Sesión
                        Button(
                            onClick = {
                                val tui = scannedTuiData
                                val uid = nfcVerifiedUid
                                if (tui != null && uid != null) {
                                    val newProfile = UserProfile(
                                        name = tui.name,
                                        dni = tui.dni,
                                        tuiCardNumber = tui.cardNumber,
                                        nfcUid = uid,
                                        colectivo = tui.colectivo,
                                        degreeId = selectedDegreeId,
                                        degreeName = selectedDegreeName,
                                        primaryCourse = selectedCourse,
                                        primaryGroup = selectedGroup,
                                        cuatrimestre = 1,
                                        matricula = userMatricula,
                                        isLoggedIn = true
                                    )
                                    UserSessionRepository.saveProfile(context, newProfile)
                                    profile = newProfile
                                    Toast.makeText(context, "¡Sesión iniciada con éxito!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = UgrRed)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Guardar e Iniciar Sesión", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Diálogo para editar asignaturas y subgrupos
    if (showSubjectEditorDialog) {
        AlertDialog(
            onDismissRequest = { showSubjectEditorDialog = false },
            title = { Text("Configurar Asignaturas y Prácticas", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Selecciona el subgrupo de prácticas asignado a cada materia o elimina las que no curses este cuatrimestre:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    catalogSubjects.forEach { subj ->
                        val currentSubg = profile.matricula[subj.acronym] ?: "A1"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BackgroundLight)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(subj.acronym, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val subgs = if (subj.subgruposDisponibles.isNotEmpty()) subj.subgruposDisponibles else listOf("A1", "A2", "A3")
                                subgs.take(3).forEach { sg ->
                                    FilterChip(
                                        selected = currentSubg == sg,
                                        onClick = {
                                            UserSessionRepository.setSubjectSubgroup(context, subj.acronym, sg)
                                            profile = UserSessionRepository.getProfile(context)
                                        },
                                        label = { Text(sg, fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubjectEditorDialog = false }) {
                    Text("Listo")
                }
            }
        )
    }
}
