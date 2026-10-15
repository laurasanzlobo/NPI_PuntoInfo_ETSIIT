package com.example.npi_puntoinfo_etsiit.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.sensors.AccelerometerManager
import com.example.npi_puntoinfo_etsiit.ui.theme.*

@Composable
fun NavigationScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(1) } // 0: Exterior, 1: Interior

    // Estado del modo de navegación interior controlado por el acelerómetro
    // false = Plano 2D (móvil horizontal/mesa); true = Guiado Paso a Paso (móvil vertical)
    var isVerticalGuidanceActive by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val manager = AccelerometerManager(context)

        manager.onPostureModeChanged = { isVertical ->
            isVerticalGuidanceActive = isVertical
        }

        manager.onDeviceLifted = {
            isVerticalGuidanceActive = true
        }

        manager.startListening()

        onDispose {
            manager.stopListening()
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
        Text(
            text = "Navegación y Guiado",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        // Selector Exterior / Interior
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceWhite,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Exterior (GPS Campus)") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Interior (ETSIIT)") }
            )
        }

        if (selectedTab == 0) {
            // Modo Exterior (GPS)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null, tint = EtsiitBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Ubicación exterior en tiempo real", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Calculando distancias a pie hacia Comedores Universitarios, Bibliotecas y paradas de metro.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EtsiitBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DirectionsWalk, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ver ruta a Comedores (Fuentenueva)")
                    }
                }
            }
        } else {
            // Modo Interior: Alterna dinámicamente con el Acelerómetro
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Cabecera de la ruta
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Ruta activa: Aula 1.2",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Planta 1 · Módulo Informática",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        // Badge dinámico según postura del móvil
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isVerticalGuidanceActive) CampusGreen.copy(alpha = 0.12f) else EtsiitBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (isVerticalGuidanceActive) "📍 Guiado Vertical Activo" else "🗺️ Modo Plano 2D",
                                color = if (isVerticalGuidanceActive) CampusGreen else EtsiitBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    AnimatedContent(
                        targetState = isVerticalGuidanceActive,
                        label = "NavigationModeAnimation"
                    ) { isVertical ->
                        if (isVertical) {
                            // =========================================================
                            // MODO VERTICAL: GUIADO PASO A PASO EN DIRECTO (Al levantar el móvil)
                            // =========================================================
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(UgrRed.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.TurnRight,
                                                contentDescription = null,
                                                tint = UgrRed,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                text = "Gira a la derecha en el pasillo",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "Sube por las escaleras hacia la Planta 1",
                                                fontSize = 13.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }

                                // Métricas de camino
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Distancia", fontSize = 11.sp, color = TextSecondary)
                                            Text("35 metros", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                        }
                                    }
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Pasos estimados", fontSize = 11.sp, color = TextSecondary)
                                            Text("~45 pasos", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = UgrRed)
                                        }
                                    }
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Tiempo", fontSize = 11.sp, color = TextSecondary)
                                            Text("1 minuto", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CampusGreen)
                                        }
                                    }
                                }

                                Text(
                                    text = "📱 Sostienes el móvil en posición vertical de marcha. Para ver el plano 2D general, inclina el móvil o colócalo sobre la mesa.",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 15.sp
                                )

                                TextButton(
                                    onClick = { isVerticalGuidanceActive = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ver plano 2D general")
                                }
                            }
                        } else {
                            // =========================================================
                            // MODO HORIZONTAL: PLANO 2D DE LA PLANTA (Móvil horizontal/mesa)
                            // =========================================================
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Croquis esquemático del plano de planta
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = BackgroundLight)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = "Plano Esquemático · Planta 1",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Representación visual de las estancias
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(60.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFFE2E8F0)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Aula 1.1", fontSize = 11.sp, color = TextSecondary)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1.2f)
                                                    .height(60.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(UgrRed.copy(alpha = 0.15f))
                                                    .border(1.5.dp, UgrRed, RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("Aula 1.2", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = UgrRed)
                                                    Text("★ Destino", fontSize = 9.sp, color = UgrRed)
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(60.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFFE2E8F0)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Despachos", fontSize = 11.sp, color = TextSecondary)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Pasillo con trazado
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(CampusTeal.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "───── Ruta: Pasillo Central → Escaleras ─────",
                                                fontSize = 10.sp,
                                                color = CampusTeal,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Aviso pedagógico de interacción gestual
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = CampusTeal.copy(alpha = 0.10f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ScreenRotation,
                                            contentDescription = null,
                                            tint = CampusTeal,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "📱 Levanta el móvil verticalmente frente a ti para activar el modo de guiado paso a paso con flechas.",
                                            fontSize = 12.sp,
                                            color = CampusTeal,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Button(
                                    onClick = { isVerticalGuidanceActive = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = UgrRed),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.DirectionsWalk, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Activar guiado paso a paso")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

