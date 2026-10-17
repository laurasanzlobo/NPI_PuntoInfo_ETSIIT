package com.example.npi_puntoinfo_etsiit.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import com.example.npi_puntoinfo_etsiit.ui.components.GestureBadge
import com.example.npi_puntoinfo_etsiit.ui.components.GestureType
import com.example.npi_puntoinfo_etsiit.sensors.AccelerometerManager
import com.example.npi_puntoinfo_etsiit.ui.navigation.Screen
import com.example.npi_puntoinfo_etsiit.ui.theme.*

data class DayDiningMenu(
    val dayName: String,
    val dayLabel: String,
    val firstCourse: String,
    val secondCourse: String,
    val dessert: String,
    val isToday: Boolean = false
)

@Composable
fun DiningScreen(
    onNavigate: (String) -> Unit = {},
    isCurrentClassDialogVisible: Boolean = false,
    navKey: Int = 0
) {
    val context = LocalContext.current

    fun navigateToDining() {
        triggerVibration(context, durationMs = 150)
        onNavigate(Screen.Navigation.createRoute("Comedor Aynadamar", "Campus Aynadamar"))
    }

    // Gesto del acelerómetro: «¡Llévame!» (giro de muñeca a la izquierda) activo mientras se consulta el comedor
    // Si el diálogo global de clase actual está abierto, se silencia para que prevalezca el de clase actual
    DisposableEffect(context, isCurrentClassDialogVisible) {
        val accelerometerManager = AccelerometerManager(context)

        if (!isCurrentClassDialogVisible) {
            accelerometerManager.onTakeMeThereGesture = {
                navigateToDining()
            }
            accelerometerManager.startListening()
        }

        onDispose {
            accelerometerManager.stopListening()
        }
    }
    val menuList = listOf(
        DayDiningMenu(
            dayName = "Hoy",
            dayLabel = "Hoy · Lunes",
            firstCourse = "Lentejas castellanas con verduras o Ensalada mixta",
            secondCourse = "Filete de merluza a la plancha o Pechuga de pollo",
            dessert = "Fruta de temporada o Yogur natural",
            isToday = true
        ),
        DayDiningMenu(
            dayName = "Mañana",
            dayLabel = "Mañana · Martes",
            firstCourse = "Paella mixta tradicional o Crema de calabacín",
            secondCourse = "Lomo de cerdo al ajillo o Tortilla francesa con champiñones",
            dessert = "Fruta de temporada o Natillas caseras",
            isToday = false
        ),
        DayDiningMenu(
            dayName = "Miércoles",
            dayLabel = "Miércoles",
            firstCourse = "Salmorejo cordobés con huevo o Macarrones a la boloñesa",
            secondCourse = "Salmón al horno con patatas o Hamburguesa de ternera",
            dessert = "Fruta de temporada o Flan de huevo",
            isToday = false
        ),
        DayDiningMenu(
            dayName = "Jueves",
            dayLabel = "Jueves",
            firstCourse = "Potaje de garbanzos con espinacas o Ensalada César",
            secondCourse = "Escalope de pollo empanado o Bacalao con tomate",
            dessert = "Fruta de temporada o Gelatina de frutas",
            isToday = false
        ),
        DayDiningMenu(
            dayName = "Viernes",
            dayLabel = "Viernes",
            firstCourse = "Arroz tres delicias o Sopa de picadillo",
            secondCourse = "Merluza a la romana o Chuleta de pavo a la plancha",
            dessert = "Fruta de temporada o Mousse de yogur",
            isToday = false
        )
    )

    val pagerState = remember(navKey) {
        PagerState(currentPage = 0, currentPageOffsetFraction = 0f) { menuList.size }
    }

    // Registro de pedidos encargados por día
    val ordersPlaced = remember { mutableStateMapOf<Int, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Desliza horizontalmente para consultar otros días",
            fontSize = 13.sp,
            color = TextSecondary
        )

        // Indicador del día actual y pista visual de deslizamiento (sin botones superiores)
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
                        text = menuList[pagerState.currentPage].dayLabel,
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
                repeat(menuList.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) UgrRed else SurfaceBorder)
                    )
                }
            }
        }

        // Pager deslizante para cambiar de día hacia la izquierda o derecha
        HorizontalPager(
            state = pagerState,
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val selectedMenu = menuList[page]
            val isCurrentDayOrdered = ordersPlaced[page] == true
            val targetDayText = if (selectedMenu.dayName.equals("Mañana", ignoreCase = true)) "mañana" else "el ${selectedMenu.dayName}"

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tarjeta del menú del día
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Menú del Día · ${selectedMenu.dayLabel}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "4.00 €",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = UgrRed
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        MenuItem(category = "1º Plato", dish = selectedMenu.firstCourse)
                        MenuItem(category = "2º Plato", dish = selectedMenu.secondCourse)
                        MenuItem(category = "Postre", dish = selectedMenu.dessert)
                        Spacer(modifier = Modifier.height(14.dp))
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
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ubicación: Comedor Aynadamar",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            }

                            // Loguito para el gesto «¡Llévame!» al comedor
                            GestureBadge(
                                type = GestureType.TAKE_ME_THERE,
                                size = 24.dp,
                                iconSize = 14.dp,
                                onClick = { navigateToDining() }
                            )
                        }
                    }
                }

                // Sección de encargo para llevar: SOLO disponible en días posteriores
                if (selectedMenu.isToday) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "El encargo para llevar debe realizarse con antelación. Desliza hacia la izquierda para ver los días posteriores y encargar tu menú.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isCurrentDayOrdered) Color(0xFFDCFCE7) else SurfaceWhite)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
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
                                        imageVector = Icons.Default.Fastfood,
                                        contentDescription = null,
                                        tint = if (isCurrentDayOrdered) CampusGreen else WarningAmber
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isCurrentDayOrdered) "¡Comida encargada para $targetDayText!" else "Encargar comida para llevar (${selectedMenu.dayName})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isCurrentDayOrdered) CampusGreen else TextPrimary
                                    )
                                }
                                // Loguito arriba a la derecha indicando el gesto para encargar comida
                                GestureBadge(type = GestureType.ACTION_TRIGGER)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isCurrentDayOrdered)
                                    "Recogida asignada para $targetDayText a las 14:15 h en la ventanilla express."
                                else
                                    "Puedes encargar tu comida para llevar con antelación para $targetDayText por 5.50 €.",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(category: String, dish: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = "$category: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
        Text(text = dish, fontSize = 13.sp, color = TextSecondary)
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

