package com.example.npi_puntoinfo_etsiit.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.data.UserSessionRepository
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

enum class DiningMenuType(val label: String) {
    TRADITIONAL("Tradicional"),
    VEGETARIAN("Vegetariano")
}

data class MenuCourses(
    val firstCourse: String,
    val secondCourse: String,
    val dessert: String
)

data class DayDiningMenu(
    val date: LocalDate,
    val dayName: String,
    val dayLabel: String,
    val traditional: MenuCourses,
    val vegetarian: MenuCourses,
    val isToday: Boolean = false
)

data class OrderSuccessInfo(
    val dayLabel: String,
    val menuTypeName: String
)

enum class PaymentMethod(val title: String, val icon: ImageVector) {
    BIZUM("Bizum", Icons.Default.PhoneAndroid),
    CARD("Tarjeta Bancaria", Icons.Default.CreditCard)
}

@Composable
fun DiningScreen(
    onNavigate: (String) -> Unit = {},
    isCurrentClassDialogVisible: Boolean = false,
    navKey: Int = 0,
    cancelPaymentKey: Int = 0,
    onPaymentModeChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val profile = UserSessionRepository.getProfile(context)
    val isLoggedIn = profile.isLoggedIn
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

    // Comedor más cercano (siempre Aynadamar en la simulación)
    val nearestDiningHall = remember(userCoordinates) {
        CampusLocationManager.findNearestDiningHall(userCoordinates?.latitude, userCoordinates?.longitude)
    }

    val distanceMeters = remember(userCoordinates, nearestDiningHall) {
        userCoordinates?.let {
            OutdoorDistances.distanceMeters(it.latitude, it.longitude, nearestDiningHall.latitude, nearestDiningHall.longitude)
        }
    }

    val distanceFormatted = distanceMeters?.let {
        if (it < 1000) "${it.roundToInt()} m"
        else String.format(Locale.getDefault(), "%.1f km", it / 1000)
    }

    fun navigateToDining() {
        triggerVibration(context, durationMs = 150)
        // Mantener el destino del comedor para enlazar el acceso ETSIIT con su tramo interior.
        onNavigate(Screen.Outdoor.createRoute("dining-aynadamar"))
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

    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now()
            delay(30_000L)
        }
    }

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
                val label = "$weekday · $dateLabel"
                val (trad, veg) = sampleDishesFor(weekday = date.dayOfWeek)
                DayDiningMenu(
                    date = date,
                    dayName = title,
                    dayLabel = label,
                    traditional = trad,
                    vegetarian = veg,
                    isToday = date == today
                )
            }
            .toList()
    }

    val pagerState = remember(navKey, today) {
        PagerState(currentPage = 0, currentPageOffsetFraction = 0f) { menuList.size }
    }

    // Tipo de menú seleccionado para visualizar en pantalla (Tradicional vs Vegetariano)
    var selectedMenuType by remember { mutableStateOf(DiningMenuType.TRADITIONAL) }

    val orderPreferences = remember(context) {
        context.getSharedPreferences("npi_demo_dining_orders", Context.MODE_PRIVATE)
    }
    val ordersPlaced = remember(orderPreferences) {
        mutableStateMapOf<String, String>().apply {
            orderPreferences.all.forEach { (key, value) ->
                if (key.startsWith("ordered_date_")) {
                    val type = (value as? String) ?: "Tradicional"
                    this[key.removePrefix("ordered_date_")] = type
                }
            }
        }
    }

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

    // Estado del diálogo de pasarela de pago simulada
    var pendingOrderDate by remember { mutableStateOf<LocalDate?>(null) }
    var orderMenuType by remember(pendingOrderDate) { mutableStateOf(selectedMenuType) }
    var selectedPaymentMethod by remember { mutableStateOf(PaymentMethod.BIZUM) }
    var phoneNumberInput by remember { mutableStateOf("612 345 678") }
    var cardNumberInput by remember { mutableStateOf("4550 1234 5678 9010") }
    var cardExpiryInput by remember { mutableStateOf("09/28") }
    var cardCvvInput by remember { mutableStateOf("123") }
    var isPaymentProcessing by remember { mutableStateOf(false) }
    var paymentSuccessDialog by remember { mutableStateOf<OrderSuccessInfo?>(null) }

    LaunchedEffect(today) {
        if (pendingOrderDate?.isAfter(today) != true) pendingOrderDate = null
    }

    // Gestos del acelerómetro
    DisposableEffect(context, isCurrentClassDialogVisible, pagerState, today, isLoggedIn) {
        val accelerometerManager = AccelerometerManager(context)
        if (!isCurrentClassDialogVisible) {
            accelerometerManager.onTakeMeThereGesture = {
                if (!pagerState.isScrollInProgress && pendingOrderDate == null && paymentSuccessDialog == null) {
                    currentNavigateToDining()
                }
            }
            accelerometerManager.onForwardTiltGesture = {
                if (!pagerState.isScrollInProgress) {
                    if (isLoggedIn) {
                        val day = pagerState.currentPage
                        val selected = menuList.getOrNull(day)
                        val dateKey = selected?.date?.toString()
                        val isOrdered = dateKey?.let { !ordersPlaced[it].isNullOrBlank() } ?: false

                        if (selected != null &&
                            selected.date.isAfter(LocalDate.now()) &&
                            !isOrdered &&
                            pendingOrderDate == null &&
                            paymentSuccessDialog == null
                        ) {
                            triggerVibration(context, durationMs = 80)
                            orderMenuType = selectedMenuType
                            pendingOrderDate = selected.date
                        } else if (selected != null && !selected.date.isAfter(LocalDate.now())) {
                            triggerVibration(context, durationMs = 40)
                            Toast.makeText(context, "El encargo para llevar es para días posteriores (desliza a mañana)", Toast.LENGTH_SHORT).show()
                        } else if (isOrdered) {
                            triggerVibration(context, durationMs = 40)
                            Toast.makeText(context, "Ya tienes un menú encargado para este día", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        triggerVibration(context, durationMs = 40)
                        Toast.makeText(context, "Inicia sesión para encargar tu menú con gestos", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            accelerometerManager.startListening()
        }
        onDispose { accelerometerManager.stopListening() }
    }

    val currentMenu = menuList[pagerState.currentPage]
    val currentDayOrderedType = ordersPlaced[currentMenu.date.toString()]
    val isDayOrdered = !currentDayOrderedType.isNullOrBlank()
    val targetDayText = when (currentMenu.date) {
        today -> "hoy"
        today.plusDays(1) -> "mañana"
        else -> "el ${currentMenu.dayName.lowercase(Locale.forLanguageTag("es-ES"))} ${currentMenu.date.dayOfMonth}/${currentMenu.date.monthValue}"
    }

    val activePendingMenu = pendingOrderDate?.let { date ->
        if (date.isAfter(today)) menuList.find { it.date == date } else null
    }

    val isPaymentActive = activePendingMenu != null
    LaunchedEffect(isPaymentActive) {
        onPaymentModeChanged(isPaymentActive)
    }

    LaunchedEffect(cancelPaymentKey) {
        if (cancelPaymentKey > 0) {
            pendingOrderDate = null
        }
    }

    if (activePendingMenu != null) {
        BackHandler(enabled = !isPaymentProcessing) {
            pendingOrderDate = null
        }

        // ==========================================
        // PÁGINA COMPLETA DE PAGO COMEDOR
        // ==========================================
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundLight
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // TARJETA DE RESUMEN DEL PEDIDO
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Restaurant,
                                        contentDescription = null,
                                        tint = UgrRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Resumen del pedido",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextPrimary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = UgrRed.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = "5.50 €",
                                        color = UgrRed,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = SurfaceBorder.copy(alpha = 0.6f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Día:", color = TextSecondary, fontSize = 13.sp)
                                Text(activePendingMenu.dayLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                            }

                            Spacer(Modifier.height(4.dp))

                            // Selector de tipo de menú
                            Text(
                                text = "Elige tu tipo de menú:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DiningMenuType.values().forEach { type ->
                                    val isSelected = orderMenuType == type
                                    val activeColor = if (type == DiningMenuType.VEGETARIAN) CampusGreen else UgrRed
                                    Surface(
                                        onClick = { orderMenuType = type },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) activeColor.copy(alpha = 0.12f) else BackgroundLight,
                                        border = androidx.compose.foundation.BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp,
                                            if (isSelected) activeColor else SurfaceBorder
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (type == DiningMenuType.VEGETARIAN) {
                                                Icon(
                                                    imageVector = Icons.Default.Eco,
                                                    contentDescription = null,
                                                    tint = if (isSelected) CampusGreen else TextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = "Menú ${type.label}",
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) activeColor else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TARJETA DE MÉTODO DE PAGO Y FORMULARIO
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Método de pago",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )

                            // Tabs Bizum vs Tarjeta
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PaymentMethod.values().forEach { method ->
                                    val isSelected = selectedPaymentMethod == method
                                    Surface(
                                        onClick = { selectedPaymentMethod = method },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) UgrRed.copy(alpha = 0.12f) else BackgroundLight,
                                        border = androidx.compose.foundation.BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp,
                                            if (isSelected) UgrRed else SurfaceBorder
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = method.icon,
                                                contentDescription = null,
                                                tint = if (isSelected) UgrRed else TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = method.title,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) UgrRed else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            // Formulario según método
                            if (selectedPaymentMethod == PaymentMethod.BIZUM) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = phoneNumberInput,
                                        onValueChange = { phoneNumberInput = it },
                                        label = { Text("Teléfono asociado a Bizum") },
                                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = UgrRed) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Text(
                                        text = "Recibirás una notificación en la app de tu banco para autorizar la operación de 5.50 €.",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        lineHeight = 16.sp
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = cardNumberInput,
                                        onValueChange = { cardNumberInput = it },
                                        label = { Text("Número de tarjeta") },
                                        leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, tint = UgrRed) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = cardExpiryInput,
                                            onValueChange = { cardExpiryInput = it },
                                            label = { Text("Caducidad (MM/AA)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = cardCvvInput,
                                            onValueChange = { cardCvvInput = it },
                                            label = { Text("CVV") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // BOTÓN DE PAGO
                    Button(
                        onClick = {
                            isPaymentProcessing = true
                            scope.launch {
                                delay(1200)
                                isPaymentProcessing = false
                                val dateKey = activePendingMenu.date.toString()
                                ordersPlaced[dateKey] = orderMenuType.label
                                orderPreferences.edit().putString("ordered_date_$dateKey", orderMenuType.label).apply()
                                triggerVibration(context, durationMs = 150)
                                paymentSuccessDialog = OrderSuccessInfo(activePendingMenu.dayLabel, orderMenuType.label)
                                pendingOrderDate = null
                            }
                        },
                        enabled = !isPaymentProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = UgrRed),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (isPaymentProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Procesando pago seguro...", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Pagar 5.50 €", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // BOTÓN CANCELAR
                    OutlinedButton(
                        onClick = { pendingOrderDate = null },
                        enabled = !isPaymentProcessing,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
                    ) {
                        Text("Cancelar y volver", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        // ==========================================
        // 1. SELECTOR DEL DÍA (DOTS + DÍA ACTUAL)
        // ==========================================
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
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
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
                        text = currentMenu.dayLabel,
                        color = UgrRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Indicador de puntos (dots)
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
                            .clickable {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                    )
                }
            }
        }

        // ==========================================
        // 2. PARTE CENTRAL E INFERIOR: PÁGINA DEL DÍA (MENÚ + ENCARGO) EN HORIZONTAL PAGER
        // ==========================================
        HorizontalPager(
            state = pagerState,
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val menu = menuList[page]
            val dayOrderedType = ordersPlaced[menu.date.toString()]
            val isThisDayOrdered = !dayOrderedType.isNullOrBlank()

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(365.dp)
                        .padding(horizontal = 2.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Cabecera: Selector de tipo de menú (Tradicional / Vegetariano) y Tarifa oficial (4.00 €)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Selector segmentado: Tradicional vs Vegetariano (sin títulos redundantes como "Menú del Día")
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BackgroundLight)
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                DiningMenuType.values().forEach { type ->
                                    val isSelected = selectedMenuType == type
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (isSelected) SurfaceWhite else Color.Transparent,
                                        shadowElevation = if (isSelected) 1.dp else 0.dp,
                                        onClick = { selectedMenuType = type }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 7.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (type == DiningMenuType.VEGETARIAN) {
                                                Icon(
                                                    imageVector = Icons.Default.Eco,
                                                    contentDescription = null,
                                                    tint = if (isSelected) CampusGreen else TextSecondary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = type.label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp,
                                                color = if (isSelected) (if (type == DiningMenuType.VEGETARIAN) CampusGreen else UgrRed) else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Tarifa oficial de comedor
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = UgrRed.copy(alpha = 0.10f)
                            ) {
                                Text(
                                    text = "4.00 €",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = UgrRed,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                )
                            }
                        }

                        // Platos agrupados con separación equilibrada y legible
                        val courses = if (selectedMenuType == DiningMenuType.TRADITIONAL) menu.traditional else menu.vegetarian
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DishVisualRow(
                                dishNumber = "1",
                                category = "Primer Plato",
                                dishName = courses.firstCourse,
                                badgeColor = EtsiitBlue
                            )
                            HorizontalDivider(color = SurfaceBorder.copy(alpha = 0.5f))
                            DishVisualRow(
                                dishNumber = "2",
                                category = "Segundo Plato",
                                dishName = courses.secondCourse,
                                badgeColor = WarningAmber
                            )
                            HorizontalDivider(color = SurfaceBorder.copy(alpha = 0.5f))
                            DishVisualRow(
                                dishNumber = "3",
                                category = "Postre",
                                dishName = courses.dessert,
                                badgeColor = CampusGreen
                            )
                        }

                        // Barra inferior de comedor más cercano (Aynadamar)
                        Surface(
                            onClick = { navigateToDining() },
                            shape = RoundedCornerShape(14.dp),
                            color = BackgroundLight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.8f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(UgrRed.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            tint = UgrRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (distanceFormatted != null) "Comedor más cercano (a $distanceFormatted):" else "Comedor más cercano:",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = nearestDiningHall.name, // SOLO el nombre (sin campus)
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                }

                                GestureBadge(
                                    type = GestureType.TAKE_ME_THERE,
                                    size = 28.dp,
                                    iconSize = 16.dp,
                                    onClick = { navigateToDining() }
                                )
                            }
                        }
                    }
                }

                // Tarjeta de encargo / reserva de comida para ESTE DÍA (Solo visible si ha iniciado sesión)
                if (isLoggedIn) {
                    if (menu.isToday && !isThisDayOrdered) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(108.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                    text = "El encargo para llevar debe realizarse con antelación. Desliza hacia la izquierda para encargar los días posteriores.",
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
                                .height(108.dp)
                                .clickable(enabled = !isThisDayOrdered && menu.date.isAfter(today)) {
                                    pendingOrderDate = menu.date
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isThisDayOrdered) Color(0xFFDCFCE7) else SurfaceWhite
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                if (isThisDayOrdered) CampusGreen else WarningAmber.copy(alpha = 0.5f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isThisDayOrdered) CampusGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isThisDayOrdered) Icons.Default.CheckCircle else Icons.Default.Fastfood,
                                            contentDescription = null,
                                            tint = if (isThisDayOrdered) CampusGreen else WarningAmber,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = if (isThisDayOrdered) "¡Menú $dayOrderedType reservado!" else "Encargar menú para llevar",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (isThisDayOrdered) Color(0xFF065F46) else TextPrimary
                                        )
                                        Text(
                                            text = "Para el ${menu.dayLabel}",
                                            fontSize = 12.sp,
                                            color = if (isThisDayOrdered) Color(0xFF047857) else TextSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (isThisDayOrdered)
                                                "Recoger a partir de las 13:00 h"
                                            else
                                                "5.50 €",
                                            fontSize = if (isThisDayOrdered) 12.sp else 14.sp,
                                            fontWeight = if (isThisDayOrdered) FontWeight.Normal else FontWeight.ExtraBold,
                                            color = if (isThisDayOrdered) TextSecondary else UgrRed
                                        )
                                    }
                                }

                                if (!isThisDayOrdered && menu.date.isAfter(today)) {
                                    GestureBadge(
                                        type = GestureType.ACTION_TRIGGER,
                                        size = 28.dp,
                                        iconSize = 16.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

    // Diálogo de Pago y Reserva Confirmada
    paymentSuccessDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { paymentSuccessDialog = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(CampusGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = CampusGreen,
                        modifier = Modifier.size(30.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Pago y reserva confirmada",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Se ha completado el pago de 5.50 € correctamente.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = { paymentSuccessDialog = null }) {
                    Text("Aceptar", color = CampusGreen, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/**
 * Fila visual para cada plato del menú (1º, 2º y Postre) con indicador numérico estético.
 */
@Composable
private fun DishVisualRow(
    dishNumber: String,
    category: String,
    dishName: String,
    badgeColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(badgeColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dishNumber,
                color = badgeColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = badgeColor,
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = dishName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = TextPrimary,
                lineHeight = 21.sp
            )
        }
    }
}

/** Platos oficiales por día de la semana para Menú Tradicional y Menú Vegetariano (platos únicos y fijos) */
private fun sampleDishesFor(weekday: DayOfWeek): Pair<MenuCourses, MenuCourses> = when (weekday) {
    DayOfWeek.MONDAY -> Pair(
        MenuCourses(
            firstCourse = "Lentejas castellanas con verduras",
            secondCourse = "Filete de merluza a la plancha",
            dessert = "Fruta de temporada"
        ),
        MenuCourses(
            firstCourse = "Lentejas castellanas con verduras",
            secondCourse = "Hamburguesa vegetal de espinacas",
            dessert = "Fruta de temporada"
        )
    )
    DayOfWeek.TUESDAY -> Pair(
        MenuCourses(
            firstCourse = "Paella mixta tradicional",
            secondCourse = "Lomo de cerdo al ajillo",
            dessert = "Natillas caseras"
        ),
        MenuCourses(
            firstCourse = "Crema de calabacín con picatostes",
            secondCourse = "Tortilla francesa con champiñones",
            dessert = "Yogur natural"
        )
    )
    DayOfWeek.WEDNESDAY -> Pair(
        MenuCourses(
            firstCourse = "Macarrones a la boloñesa",
            secondCourse = "Salmón al horno con patatas",
            dessert = "Flan de huevo"
        ),
        MenuCourses(
            firstCourse = "Salmorejo cordobés con picatoste y huevo",
            secondCourse = "Berenjenas rellenas de soja texturizada",
            dessert = "Flan de huevo"
        )
    )
    DayOfWeek.THURSDAY -> Pair(
        MenuCourses(
            firstCourse = "Potaje de garbanzos con espinacas",
            secondCourse = "Escalope de pollo empanado",
            dessert = "Gelatina de frutas"
        ),
        MenuCourses(
            firstCourse = "Potaje de garbanzos con espinacas",
            secondCourse = "Revuelto de setas y trigueros",
            dessert = "Gelatina de frutas"
        )
    )
    DayOfWeek.FRIDAY -> Pair(
        MenuCourses(
            firstCourse = "Arroz tres delicias con tortilla",
            secondCourse = "Merluza a la romana con ensalada",
            dessert = "Mousse de yogur"
        ),
        MenuCourses(
            firstCourse = "Sopa minestrone de verduras",
            secondCourse = "Milanesa de tofu crujiente",
            dessert = "Mousse de yogur"
        )
    )
    else -> Pair(
        MenuCourses("Arroz con verduras", "Pechuga de pollo a la plancha", "Fruta de temporada"),
        MenuCourses("Arroz con verduras", "Hamburguesa vegetal", "Fruta de temporada")
    )
}

/**
 * Emite una vibración háptica al disparar un gesto.
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
