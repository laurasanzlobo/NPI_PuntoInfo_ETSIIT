package com.example.npi_puntoinfo_etsiit.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.outdoor.OutdoorNavigationRoute
import com.example.npi_puntoinfo_etsiit.sensors.AccelerometerManager
import com.example.npi_puntoinfo_etsiit.ui.components.GestureBadge
import com.example.npi_puntoinfo_etsiit.ui.components.GestureType
import com.example.npi_puntoinfo_etsiit.ui.theme.*
import org.json.JSONObject
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

data class ProfessorClass(
    val subject: String,
    val type: String,
    val room: String,
    val schedule: String
)

data class Professor(
    val name: String,
    val subject: String,
    val office: String,
    val officeHours: String,
    val email: String,
    val isMyTeacher: Boolean = false,
    val department: String = "",
    val isExternal: Boolean = false,
    val campus: String = "Campus Aynadamar (ETSIIT)",
    val avatar: String = "",
    val classes: List<ProfessorClass> = emptyList()
)

enum class ProfessorActivityType {
    IN_OFFICE_HOURS, // 🟢 En tutoría
    IN_CLASS,        // 🔵 En clase
    OFFLINE          // ⚪ Fuera de horario
}

data class ProfessorActivityStatus(
    val type: ProfessorActivityType,
    val badgeText: String,
    val badgeColor: Color,
    val dialogMessage: String
)

data class DemoTimeOption(
    val label: String,
    val dayOfWeek: Int, // 1: Lunes, 2: Martes, 3: Miercoles, 4: Jueves, 5: Viernes
    val minutesOfDay: Int,
    val description: String
)

/**
 * Función que evalúa en tiempo real si el profesor está en tutoría, en clase o fuera de horario
 */
fun getProfessorActivityStatus(
    prof: Professor,
    dayOfWeek: Int,
    minutesOfDay: Int
): ProfessorActivityStatus {
    val diasMap = mapOf(
        "lunes" to 1, "martes" to 2, "miércoles" to 3, "miercoles" to 3,
        "jueves" to 4, "viernes" to 5
    )

    fun parseTime(tStr: String): Int {
        val parts = tStr.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    // 1. Comprobar si está impartiendo clase ahora mismo
    for (c in prof.classes) {
        val regex = Regex("""(lunes|martes|miércoles|miercoles|jueves|viernes)\s+(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})""", RegexOption.IGNORE_CASE)
        val match = regex.find(c.schedule)
        if (match != null) {
            val dName = match.groupValues[1].lowercase()
            val dNum = diasMap[dName] ?: -1
            val ini = parseTime(match.groupValues[2])
            val fin = parseTime(match.groupValues[3])
            if (dNum == dayOfWeek && minutesOfDay in ini until fin) {
                val roomFormatted = c.room.replace("_", "-")
                return ProfessorActivityStatus(
                    type = ProfessorActivityType.IN_CLASS,
                    badgeText = "En clase: $roomFormatted",
                    badgeColor = CampusTeal,
                    dialogMessage = "En este momento ${prof.name} está impartiendo clase de ${c.subject} en el aula $roomFormatted hasta las ${match.groupValues[3]}. Te recomendamos esperar o escribirle un correo."
                )
            }
        }
    }

    // 2. Comprobar si está en su despacho en horario de tutoría
    val tut = prof.officeHours
    val regexTut = Regex("""(?:(?:y|,)?\s*(lunes|martes|miércoles|miercoles|jueves|viernes)(?:\s+y\s+(lunes|martes|miércoles|miercoles|jueves|viernes))?\s+de\s+(\d{1,2}:\d{2})\s+a\s+(\d{1,2}:\d{2}))""", RegexOption.IGNORE_CASE)
    val matches = regexTut.findAll(tut)
    for (m in matches) {
        val d1 = diasMap[m.groupValues[1].lowercase()] ?: -1
        val d2 = diasMap[m.groupValues[2].lowercase()] ?: -1
        val ini = parseTime(m.groupValues[3])
        val fin = parseTime(m.groupValues[4])
        if ((d1 == dayOfWeek || d2 == dayOfWeek) && minutesOfDay in ini until fin) {
            val officeClean = if (prof.office.startsWith("D_")) "Despacho D-${prof.office.removePrefix("D_")}" else prof.office
            return ProfessorActivityStatus(
                type = ProfessorActivityType.IN_OFFICE_HOURS,
                badgeText = "En tutoría",
                badgeColor = Color(0xFF16A34A), // Verde vivo
                dialogMessage = "¡Buenas noticias! ${prof.name} se encuentra ahora mismo en su $officeClean atendiendo tutorías hasta las ${m.groupValues[4]}. ¿Quieres que te acompañe hasta allí?"
            )
        }
    }

    // 3. Fuera de horario de actividad
    val officeClean = if (prof.office.startsWith("D_")) "Despacho D-${prof.office.removePrefix("D_")}" else prof.office
    val msg = if (prof.isExternal) {
        "${prof.name} se encuentra en ${prof.campus}. Sus tutorías habituales son: ${prof.officeHours}."
    } else {
        "${prof.name} no tiene tutorías ni clases ahora mismo. Sus tutorías en el $officeClean son: ${prof.officeHours}. Puedes redactar un correo para solicitar cita."
    }

    return ProfessorActivityStatus(
        type = ProfessorActivityType.OFFLINE,
        badgeText = "Fuera de horario",
        badgeColor = Color(0xFF94A3B8),
        dialogMessage = msg
    )
}

/**
 * Punto de estado animado (parpadea si está en tutoría)
 */
@Composable
fun StatusDot(color: Color, isPulsing: Boolean = false) {
    val alpha = if (isPulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotAlpha"
        ).value
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

/**
 * Carga la foto del profesor desde assets/avatars/ soportando .jpeg y .jpg
 */
fun loadAvatarBitmap(context: Context, avatarName: String): ImageBitmap? {
    if (avatarName.isBlank()) return null
    return try {
        val stream = try {
            context.assets.open("avatars/$avatarName.jpeg")
        } catch (_: Exception) {
            context.assets.open("avatars/$avatarName.jpg")
        }
        val bmp = BitmapFactory.decodeStream(stream)
        bmp?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/**
 * Componente visual de foto de perfil con fallback elegante a iniciales
 */
@Composable
fun ProfessorAvatar(
    prof: Professor,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 46.dp
) {
    val context = LocalContext.current
    val bitmap = remember(prof.avatar) { loadAvatarBitmap(context, prof.avatar) }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = prof.name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        val initials = remember(prof.name) {
            prof.name.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .map { it.first().uppercase() }
                .joinToString("")
        }
        val tintColor = if (prof.isMyTeacher) UgrRed else CampusTeal
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(tintColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = tintColor,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp
            )
        }
    }
}

private fun normalizarTexto(s: String): String {
    val norm = Normalizer.normalize(s, Normalizer.Form.NFD)
    return norm.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase().trim()
}

@Composable
fun ProfessorsScreen(onNavigate: (String) -> Unit, searchedName: String? = null) {
    val context = LocalContext.current
    var selectedProfessor by remember { mutableStateOf<Professor?>(null) }
    var emailGestureConfirmProf by remember { mutableStateOf<Professor?>(null) }
    var searchQuery by remember { mutableStateOf(searchedName ?: "") }
    var selectedFilterCategory by remember { mutableStateOf("Todos") }

    // Motor Text-to-Speech (TTS) en español para el diálogo conversacional
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.language = Locale("es", "ES")
            }
        }
        tts = ttsInstance
        onDispose {
            ttsInstance?.stop()
            ttsInstance?.shutdown()
            tts = null
        }
    }

    fun speak(text: String) {
        tts?.let {
            if (it.isSpeaking) {
                it.stop()
                isSpeaking = false
            } else {
                isSpeaking = true
                it.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prof_dialog")
            }
        }
    }

    // Opciones para el Modo Demo de horario
    val demoOptions = remember {
        listOf(
            DemoTimeOption("Hora real del móvil", -1, -1, "Hora actual del sistema"),
            DemoTimeOption("Jueves 09:30 (Clase NPI)", 4, 9 * 60 + 30, "Marcelino en Clase (Aula A-05)"),
            DemoTimeOption("Lunes 11:00 (Tutorías)", 1, 11 * 60, "Marcelino y Campos en Despacho"),
            DemoTimeOption("Martes 12:00 (Tutoría Zoraida)", 2, 12 * 60, "Zoraida en Despacho D-202"),
            DemoTimeOption("Viernes 09:00 (Clase Visión)", 5, 9 * 60, "Pablo Mesejo en Clase (L-26)")
        )
    }
    var selectedDemoIndex by remember { mutableStateOf(0) }
    var showDemoDialog by remember { mutableStateOf(false) }

    val (currentDayOfWeek, currentMinutesOfDay) = remember(selectedDemoIndex) {
        val opt = demoOptions[selectedDemoIndex]
        if (opt.dayOfWeek == -1) {
            val cal = Calendar.getInstance()
            val raw = cal.get(Calendar.DAY_OF_WEEK)
            val d = when (raw) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }
            val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            d to m
        } else {
            opt.dayOfWeek to opt.minutesOfDay
        }
    }

    val professorsList = remember { loadProfessors(context) }

    // Launcher de reconocimiento por voz integrado
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            if (!spokenText.isNullOrBlank()) {
                triggerVibration(context, durationMs = 120)
                searchQuery = spokenText
            }
        }
    }

    fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el nombre, apellido o departamento")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "El reconocimiento por voz no está disponible", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher de reconocimiento por voz para responder a las preguntas del Asistente
    val dialogSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            if (!spokenText.isNullOrBlank() && selectedProfessor != null) {
                val prof = selectedProfessor!!
                val clean = normalizarTexto(spokenText)

                if (clean.contains("si") || clean.contains("llevame") || clean.contains("guiame") || clean.contains("ir") || clean.contains("vamos") || clean.contains("despacho")) {
                    triggerVibration(context, 150)
                    if (!prof.isExternal) {
                        val roomName = prof.office.split("·")[0].trim()
                        val floorName = prof.office.split("·").getOrElse(1) { "ETSIIT" }.trim()
                        selectedProfessor = null
                        tts?.stop()
                        isSpeaking = false
                        val locationText = if (floorName.isNotBlank() && floorName != "ETSIIT") "${prof.name} · $floorName" else prof.name
                        onNavigate(OutdoorNavigationRoute.forProfessor(roomName, locationText))
                    } else {
                        Toast.makeText(context, "El profesor se encuentra en ${prof.campus}", Toast.LENGTH_LONG).show()
                    }
                } else if (clean.contains("correo") || clean.contains("email") || clean.contains("mail") || clean.contains("escribir") || clean.contains("mensaje")) {
                    triggerVibration(context, 150)
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:${prof.email}")
                        putExtra(Intent.EXTRA_SUBJECT, "Tutoría: [Indica tu motivo]")
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Abriendo correo. ¡Recuerda que también puedes inclinar el móvil!", Toast.LENGTH_SHORT).show()
                } else if (clean.contains("no") || clean.contains("cancelar") || clean.contains("cerrar") || clean.contains("nada")) {
                    triggerVibration(context, 80)
                    tts?.stop()
                    isSpeaking = false
                    selectedProfessor = null
                } else {
                    Toast.makeText(context, "Has dicho: \"$spokenText\". Prueba con 'Sí', 'Correo' o 'No'", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startDialogVoiceReply() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Di 'Sí', 'Llévame', 'Correo' o 'No'")
        }
        try {
            dialogSpeechLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "El reconocimiento por voz no está disponible", Toast.LENGTH_SHORT).show()
        }
    }

    androidx.compose.runtime.LaunchedEffect(searchedName) {
        if (!searchedName.isNullOrBlank()) {
            searchQuery = searchedName
            val found = professorsList.find { it.name == searchedName }
            if (found != null) {
                selectedProfessor = found
            }
        }
    }

    // Acelerómetro: gesto «¡Llévame!» y correo al inclinar
    DisposableEffect(selectedProfessor) {
        val manager = AccelerometerManager(context)

        if (selectedProfessor != null) {
            manager.onTakeMeThereGesture = {
                triggerVibration(context, durationMs = 150)
                val prof = selectedProfessor
                if (prof != null) {
                    if (prof.isExternal) {
                        Toast.makeText(context, "El profesor se encuentra en ${prof.campus}", Toast.LENGTH_LONG).show()
                    } else {
                        val roomName = prof.office.split("·")[0].trim()
                        val floorName = prof.office.split("·").getOrElse(1) { "ETSIIT" }.trim()
                        selectedProfessor = null
                        val locationText = if (floorName.isNotBlank() && floorName != "ETSIIT") "${prof.name} · $floorName" else prof.name
                        onNavigate(OutdoorNavigationRoute.forProfessor(roomName, locationText))
                    }
                }
            }

            manager.onNodStarted = {
                // Fase 1: Inclinación hacia adelante (sale del campo de visión)
                triggerVibration(context, durationMs = 35)
            }

            manager.onForwardTiltGesture = {
                // Fase 2: Retorno a la postura erguida (vuelve al campo de visión)
                triggerVibration(context, durationMs = 150)
                val prof = selectedProfessor
                if (prof != null) {
                    emailGestureConfirmProf = prof
                }
            }

            manager.startListening()
        }

        onDispose {
            manager.stopListening()
            tts?.stop()
            isSpeaking = false
        }
    }

    // Filtrado en tiempo real
    val filteredList = remember(professorsList, searchQuery, selectedFilterCategory) {
        val queryNorm = normalizarTexto(searchQuery)

        professorsList.filter { prof ->
            val matchesCategory = when (selectedFilterCategory) {
                "Mis Profesores" -> prof.isMyTeacher
                "CCIA" -> prof.department.contains("Computación", ignoreCase = true)
                "LSI" -> prof.department.contains("Lenguajes", ignoreCase = true)
                "Otras Áreas" -> !prof.isExternal && !prof.department.contains("Computación", ignoreCase = true) && !prof.department.contains("Lenguajes", ignoreCase = true)
                "Exterior UGR" -> prof.isExternal
                else -> true
            }

            val matchesSearch = if (queryNorm.isBlank()) {
                true
            } else {
                normalizarTexto(prof.name).contains(queryNorm) ||
                        normalizarTexto(prof.department).contains(queryNorm) ||
                        normalizarTexto(prof.subject).contains(queryNorm) ||
                        normalizarTexto(prof.office).contains(queryNorm) ||
                        normalizarTexto(prof.campus).contains(queryNorm)
            }

            matchesCategory && matchesSearch
        }
    }

    val myTeachers = remember(professorsList) { professorsList.filter { it.isMyTeacher } }

    // Diálogo de Selección de Hora Simulada (Modo Demo)
    if (showDemoDialog) {
        AlertDialog(
            onDismissRequest = { showDemoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, tint = CampusTeal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simular Hora (Modo Demo)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Selecciona un momento para probar en directo cómo la app detecta si un profesor está en tutoría o en clase:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    demoOptions.forEachIndexed { idx, opt ->
                        Surface(
                            onClick = {
                                selectedDemoIndex = idx
                                showDemoDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedDemoIndex == idx) CampusTeal.copy(alpha = 0.15f) else BackgroundLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = opt.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (selectedDemoIndex == idx) CampusTeal else TextPrimary
                                )
                                Text(
                                    text = opt.description,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDemoDialog = false }) {
                    Text("Cerrar", color = CampusTeal)
                }
            }
        )
    }

    // Diálogo con información detallada y Diálogo Conversacional Asistente
    if (selectedProfessor != null) {
        val prof = selectedProfessor!!
        val status = remember(prof, currentDayOfWeek, currentMinutesOfDay) {
            getProfessorActivityStatus(prof, currentDayOfWeek, currentMinutesOfDay)
        }

        AlertDialog(
            onDismissRequest = {
                selectedProfessor = null
                tts?.stop()
                isSpeaking = false
            },
            containerColor = SurfaceWhite,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary,
            icon = {
                ProfessorAvatar(prof = prof, size = 64.dp)
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = prof.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = prof.subject,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Estado de actividad y rol
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = status.badgeColor.copy(alpha = 0.14f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                StatusDot(
                                    color = status.badgeColor,
                                    isPulsing = status.type == ProfessorActivityType.IN_OFFICE_HOURS
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = status.badgeText.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = status.badgeColor
                                )
                            }
                        }

                        if (prof.isMyTeacher) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = UgrRed.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = UgrRed,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "TU PROFESOR",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = UgrRed
                                    )
                                }
                            }
                        }
                    }

                    // --- BURBUJA DE DIÁLOGO INTELIGENTE (ASISTENTE NPI CON DIÁLOGO MULTIMODAL) ---
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CampusTeal.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = CampusTeal,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Asistente UGR",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CampusTeal
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // 1. Botón para ESCUCHAR por voz (TTS)
                                    FilledTonalButton(
                                        onClick = { speak(status.dialogMessage) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (isSpeaking) UgrRed.copy(alpha = 0.15f) else CampusTeal.copy(alpha = 0.15f),
                                            contentColor = if (isSpeaking) UgrRed else CampusTeal
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(if (isSpeaking) "Parar" else "Oír", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // 2. Botón para RESPONDER con la voz
                                    FilledTonalButton(
                                        onClick = { startDialogVoiceReply() },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = CampusTeal.copy(alpha = 0.15f),
                                            contentColor = CampusTeal
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Hablar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = status.dialogMessage,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                lineHeight = 17.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // --- RESPUESTAS RÁPIDAS (ACCIONES TÁCTILES Y GESTOS) ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (status.type == ProfessorActivityType.IN_OFFICE_HOURS && !prof.isExternal) {
                                    Button(
                                        onClick = {
                                            val roomName = prof.office.split("·")[0].trim()
                                            val floorName = prof.office.split("·").getOrElse(1) { "ETSIIT" }.trim()
                                            selectedProfessor = null
                                            tts?.stop()
                                            isSpeaking = false
                                            val locationText = if (floorName.isNotBlank() && floorName != "ETSIIT") "${prof.name} · $floorName" else prof.name
                                            onNavigate(OutdoorNavigationRoute.forProfessor(roomName, locationText))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CampusTeal),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) {
                                        Text("🚶 Sí, llévame", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = android.net.Uri.parse("mailto:${prof.email}")
                                            putExtra(Intent.EXTRA_SUBJECT, "Tutoría: [Indica tu motivo]")
                                        }
                                        context.startActivity(intent)
                                        Toast.makeText(context, "Abriendo correo. ¡Recuerda que también puedes inclinar el móvil!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text("✉️ Correo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        GestureBadge(
                                            type = GestureType.ACTION_TRIGGER,
                                            size = 24.dp,
                                            iconSize = 14.dp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Datos de despacho, tutorías y correo
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Despacho
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (prof.isExternal) Icons.Default.LocationOn else Icons.Default.MeetingRoom,
                                        contentDescription = null,
                                        tint = CampusTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = prof.office,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = TextPrimary
                                        )
                                        if (prof.isExternal) {
                                            Text(
                                                text = prof.campus,
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }

                                if (!prof.isExternal) {
                                    GestureBadge(
                                        type = GestureType.TAKE_ME_THERE,
                                        size = 24.dp,
                                        iconSize = 14.dp,
                                        onClick = {
                                            val roomName = prof.office.split("·")[0].trim()
                                            val floorName = prof.office.split("·").getOrElse(1) { "ETSIIT" }.trim()
                                            selectedProfessor = null
                                            val locationText = if (floorName.isNotBlank() && floorName != "ETSIIT") "${prof.name} · $floorName" else prof.name
                                            onNavigate(OutdoorNavigationRoute.forProfessor(roomName, locationText))
                                        }
                                    )
                                }
                            }

                            // Horario de tutorías
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tutorías: ${prof.officeHours}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }

                            // Correo electrónico
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = CampusTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = prof.email,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                }
                                GestureBadge(
                                    type = GestureType.ACTION_TRIGGER,
                                    size = 24.dp,
                                    iconSize = 14.dp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedProfessor = null
                    tts?.stop()
                    isSpeaking = false
                }) {
                    Text("Cerrar", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Diálogo visual temporal del gesto de inclinación (espera al campo de visión y abre el correo automáticamente)
    if (emailGestureConfirmProf != null) {
        val prof = emailGestureConfirmProf!!

        LaunchedEffect(prof) {
            delay(1800L) // Da 1.8 segundos para leer el mensaje con tranquilidad antes de abrir el correo
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:${prof.email}")
                putExtra(Intent.EXTRA_SUBJECT, "Tutoría: [Indica tu motivo]")
            }
            context.startActivity(intent)
            emailGestureConfirmProf = null
        }

        AlertDialog(
            onDismissRequest = { emailGestureConfirmProf = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(CampusTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    GestureBadge(
                        type = GestureType.ACTION_TRIGGER,
                        size = 32.dp,
                        iconSize = 18.dp
                    )
                }
            },
            title = {
                Text(
                    text = "Gesto completado",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "El terminal ha vuelto a tu campo de visión.\nAbriendo correo para ${prof.name}...",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = CampusTeal,
                        trackColor = CampusTeal.copy(alpha = 0.2f)
                    )
                }
            },
            confirmButton = {}
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        // --- CABECERA Y BUSCADOR POR VOZ / TEXTO ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceWhite)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Docentes y Tutorías",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Disponibilidad en tiempo real y contacto",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                // Botón selector de Modo Demo / Hora Simulada
                Surface(
                    onClick = { showDemoDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedDemoIndex == 0) BackgroundLight else CampusTeal.copy(alpha = 0.14f),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = if (selectedDemoIndex == 0) TextSecondary else CampusTeal,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedDemoIndex == 0) "Hora actual" else demoOptions[selectedDemoIndex].label.split(" ")[0],
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedDemoIndex == 0) TextSecondary else CampusTeal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Barra de búsqueda compacta con Micrófono y Limpiar integrados
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                placeholder = {
                    Text(
                        "Buscar profesor, departamento...",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpiar",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { startVoiceSearch() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Buscar por voz",
                                tint = CampusTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CampusTeal,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedContainerColor = BackgroundLight,
                    unfocusedContainerColor = BackgroundLight
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Chips de filtrado rápido
            val categories = listOf("Todos", "Mis Profesores", "CCIA", "LSI", "Otras Áreas", "Exterior UGR")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedFilterCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilterCategory = category },
                        label = {
                            Text(
                                text = category,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (category == "Mis Profesores") UgrRed else CampusTeal,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // --- CONTENIDO DE LA PANTALLA ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // SI NO HAY BÚSQUEDA ACTIVA NI FILTRO: Mostramos carrusel destacado de "Tus Profesores"
            if (searchQuery.isBlank() && selectedFilterCategory == "Todos") {
                item {
                    Text(
                        text = "⭐ TUS PROFESORES DE CLASE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = UgrRed,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(myTeachers) { prof ->
                            val status = getProfessorActivityStatus(prof, currentDayOfWeek, currentMinutesOfDay)
                            FeaturedTeacherCard(
                                prof = prof,
                                status = status,
                                onClick = { selectedProfessor = prof }
                            )
                        }
                    }
                }

                // Banner interactivo: Preguntar si busca a otro docente
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CampusTeal.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(CampusTeal.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = CampusTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "¿Buscas a otro docente de la ETSIIT?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Usa la búsqueda por voz o escribe su departamento",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "DIRECTORIO GENERAL (${professorsList.size} DOCENTES)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }
            } else {
                item {
                    Text(
                        text = "RESULTADOS (${filteredList.size} ENCONTRADOS)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }
            }

            // LISTA GENERAL O FILTRADA
            if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No se han encontrado profesores para \"$searchQuery\"",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredList) { prof ->
                    val status = getProfessorActivityStatus(prof, currentDayOfWeek, currentMinutesOfDay)
                    ProfessorListItem(
                        prof = prof,
                        status = status,
                        onClick = { selectedProfessor = prof }
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta destacada con Semáforo en vivo de disponibilidad para "Tus profesores"
 */
@Composable
private fun FeaturedTeacherCard(
    prof: Professor,
    status: ProfessorActivityStatus,
    onClick: () -> Unit
) {
    val formattedOffice = remember(prof.office) {
        if (prof.office.startsWith("D_")) "D-${prof.office.removePrefix("D_")}"
        else prof.office
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(185.dp)
            .height(230.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ProfessorAvatar(prof = prof, size = 54.dp)
                    Spacer(modifier = Modifier.height(4.dp))

                    // Píldora de Semáforo en Tiempo Real
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = status.badgeColor.copy(alpha = 0.12f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            StatusDot(
                                color = status.badgeColor,
                                isPulsing = status.type == ProfessorActivityType.IN_OFFICE_HOURS
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = status.badgeText,
                                color = status.badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = prof.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = prof.subject,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = BackgroundLight,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MeetingRoom,
                            contentDescription = null,
                            tint = CampusTeal,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formattedOffice,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Insignia discreta de estrella en la esquina superior derecha
            Surface(
                shape = CircleShape,
                color = UgrRed.copy(alpha = 0.12f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Tu profesor",
                        tint = UgrRed,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Fila en la lista de profesores con avatar, semáforo de disponibilidad y badges
 */
@Composable
private fun ProfessorListItem(
    prof: Professor,
    status: ProfessorActivityStatus,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite, contentColor = TextPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                ProfessorAvatar(prof = prof, size = 46.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = prof.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        if (prof.isMyTeacher) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Tu profesor",
                                tint = UgrRed,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Píldora de estado en tiempo real en la fila
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = status.badgeColor.copy(alpha = 0.12f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                StatusDot(
                                    color = status.badgeColor,
                                    isPulsing = status.type == ProfessorActivityType.IN_OFFICE_HOURS
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = status.badgeText,
                                    color = status.badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = if (prof.isExternal) prof.campus else prof.subject,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${prof.office} · ${prof.department.replace("Departamento de ", "")}",
                        fontSize = 11.sp,
                        color = TextSecondary.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Ver detalles",
                tint = TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Emite una vibración háptica al disparar un gesto
 */
private fun triggerVibration(context: Context, durationMs: Long = 120) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        }
    } catch (_: Exception) {
    }
}

fun loadProfessors(context: Context): List<Professor> {
    val jsonString = context.assets.open("docentes.json").bufferedReader().use { it.readText() }
    val jsonArray = JSONObject(jsonString).getJSONArray("profesores")
    val list = mutableListOf<Professor>()

    for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val classesList = mutableListOf<ProfessorClass>()
        val clasesArray = obj.optJSONArray("clases")
        var firstSubject = ""

        if (clasesArray != null && clasesArray.length() > 0) {
            firstSubject = clasesArray.getJSONObject(0).optString("asignatura", "")
            for (j in 0 until clasesArray.length()) {
                val cObj = clasesArray.getJSONObject(j)
                classesList.add(
                    ProfessorClass(
                        subject = cObj.optString("asignatura", ""),
                        type = cObj.optString("tipo", ""),
                        room = cObj.optString("aula", ""),
                        schedule = cObj.optString("horario", "")
                    )
                )
            }
        }

        val subjectText = if (firstSubject.isNotBlank()) firstSubject else obj.optString("departamento", "")

        list.add(
            Professor(
                name = obj.getString("nombre"),
                subject = subjectText,
                office = obj.getString("despacho"),
                officeHours = obj.optString("tutorias", "Consultar"),
                email = obj.optString("email", ""),
                isMyTeacher = obj.optBoolean("es_profesor_actual", false),
                department = obj.optString("departamento", ""),
                isExternal = obj.optBoolean("es_externo", false),
                campus = obj.optString("campus", "Campus Aynadamar (ETSIIT)"),
                avatar = obj.optString("avatar", ""),
                classes = classesList
            )
        )
    }
    return list.sortedWith(compareByDescending<Professor> { it.isMyTeacher }.thenBy { it.name })
}
