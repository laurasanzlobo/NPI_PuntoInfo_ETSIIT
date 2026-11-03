package com.example.npi_puntoinfo_etsiit.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.data.ClassItem
import com.example.npi_puntoinfo_etsiit.data.DayScheduleInfo
import com.example.npi_puntoinfo_etsiit.data.ScheduleRepository
import com.example.npi_puntoinfo_etsiit.ui.components.GestureBadge
import com.example.npi_puntoinfo_etsiit.ui.components.GestureType
import com.example.npi_puntoinfo_etsiit.sensors.AccelerometerManager
import com.example.npi_puntoinfo_etsiit.ui.navigation.Screen
import com.example.npi_puntoinfo_etsiit.ui.theme.*

@Composable
fun ScheduleScreen(
    onNavigate: (String) -> Unit = {},
    navKey: Int = 0
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedClass by remember { mutableStateOf<ClassItem?>(null) }

    val today = remember { LocalDate.now() }
    val now = remember { LocalTime.now() }
    val profile = remember(context) { com.example.npi_puntoinfo_etsiit.data.UserSessionRepository.getProfile(context) }

    // Diccionario que relaciona una asignatura con el nombre de su profesor real
    val subjectToProfessorMap = remember {
        val map = mutableMapOf<String, String>()
        try {
            val jsonString = context.assets.open("docentes.json").bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONObject(jsonString).getJSONArray("profesores")
            for (i in 0 until jsonArray.length()) {
                val profObj = jsonArray.getJSONObject(i)
                val profName = profObj.getString("nombre")
                if (profObj.has("clases")) {
                    val clasesArray = profObj.getJSONArray("clases")
                    for (j in 0 until clasesArray.length()) {
                        val claseObj = clasesArray.getJSONObject(j)
                        val asig = claseObj.getString("asignatura")

                        // Leemos el tipo, lo pasamos a minúsculas y comprobamos ambas opciones
                        val tipo = claseObj.optString("tipo", "Teoría").lowercase()
                        val esPractica = tipo.contains("práct") || tipo.contains("prac")

                        val sufijo = if (esPractica) "-PRAC" else "-TEO"
                        map["$asig$sufijo"] = profName
                    }
                }
            }
        } catch (e: Exception) {
            // Ignoramos errores de formato
        }
        map
    }

    // El acelerómetro con el gesto «¡Llévame!» SOLO está activo si el usuario está viendo el detalle de una clase concreta
    DisposableEffect(selectedClass) {
        val manager = AccelerometerManager(context)

        if (selectedClass != null) {
            manager.onTakeMeThereGesture = {
                triggerVibration(context, durationMs = 150)
                val currentClass = selectedClass
                if (currentClass != null) {
                    val roomName = currentClass.room.split("·")[0].trim()
                    val floorName = currentClass.room.split("·").getOrElse(1) { "ETSIIT" }.trim()
                    selectedClass = null
                    onNavigate(Screen.Navigation.createRoute(roomName, floorName))
                }
            }
            manager.startListening()
        }

        onDispose {
            manager.stopListening()
        }
    }

    val daysSchedule = remember(today, now, profile, subjectToProfessorMap) {
        (0 until 7).map { offset ->
            val date = today.plusDays(offset.toLong())
            val dayOfWeek = date.dayOfWeek
            val isToday = (offset == 0)

            val dayName = when (dayOfWeek) {
                DayOfWeek.MONDAY -> "Lunes"
                DayOfWeek.TUESDAY -> "Martes"
                DayOfWeek.WEDNESDAY -> "Miércoles"
                DayOfWeek.THURSDAY -> "Jueves"
                DayOfWeek.FRIDAY -> "Viernes"
                DayOfWeek.SATURDAY -> "Sábado"
                DayOfWeek.SUNDAY -> "Domingo"
            }

            val dayLabel = when (offset) {
                0 -> "$dayName · Hoy"
                1 -> "$dayName · Mañana"
                else -> dayName
            }

            val baseClasses = ScheduleRepository.getClassesForDay(context, dayOfWeek, profile)

            val classesForDay = baseClasses.map { item ->
                // Creamos el mismo sufijo basándonos en el boolean isPractice del horario
                val sufijo = if (item.isPractice) "-PRAC" else "-TEO"

                // Buscamos con la clave compuesta (ej. "NPI-PRAC")
                val realProfessor = subjectToProfessorMap["${item.acronym}$sufijo"]
                    ?: subjectToProfessorMap["${item.subject}$sufijo"]
                    ?: item.professor

                item.copy(
                    isCurrent = isToday && isTimeInRange(item.time, now),
                    professor = realProfessor
                )
            }

            DayScheduleInfo(
                dayName = dayName,
                dayLabel = dayLabel,
                classes = classesForDay
            )
        }
    }

    val pagerState = remember(navKey) {
        PagerState(currentPage = 0, currentPageOffsetFraction = 0f) { daysSchedule.size }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Indicador del día actual y puntos (dots) de la semana (diseño idéntico a Comedor)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = UgrRed.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = UgrRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = daysSchedule[pagerState.currentPage].dayLabel,
                        color = UgrRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Indicador de puntos (dots) de la semana
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(daysSchedule.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) UgrRed else SurfaceBorder)
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                    )
                }
            }
        }

        // Pager deslizante horizontal con los días de la semana
        HorizontalPager(
            state = pagerState,
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val dayClasses = daysSchedule[page].classes

            if (dayClasses.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(UgrRed.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventBusy,
                                    contentDescription = null,
                                    tint = UgrRed,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (page == 0) "Hoy no tienes clase" else "El ${daysSchedule[page].dayName} no tienes clase",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Fin de semana libre sin docencia programada en la ETSIIT.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    dayClasses.forEach { item ->
                        ClassCard(
                            item = item,
                            onClick = { selectedClass = item }
                        )
                    }
                }
            }
        }
    }

    // Diálogo informativo detallado de una clase concreta al pulsar en su recuadro
    if (selectedClass != null) {
        val item = selectedClass!!
        AlertDialog(
            onDismissRequest = { selectedClass = null },
            containerColor = SurfaceWhite,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary,
            iconContentColor = UgrRed,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(UgrRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = UgrRed,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.subject,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = if (item.isPractice) "PRÁCTICAS" else "TEORÍA",
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (item.acronym.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE2E8F0)
                            ) {
                                Text(
                                    text = item.acronym,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Horario y Ubicación
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = CampusGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Horario: ${item.time}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
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
                                        imageVector = Icons.Default.MeetingRoom,
                                        contentDescription = null,
                                        tint = UgrRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.room,
                                        fontSize = 13.sp,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                // Loguito del gesto «¡Llévame!» hacia el aula
                                GestureBadge(
                                    type = GestureType.TAKE_ME_THERE,
                                    onClick = {
                                        val roomName = item.room.split("·")[0].trim()
                                        val floorName = item.room.split("·").getOrElse(1) { "ETSIIT" }.trim()
                                        selectedClass = null
                                        onNavigate(Screen.Navigation.createRoute(roomName, floorName))
                                    }
                                )
                            }
                        }
                    }

                    // Profesor asignado (pulsable para ver su ficha completa en Profesores)
                    Card(
                        onClick = {
                            if (item.professor.isNotBlank() && item.professor != "Profesor por asignar") {
                                selectedClass = null
                                onNavigate("${Screen.Professors.route}?nombre=${Uri.encode(item.professor)}")
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = CampusTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Profesor/a:",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = item.professor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Ver ficha del profesor",
                                tint = CampusTeal,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedClass = null },
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
}

@Composable
private fun ClassCard(
    item: ClassItem,
    onClick: () -> Unit
) {
    // Si la clase está en curso: verde menta con borde esmeralda.
    // Si es teoría: fondo blanco limpio con borde sutil.
    // Si es prácticas: fondo gris clarito (estilo oficial facultad).
    val cardBg = when {
        item.isCurrent -> Color(0xFFDCFCE7)
        item.isPractice -> Color(0xFFF1F5F9)
        else -> SurfaceWhite
    }
    val borderColor = when {
        item.isCurrent -> CampusGreen
        item.isPractice -> Color(0xFFE2E8F0)
        else -> SurfaceBorder
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(if (item.isCurrent) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isCurrent) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Fila superior: Hora y Badges (Teoría / Prácticas + En Curso)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (item.isCurrent) CampusGreen else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.time,
                        fontSize = 13.sp,
                        fontWeight = if (item.isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (item.isCurrent) CampusGreen else TextSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Badge de Teoría o Prácticas
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.isCurrent) {
                            CampusGreen.copy(alpha = 0.20f)
                        } else if (item.isPractice) {
                            SurfaceWhite
                        } else {
                            Color(0xFFF1F5F9)
                        }
                    ) {
                        Text(
                            text = if (item.isPractice) "PRÁCTICAS" else "TEORÍA",
                            color = if (item.isCurrent) CampusGreen else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    if (item.isCurrent) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CampusGreen
                        ) {
                            Text(
                                text = "EN CURSO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fila central: Chip del Acrónimo y Nombre Completo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        item.isCurrent -> CampusGreen
                        item.isPractice -> SurfaceWhite
                        else -> Color(0xFFF1F5F9)
                    }
                ) {
                    Text(
                        text = item.acronym,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = when {
                            item.isCurrent -> Color.White
                            else -> TextPrimary
                        },
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item.subject,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isCurrent) Color(0xFF064E3B) else TextPrimary,
                    maxLines = 2,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fila inferior: Aula y Profesor asignado
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
                        imageVector = Icons.Default.MeetingRoom,
                        contentDescription = null,
                        tint = if (item.isCurrent) CampusGreen else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = item.room,
                        fontSize = 12.sp,
                        color = if (item.isCurrent) Color(0xFF065F46) else TextSecondary,
                        fontWeight = if (item.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                if (item.professor.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.professor.split(" ").take(2).joinToString(" "),
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/**
 * Función auxiliar para emitir una vibración háptica al disparar un gesto
 */
private fun triggerVibration(context: Context, durationMs: Long = 150) {
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

/**
 * Comprueba si la hora actual se encuentra dentro del rango de una clase (formato "HH:mm - HH:mm").
 */
private fun isTimeInRange(timeRange: String, currentTime: LocalTime): Boolean {
    return try {
        val parts = timeRange.split("-").map { it.trim() }
        if (parts.size == 2) {
            val start = LocalTime.parse(parts[0])
            val end = LocalTime.parse(parts[1])
            !currentTime.isBefore(start) && currentTime.isBefore(end)
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}

