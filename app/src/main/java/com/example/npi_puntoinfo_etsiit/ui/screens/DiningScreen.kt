package com.example.npi_puntoinfo_etsiit.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.npi_puntoinfo_etsiit.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npi_puntoinfo_etsiit.data.UserSessionRepository
import com.example.npi_puntoinfo_etsiit.data.DiningPaymentAuthorizer
import com.example.npi_puntoinfo_etsiit.data.DiningReservationPolicy
import com.example.npi_puntoinfo_etsiit.ui.components.GestureBadge
import com.example.npi_puntoinfo_etsiit.ui.components.GestureType
import com.example.npi_puntoinfo_etsiit.sensors.AccelerometerManager
import com.example.npi_puntoinfo_etsiit.sensors.CampusLocationManager
import com.example.npi_puntoinfo_etsiit.sensors.DeviceLocationProvider
import com.example.npi_puntoinfo_etsiit.sensors.GpsLocationReader
import com.example.npi_puntoinfo_etsiit.sensors.GpsLocationState
import com.example.npi_puntoinfo_etsiit.sensors.LocationPermission
import com.example.npi_puntoinfo_etsiit.sensors.OutdoorDistances
import com.example.npi_puntoinfo_etsiit.sensors.DeviceAuthenticator
import com.example.npi_puntoinfo_etsiit.sensors.DeviceAuthenticationResult
import com.example.npi_puntoinfo_etsiit.sensors.rememberDeviceAuthenticator
import com.example.npi_puntoinfo_etsiit.ui.navigation.Screen
import com.example.npi_puntoinfo_etsiit.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class DiningMenuType(val labelEs: String, val labelEn: String) {
    TRADITIONAL("Tradicional", "Traditional"),
    VEGETARIAN("Vegetariano", "Vegetarian");

    fun getLabel(isEnglish: Boolean): String = if (isEnglish) labelEn else labelEs
    val label: String get() = labelEs
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

enum class PaymentMethod(val titleEs: String, val titleEn: String, val icon: ImageVector) {
    BIZUM("Bizum", "Bizum", Icons.Default.PhoneAndroid),
    CARD("Tarjeta Bancaria", "Credit Card", Icons.Default.CreditCard);

    fun getTitle(isEnglish: Boolean): String = if (isEnglish) titleEn else titleEs
    val title: String get() = titleEs
}

@Composable
fun DiningScreen(
    onNavigate: (String) -> Unit = {},
    isCurrentClassDialogVisible: Boolean = false,
    navKey: Int = 0,
    cancelPaymentKey: Int = 0,
    onPaymentModeChanged: (Boolean) -> Unit = {},
    paymentAuthenticator: DeviceAuthenticator? = null,
    currentDate: () -> LocalDate = LocalDate::now
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

    val dateProvider by rememberUpdatedState(currentDate)
    var today by remember { mutableStateOf(dateProvider()) }
    LaunchedEffect(Unit) {
        while (true) {
            today = dateProvider()
            delay(30_000L)
        }
    }

    val isEnglish = profile.language == "en"
    val menuList = remember(today, isEnglish) {
        DiningReservationPolicy.menuDates(today)
            .map { date ->
                val locale = if (isEnglish) Locale.ENGLISH else Locale.forLanguageTag("es-ES")
                val weekday = date.dayOfWeek
                    .getDisplayName(TextStyle.FULL, locale)
                    .replaceFirstChar { it.uppercaseChar() }
                val title = when (date) {
                    today -> if (isEnglish) "Today" else "Hoy"
                    today.plusDays(1) -> if (isEnglish) "Tomorrow" else "Mañana"
                    else -> weekday
                }
                val dateLabel = "${date.dayOfMonth}/${date.monthValue}"
                val label = "$weekday · $dateLabel"
                val (trad, veg) = sampleDishesFor(weekday = date.dayOfWeek, isEnglish = isEnglish)
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
    var selectedMenuType by rememberSaveable { mutableStateOf(DiningMenuType.TRADITIONAL) }

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

    // Solo se guarda el estado de la maqueta; no se solicitan datos bancarios reales.
    var pendingOrderDateKey by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingOrderDate = pendingOrderDateKey?.let(LocalDate::parse)
    var orderMenuType by rememberSaveable { mutableStateOf(DiningMenuType.TRADITIONAL) }
    var selectedPaymentMethod by rememberSaveable { mutableStateOf(PaymentMethod.BIZUM) }
    var isPaymentProcessing by remember { mutableStateOf(false) }
    var paymentError by remember { mutableStateOf<String?>(null) }
    var paymentSuccessDialog by remember { mutableStateOf<OrderSuccessInfo?>(null) }
    val systemAuthenticator = rememberDeviceAuthenticator()
    val authorizer = remember(paymentAuthenticator, systemAuthenticator) {
        DiningPaymentAuthorizer(paymentAuthenticator ?: systemAuthenticator)
    }

    fun cancelPayment() {
        authorizer.cancel()
        isPaymentProcessing = false
        paymentError = null
        pendingOrderDateKey = null
    }

    fun reserveMenu(menu: DayDiningMenu) {
        if (!DiningReservationPolicy.canReserve(menu.date, dateProvider()) ||
            ordersPlaced.containsKey(menu.date.toString()) || isPaymentProcessing) return
        orderMenuType = selectedMenuType
        paymentError = null
        pendingOrderDateKey = menu.date.toString()
    }

    val currentReserveMenu by rememberUpdatedState(::reserveMenu)
    DisposableEffect(authorizer) {
        onDispose { authorizer.cancel() }
    }

    LaunchedEffect(today) {
        if (pendingOrderDate != null && !DiningReservationPolicy.canReserve(pendingOrderDate, today)) cancelPayment()
    }

    // Gestos del acelerómetro
    DisposableEffect(context, isCurrentClassDialogVisible, pagerState, today, isLoggedIn,
        pendingOrderDateKey, paymentSuccessDialog) {
        val accelerometerManager = AccelerometerManager(context)
        if (!isCurrentClassDialogVisible && pendingOrderDateKey == null && paymentSuccessDialog == null) {
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
                            DiningReservationPolicy.canReserve(selected.date, dateProvider()) &&
                            !isOrdered &&
                            pendingOrderDate == null &&
                            paymentSuccessDialog == null
                        ) {
                            triggerVibration(context, durationMs = 80)
                            currentReserveMenu(selected)
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

    val activePendingMenu = pendingOrderDate?.let { date ->
        if (DiningReservationPolicy.canReserve(date, today)) menuList.find { it.date == date } else null
    }

    val isPaymentActive = activePendingMenu != null
    LaunchedEffect(isPaymentActive) {
        onPaymentModeChanged(isPaymentActive)
    }

    LaunchedEffect(cancelPaymentKey) {
        if (cancelPaymentKey > 0) {
            cancelPayment()
        }
    }

    if (activePendingMenu != null) {
        BackHandler { cancelPayment() }

        // ==========================================
        // PÁGINA COMPLETA DE PAGO COMEDOR
        // ==========================================
        Surface(
            modifier = Modifier.fillMaxSize().testTag("dining-payment-screen"),
            color = BackgroundLight
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                    Text(
                        "Pasarela de pago",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = UgrRed
                    )
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
                                        text = stringResource(R.string.dining_order_summary),
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
                                        text = DiningReservationPolicy.PRICE_LABEL,
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
                                Text(stringResource(R.string.dining_day), color = TextSecondary, fontSize = 13.sp)
                                Text(activePendingMenu.dayLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                            }

                            Spacer(Modifier.height(4.dp))

                            // Selector de tipo de menú
                            Text(
                                text = stringResource(R.string.dining_choose_menu_type),
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
                                        onClick = { if (!isPaymentProcessing) orderMenuType = type },
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
                                                text = stringResource(R.string.dining_menu_label, type.getLabel(isEnglish)),
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

                    // Métodos visuales de prueba: sin formulario de datos bancarios.
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
                                text = stringResource(R.string.dining_payment_method),
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
                                        onClick = { if (!isPaymentProcessing) selectedPaymentMethod = method },
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
                                                text = method.getTitle(isEnglish),
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) UgrRed else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                        }
                    }

                    Text(
                        stringResource(R.string.dining_payment_proceed_question),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        stringResource(R.string.dining_payment_proceed_desc, DiningReservationPolicy.PRICE_LABEL),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    paymentError?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("dining-payment-error"))
                    }

                    // El único resultado que permite reservar es una autenticación completada.
                    Button(
                        onClick = {
                            if (!DiningReservationPolicy.canReserve(activePendingMenu.date, dateProvider())) {
                                today = dateProvider()
                                cancelPayment()
                                return@Button
                            }
                            isPaymentProcessing = true
                            paymentError = null
                            val confirmedMenu = activePendingMenu
                            val confirmedType = orderMenuType
                            authorizer.confirm { result ->
                                isPaymentProcessing = false
                                when (result) {
                                    DeviceAuthenticationResult.Authenticated -> {
                                        val confirmationDate = dateProvider()
                                        if (!DiningReservationPolicy.canReserve(confirmedMenu.date, confirmationDate)) {
                                            today = confirmationDate
                                            cancelPayment()
                                            val expiredMsg = if (isEnglish) "The deadline to reserve this menu has ended." else "El plazo para reservar este menú ha terminado."
                                            Toast.makeText(context, expiredMsg, Toast.LENGTH_LONG).show()
                                            return@confirm
                                        }
                                        // Solo estado local de frontend: no se llama a ninguna API de pago/reserva.
                                        val dateKey = confirmedMenu.date.toString()
                                        ordersPlaced[dateKey] = confirmedType.getLabel(isEnglish)
                                        orderPreferences.edit().putString("ordered_date_$dateKey", confirmedType.getLabel(isEnglish)).apply()
                                        selectedMenuType = confirmedType
                                        triggerVibration(context, durationMs = 150)
                                        paymentSuccessDialog = OrderSuccessInfo(confirmedMenu.dayLabel, confirmedType.getLabel(isEnglish))
                                        pendingOrderDateKey = null
                                    }
                                    DeviceAuthenticationResult.Cancelled ->
                                        paymentError = if (isEnglish) "Authentication cancelled. Menu has not been reserved." else "Autenticación cancelada. No se ha reservado el menú."
                                    is DeviceAuthenticationResult.Error -> paymentError = result.message
                                }
                            }
                        },
                        enabled = !isPaymentProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = UgrRed),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("dining-confirm-payment")
                    ) {
                        if (isPaymentProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.dining_waiting_auth), color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.dining_proceed_payment_btn), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // BOTÓN CANCELAR
                    OutlinedButton(
                        onClick = { cancelPayment() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
                    ) {
                        Text(stringResource(R.string.cancel), color = TextSecondary, fontWeight = FontWeight.SemiBold)
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
                        text = if (currentMenu.date == today || currentMenu.date == today.plusDays(1))
                            "${currentMenu.dayName} · ${currentMenu.dayLabel}" else currentMenu.dayLabel,
                        modifier = Modifier.testTag("dining-current-day"),
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
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("dining-menu-pager")
        ) { page ->
            val menu = menuList[page]
            val dayOrderedType = ordersPlaced[menu.date.toString()]
            val isThisDayOrdered = !dayOrderedType.isNullOrBlank()
            val isDisplayedMenuReserved = dayOrderedType == selectedMenuType.label
            val hasService = DiningReservationPolicy.hasService(menu.date)
            val canReserve = DiningReservationPolicy.canReserve(menu.date, today)

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(365.dp)
                        .padding(horizontal = 2.dp)
                        .testTag("dining-menu-${menu.date}")
                        .semantics { stateDescription = when {
                            isDisplayedMenuReserved -> "Reservado"
                            !hasService -> "Sin servicio"
                            else -> "Sin reservar"
                        } },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDisplayedMenuReserved) Color(0xFFDCFCE7) else SurfaceWhite
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, if (isDisplayedMenuReserved) CampusGreen else SurfaceBorder
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Cabecera: Selector de tipo de menú y tarifa de comedor.
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
                                                text = type.getLabel(isEnglish),
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
                                    text = DiningReservationPolicy.PRICE_LABEL,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = UgrRed,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                )
                            }
                        }

                        // Platos agrupados con separación equilibrada y legible
                        val courses = if (selectedMenuType == DiningMenuType.TRADITIONAL) menu.traditional else menu.vegetarian
                        if (hasService) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DishVisualRow(
                                    dishNumber = "1",
                                    category = if (isEnglish) "First Course" else "Primer Plato",
                                    dishName = courses.firstCourse,
                                    badgeColor = EtsiitBlue
                                )
                                HorizontalDivider(color = SurfaceBorder.copy(alpha = 0.5f))
                                DishVisualRow(
                                    dishNumber = "2",
                                    category = if (isEnglish) "Second Course" else "Segundo Plato",
                                    dishName = courses.secondCourse,
                                    badgeColor = WarningAmber
                                )
                                HorizontalDivider(color = SurfaceBorder.copy(alpha = 0.5f))
                                DishVisualRow(
                                    dishNumber = "3",
                                    category = if (isEnglish) "Dessert" else "Postre",
                                    dishName = courses.dessert,
                                    badgeColor = CampusGreen
                                )
                            }
                        } else {
                            Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.EventBusy, null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                                Text(if (isEnglish) "No dining service for this day" else "No hay servicio de comedor este día", fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center, color = TextPrimary)
                                Text(if (isEnglish) "The dining hall is open Monday through Friday." else "El comedor abre de lunes a viernes.", color = TextSecondary)
                            }
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
                                            text = if (distanceFormatted != null) {
                                                if (isEnglish) "Nearest dining hall (at $distanceFormatted):" else "Comedor más cercano (a $distanceFormatted):"
                                            } else {
                                                if (isEnglish) "Nearest dining hall:" else "Comedor más cercano:"
                                            },
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = nearestDiningHall.getName(isEnglish), // SOLO el nombre (sin campus)
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

                // Los cuatro próximos días laborables admiten reservas; hoy conserva las anteriores.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isThisDayOrdered) Color(0xFFDCFCE7) else SurfaceWhite
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, if (isThisDayOrdered) CampusGreen else SurfaceBorder
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                if (isThisDayOrdered) Icons.Default.CheckCircle else Icons.Default.Restaurant,
                                null, tint = if (isThisDayOrdered) CampusGreen else UgrRed
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (isThisDayOrdered) {
                                        if (isEnglish) "Menu $dayOrderedType reserved" else "Menú $dayOrderedType reservado"
                                    } else {
                                        if (isEnglish) "Order your takeaway menu" else "Reserva tu menú para llevar"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = if (isThisDayOrdered) Color(0xFF065F46) else TextPrimary
                                )
                                Text(
                                    when {
                                        isThisDayOrdered -> if (isEnglish) "Reservation confirmed · ${DiningReservationPolicy.PRICE_LABEL}" else "Reserva confirmada · ${DiningReservationPolicy.PRICE_LABEL}"
                                        !hasService -> if (isEnglish) "No dining service" else "Sin servicio de comedor"
                                        canReserve -> if (isEnglish) "For ${menu.dayLabel} · ${DiningReservationPolicy.PRICE_LABEL}" else "Para el ${menu.dayLabel} · ${DiningReservationPolicy.PRICE_LABEL}"
                                        else -> DiningReservationPolicy.PRICE_LABEL
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = TextSecondary
                                )
                            }

                            if (canReserve && !isThisDayOrdered) {
                                GestureBadge(
                                    type = GestureType.ACTION_TRIGGER,
                                    size = 28.dp,
                                    iconSize = 16.dp,
                                    onClick = { reserveMenu(menu) }
                                )
                            }
                        }
                        if (canReserve || isThisDayOrdered) {
                            Button(
                                onClick = { reserveMenu(menu) },
                                enabled = canReserve && !isThisDayOrdered,
                                modifier = Modifier.fillMaxWidth().testTag("dining-reserve-${menu.date}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = UgrRed,
                                    disabledContainerColor = if (isThisDayOrdered) CampusGreen else SurfaceBorder,
                                    disabledContentColor = if (isThisDayOrdered) Color.White else TextSecondary
                                )
                            ) {
                                Text(when {
                                    isThisDayOrdered -> if (isEnglish) "Reserved" else "Reservado"
                                    !hasService -> if (isEnglish) "No service" else "Sin servicio"
                                    else -> if (isEnglish) "Reserve" else "Reservar"
                                }, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!isThisDayOrdered && hasService && !canReserve) {
                            Text(
                                if (isEnglish) "Reservations are not allowed for today. Swipe to reserve one of the next four working days." else "No se admiten reservas para hoy. Desliza para reservar uno de los cuatro próximos días laborables.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                                color = TextSecondary
                            )
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
                    text = if (isEnglish) "Reserved" else "Reservado",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = if (isEnglish)
                        "${info.menuTypeName} Menu · ${info.dayLabel}\n\nReservation confirmed · ${DiningReservationPolicy.PRICE_LABEL}"
                    else
                        "Menú ${info.menuTypeName} · ${info.dayLabel}\n\nReserva confirmada · ${DiningReservationPolicy.PRICE_LABEL}",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = { paymentSuccessDialog = null }) {
                    Text(if (isEnglish) "OK" else "Aceptar", color = CampusGreen, fontWeight = FontWeight.Bold)
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
private fun sampleDishesFor(weekday: DayOfWeek, isEnglish: Boolean = false): Pair<MenuCourses, MenuCourses> = when (weekday) {
    DayOfWeek.MONDAY -> Pair(
        MenuCourses(
            firstCourse = if (isEnglish) "Castilian lentils with vegetables" else "Lentejas castellanas con verduras",
            secondCourse = if (isEnglish) "Grilled hake fillet" else "Filete de merluza a la plancha",
            dessert = if (isEnglish) "Seasonal fruit" else "Fruta de temporada"
        ),
        MenuCourses(
            firstCourse = if (isEnglish) "Castilian lentils with vegetables" else "Lentejas castellanas con verduras",
            secondCourse = if (isEnglish) "Spinach veggie burger" else "Hamburguesa vegetal de espinacas",
            dessert = if (isEnglish) "Seasonal fruit" else "Fruta de temporada"
        )
    )
    DayOfWeek.TUESDAY -> Pair(
        MenuCourses(
            firstCourse = if (isEnglish) "Traditional mixed paella" else "Paella mixta tradicional",
            secondCourse = if (isEnglish) "Garlic pork loin" else "Lomo de cerdo al ajillo",
            dessert = if (isEnglish) "Homemade custard" else "Natillas caseras"
        ),
        MenuCourses(
            firstCourse = if (isEnglish) "Zucchini cream with croutons" else "Crema de calabacín con picatostes",
            secondCourse = if (isEnglish) "French omelette with mushrooms" else "Tortilla francesa con champiñones",
            dessert = if (isEnglish) "Natural yogurt" else "Yogur natural"
        )
    )
    DayOfWeek.WEDNESDAY -> Pair(
        MenuCourses(
            firstCourse = if (isEnglish) "Bolognese macaroni" else "Macarrones a la boloñesa",
            secondCourse = if (isEnglish) "Baked salmon with potatoes" else "Salmón al horno con patatas",
            dessert = if (isEnglish) "Egg flan" else "Flan de huevo"
        ),
        MenuCourses(
            firstCourse = if (isEnglish) "Cordoban salmorejo with crouton and egg" else "Salmorejo cordobés con picatoste y huevo",
            secondCourse = if (isEnglish) "Stuffed eggplants with textured soy" else "Berenjenas rellenas de soja texturizada",
            dessert = if (isEnglish) "Egg flan" else "Flan de huevo"
        )
    )
    DayOfWeek.THURSDAY -> Pair(
        MenuCourses(
            firstCourse = if (isEnglish) "Chickpea stew with spinach" else "Potaje de garbanzos con espinacas",
            secondCourse = if (isEnglish) "Breaded chicken escalope" else "Escalope de pollo empanado",
            dessert = if (isEnglish) "Fruit jelly" else "Gelatina de frutas"
        ),
        MenuCourses(
            firstCourse = if (isEnglish) "Chickpea stew with spinach" else "Potaje de garbanzos con espinacas",
            secondCourse = if (isEnglish) "Scrambled mushrooms and wild asparagus" else "Revuelto de setas y trigueros",
            dessert = if (isEnglish) "Fruit jelly" else "Gelatina de frutas"
        )
    )
    DayOfWeek.FRIDAY -> Pair(
        MenuCourses(
            firstCourse = if (isEnglish) "Three-delicacy rice with omelette" else "Arroz tres delicias con tortilla",
            secondCourse = if (isEnglish) "Roman-style hake with salad" else "Merluza a la romana con ensalada",
            dessert = if (isEnglish) "Yogurt mousse" else "Mousse de yogur"
        ),
        MenuCourses(
            firstCourse = if (isEnglish) "Vegetable minestrone soup" else "Sopa minestrone de verduras",
            secondCourse = if (isEnglish) "Crispy tofu Milanesa" else "Milanesa de tofu crujiente",
            dessert = if (isEnglish) "Yogurt mousse" else "Mousse de yogur"
        )
    )
    else -> Pair(
        MenuCourses(
            if (isEnglish) "Rice with vegetables" else "Arroz con verduras",
            if (isEnglish) "Grilled chicken breast" else "Pechuga de pollo a la plancha",
            if (isEnglish) "Seasonal fruit" else "Fruta de temporada"
        ),
        MenuCourses(
            if (isEnglish) "Rice with vegetables" else "Arroz con verduras",
            if (isEnglish) "Veggie burger" else "Hamburguesa vegetal",
            if (isEnglish) "Seasonal fruit" else "Fruta de temporada"
        )
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
