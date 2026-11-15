package com.example.npi_puntoinfo_etsiit.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.data.ClassItem
import com.example.npi_puntoinfo_etsiit.data.ScheduleRepository
import com.example.npi_puntoinfo_etsiit.sensors.AccelerometerManager
import com.example.npi_puntoinfo_etsiit.ui.components.GestureBadge
import com.example.npi_puntoinfo_etsiit.ui.components.GestureType
import com.example.npi_puntoinfo_etsiit.ui.navigation.Screen
import com.example.npi_puntoinfo_etsiit.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/**
 * Modos de visualización del Horario:
 * - PANORAMIC_3_DAYS: Vista panorámica de 3 días simultáneos con el día central destacado y salido hacia afuera.
 * - DETAILED_DAY: Vista ampliada de 1 día completo (activada por el gesto de zoom / pellizco de 2 dedos o botón).
 */
enum class ScheduleViewMode {
    PANORAMIC_3_DAYS,
    DETAILED_DAY
}

/**
 * Datos estructurados para un día concreto en la semana lectiva.
 */
data class DayScheduleSlot(
    val date: LocalDate,
    val dayOfWeek: DayOfWeek,
    val dayName: String,
    val dayLabel: String,
    val relativeLabel: String,
    val isToday: Boolean,
    val classes: List<ClassItem>
)

@Composable
fun ScheduleScreen(
    onNavigate: (String) -> Unit = {},
    navKey: Int = 0,
    toggleZoomKey: Int = 0
) {
    val context = LocalContext.current
    var selectedClass by remember { mutableStateOf<ClassItem?>(null) }
    var viewMode by rememberSaveable(navKey) { mutableStateOf(ScheduleViewMode.PANORAMIC_3_DAYS) }

    val today = remember { LocalDate.now() }
    val now = remember { LocalTime.now() }
    val profile = remember(context) { com.example.npi_puntoinfo_etsiit.data.UserSessionRepository.getProfile(context) }
    val coroutineScope = rememberCoroutineScope()

    // Diccionario de asignaturas y sus profesores asignados
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
                        val tipo = claseObj.optString("tipo", "Teoría").lowercase()
                        val esPractica = tipo.contains("práct") || tipo.contains("prac")
                        val sufijo = if (esPractica) "-PRAC" else "-TEO"
                        map["$asig$sufijo"] = profName
                    }
                }
            }
        } catch (_: Exception) { }
        map
    }

    // Ventana deslizante de días: 3 días hacia atrás, hoy (offset 0), y 5 días hacia adelante
    val dayOffsets = remember { listOf(-3, -2, -1, 0, 1, 2, 3, 4, 5) }
    val todayOffsetIndex = remember { dayOffsets.indexOf(0) } // Índice 3

    // Días modelados para la navegación
    val daysSchedule = remember(today, now, profile, subjectToProfessorMap) {
        dayOffsets.map { offset ->
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
                -1 -> "$dayName · Ayer"
                else -> "$dayName ${date.dayOfMonth}/${date.monthValue}"
            }

            val relativeLabel = when (offset) {
                0 -> "Hoy"
                1 -> "Mañana"
                -1 -> "Ayer"
                2 -> "Pasado mañ."
                -2 -> "Anteayer"
                else -> "${date.dayOfMonth}/${date.monthValue}"
            }

            val baseClasses = ScheduleRepository.getClassesForDay(context, dayOfWeek, profile)

            val classesForDay = baseClasses.mapIndexed { _, item ->
                val sufijo = if (item.isPractice) "-PRAC" else "-TEO"
                val realProfessor = subjectToProfessorMap["${item.acronym}$sufijo"]
                    ?: subjectToProfessorMap["${item.subject}$sufijo"]
                    ?: item.professor

                val isCurrent = if (isToday) isTimeInRange(item.time, now) else false

                item.copy(
                    isCurrent = isCurrent,
                    professor = realProfessor
                )
            }

            DayScheduleSlot(
                date = date,
                dayOfWeek = dayOfWeek,
                dayName = dayName,
                dayLabel = dayLabel,
                relativeLabel = relativeLabel,
                isToday = isToday,
                classes = classesForDay
            )
        }
    }

    val pagerState = rememberPagerState(initialPage = todayOffsetIndex) { daysSchedule.size }

    // Reaccionar al pulsar la pestaña Horario en la barra inferior (navKey) para reiniciar a Hoy
    LaunchedEffect(navKey) {
        if (navKey > 0) {
            pagerState.scrollToPage(todayOffsetIndex)
        }
    }

    // Reaccionar al pulsar el icono de zoom superior en la TopAppBar
    LaunchedEffect(toggleZoomKey) {
        if (toggleZoomKey > 0) {
            viewMode = if (viewMode == ScheduleViewMode.PANORAMIC_3_DAYS) {
                ScheduleViewMode.DETAILED_DAY
            } else {
                ScheduleViewMode.PANORAMIC_3_DAYS
            }
            triggerVibration(context, durationMs = 60)
        }
    }

    // Acelerómetro: gesto «¡Llévame!» al examinar el detalle de una clase
    DisposableEffect(selectedClass) {
        val manager = AccelerometerManager(context)
        if (selectedClass != null) {
            manager.onTakeMeThereGesture = {
                val currentClass = selectedClass
                if (currentClass != null) {
                    triggerVibration(context, durationMs = 150)
                    val roomName = currentClass.room.split("·")[0].trim()
                    val floorName = currentClass.room.split("·").getOrElse(1) { "ETSIIT" }.trim()
                    selectedClass = null
                    onNavigate(Screen.Navigation.createRoute(roomName, floorName))
                }
            }
            manager.startListening()
        }
        onDispose { manager.stopListening() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(horizontal = 2.dp, vertical = 6.dp)
            // Detector de Gesto de Pellizco (Pinch-to-zoom) con dos dedos: Ampliar y Achicar
            .pointerInput(viewMode) {
                detectPinchZoomGesture(
                    onZoomIn = {
                        if (viewMode == ScheduleViewMode.PANORAMIC_3_DAYS) {
                            viewMode = ScheduleViewMode.DETAILED_DAY
                            triggerVibration(context, durationMs = 60)
                        }
                    },
                    onZoomOut = {
                        if (viewMode == ScheduleViewMode.DETAILED_DAY) {
                            viewMode = ScheduleViewMode.PANORAMIC_3_DAYS
                            triggerVibration(context, durationMs = 60)
                        }
                    }
                )
            }
    ) {
        if (viewMode == ScheduleViewMode.PANORAMIC_3_DAYS) {
            // Carrusel panorámico de 3 días: interpolación continua fluida sin tirones y sin sombras recortadas
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(top = 34.dp, bottom = 14.dp)
            ) {
                val screenWidth = maxWidth
                val pageSpacing = 6.dp
                val marginExt = 8.dp
                val available = screenWidth - (pageSpacing * 2) - (marginExt * 2)
                val centerCardWidth = (available * 0.40f).coerceIn(124.dp, 155.dp)
                val sideCardWidth = (available * 0.30f).coerceIn(98.dp, 120.dp)
                val horizontalPadding = ((screenWidth - centerCardWidth) / 2).coerceAtLeast(0.dp)

                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 50.dp),
                    pageSpacing = pageSpacing,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val day = daysSchedule[page]
                    val signedOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
                    val pageOffset = signedOffset.absoluteValue
                    val focusFactor = (1f - pageOffset).coerceIn(0f, 1f)
                    val cardWidth = sideCardWidth + (centerCardWidth - sideCardWidth) * focusFactor
                    val alpha = 1f - (pageOffset * 0.12f)
                    val isFocused = pageOffset < 0.5f

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(490.dp),
                        contentAlignment = BiasAlignment(horizontalBias = signedOffset, verticalBias = 0f)
                    ) {
                        CarouselDayCard(
                            day = day,
                            isFocused = isFocused,
                            focusFactor = focusFactor,
                            modifier = Modifier
                                .width(cardWidth)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    this.alpha = alpha
                                },
                            onClassClick = { item -> selectedClass = item },
                            onCardClick = {
                                if (pagerState.currentPage != page) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(page)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // Vista detallada de 1 día (modo zoom ampliado) con carrusel deslizable HorizontalPager
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val currentDay = daysSchedule[pagerState.currentPage]

                // Cabecera del día seleccionado: Chip rojo sólido para HOY vs neutro para otros días + Dots interactivos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (currentDay.isToday) UgrRed else Color(0xFFF1F5F9),
                        border = if (currentDay.isToday) null else BorderStroke(1.dp, SurfaceBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = if (currentDay.isToday) Color.White else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentDay.dayLabel,
                                color = if (currentDay.isToday) Color.White else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Indicador de puntos (dots) interactivos con resalte para Hoy
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(daysSchedule.size) { index ->
                            val isSelected = pagerState.currentPage == index
                            val isThisDotToday = daysSchedule[index].isToday
                            val dotColor = when {
                                isSelected && isThisDotToday -> UgrRed
                                isSelected -> TextPrimary
                                isThisDotToday -> UgrRed.copy(alpha = 0.45f)
                                else -> SurfaceBorder
                            }
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 10.dp else 7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                            )
                        }
                    }
                }

                // Deslizamiento HorizontalPager entre días anteriores y posteriores
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 16.dp,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val day = daysSchedule[page]
                    SingleDetailedDayContent(
                        day = day,
                        onSelectClass = { item -> selectedClass = item },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // =========================================================
    // 4. DIÁLOGO DETALLADO Y GESTO «¡LLÉVAME!» AL SELECCIONAR CLASE
    // =========================================================
    if (selectedClass != null) {
        val item = selectedClass!!
        val (subjectColor, subjectBg) = subjectColorFor(item.acronym)
        AlertDialog(
            onDismissRequest = { selectedClass = null },
            containerColor = SurfaceWhite,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary,
            icon = {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (item.isCurrent) CampusGreen.copy(alpha = 0.15f) else subjectBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = if (item.isCurrent) CampusGreen else subjectColor,
                        modifier = Modifier.size(26.dp)
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
                        fontSize = 17.sp,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = subjectBg,
                            border = BorderStroke(1.dp, subjectColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = item.acronym,
                                color = subjectColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (item.isCurrent) CampusGreen.copy(alpha = 0.15f) else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (item.isCurrent) CampusGreen.copy(alpha = 0.3f) else Color(0xFFE2E8F0))
                        ) {
                            Text(
                                text = if (item.isPractice) "PRÁCTICAS" else "TEORÍA",
                                color = if (item.isCurrent) CampusGreen else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (item.isCurrent) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CampusGreen
                            ) {
                                Text(
                                    text = "EN CURSO",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
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
                    // Horario y Ubicación con el gesto «¡Llévame!»
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, SurfaceBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = if (item.isCurrent) CampusGreen else TextSecondary,
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

                    // Ficha del profesor con acceso directo
                    if (item.professor.isNotBlank() && item.professor != "Profesor por asignar") {
                        Card(
                            onClick = {
                                selectedClass = null
                                onNavigate("${Screen.Professors.route}?nombre=${Uri.encode(item.professor)}")
                            },
                            colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, SurfaceBorder)
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
                                        Text("Profesor/a:", fontSize = 11.sp, color = TextSecondary)
                                        Text(item.professor, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = "Ver ficha",
                                    tint = CampusTeal,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedClass = null },
                    colors = ButtonDefaults.buttonColors(containerColor = UgrRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text("Cerrar", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                }
            }
        )
    }
}

/**
 * Tarjeta de día en el carrusel panorámico de 3 días (deslizamiento continuo fluido).
 */
@Composable
private fun CarouselDayCard(
    day: DayScheduleSlot,
    isFocused: Boolean,
    focusFactor: Float = if (isFocused) 1f else 0f,
    modifier: Modifier = Modifier,
    onClassClick: (ClassItem) -> Unit,
    onCardClick: () -> Unit
) {
    val hasActiveNow = day.classes.any { it.isCurrent }
    val borderColor = when {
        hasActiveNow -> CampusGreen
        day.isToday -> UgrRed
        else -> SurfaceBorder
    }
    val f = focusFactor.coerceIn(0f, 1f)

    Card(
        modifier = modifier.clickable(enabled = !isFocused, onClick = onCardClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = (1f + 1.5f * f).dp),
        border = BorderStroke(if (day.isToday || hasActiveNow) 2.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Cabecera del día centrada: si es HOY siempre se mantiene roja (incluso al deslizar a otros días)
            Surface(
                color = if (day.isToday) UgrRed else Color(0xFFF1F5F9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = (9f + 2f * f).dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.dayName.uppercase(),
                        fontSize = (14f + 2f * f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (day.isToday) Color.White else TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Lista centrada de clases con márgenes optimizados
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = (3f + 3f * f).dp, vertical = (4f + 3f * f).dp),
                verticalArrangement = Arrangement.spacedBy((4f + 3f * f).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (day.classes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size((22f + 8f * f).dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Sin docencia",
                                fontSize = (11f + 3f * f).sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Día libre",
                                fontSize = (9f + 2.5f * f).sp,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    day.classes.forEach { item ->
                        CarouselCompactClassCard(
                            item = item,
                            focusFactor = f,
                            onClick = { onClassClick(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Paleta de colores distintivos y armoniosos por asignatura para destacar la materia en el horario.
 */
private fun subjectColorFor(acronym: String): Pair<Color, Color> {
    return when (acronym.uppercase()) {
        "NPI" -> Color(0xFF7C3AED) to Color(0xFFEDE9FE) // Violeta
        "PTC" -> Color(0xFF0284C7) to Color(0xFFE0F2FE) // Azul cielo
        "PL"  -> Color(0xFFD97706) to Color(0xFFFEF3C7) // Ámbar / Naranja
        "VC"  -> Color(0xFF059669) to Color(0xFFD1FAE5) // Esmeralda / Verde
        "SS"  -> Color(0xFFE11D48) to Color(0xFFFFE4E6) // Rosa / Carmesí
        else  -> Color(0xFF475569) to Color(0xFFF1F5F9) // Pizarra / Gris neutro
    }
}

/**
 * Tarjeta compacta para una clase en el carrusel de 3 días:
 * - Arriba y destacado: Asignatura con su propio color identificativo (NPI, PTC, PL, VC, SS).
 * - Interpolación continua según focusFactor (0f lateral -> 1f centro): transición suave sin tirones.
 * - Abajo de la asignatura: Tipo de clase escrito como "Prácticas" o "Teoría" en tamaño más discreto.
 * - Horario y aula centrados (sustituyendo siempre 'Laboratorio' por 'Aula' para máxima compacidad).
 */
@Composable
private fun CarouselCompactClassCard(
    item: ClassItem,
    focusFactor: Float = 0f,
    onClick: () -> Unit
) {
    val isPrac = item.isPractice
    val (subjectColor, subjectBg) = subjectColorFor(item.acronym)
    val f = focusFactor.coerceIn(0f, 1f)

    val cleanRoom = item.room
        .split("·")[0]
        .replace("Laboratorio", "Aula", ignoreCase = true)
        .replace("Lab.", "Aula", ignoreCase = true)
        .replace("Lab", "Aula", ignoreCase = true)
        .trim()

    // Fondo: En curso verde claro, Prácticas más grisáceo (F1F5F9) y Teorías en blanco (SurfaceWhite)
    val cardColor = when {
        item.isCurrent -> Color(0xFFDCFCE7)
        isPrac -> Color(0xFFF1F5F9)
        else -> SurfaceWhite
    }

    val cardBorder = when {
        item.isCurrent -> CampusGreen
        isPrac -> Color(0xFFE2E8F0)
        else -> SurfaceBorder
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = cardColor,
        border = BorderStroke(
            if (item.isCurrent) 1.5.dp else 1.dp,
            cardBorder
        ),
        shadowElevation = if (item.isCurrent) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = (3f + 2f * f).dp, vertical = (4f + 2f * f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 1. ARRIBA: Abreviatura / Acrónimo de la asignatura destacado con su propio color
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (item.isCurrent) Color.White.copy(alpha = 0.9f) else subjectBg,
                border = BorderStroke(1.dp, subjectColor.copy(alpha = 0.6f))
            ) {
                Text(
                    text = item.acronym,
                    fontSize = (16.5f + 3.5f * f).sp,
                    fontWeight = FontWeight.Black,
                    color = subjectColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = (6f + 4f * f).dp, vertical = 2.dp)
                )
            }

            // 2. TIPO DE CLASE: "Prácticas" o "Teoría" más pequeño y discreto
            Text(
                text = if (isPrac) "Prácticas" else "Teoría",
                fontSize = (10f + 1f * f).sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            // 3. Horario centrado
            Text(
                text = item.time,
                fontSize = (11f + 2f * f).sp,
                fontWeight = FontWeight.Bold,
                color = if (item.isCurrent) CampusGreen else TextPrimary,
                textAlign = TextAlign.Center
            )

            // 4. Aula centrada
            Text(
                text = cleanRoom,
                fontSize = (11f + 2f * f).sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.isCurrent) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CampusGreen
                ) {
                    Text(
                        text = "● EN CURSO",
                        fontSize = (7.5f + 1.5f * f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * Detector de gesto de zoom con dos dedos:
 * - Ampliar (Pinch-out / dedos hacia afuera): Pasa a vista de 1 día detallado.
 * - Achicar (Pinch-in / dedos hacia adentro): Pasa a vista panorámica de 3 días.
 */
private suspend fun PointerInputScope.detectPinchZoomGesture(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    awaitEachGesture {
        var zoom = 1f
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            if (event.changes.size >= 2) {
                val currentZoom = event.calculateZoom()
                zoom *= currentZoom
                if (zoom > 1.18f) {
                    onZoomIn()
                    event.changes.forEach { it.consume() }
                    break
                } else if (zoom < 0.82f) {
                    onZoomOut()
                    event.changes.forEach { it.consume() }
                    break
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * Vista detallada de 1 día completo (con nombres íntegros, aulas y acceso directo ¡Llévame!).
 */
/**
 * Contenido de clases para la vista detallada de 1 día (sin botón de volver a 3 días,
 * con palabras PRÁCTICA y TEORÍA y sin logos de llévame en la tarjeta).
 */
@Composable
private fun SingleDetailedDayContent(
    day: DayScheduleSlot,
    onSelectClass: (ClassItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (day.isToday) UgrRed else SurfaceBorder
    val borderWidth = if (day.isToday) 2.dp else 1.dp

    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (day.isToday) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (day.classes.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                    border = BorderStroke(1.dp, SurfaceBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(UgrRed.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = UgrRed,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (day.isToday) "Hoy no tienes clase" else "El ${day.dayName} no tienes clase",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Día libre sin docencia programada en la ETSIIT.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                day.classes.forEach { item ->
                    DetailedClassCard(
                        item = item,
                        onClick = { onSelectClass(item) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Tarjeta completa para la vista de 1 día:
 * - Muestra las palabras completas "PRÁCTICA" y "TEORÍA"
 * - Sin logo de "¡Llévame!"
 * - Al pulsar la tarjeta se abre el diálogo con toda la información y guiado
 */
@Composable
private fun DetailedClassCard(
    item: ClassItem,
    onClick: () -> Unit
) {
    val isPrac = item.isPractice
    val (subjectColor, subjectBg) = subjectColorFor(item.acronym)

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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(if (item.isCurrent) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isCurrent) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Fila superior: Hora y Badge de Teoría / Práctica completa
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
                    // Badge neutro grisáceo idéntico para Teoría y Práctica (sin sobrecargar de color)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Text(
                            text = if (isPrac) "PRÁCTICA" else "TEORÍA",
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Acrónimo con su color distintivo y Nombre íntegro de la asignatura
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.isCurrent) Color.White.copy(alpha = 0.9f) else subjectBg,
                    border = BorderStroke(1.dp, subjectColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = item.acronym,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = subjectColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

            // Fila inferior: Aula limpia sin logos de guiado
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.MeetingRoom,
                    contentDescription = null,
                    tint = if (item.isCurrent) CampusGreen else TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.room,
                    fontSize = 12.sp,
                    color = if (item.isCurrent) Color(0xFF065F46) else TextSecondary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    } catch (_: Exception) { }
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

/**
 * Encuentra el índice de la próxima clase del día según la hora actual.
 */
private fun findNextClassIndex(classes: List<ClassItem>, currentTime: LocalTime): Int {
    return classes.indexOfFirst { item ->
        val startStr = item.time.split("-")[0].trim()
        val start = runCatching { LocalTime.parse(startStr) }.getOrNull()
        start != null && currentTime.isBefore(start)
    }
}

/**
 * Convierte el número de mes al nombre oficial en español.
 */
private fun getMonthNameSpanish(month: Int): String {
    return when (month) {
        1 -> "Enero"
        2 -> "Febrero"
        3 -> "Marzo"
        4 -> "Abril"
        5 -> "Mayo"
        6 -> "Junio"
        7 -> "Julio"
        8 -> "Agosto"
        9 -> "Septiembre"
        10 -> "Octubre"
        11 -> "Noviembre"
        12 -> "Diciembre"
        else -> ""
    }
}
