package com.example.npi_puntoinfo_etsiit.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
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
import com.example.npi_puntoinfo_etsiit.sensors.CampusLocationManager
import com.example.npi_puntoinfo_etsiit.sensors.DeviceCoordinates
import com.example.npi_puntoinfo_etsiit.sensors.DeviceLocationProvider
import com.example.npi_puntoinfo_etsiit.sensors.GpsLocationReader
import com.example.npi_puntoinfo_etsiit.sensors.GpsLocationState
import com.example.npi_puntoinfo_etsiit.sensors.LocationPermission
import com.example.npi_puntoinfo_etsiit.sensors.OutdoorDistances
import com.example.npi_puntoinfo_etsiit.sensors.UgrDiningHall
import com.example.npi_puntoinfo_etsiit.ui.navigation.Screen
import com.example.npi_puntoinfo_etsiit.outdoor.OutdoorDestinations
import com.example.npi_puntoinfo_etsiit.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class DayDiningMenu(
    val date: LocalDate,
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
    val scope = rememberCoroutineScope()
    val locationProvider = remember(context) { DeviceLocationProvider(context) }
    val gpsReader = remember(locationProvider) { GpsLocationReader(locationProvider) }
    var userCoordinates by remember {
        val initial = DeviceLocationProvider.getInstantLocation(context) ?: CampusLocationManager.lastKnownLocation
        if (initial != null) {
            CampusLocationManager.lastKnownLocation = initial
        }
        mutableStateOf(initial)
    }
    var showAllHallsDialog by remember { mutableStateOf(false) }

    // Calcula automáticamente el comedor más cercano según las coordenadas GPS actuales
    val nearestDiningHall = remember(userCoordinates) {
        CampusLocationManager.findNearestDiningHall(userCoordinates?.latitude, userCoordinates?.longitude)
    }
    val currentNearestDiningHall by rememberUpdatedState(nearestDiningHall)

    val distanceMeters = remember(userCoordinates, nearestDiningHall) {
        userCoordinates?.let {
            OutdoorDistances.distanceMeters(it.latitude, it.longitude, nearestDiningHall.latitude, nearestDiningHall.longitude)
        }
    }

    val distanceFormatted = distanceMeters?.let {
        if (it < 1000) "${it.roundToInt()} m"
        else String.format(Locale.getDefault(), "%.1f km", it / 1000)
    }

    fun navigateToDining(hall: UgrDiningHall? = null) {
        val targetHall = hall ?: currentNearestDiningHall
        triggerVibration(context, durationMs = 150)
        val outdoorDest = OutdoorDestinations.forDiningHall(targetHall.id)
        if (outdoorDest != null) {
            onNavigate(Screen.Outdoor.createRoute(outdoorDest.id))
        }
    }

    val currentNavigateToDining by rememberUpdatedState(::navigateToDining)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            scope.launch {
                val instant = DeviceLocationProvider.getInstantLocation(context)
                if (instant != null) {
                    userCoordinates = instant
                    CampusLocationManager.lastKnownLocation = instant
                }
                val last = locationProvider.lastKnownLocation()
                if (last != null) {
                    userCoordinates = last
                    CampusLocationManager.lastKnownLocation = last
                }
                val state = gpsReader.read()
                if (state is GpsLocationState.Available) {
                    userCoordinates = state.coordinates
                    CampusLocationManager.lastKnownLocation = state.coordinates
                }
            }
        }
    }

    // Al abrir la pantalla, si tenemos permiso obtenemos la ubicación inmediatamente
    // o solicitamos permiso de ubicación para determinar el comedor más cercano
    LaunchedEffect(Unit) {
        if (locationProvider.permission() == LocationPermission.DENIED) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            val instant = DeviceLocationProvider.getInstantLocation(context)
            if (instant != null) {
                userCoordinates = instant
                CampusLocationManager.lastKnownLocation = instant
            }
            val last = locationProvider.lastKnownLocation()
            if (last != null) {
                userCoordinates = last
                CampusLocationManager.lastKnownLocation = last
            }
            val state = gpsReader.read()
            if (state is GpsLocationState.Available) {
                userCoordinates = state.coordinates
                CampusLocationManager.lastKnownLocation = state.coordinates
            }
        }
    }

    // Tomamos la fecha real del teléfono y la revisamos periódicamente mientras
    // Comedores está abierta: también se actualiza cuando cambia el día.
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now()
            delay(30_000L)
        }
    }

    // Mostramos cinco días lectivos: desde hoy si es lunes-viernes, o desde
    // el próximo lunes si es fin de semana. Los platos siguen siendo ficticios.
    val menuList = remember(today) {
        generateSequence(today) { it.plusDays(1) }
            .filter { it.dayOfWeek.value in 1..5 }
            .take(5)
            .map { date ->
                val weekday = date.dayOfWeek
                    .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-ES"))
                    .replaceFirstChar { it.uppercaseChar() }
                val title = when (date) {
                    today -> "Hoy"
                    today.plusDays(1) -> "Mañana"
                    else -> weekday
                }
                val dateLabel = "${date.dayOfMonth}/${date.monthValue}"
                val label = if (date == today || date == today.plusDays(1)) {
                    "$weekday · $dateLabel"
                } else {
                    "$weekday · $dateLabel"
                }
                val (first, second, dessert) = sampleDishesFor(weekday = date.dayOfWeek)
                DayDiningMenu(
                    date = date,
                    dayName = title,
                    dayLabel = label,
                    firstCourse = first,
                    secondCourse = second,
                    dessert = dessert,
                    isToday = date == today
                )
            }
            .toList()
    }

    // Al cambiar de fecha, volvemos al primer día disponible.
    val pagerState = remember(navKey, today) {
        PagerState(currentPage = 0, currentPageOffsetFraction = 0f) { menuList.size }
    }

    // Cada encargo se guarda con una FECHA REAL (ISO 8601), no con el índice
    // del menú. Así los pedidos de la semana pasada no aparecen en la actual.
    val orderPreferences = remember(context) {
        context.getSharedPreferences("npi_demo_dining_orders", Context.MODE_PRIVATE)
    }
    val ordersPlaced = remember(orderPreferences) {
        mutableStateMapOf<String, Boolean>().apply {
            orderPreferences.all.forEach { (key, value) ->
                if (key.startsWith("ordered_date_") && value == true) {
                    this[key.removePrefix("ordered_date_")] = true
                }
            }
        }
    }

    // Los pedidos antiguos, guardados como ordered_day_1, ordered_day_2...
    // carecen de fecha: no se reutilizan para evitar reservas incorrectas.
    LaunchedEffect(today) {
        val expired = ordersPlaced.keys.filter { dateKey ->
            val orderDate = runCatching { LocalDate.parse(dateKey) }.getOrNull()
            orderDate == null || orderDate.isBefore(today)
        }
        if (expired.isNotEmpty()) {
            val editor = orderPreferences.edit()
            expired.forEach { dateKey ->
                ordersPlaced.remove(dateKey)
                editor.remove("ordered_date_$dateKey")
            }
            editor.apply()
        }
    }

    // Guardamos la fecha que se desea encargar: no el índice de su página.
    var pendingOrderDate by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(today) {
        if (pendingOrderDate?.isAfter(today) != true) pendingOrderDate = null
    }

    // Esta pantalla dispone de dos gestos independientes:
    // 1) Giro lateral («¡Llévame!»): navegación exterior al comedor mostrado.
    // 2) Bajar brevemente la parte superior y volver: confirmar menú visible.
    // El gestor se escucha solo mientras esta pantalla está activa y no está abierto
    // el diálogo global de clase actual.
    DisposableEffect(context, isCurrentClassDialogVisible, pagerState, today) {
        val accelerometerManager = AccelerometerManager(context)
        if (!isCurrentClassDialogVisible) {
            accelerometerManager.onTakeMeThereGesture = {
                if (pendingOrderDate == null) currentNavigateToDining(null)
            }
            accelerometerManager.onForwardTiltGesture = {
                val day = pagerState.currentPage
                val selected = menuList.getOrNull(day)
                val dateKey = selected?.date?.toString()
                val isOrdered = dateKey?.let { ordersPlaced[it] == true } ?: false
                Log.i(
                    "NPI_COMEDOR_GESTO",
                    "Recibido en Comedores: fecha=$dateKey, reservado=$isOrdered, " +
                            "diálogoAbierto=${pendingOrderDate != null}, " +
                            "deslizando=${pagerState.isScrollInProgress}"
                )
                // El gesto solo abre la confirmación para un día futuro
                // que no esté reservado (nunca para hoy).
                if (selected != null &&
                    selected.date.isAfter(LocalDate.now()) &&
                    !isOrdered &&
                    pendingOrderDate == null &&
                    !pagerState.isScrollInProgress
                ) {
                    triggerVibration(context, durationMs = 80)
                    pendingOrderDate = selected.date
                }
            }
            accelerometerManager.startListening()
        }
        onDispose { accelerometerManager.stopListening() }
    }

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
            val isCurrentDayOrdered = ordersPlaced[selectedMenu.date.toString()] == true
            val targetDayText = when (selectedMenu.date) {
                today -> "hoy"
                today.plusDays(1) -> "mañana"
                else -> "el ${selectedMenu.dayName.lowercase(Locale.forLanguageTag("es-ES"))} " +
                        "${selectedMenu.date.dayOfMonth}/${selectedMenu.date.monthValue}"
            }

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
                                text = "Menú del Día · ${selectedMenu.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-ES")).replaceFirstChar { it.uppercaseChar() }}",
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
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showAllHallsDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = UgrRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = if (distanceFormatted != null) {
                                            "Comedor más cercano (a $distanceFormatted):"
                                        } else {
                                            "Comedor más cercano:"
                                        },
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "${nearestDiningHall.name} · ${nearestDiningHall.campus}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
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

                // El menú de hoy no puede reservarse ahora, pero si se encargó
                // ayer debe seguir mostrándose en verde durante todo el día.
                if (selectedMenu.isToday && !isCurrentDayOrdered) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isCurrentDayOrdered && selectedMenu.date.isAfter(today)) {
                                pendingOrderDate = selectedMenu.date
                            },
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

    // No confirmamos pedidos de hoy o de días pasados, aunque la fecha cambie
    // mientras el diálogo está abierto.
    pendingOrderDate?.let { date ->
        val pendingMenu = menuList.find { it.date == date }
        if (pendingMenu != null && date.isAfter(today)) {
            AlertDialog(
                onDismissRequest = { pendingOrderDate = null },
                title = { Text("Confirmar encargo") },
                text = {
                    Text(
                        "¿Quieres encargar el menú de ${pendingMenu.dayLabel} para llevar " +
                                "por 5,50 €?"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (date.isAfter(LocalDate.now())) {
                                val dateKey = date.toString()
                                ordersPlaced[dateKey] = true
                                orderPreferences.edit()
                                    .putBoolean("ordered_date_$dateKey", true)
                                    .apply()
                            }
                            pendingOrderDate = null
                        }
                    ) {
                        Text("Confirmar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingOrderDate = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }

    if (showAllHallsDialog) {
        AlertDialog(
            onDismissRequest = { showAllHallsDialog = false },
            title = {
                Text(
                    text = "Comedores Universitarios UGR",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Selecciona un comedor para iniciar la ruta a pie según tu ubicación:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    CampusLocationManager.ugrDiningHalls.forEach { hall ->
                        val isNearest = hall.id == nearestDiningHall.id
                        val hallDistance = userCoordinates?.let {
                            OutdoorDistances.distanceMeters(it.latitude, it.longitude, hall.latitude, hall.longitude)
                        }
                        val hallDistStr = hallDistance?.let {
                            if (it < 1000) "${it.roundToInt()} m"
                            else String.format(Locale.getDefault(), "%.1f km", it / 1000)
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isNearest) UgrRed.copy(alpha = 0.08f) else SurfaceWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isNearest) UgrRed else SurfaceBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAllHallsDialog = false
                                    navigateToDining(hall)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = hall.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = TextPrimary
                                        )
                                        if (isNearest) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = CampusGreen.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "Más cercano",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CampusGreen,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = hall.campus,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    if (hallDistStr != null) {
                                        Text(
                                            text = "A $hallDistStr de tu posición actual",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isNearest) UgrRed else TextSecondary
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                    contentDescription = "Ir",
                                    tint = if (isNearest) UgrRed else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllHallsDialog = false }) {
                    Text("Cerrar", color = TextSecondary)
                }
            }
        )
    }
}

/** Platos ficticios por día de la semana; solo cambian las fechas visibles. */
private fun sampleDishesFor(weekday: DayOfWeek): Triple<String, String, String> = when (weekday) {
    DayOfWeek.MONDAY -> Triple(
        "Lentejas castellanas con verduras o Ensalada mixta",
        "Filete de merluza a la plancha o Pechuga de pollo",
        "Fruta de temporada o Yogur natural"
    )
    DayOfWeek.TUESDAY -> Triple(
        "Paella mixta tradicional o Crema de calabacín",
        "Lomo de cerdo al ajillo o Tortilla francesa con champiñones",
        "Fruta de temporada o Natillas caseras"
    )
    DayOfWeek.WEDNESDAY -> Triple(
        "Salmorejo cordobés con huevo o Macarrones a la boloñesa",
        "Salmón al horno con patatas o Hamburguesa de ternera",
        "Fruta de temporada o Flan de huevo"
    )
    DayOfWeek.THURSDAY -> Triple(
        "Potaje de garbanzos con espinacas o Ensalada César",
        "Escalope de pollo empanado o Bacalao con tomate",
        "Fruta de temporada o Gelatina de frutas"
    )
    DayOfWeek.FRIDAY -> Triple(
        "Arroz tres delicias o Sopa de picadillo",
        "Merluza a la romana o Chuleta de pavo a la plancha",
        "Fruta de temporada o Mousse de yogur"
    )
    else -> error("El comedor no tiene menú de fin de semana")
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
