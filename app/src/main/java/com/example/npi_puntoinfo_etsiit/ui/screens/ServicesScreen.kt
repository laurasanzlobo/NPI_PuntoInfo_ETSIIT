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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.npi_puntoinfo_etsiit.data.UserSessionRepository
import com.example.npi_puntoinfo_etsiit.ui.navigation.Screen
import com.example.npi_puntoinfo_etsiit.ui.theme.*

data class CampusServiceItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val isFunctional: Boolean = true,
    val accentColor: Color,
    val bgColor: Color,
    val borderColor: Color,
    val routeToNavigate: String? = null
)

/**
 * Pantalla dedicada de Servicios del Campus (ETSIIT):
 * Muestra los 6 servicios clave distribuidos en 3 filas y 2 columnas con diseño limpio y táctil.
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
                title = if (isEnglish) "Reception / Janitor" else "Conserjería",
                subtitle = if (isEnglish) "Ground Floor · Help" else "Planta Baja · Ayuda",
                icon = Icons.Default.SupportAgent,
                isFunctional = true,
                accentColor = Color(0xFFD97706),
                bgColor = Color(0xFFFFFBEB),
                borderColor = Color(0xFFFDE68A),
                routeToNavigate = Screen.Navigation.createRoute("Conserjeria_Aulas_PB", "Planta Baja")
            ),
            CampusServiceItem(
                id = "biblioteca",
                title = if (isEnglish) "Library" else "Biblioteca",
                subtitle = if (isEnglish) "1st Floor · Study" else "Planta 1 · Estudio",
                icon = Icons.Default.LocalLibrary,
                isFunctional = true,
                accentColor = Color(0xFF1D4ED8),
                bgColor = Color(0xFFF0F7FF),
                borderColor = Color(0xFFBFDBFE),
                routeToNavigate = Screen.Navigation.createRoute("Biblioteca", "Planta 1")
            ),
            CampusServiceItem(
                id = "cafeteria",
                title = if (isEnglish) "Cafeteria" else "Cafetería",
                subtitle = if (isEnglish) "Floor -1 · Breakfast" else "Planta -1 · Desayunos",
                icon = Icons.Default.LocalCafe,
                isFunctional = true,
                accentColor = Color(0xFFC2410C),
                bgColor = Color(0xFFFFF7ED),
                borderColor = Color(0xFFFED7AA),
                routeToNavigate = Screen.Navigation.createRoute("Cafeteria", "Planta -1")
            ),
            CampusServiceItem(
                id = "secretaria",
                title = if (isEnglish) "Secretary's Office" else "Secretaría",
                subtitle = if (isEnglish) "Ground Floor · Administration" else "Planta Baja · Trámites",
                icon = Icons.Default.MeetingRoom,
                isFunctional = true,
                accentColor = Color(0xFF7C3AED),
                bgColor = Color(0xFFFAF5FF),
                borderColor = Color(0xFFDDD6FE),
                routeToNavigate = Screen.Navigation.createRoute("Secretaria", "Planta Baja")
            ),
            CampusServiceItem(
                id = "clubes",
                title = if (isEnglish) "Clubs & Activities" else "Clubes y Actividades",
                subtitle = if (isEnglish) "Floor -1 · Leisure" else "Planta -1 · Ocio",
                icon = Icons.Default.Groups,
                isFunctional = false,
                accentColor = Color(0xFFE11D48),
                bgColor = Color(0xFFFFF1F2),
                borderColor = Color(0xFFFECDD3),
                routeToNavigate = null
            ),
            CampusServiceItem(
                id = "movilidad",
                title = if (isEnglish) "International Relations" else "Relaciones Inter.",
                subtitle = if (isEnglish) "Ground Floor · Mobility" else "Planta Baja · Movilidad",
                icon = Icons.Default.Public,
                isFunctional = false,
                accentColor = Color(0xFF0284C7),
                bgColor = Color(0xFFF0F9FF),
                borderColor = Color(0xFFBAE6FD),
                routeToNavigate = null
            )
        )
    }

    var selectedInfoService by remember { mutableStateOf<CampusServiceItem?>(null) }

    // Diálogo informativo breve para servicios que son solo etiqueta
    if (selectedInfoService != null) {
        val s = selectedInfoService!!
        val plantaUbicacion = if (isEnglish) (if (s.id == "clubes") "Floor -1" else "Ground Floor") else (if (s.id == "clubes") "Planta -1" else "Planta Baja")
        AlertDialog(
            onDismissRequest = { selectedInfoService = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = s.icon, contentDescription = null, tint = s.accentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(s.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Text(
                    text = if (isEnglish)
                        "The ${s.title} service is located on $plantaUbicacion at ETSIIT. Currently configured as an informational service."
                    else
                        "El servicio de ${s.title} se encuentra ubicado en la $plantaUbicacion de la ETSIIT. Actualmente está configurado como servicio informativo.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { selectedInfoService = null }) {
                    Text(if (isEnglish) "Got it" else "Entendido", color = CampusTeal, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Encabezado descriptivo
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CampusTeal.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CampusTeal,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = if (isEnglish) "Campus Services" else "Servicios del Campus",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isEnglish) "Select a service to view its route or information" else "Selecciona un servicio para ver su ruta o información",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // Tarjeta destacada de acceso a Profesores y Tutorías (disponible con y sin sesión iniciada)
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F766E).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "Profesores",
                        tint = Color(0xFF0F766E),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isEnglish) "Professors & Office Hours" else "Profesores y Tutorías",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
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
            }
        }

        // Rejilla de 6 servicios: 3 filas y 2 columnas
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(services) { service ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clickable {
                            if (service.isFunctional && service.routeToNavigate != null) {
                                onNavigate(service.routeToNavigate)
                            } else {
                                selectedInfoService = service
                            }
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = service.bgColor),
                    border = androidx.compose.foundation.BorderStroke(1.dp, service.borderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(service.accentColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = service.icon,
                                contentDescription = service.title,
                                tint = service.accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = service.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                lineHeight = 16.sp,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = service.subtitle,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
