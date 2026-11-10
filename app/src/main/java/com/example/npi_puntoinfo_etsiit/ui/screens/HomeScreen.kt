package com.example.npi_puntoinfo_etsiit.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.R
import com.example.npi_puntoinfo_etsiit.data.ScheduleRepository
import com.example.npi_puntoinfo_etsiit.data.UserSessionRepository
import com.example.npi_puntoinfo_etsiit.ui.navigation.Screen
import com.example.npi_puntoinfo_etsiit.ui.theme.*
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate

data class NewsItem(
    val title: String,
    val subtitle: String,
    @DrawableRes val imageRes: Int
)

/**
 * Pantalla Principal (Home) sin scroll vertical:
 * - Arriba: Tarjeta compacta "Próxima Clase" con acrónimo (ej. TIC), horario y aula sin cortarse.
 * - Centro: Carrusel de noticias reales oficiales de la ETSIIT con fotos reales y texto descriptivo sobreimpreso.
 * - Abajo: 3 botones rectangulares apilados (Mi Horario, Comedores, Profesores) con icono y texto en horizontal.
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onLaunchVoiceSearch: () -> Unit = {}
) {
    val context = LocalContext.current
    val profile = UserSessionRepository.getProfile(context)

    val currentOrNextClass = remember(context, profile) {
        if (!profile.isLoggedIn) {
            null
        } else {
            ScheduleRepository.getCurrentOrUpcomingClass(context = context, profile = profile)
        }
    }

    // Noticias oficiales reales de la ETSIIT con sus fotografías oficiales
    val newsList = remember {
        listOf(
            NewsItem(
                title = "IV Olimpiada Informática de Granada",
                subtitle = "Fase clasificatoria provincial integrada en la Olimpiada de Andalucía.",
                imageRes = R.drawable.noticia_olimpiada
            ),
            NewsItem(
                title = "Liderazgo en el Ranking Shanghai 2026",
                subtitle = "La ETSIIT y la UGR se consolidan en el top internacional de informática.",
                imageRes = R.drawable.noticia_shanghai
            ),
            NewsItem(
                title = "Emisión Instantánea de la TUI 2026/27",
                subtitle = "Puntos de emisión activa en el campus para obtener tu carné universitario.",
                imageRes = R.drawable.noticia_tui
            ),
            NewsItem(
                title = "Plan de Tutorización Nuevos Alumnos",
                subtitle = "Apoyo y mentoría para estudiantes que inician sus estudios en la escuela.",
                imageRes = R.drawable.noticia_tutorizacion
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { newsList.size })

    // Autoplay suave del carrusel de noticias
    LaunchedEffect(pagerState) {
        while (true) {
            delay(5000)
            val nextPage = (pagerState.currentPage + 1) % newsList.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ==========================================
        // 1. TARJETA SUPERIOR: PRÓXIMA CLASE (Solo visible si ha iniciado sesión y hay clase; si no, se oculta y las noticias se expanden)
        // ==========================================
        if (profile.isLoggedIn && currentOrNextClass != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Screen.Schedule.route) },
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(UgrRed, UgrRedDark)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    val rawRoomName = currentOrNextClass.room.split("·")[0].trim()
                    val cleanRoom = rawRoomName
                        .replace("Laboratorio", "", ignoreCase = true)
                        .replace("Aula", "", ignoreCase = true)
                        .replace("Lab", "", ignoreCase = true)
                        .trim()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PRÓXIMA CLASE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White.copy(alpha = 0.85f),
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentOrNextClass.acronym,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = "${currentOrNextClass.time}  ·  $cleanRoom",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.95f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = {
                                val roomName = currentOrNextClass.room.split("·")[0].trim()
                                val floorName = currentOrNextClass.room.split("·").getOrElse(1) { "ETSIIT" }.trim()
                                onNavigate(Screen.Navigation.createRoute(roomName, floorName))
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = "Guiar al aula",
                                    tint = UgrRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Ir",
                                    color = UgrRed,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. PARTE CENTRAL: CARRUSEL DE NOTICIAS REALES ETSIIT CON FOTO
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 10.dp
            ) { page ->
                val news = newsList[page]
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Fotografía oficial real de fondo
                        Image(
                            painter = painterResource(id = news.imageRes),
                            contentDescription = news.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Gradiente oscuro sobreimpreso para que los textos sean 100% legibles
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.50f),
                                            Color.Black.copy(alpha = 0.90f)
                                        ),
                                        startY = 100f
                                    )
                                )
                        )

                        // Textos de la noticia en la parte inferior
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = news.title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 21.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = news.subtitle,
                                color = Color.White.copy(alpha = 0.90f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Indicador de puntos (dots) del carrusel en la esquina superior derecha
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 14.dp, top = 12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(newsList.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.50f))
                    )
                }
            }
        }

        // ==========================================
        // 3. PARTE INFERIOR: NAVEGACIÓN SEGÚN ESTADO DE SESIÓN
        // ==========================================
        if (profile.isLoggedIn) {
            // CON SESIÓN INICIADA: Mi Horario, Servicios y Comedores
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeFlatButton(
                    title = "Mi Horario",
                    icon = Icons.Default.DateRange,
                    accentColor = Color(0xFF1D4ED8),
                    bgColor = Color(0xFFF0F7FF),
                    borderColor = Color(0xFFBFDBFE),
                    textColor = Color(0xFF1E3A8A),
                    iconGradient = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
                    onClick = { onNavigate(Screen.Schedule.route) }
                )

                HomeFlatButton(
                    title = "Servicios",
                    icon = Icons.Default.Info,
                    accentColor = Color(0xFF0F766E),
                    bgColor = Color(0xFFF0FDFA),
                    borderColor = Color(0xFF99F6E4),
                    textColor = Color(0xFF115E59),
                    iconGradient = listOf(Color(0xFF14B8A6), Color(0xFF0F766E)),
                    onClick = { onNavigate(Screen.Services.route) }
                )

                HomeFlatButton(
                    title = "Comedores",
                    icon = Icons.Default.Restaurant,
                    accentColor = Color(0xFFD97706),
                    bgColor = Color(0xFFFFFBEB),
                    borderColor = Color(0xFFFDE68A),
                    textColor = Color(0xFF92400E),
                    iconGradient = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
                    onClick = { onNavigate(Screen.Dining.route) }
                )
            }
        } else {
            // SIN SESIÓN INICIADA: Servicios y Comedores
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeFlatButton(
                    title = "Servicios",
                    icon = Icons.Default.Info,
                    accentColor = Color(0xFF0F766E),
                    bgColor = Color(0xFFF0FDFA),
                    borderColor = Color(0xFF99F6E4),
                    textColor = Color(0xFF115E59),
                    iconGradient = listOf(Color(0xFF14B8A6), Color(0xFF0F766E)),
                    onClick = { onNavigate(Screen.Services.route) }
                )

                HomeFlatButton(
                    title = "Comedores",
                    icon = Icons.Default.Restaurant,
                    accentColor = Color(0xFFD97706),
                    bgColor = Color(0xFFFFFBEB),
                    borderColor = Color(0xFFFDE68A),
                    textColor = Color(0xFF92400E),
                    iconGradient = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
                    onClick = { onNavigate(Screen.Dining.route) }
                )
            }
        }
    }
}

/**
 * Tarjeta de servicio compacta para visitantes sin sesión
 */
@Composable
private fun ServiceQuickCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    bgColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(68.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Píldora/Etiqueta de servicio informativo (solo etiqueta según feedback)
 */
@Composable
private fun ServiceTagBadge(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(30.dp),
        shape = RoundedCornerShape(99.dp),
        color = BackgroundLight,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * Botón rectangular profesional y equilibrado:
 * Fondo pastel suave, insignia con degradado vibrante, tipografía oscura nítida y flecha.
 */
@Composable
private fun HomeFlatButton(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    accentColor: Color,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    iconGradient: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 80.dp else 66.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Insignia con degradado elegante para el icono
                Box(
                    modifier = Modifier
                        .size(if (subtitle != null) 52.dp else 44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(iconGradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(if (subtitle != null) 28.dp else 24.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = title,
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = textColor.copy(alpha = 0.70f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Flecha sutil en el color temático
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
