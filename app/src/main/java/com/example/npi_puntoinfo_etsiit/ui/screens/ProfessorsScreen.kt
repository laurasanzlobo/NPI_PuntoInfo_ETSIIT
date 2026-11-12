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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.npi_puntoinfo_etsiit.data.UserSessionRepository
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
                    dialogMessage = "En este momento ${prof.name} está impartiendo clase de ${c.subject} en el aula $roomFormatted hasta las ${match.groupValues[3]}."
                )
            }
        }
    }

    // Obtener los dígitos del despacho para locución TTS natural (ej. "el despacho 225" en vez de "D225")
    val officeDigits = prof.office.split("·")[0]
        .replace("Despacho", "", ignoreCase = true)
        .replace("D_", "")
        .replace("D-", "")
        .replace("D", "")
        .trim()
    val officeSpoken = if (officeDigits.isNotBlank() && !officeDigits.equals("Consultar", ignoreCase = true)) {
        "el despacho $officeDigits"
    } else {
        "su despacho"
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
            return ProfessorActivityStatus(
                type = ProfessorActivityType.IN_OFFICE_HOURS,
                badgeText = "En tutoría",
                badgeColor = Color(0xFF16A34A), // Verde vivo
                dialogMessage = "${prof.name} se encuentra ahora mismo en $officeSpoken atendiendo tutorías hasta las ${m.groupValues[4]}."
            )
        }
    }

    // 3. Fuera de horario de actividad
    val msg = if (prof.isExternal) {
        "${prof.name} se encuentra en ${prof.campus}. Sus tutorías habituales son: ${prof.officeHours}."
    } else {
        "${prof.name} no tiene tutorías ni clases ahora mismo. Sus tutorías en $officeSpoken son: ${prof.officeHours}."
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

/**
 * Obtiene la versión corta del nombre: Nombre de pila + Primer Apellido
 * Ejemplos: "Marcelino José Cabrera Cuevas" -> "Marcelino Cabrera", "Zoraida Callejas Carrión" -> "Zoraida Callejas"
 */
fun formatShortProfessorName(fullName: String): String {
    val partes = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (partes.size <= 2) return fullName

    val compuestosNombre = setOf(
        "jose", "josé", "maria", "maría", "juan", "francisco", "jesus", "jesús",
        "antonio", "luis", "carmen", "ana", "manuel", "ramon", "ramón", "enrique",
        "javier", "miguel", "luisa", "belen", "belén", "julian", "julián", "celeste",
        "ángel", "angel"
    )

    // Manejar apellidos con partículas compuestas como "Begoña del Pino Prieto" -> "Begoña del Pino"
    for (i in 1 until partes.size - 1) {
        if (partes[i].lowercase() in setOf("del", "de")) {
            return "${partes[0]} " + partes.subList(i, (i + 2).coerceAtMost(partes.size)).joinToString(" ")
        }
    }

    if (partes[0].equals("Siham", ignoreCase = true)) {
        return "Siham Tabik"
    }

    // Si tiene 4 partes y la segunda es un segundo nombre habitual (ej: Marcelino José Cabrera Cuevas)
    if (partes.size >= 4 && partes[1].lowercase() in compuestosNombre) {
        return "${partes[0]} ${partes[2]}"
    }

    return "${partes[0]} ${partes[1]}"
}

/**
 * Obtiene la sigla o abreviatura oficial y compacta del departamento para la vista de lista
 */
fun getShortDepartment(department: String): String {
    val clean = department.replace("Departamento de ", "").trim()
    return when {
        clean.contains("Computación", ignoreCase = true) -> "CCIA"
        clean.contains("Lenguajes", ignoreCase = true) -> "LSI"
        clean.contains("Arquitectura", ignoreCase = true) -> "ATC"
        clean.contains("Señal", ignoreCase = true) -> "TSTC"
        clean.contains("Electrónica", ignoreCase = true) -> "ETC"
        clean.contains("Estadística", ignoreCase = true) -> "EIO"
        clean.contains("Organización", ignoreCase = true) -> "DOE"
        clean.contains("Información", ignoreCase = true) -> "DIC"
        clean.contains("Física", ignoreCase = true) -> "Física Aplicada"
        clean.contains("Civil", ignoreCase = true) -> "Ing. Civil"
        clean.contains("Bioquímica", ignoreCase = true) -> "Bioquímica"
        clean.contains("Anatomía", ignoreCase = true) -> "Anatomía"
        clean.contains("Pintura", ignoreCase = true) -> "Bellas Artes"
        clean.contains("Traducción", ignoreCase = true) -> "Traducción"
        clean.length > 20 -> clean.split(" ").filter { it.length > 3 }.take(3).joinToString(" ")
        else -> clean
    }
}

/**
 * Obtiene la sigla o acrónimo conciso de una asignatura universitaria
 * Ejemplos: "Nuevos Paradigmas de Interacción" -> "NPI", "Visión por Computador" -> "VC"
 */
fun getSubjectAcronym(subject: String): String {
    val clean = subject.trim()
    return when {
        clean.contains("Nuevos Paradigmas", ignoreCase = true) -> "NPI"
        clean.contains("Visión por Computador", ignoreCase = true) -> "VC"
        clean.contains("Programación y Diseño", ignoreCase = true) -> "PDOO"
        clean.contains("Aprendizaje Automático", ignoreCase = true) -> "AA"
        clean.contains("Arquitectura de Computadores", ignoreCase = true) -> "AC"
        clean.contains("Bases de Datos Avanzadas", ignoreCase = true) -> "BDA"
        clean.contains("Bioinformática", ignoreCase = true) -> "BIO"
        clean.contains("Bioquímica Clínica", ignoreCase = true) -> "BQC"
        clean.contains("Computación Ubicua", ignoreCase = true) -> "CU"
        clean.contains("Comunicación Audiovisual", ignoreCase = true) -> "CAV"
        clean.contains("Comunicaciones Móviles", ignoreCase = true) -> "CM"
        clean.contains("Diseño Multimedia", ignoreCase = true) -> "DMAD"
        clean.contains("Estadística Aplicada", ignoreCase = true) -> "EA"
        clean.contains("Física Computacional", ignoreCase = true) -> "FC"
        clean.contains("Gestión de Empresas", ignoreCase = true) -> "GES"
        clean.contains("Informática Gráfica", ignoreCase = true) -> "IG"
        clean.contains("Informática Médica", ignoreCase = true) -> "IM"
        clean.contains("Infraestructura Virtual", ignoreCase = true) -> "IV"
        clean.contains("Ingeniería de Requisitos", ignoreCase = true) -> "IR"
        clean.contains("Minería de Datos", ignoreCase = true) -> "MD"
        clean.contains("Modelado 3D", ignoreCase = true) -> "M3D"
        clean.contains("Procesadores de Lenguajes", ignoreCase = true) -> "PL"
        clean.contains("Programación Técnica", ignoreCase = true) -> "PTC"
        clean.contains("Programación Web", ignoreCase = true) -> "PW"
        clean.contains("Reconocimiento de Formas", ignoreCase = true) -> "RF"
        clean.contains("Redes de Comunicaciones", ignoreCase = true) -> "RC"
        clean.contains("Seguridad en Redes", ignoreCase = true) -> "SR"
        clean.contains("Simulación de Sistemas", ignoreCase = true) -> "SS"
        clean.contains("Sistemas Concurrentes", ignoreCase = true) -> "SC"
        clean.contains("Sistemas Empotrados", ignoreCase = true) -> "SE"
        clean.contains("Sistemas Inteligentes", ignoreCase = true) -> "SI"
        clean.contains("Tecnologías de la Traducción", ignoreCase = true) -> "TT"
        clean.contains("Teoría de la Información", ignoreCase = true) -> "TIC"
        clean.contains("Web Semántica", ignoreCase = true) -> "WS"
        else -> {
            val stopWords = setOf("de", "la", "el", "los", "las", "y", "en", "por", "del")
            val words = clean.split(Regex("\\s+")).filter { it.lowercase() !in stopWords && it.isNotBlank() }
            if (words.size in 2..4) {
                words.map { it.first().uppercase() }.joinToString("")
            } else if (clean.length > 5) {
                clean.take(4).uppercase()
            } else {
                clean
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessorsScreen(onNavigate: (String) -> Unit, searchedName: String? = null) {
    val context = LocalContext.current
    val profile = UserSessionRepository.getProfile(context)
    val isLoggedIn = profile.isLoggedIn
    val isEnglish = profile.language == "en"
    var selectedProfessor by remember { mutableStateOf<Professor?>(null) }
    var searchQuery by remember { mutableStateOf(searchedName ?: "") }
    var selectedFilterCategory by remember(isLoggedIn, isEnglish) {
        mutableStateOf(if (isLoggedIn) (if (isEnglish) "My Professors" else "Mis Profesores") else (if (isEnglish) "All" else "Todos"))
    }

    LaunchedEffect(isLoggedIn, isEnglish) {
        selectedFilterCategory = if (isLoggedIn) (if (isEnglish) "My Professors" else "Mis Profesores") else (if (isEnglish) "All" else "Todos")
    }

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

    val professorsList = remember(isLoggedIn) {
        val raw = loadProfessors(context)
        if (isLoggedIn) raw else raw.sortedBy { normalizarTexto(it.name) }
    }

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
            val decoded = try { java.net.URLDecoder.decode(searchedName, "UTF-8") } catch (_: Exception) { searchedName }
            searchQuery = decoded
            val normSearch = normalizarTexto(decoded)
            val found = professorsList.find {
                it.name.equals(decoded, ignoreCase = true) ||
                normalizarTexto(it.name) == normSearch ||
                normalizarTexto(it.name).contains(normSearch) ||
                normSearch.contains(normalizarTexto(it.name))
            }
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
                val prof = selectedProfessor
                if (prof != null) {
                    triggerVibration(context, durationMs = 150)
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

            manager.onForwardTiltGesture = {
                // Retorno a la postura de lectura: abre directamente el correo
                val prof = selectedProfessor
                if (prof != null) {
                    triggerVibration(context, durationMs = 150)
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:${prof.email}")
                        putExtra(Intent.EXTRA_SUBJECT, "Tutoría: [Indica tu motivo]")
                    }
                    context.startActivity(intent)
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

    // Filtrado en tiempo real con ordenación prioritaria de horario
    val filteredList = remember(professorsList, searchQuery, selectedFilterCategory, currentDayOfWeek, currentMinutesOfDay, isLoggedIn) {
        val queryNorm = normalizarTexto(searchQuery)

        val filtered = professorsList.filter { prof ->
            val matchesCategory = when (selectedFilterCategory) {
                "Mis Profesores", "My Professors" -> prof.isMyTeacher
                "CCIA" -> prof.department.contains("Computación", ignoreCase = true)
                "LSI" -> prof.department.contains("Lenguajes", ignoreCase = true)
                "Otras Áreas", "Other Departments" -> !prof.isExternal && !prof.department.contains("Computación", ignoreCase = true) && !prof.department.contains("Lenguajes", ignoreCase = true)
                "Exterior UGR", "External UGR" -> prof.isExternal
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

        // Ordenación:
        // 1. Si ha iniciado sesión, poner siempre primero a tus profesores (con estrellita / isMyTeacher)
        // 2. Priorizar los que están en horario: Tutorías (0) -> En clase (1) -> Fuera de horario (2)
        // 3. Alfabéticamente por nombre
        filtered.sortedWith(
            compareBy<Professor> { prof ->
                if (isLoggedIn && prof.isMyTeacher) 0 else 1
            }.thenBy { prof ->
                val status = getProfessorActivityStatus(prof, currentDayOfWeek, currentMinutesOfDay)
                when (status.type) {
                    ProfessorActivityType.IN_OFFICE_HOURS -> 0 // 🟢 En tutoría (máxima prioridad)
                    ProfessorActivityType.IN_CLASS -> 1        // 🔵 En clase
                    ProfessorActivityType.OFFLINE -> 2         // ⚪ Fuera de horario
                }
            }.thenBy { prof ->
                normalizarTexto(prof.name)
            }
        )
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
                Button(
                    onClick = { showDemoDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UgrRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Text(
                        text = "Cerrar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        )
    }

    // Diálogo con información detallada y Diálogo Conversacional Asistente
    if (selectedProfessor != null) {
        val prof = selectedProfessor!!
        var showEnlargedPhoto by remember { mutableStateOf(false) }
        val status = remember(prof, currentDayOfWeek, currentMinutesOfDay) {
            getProfessorActivityStatus(prof, currentDayOfWeek, currentMinutesOfDay)
        }

        // Diálogo para visualizar la foto del docente en tamaño grande
        if (showEnlargedPhoto) {
            Dialog(
                onDismissRequest = { showEnlargedPhoto = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceWhite,
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val context = LocalContext.current
                        val bitmap = remember(prof.avatar) { loadAvatarBitmap(context, prof.avatar) }

                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Foto ampliada de ${prof.name}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(20.dp))
                            )
                        } else {
                            ProfessorAvatar(prof = prof, size = 190.dp)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = prof.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                lineHeight = 21.sp,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            if (prof.subject.isNotBlank()) {
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = prof.subject,
                                    fontSize = 13.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CampusTeal,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (prof.department.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = prof.department,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Button(
                            onClick = { showEnlargedPhoto = false },
                            colors = ButtonDefaults.buttonColors(containerColor = UgrRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cerrar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                selectedProfessor = null
                tts?.stop()
                isSpeaking = false
            },
            containerColor = SurfaceWhite,
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextSecondary.copy(alpha = 0.25f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cabecera: Foto grande + Nombre completo + Asignatura + Departamento + Semáforo + Botón Cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier.clickable { showEnlargedPhoto = true }
                        ) {
                            ProfessorAvatar(prof = prof, size = 64.dp)
                            Surface(
                                shape = CircleShape,
                                color = UgrRed,
                                shadowElevation = 2.dp,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Ampliar foto",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = prof.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    lineHeight = 21.sp,
                                    color = TextPrimary
                                )
                                if (isLoggedIn && prof.isMyTeacher) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Tu profesor",
                                        tint = UgrRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (prof.subject.isNotBlank()) {
                                Text(
                                    text = prof.subject,
                                    fontSize = 13.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CampusTeal
                                )
                            }

                            if (prof.department.isNotBlank()) {
                                Text(
                                    text = prof.department,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Píldora de estado en tiempo real (Tutoría / Clase / Fuera de horario)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = status.badgeColor.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    StatusDot(
                                        color = status.badgeColor,
                                        isPulsing = status.type == ProfessorActivityType.IN_OFFICE_HOURS
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = status.badgeText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = status.badgeColor
                                    )
                                }
                            }
                        }
                    }

                    // Botón discreto cerrar (X)
                    IconButton(
                        onClick = {
                            selectedProfessor = null
                            tts?.stop()
                            isSpeaking = false
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextSecondary
                        )
                    }
                }

                // Información clave concisa: Tutorías + Botón discreto [🔊 Oír]
                val cleanOffice = prof.office
                    .split("·")[0]
                    .replace("Despacho", "", ignoreCase = true)
                    .replace("D_", "D")
                    .replace("D-", "D")
                    .trim()
                    .let {
                        if (it.firstOrNull()?.isDigit() == true) "D$it" else it
                    }
                val formattedOfficeHours = remember(prof.officeHours) {
                    prof.officeHours.replace(Regex("(\\d{2}:\\d{2})\\s+y\\s+([A-ZÁÉÍÓÚ])"), "$1\n$2")
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BackgroundLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 1.dp)
                            )
                            Column {
                                Text(
                                    text = "Tutorías:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary
                                )
                                Text(
                                    text = formattedOfficeHours,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = TextPrimary
                                )
                            }
                        }

                        // Botón discreto [🔊 Oír] integrado sin estorbar
                        FilledTonalButton(
                            onClick = { speak(status.dialogMessage) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
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
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSpeaking) "Parar" else "Oír",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ========================================================
                // LAS DOS OPCIONES REINA DE NAVEGACIÓN: DESPACHO Y CORREO
                // ========================================================
                val onGoToOffice = {
                    if (!prof.isExternal) {
                        val roomName = prof.office.split("·")[0].trim().replace("D_", "D").replace("D-", "D")
                        val floorName = prof.office.split("·").getOrElse(1) { "ETSIIT" }.trim()
                        selectedProfessor = null
                        tts?.stop()
                        isSpeaking = false
                        val locationText = if (floorName.isNotBlank() && floorName != "ETSIIT") "${prof.name} · $floorName" else prof.name
                        onNavigate(OutdoorNavigationRoute.forProfessor(roomName, locationText))
                    } else {
                        Toast.makeText(context, "El profesor se encuentra en ${prof.campus}", Toast.LENGTH_LONG).show()
                    }
                }

                val onSendEmail = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:${prof.email}")
                        putExtra(Intent.EXTRA_SUBJECT, "Tutoría: [Indica tu motivo]")
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Abriendo correo...", Toast.LENGTH_SHORT).show()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Botón 1: Despacho (con código DXXX y badge del gesto ¡Llévame!)
                    val officeBtnText = if (cleanOffice.isNotBlank() && !cleanOffice.equals("Consultar", ignoreCase = true)) cleanOffice else "Despacho"
                    Button(
                        onClick = onGoToOffice,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CampusTeal)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MeetingRoom,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = officeBtnText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            GestureBadge(
                                type = GestureType.TAKE_ME_THERE,
                                size = 22.dp,
                                iconSize = 13.dp,
                                containerColor = Color.White,
                                contentColor = CampusTeal
                            )
                        }
                    }

                    // Botón 2: Correo (con badge de cabeceo hacia ti)
                    OutlinedButton(
                        onClick = onSendEmail,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, UgrRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = UgrRed)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Text("Correo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            GestureBadge(
                                type = GestureType.ACTION_TRIGGER,
                                size = 22.dp,
                                iconSize = 13.dp
                            )
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        // --- BUSCADOR POR VOZ / TEXTO ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceWhite)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
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
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(min = 48.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpiar",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            GestureBadge(
                                type = GestureType.PROXIMITY_VOICE,
                                size = 32.dp,
                                iconSize = 17.dp,
                                showTooltipOnClick = false,
                                onClick = { startVoiceSearch() }
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
            val categories = if (isLoggedIn) {
                if (isEnglish) listOf("My Professors", "All", "CCIA", "LSI", "Other Departments", "External UGR")
                else listOf("Mis Profesores", "Todos", "CCIA", "LSI", "Otras Áreas", "Exterior UGR")
            } else {
                if (isEnglish) listOf("All", "CCIA", "LSI", "Other Departments", "External UGR")
                else listOf("Todos", "CCIA", "LSI", "Otras Áreas", "Exterior UGR")
            }
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

        // --- CONTENIDO DE LA PANTALLA: GRID DE PROFESORES ---
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Título solo para búsqueda activa (al pulsar los chips de filtro no repetimos el título del chip)
            if (searchQuery.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "RESULTADOS (${filteredList.size} ENCONTRADOS)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // LISTA GENERAL O FILTRADA EN GRID
            if (filteredList.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
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
                    ProfessorGridCard(
                        prof = prof,
                        status = status,
                        showMyTeacherStar = isLoggedIn && prof.isMyTeacher,
                        onClick = { selectedProfessor = prof }
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta en cuadrícula limpia y minimalista:
 * - Foto en grande (80dp) con anillo exterior de disponibilidad
 * - Píldora de estado en vivo flotante
 * - Nombre de pila + Primer apellido
 * - Sin etiquetas de asignatura ni despacho (la información detallada se consulta al pulsar)
 */
@Composable
private fun ProfessorGridCard(
    prof: Professor,
    status: ProfessorActivityStatus,
    showMyTeacherStar: Boolean,
    onClick: () -> Unit
) {
    val ringColor = when (status.type) {
        ProfessorActivityType.IN_OFFICE_HOURS -> Color(0xFF16A34A) // Verde esmeralda vivo
        ProfessorActivityType.IN_CLASS -> CampusTeal
        ProfessorActivityType.OFFLINE -> Color(0xFFE2E8F0)
    }

    val cardBorder = when (status.type) {
        ProfessorActivityType.IN_OFFICE_HOURS -> androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF86EFAC))
        else -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0).copy(alpha = 0.8f))
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (status.type == ProfessorActivityType.IN_OFFICE_HOURS) 3.dp else 1.5.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Contenedor del Avatar con Anillo exterior de disponibilidad (Foto en grande)
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(ringColor.copy(alpha = if (status.type == ProfessorActivityType.OFFLINE) 0.35f else 0.22f))
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(CircleShape)
                                .background(SurfaceWhite)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ProfessorAvatar(prof = prof, size = 78.dp)
                        }
                    }

                    // Píldora de estado en vivo flotante en la base del avatar
                    if (status.type != ProfessorActivityType.OFFLINE) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceWhite,
                            shadowElevation = 3.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, status.badgeColor.copy(alpha = 0.35f)),
                            modifier = Modifier.offset(y = 6.dp)
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
                                    text = if (status.type == ProfessorActivityType.IN_OFFICE_HOURS) "Tutoría" else "En clase",
                                    color = status.badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Nombre corto (Nombre de pila + Primer apellido)
                Text(
                    text = formatShortProfessorName(prof.name),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Estrella si es tu profesor en la esquina superior derecha
            if (showMyTeacherStar) {
                Surface(
                    shape = CircleShape,
                    color = UgrRed.copy(alpha = 0.12f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Tu profesor",
                            tint = UgrRed,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
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
                office = obj.getString("despacho").replace("D_", "D").replace("D-", "D"),
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
    return list.sortedWith(compareByDescending<Professor> { it.isMyTeacher }.thenBy { normalizarTexto(it.name) })
}
