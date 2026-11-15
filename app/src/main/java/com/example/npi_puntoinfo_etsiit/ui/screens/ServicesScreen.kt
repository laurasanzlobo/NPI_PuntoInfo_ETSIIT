package com.example.npi_puntoinfo_etsiit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.data.UserSessionRepository
import com.example.npi_puntoinfo_etsiit.ui.navigation.Screen
import com.example.npi_puntoinfo_etsiit.ui.theme.*

data class CampusServiceItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val accentColor: Color,
    val bgColor: Color,
    val borderColor: Color
)

/**
 * Pantalla dedicada de Servicios del Campus (ETSIIT):
 * - Acceso destacado a Profesores y Tutorías (único elemento con navegación interactiva).
 * - Cuadrícula de 6 servicios informativos centrados y ampliados que ocupan la pantalla,
 *   con feedback táctil (ripple) al pulsar pero sin navegación.
 */
@Composable
fun ServicesScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val profile = UserSessionRepository.getProfile(context)
    val isLoggedIn = profile.isLoggedIn
    val isEnglish = profile.language == "en"

    val services = remember(isEnglish) {
        listOf(
            CampusServiceItem(
                id = "conserjeria",
                title = if (isEnglish) "Reception" else "Conserjería",
                icon = Icons.Default.SupportAgent,
                accentColor = Color(0xFFD97706),
                bgColor = Color(0xFFFFFBEB),
                borderColor = Color(0xFFFDE68A)
            ),
            CampusServiceItem(
                id = "biblioteca",
                title = if (isEnglish) "Library" else "Biblioteca",
                icon = Icons.Default.LocalLibrary,
                accentColor = Color(0xFF1D4ED8),
                bgColor = Color(0xFFF0F7FF),
                borderColor = Color(0xFFBFDBFE)
            ),
            CampusServiceItem(
                id = "cafeteria",
                title = if (isEnglish) "Cafeteria" else "Cafetería",
                icon = Icons.Default.LocalCafe,
                accentColor = Color(0xFFC2410C),
                bgColor = Color(0xFFFFF7ED),
                borderColor = Color(0xFFFED7AA)
            ),
            CampusServiceItem(
                id = "secretaria",
                title = if (isEnglish) "Secretary's Office" else "Secretaría",
                icon = Icons.Default.MeetingRoom,
                accentColor = Color(0xFF7C3AED),
                bgColor = Color(0xFFFAF5FF),
                borderColor = Color(0xFFDDD6FE)
            ),
            CampusServiceItem(
                id = "clubes",
                title = if (isEnglish) "Clubs & Activities" else "Clubes y Actividades",
                icon = Icons.Default.Groups,
                accentColor = Color(0xFFE11D48),
                bgColor = Color(0xFFFFF1F2),
                borderColor = Color(0xFFFECDD3)
            ),
            CampusServiceItem(
                id = "movilidad",
                title = if (isEnglish) "International Relations" else "Relaciones Inter.",
                icon = Icons.Default.Public,
                accentColor = Color(0xFF0284C7),
                bgColor = Color(0xFFF0F9FF),
                borderColor = Color(0xFFBAE6FD)
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Tarjeta destacada de acceso a Profesores y Tutorías (único servicio navegable)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(Screen.Professors.route) },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDFA)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF99F6E4)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F766E).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "Profesores",
                        tint = Color(0xFF0F766E),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isEnglish) "Professors & Office Hours" else "Profesores y Tutorías",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF115E59)
                    )
                    if (isLoggedIn) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Tus profesores",
                            tint = UgrRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF0F766E),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Rejilla de los 6 servicios: centrados, iconos grandes y nombres ampliados
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(services) { service ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .clickable { /* Feedback táctil de pulsación sin navegación */ },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = service.bgColor),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, service.borderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(service.accentColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = service.icon,
                                contentDescription = service.title,
                                tint = service.accentColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = service.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
