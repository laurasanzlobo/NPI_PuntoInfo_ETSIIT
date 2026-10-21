package com.example.npi_puntoinfo_etsiit.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.core.content.ContextCompat
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
    var searchQuery by remember { mutableStateOf("") }
    var courseFilter by remember { mutableIntStateOf(0) } // 0 = Todos, 1..4

    // Cargar catálogo de asignaturas del grado
    val allDegreeSubjects = remember(context, selectedDegreeId) {
        (1..4).flatMap { c -> ScheduleRepository.getCatalogSubjects(context, selectedDegreeId, c) }
            .distinctBy { it.acronym }
            .sortedBy { it.acronym }
    }

    // Inicializar asignaturas cuando se selecciona grado o curso principal
    LaunchedEffect(selectedDegreeId, selectedCourse) {
        val courseSubjects = ScheduleRepository.getCatalogSubjects(context, selectedDegreeId, selectedCourse)
        if (courseSubjects.isNotEmpty() && userMatricula.isEmpty()) {
            val defaultMatricula = mutableMapOf<String, String>()
            courseSubjects.forEach { subj ->
                val defaultSubgroup = subj.subgruposDisponibles.firstOrNull() ?: "A1"
                defaultMatricula[subj.acronym] = defaultSubgroup
            }
            userMatricula = defaultMatricula
        }
    }

    // Listener universal del sensor NFC (acepta cualquier tarjeta física)
    DisposableEffect(activity, scannedTuiData, nfcVerifiedUid) {
        if (activity != null && scannedTuiData != null && nfcVerifiedUid == null) {
            val nfcReader = NfcCardReader(activity)
            nfcReader.onCardDiscovered = { uid ->
                nfcVerifiedUid = uid
                Toast.makeText(context, "Tarjeta NFC detectada y verificada: $uid", Toast.LENGTH_SHORT).show()
            }
            nfcReader.startListening()

            onDispose {
                nfcReader.stopListening()
            }
        } else {
            onDispose {}
        }
    }

    // Procesador seguro de Bitmap
    fun processBitmapSafe(bitmap: Bitmap?) {
        if (bitmap == null) return
        isProcessingOcr = true
        try {
            TuiOcrScanner.processImage(
                bitmap = bitmap,
                onSuccess = { data ->
                    isProcessingOcr = false
                    scannedTuiData = data
                    Toast.makeText(context, "TUI leída: ${data.name}", Toast.LENGTH_SHORT).show()
                },
                onError = { e ->
                    isProcessingOcr = false
                    Toast.makeText(context, "Error OCR: ${e.localizedMessage ?: "desconocido"}", Toast.LENGTH_SHORT).show()
                }
            )
        } catch (e: Exception) {
            isProcessingOcr = false
            Toast.makeText(context, "Excepción al procesar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher de cámara protegido con try-catch
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        processBitmapSafe(bitmap)
    }

    // Launcher para solicitar permiso de cámara en tiempo de ejecución (evita crash)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            try {
                takePhotoLauncher.launch(null)
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo abrir la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permiso de cámara denegado. Puedes usar la galería o TUI demo.", Toast.LENGTH_LONG).show()
        }
    }

    // Launcher de galería (alternativa sin necesidad de cámara)
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                processBitmapSafe(bitmap)
            } catch (e: Exception) {
                Toast.makeText(context, "Error cargando imagen de galería: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
            // VISTA CON SESIÓN INICIADA
            // ==========================================

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
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = profile.nfcUid,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
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
                            Spacer(modifier = Modifier.width(14.dp))
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

            // Sección de asignaturas matriculadas
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
                        Column {
                            Text(
                                text = "Mis Asignaturas (${profile.matricula.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Horario filtrado por tus materias y subgrupos",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        IconButton(onClick = { showSubjectEditorDialog = true }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar matrícula", tint = EtsiitBlue)
                        }
                    }

                    profile.matricula.forEach { (acronym, subgroup) ->
                        val subName = allDegreeSubjects.find { it.acronym == acronym }?.name ?: acronym
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = BackgroundLight)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = acronym, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CampusTeal.copy(alpha = 0.15f)
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
                                Text(text = subName, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                            }
                        }
                    }
                }
            }

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
            // VISTA SIN SESIÓN (ASISTENTE DE LOGIN)
            // ==========================================

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Inicio de Sesión TUI + NFC",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Identifícate con tu carné universitario físico para cargar tu horario exacto y filtrar solo tus asignaturas y subgrupos.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            // PASO 1: Captura Óptica
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = (if (scannedTuiData != null) Color(0xFF10B981) else UgrRed).copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("1", fontWeight = FontWeight.Bold, color = if (scannedTuiData != null) Color(0xFF10B981) else UgrRed)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Captura de la TUI", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    }

                    if (scannedTuiData == null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (hasPermission) {
                                            try {
                                                takePhotoLauncher.launch(null)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error al abrir cámara: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = UgrRed)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cámara", fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = { pickImageLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Galería", fontSize = 13.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    scannedTuiData = TuiOcrScanner.getDemoTui()
                                    Toast.makeText(context, "Cargada TUI demo de Laura Sanz", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Probar con TUI Demo (Sin foto)", fontSize = 13.sp)
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
                                Text("Analizando texto con ML Kit...", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(BackgroundLight)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Estudiante: ${scannedTuiData?.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                            Text("DNI: ${scannedTuiData?.dni} · TUI: ${scannedTuiData?.cardNumber?.chunked(4)?.joinToString(" ")}", fontSize = 12.sp, color = TextSecondary)
                            Text("Colectivo: ${scannedTuiData?.colectivo}", fontSize = 12.sp, color = CampusTeal, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // PASO 2: Verificación NFC Universal
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = (if (nfcVerifiedUid != null) Color(0xFF10B981) else EtsiitBlue).copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("2", fontWeight = FontWeight.Bold, color = if (nfcVerifiedUid != null) Color(0xFF10B981) else EtsiitBlue)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Verificación NFC Física", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    }

                    if (nfcVerifiedUid == null) {
                        Text(
                            text = if (scannedTuiData != null)
                                "Acerca cualquier tarjeta física TUI al lector NFC del móvil para capturar su UID inmutable anti-copia."
                            else
                                "Completa primero la foto de la TUI en el Paso 1.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        if (scannedTuiData != null) {
                            OutlinedButton(
                                onClick = {
                                    nfcVerifiedUid = "EA:76:97:6E"
                                    Toast.makeText(context, "UID verificado: EA:76:97:6E", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simular toque NFC (UID: EA:76:97:6E)", fontSize = 13.sp)
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
                                Text("UID hardware capturado: $nfcVerifiedUid", fontSize = 12.sp, color = Color(0xFF047857))
                            }
                        }
                    }
                }
            }

            // PASO 3: Selección de Grado, Asignaturas con Buscador
            AnimatedVisibility(visible = scannedTuiData != null && nfcVerifiedUid != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = CampusTeal.copy(alpha = 0.15f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("3", fontWeight = FontWeight.Bold, color = CampusTeal)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Matrícula y Asignaturas", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
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
                                        .background(if (selectedDegreeId == id) EtsiitBlue.copy(alpha = 0.1f) else BackgroundLight)
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

                        // Selector de Curso y Grupo Principal
                        Text("Curso y Grupo principal:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Curso:", fontSize = 12.sp, color = TextSecondary)
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
                                Text("Grupo:", fontSize = 12.sp, color = TextSecondary)
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

                        // BARRA DE BÚSQUEDA DE ASIGNATURAS
                        Text("Personalizar asignaturas y subgrupos:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar por nombre o acrónimo (ej. NPI, VC)...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = null, tint = TextSecondary)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        // Filtro rápido de curso para asignaturas
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(0 to "Todos", 1 to "1º", 2 to "2º", 3 to "3º", 4 to "4º").forEach { (c, label) ->
                                FilterChip(
                                    selected = courseFilter == c,
                                    onClick = { courseFilter = c },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Lista filtrada de asignaturas
                        val filteredSubjects = remember(allDegreeSubjects, searchQuery, courseFilter) {
                            allDegreeSubjects.filter { subj ->
                                val matchesSearch = searchQuery.isBlank() ||
                                        subj.acronym.contains(searchQuery, ignoreCase = true) ||
                                        subj.name.contains(searchQuery, ignoreCase = true)
                                val matchesCourse = courseFilter == 0 || subj.curso == courseFilter
                                matchesSearch && matchesCourse
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredSubjects.forEach { subj ->
                                val isEnrolled = userMatricula.containsKey(subj.acronym)
                                val currentSubg = userMatricula[subj.acronym] ?: "A1"

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isEnrolled) EtsiitBlue.copy(alpha = 0.05f) else BackgroundLight
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = isEnrolled,
                                                    onCheckedChange = { checked ->
                                                        val mutable = userMatricula.toMutableMap()
                                                        if (checked) {
                                                            mutable[subj.acronym] = subj.subgruposDisponibles.firstOrNull() ?: "A1"
                                                        } else {
                                                            mutable.remove(subj.acronym)
                                                        }
                                                        userMatricula = mutable
                                                    }
                                                )
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(text = subj.acronym, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = EtsiitBlue.copy(alpha = 0.12f)
                                                        ) {
                                                            Text(
                                                                text = "${subj.curso}º curso",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = EtsiitBlue,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(text = subj.name, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                                                }
                                            }
                                        }

                                        // Subgrupos de prácticas (solo si está matriculada)
                                        if (isEnrolled) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Prácticas:", fontSize = 11.sp, color = TextSecondary)
                                                val subgs = if (subj.subgruposDisponibles.isNotEmpty()) subj.subgruposDisponibles else listOf("A1", "A2", "A3")
                                                subgs.take(4).forEach { sg ->
                                                    FilterChip(
                                                        selected = currentSubg == sg,
                                                        onClick = {
                                                            userMatricula = userMatricula.toMutableMap().apply { put(subj.acronym, sg) }
                                                        },
                                                        label = { Text(sg, fontSize = 10.sp) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Botón de Inicio de Sesión
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

    // Diálogo de edición de asignaturas
    if (showSubjectEditorDialog) {
        var dialogSearch by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSubjectEditorDialog = false },
            title = { Text("Gestionar Matrícula", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = dialogSearch,
                        onValueChange = { dialogSearch = it },
                        placeholder = { Text("Buscar asignatura...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    allDegreeSubjects.filter {
                        dialogSearch.isBlank() || it.acronym.contains(dialogSearch, ignoreCase = true) || it.name.contains(dialogSearch, ignoreCase = true)
                    }.forEach { subj ->
                        val isEnrolled = profile.matricula.containsKey(subj.acronym)
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
                            Checkbox(
                                checked = isEnrolled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        UserSessionRepository.setSubjectSubgroup(context, subj.acronym, "A1")
                                    } else {
                                        UserSessionRepository.removeSubject(context, subj.acronym)
                                    }
                                    profile = UserSessionRepository.getProfile(context)
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(subj.acronym, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(subj.name, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                            }
                            if (isEnrolled) {
                                val subgs = if (subj.subgruposDisponibles.isNotEmpty()) subj.subgruposDisponibles else listOf("A1", "A2", "A3")
                                subgs.take(3).forEach { sg ->
                                    FilterChip(
                                        selected = currentSubg == sg,
                                        onClick = {
                                            UserSessionRepository.setSubjectSubgroup(context, subj.acronym, sg)
                                            profile = UserSessionRepository.getProfile(context)
                                        },
                                        label = { Text(sg, fontSize = 9.sp) }
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
