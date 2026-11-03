package com.example.npi_puntoinfo_etsiit.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.npi_puntoinfo_etsiit.R
import com.example.npi_puntoinfo_etsiit.data.UserProfile
import com.example.npi_puntoinfo_etsiit.data.UserSessionRepository
import com.example.npi_puntoinfo_etsiit.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay

/**
 * Pantalla de Mi Perfil sin scroll vertical:
 * - Si NO ha iniciado sesión: abre directamente la cámara en vivo con el recuadro de enfoque
 *   y el mensaje "Enfoca tu TUI o escanea el código QR de la TUI" para iniciar sesión al instante.
 * - Si HA iniciado sesión: muestra la ficha completa de la estudiante (Elena Morales Vega),
 *   expediente académico UGR, preferencias de navegación accesible y cierre de sesión.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit = {},
    onLoggedOut: () -> Unit = onBack
) {
    val context = LocalContext.current
    val profile = UserSessionRepository.getProfile(context)
    var isLoggingOut by remember { mutableStateOf(false) }

    // Función para iniciar sesión inmediatamente con el perfil oficial preconfigurado
    fun loginConPerfilOficial() {
        isLoggingOut = false
        val loggedInProfile = UserSessionRepository.loginWithOfficialProfile(
            context = context,
            isAccessibleRouting = profile.isAccessibleRouting
        )
        Toast.makeText(context, "¡TUI verificada! Sesión iniciada como ${loggedInProfile.name}", Toast.LENGTH_SHORT).show()
    }

    if (profile.isLoggedIn || isLoggingOut) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // 1. CABECERA DESGLOSADA: FOTO Y DATOS PERSONALES
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Foto real de la estudiante con borde rojo UGR
                    Image(
                        painter = painterResource(id = R.drawable.foto_estudiante),
                        contentDescription = "Foto de perfil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, UgrRed, CircleShape)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (profile.name.isNotBlank() && !profile.name.contains("José", ignoreCase = true))
                                profile.name
                            else
                                "Elena Morales Vega",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "DNI ${if (profile.dni.isNotBlank() && profile.dni != "75932717") (if (profile.dni.matches(Regex(""".*[A-Za-z]$"""))) profile.dni else "${profile.dni}R") else "48291054R"}",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = UgrRedLight
                        ) {
                            Text(
                                text = "ESTUDIANTE",
                                color = UgrRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 2. BLOQUE ACADÉMICO (ROJO UGR ELEGANTE, SIN MENCIÓN)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
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
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "EXPEDIENTE ACADÉMICO",
                            color = Color.White.copy(alpha = 0.80f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Grado en Ingeniería Informática",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = "4º Año Matriculado",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = "Grupo A",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. PREFERENCIAS DE ACCESIBILIDAD (EVALUACIÓN)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Preferencias de Navegación",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )

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
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CampusTeal.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (profile.isAccessibleRouting) Icons.AutoMirrored.Filled.Accessible else Icons.AutoMirrored.Filled.DirectionsWalk,
                                    contentDescription = null,
                                    tint = CampusTeal,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (profile.isAccessibleRouting) "Ruta Accesible (Ascensor)" else "Ruta Rápida (Escaleras)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (profile.isAccessibleRouting)
                                        "Prioriza ascensores para movilidad reducida"
                                    else
                                        "Usa escaleras y tramos directos entre plantas",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Switch(
                            checked = profile.isAccessibleRouting,
                            onCheckedChange = { isChecked ->
                                val updated = profile.copy(isAccessibleRouting = isChecked)
                                UserSessionRepository.saveProfile(context, updated)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CampusTeal
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ==========================================
            // 4. BOTÓN CERRAR SESIÓN
            // ==========================================
            OutlinedButton(
                onClick = {
                    isLoggingOut = true
                    onLoggedOut()
                    UserSessionRepository.logout(context)
                    Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = UgrRed)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrar Sesión", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        // ==========================================
        // VISTA SIN SESIÓN: CÁMARA EN DIRECTO ABIERTA PARA ESCANEAR TUI / QR
        // ==========================================
        LiveTuiQrCameraScanner(
            onScanSuccess = { loginConPerfilOficial() },
            onBack = onBack
        )
    }
}

/**
 * Vista de cámara abierta en directo a pantalla completa con visor para escanear
 * el código QR del reverso de la TUI.
 * Utiliza Google ML Kit Barcode Scanning configurado exclusivamente en formato QR.
 * NO inicia sesión hasta que se detecta un código QR real frente a la cámara.
 */
@Composable
private fun LiveTuiQrCameraScanner(
    onScanSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val currentOnScanSuccess by rememberUpdatedState(onScanSuccess)
    var isScanningTriggered by remember { mutableStateOf(false) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

    // Escáner de Google ML Kit especializado estrictamente en códigos QR
    val qrScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    // Escaneo continuo en vivo sobre la vista de cámara: busca únicamente códigos QR
    LaunchedEffect(hasCameraPermission, textureViewRef) {
        val tv = textureViewRef ?: return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect

        while (!isScanningTriggered) {
            delay(350L) // Analiza 3 fotogramas por segundo para máxima respuesta sin saturar CPU
            val bmp = try {
                if (tv.isAvailable) tv.getBitmap(480, 480) else null
            } catch (_: Exception) {
                null
            }
            if (bmp != null) {
                val inputImage = InputImage.fromBitmap(bmp, 0)
                qrScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val foundQr = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                        if (foundQr != null && !isScanningTriggered) {
                            isScanningTriggered = true
                            currentOnScanSuccess()
                        }
                    }
                    .addOnFailureListener {
                        // Si no hay QR en el frame, sigue esperando
                    }
            }
        }
    }

    // Animación láser vertical del visor de escaneo
    val infiniteTransition = rememberInfiniteTransition(label = "qr_laser_anim")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_line"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // 1. Previsualización de la cámara trasera en vivo
        if (hasCameraPermission) {
            Camera2LivePreview(
                modifier = Modifier.fillMaxSize(),
                onTextureViewReady = { textureViewRef = it }
            )
        }

        // 2. Superposición oscura con ventana central cuadrada y esquinas de enfoque de QR
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameSide = size.width * 0.72f
            val left = (size.width - frameSide) / 2f
            val top = (size.height - frameSide) * 0.48f
            val cornerLen = 32.dp.toPx()
            val strokeW = 4.dp.toPx()

            // Sombreado exterior alrededor del visor QR
            drawRect(
                color = Color.Black.copy(alpha = 0.52f),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, top)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.52f),
                topLeft = Offset(0f, top + frameSide),
                size = Size(size.width, size.height - (top + frameSide))
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.52f),
                topLeft = Offset(0f, top),
                size = Size(left, frameSide)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.52f),
                topLeft = Offset(left + frameSide, top),
                size = Size(size.width - (left + frameSide), frameSide)
            )

            // Marco fino cuadrado del área de escaneo
            drawRoundRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset(left, top),
                size = Size(frameSide, frameSide),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 4 esquinas destacadas del lector QR
            val cornerColor = Color.White
            // Arriba-Izquierda
            drawLine(cornerColor, Offset(left, top + cornerLen), Offset(left, top), strokeW, StrokeCap.Round)
            drawLine(cornerColor, Offset(left, top), Offset(left + cornerLen, top), strokeW, StrokeCap.Round)
            // Arriba-Derecha
            drawLine(cornerColor, Offset(left + frameSide - cornerLen, top), Offset(left + frameSide, top), strokeW, StrokeCap.Round)
            drawLine(cornerColor, Offset(left + frameSide, top), Offset(left + frameSide, top + cornerLen), strokeW, StrokeCap.Round)
            // Abajo-Izquierda
            drawLine(cornerColor, Offset(left, top + frameSide - cornerLen), Offset(left, top + frameSide), strokeW, StrokeCap.Round)
            drawLine(cornerColor, Offset(left, top + frameSide), Offset(left + cornerLen, top + frameSide), strokeW, StrokeCap.Round)
            // Abajo-Derecha
            drawLine(cornerColor, Offset(left + frameSide - cornerLen, top + frameSide), Offset(left + frameSide, top + frameSide), strokeW, StrokeCap.Round)
            drawLine(cornerColor, Offset(left + frameSide, top), Offset(left + frameSide, top + frameSide - cornerLen), strokeW, StrokeCap.Round)

            // Línea láser animada de escaneo
            val laserY = top + frameSide * laserProgress
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, UgrRed, Color(0xFFFF6B6B), UgrRed, Color.Transparent),
                    startX = left,
                    endX = left + frameSide
                ),
                start = Offset(left + 10.dp.toPx(), laserY),
                end = Offset(left + frameSide - 10.dp.toPx(), laserY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 3. Botón con flecha hacia la izquierda incrustada en la propia cámara para salir
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.60f))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // 4. Indicador superior: Mensaje explícito para escanear el QR del reverso de la TUI (ubicado debajo de la flecha, centrado)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 58.dp, start = 20.dp, end = 20.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.78f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Escanea el código QR de tu TUI",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Enfoca el código QR situado en el reverso de la tarjeta",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 5. Pie inferior con botón de respaldo para pruebas / simulación
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedButton(
                onClick = {
                    if (!isScanningTriggered) {
                        isScanningTriggered = true
                        currentOnScanSuccess()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Simular (este botón se borrará en un futuro)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Componente nativo Camera2 que muestra la vista previa de la cámara trasera directamente en Compose.
 */
@SuppressLint("MissingPermission")
@Composable
private fun Camera2LivePreview(
    modifier: Modifier = Modifier,
    onTextureViewReady: (TextureView) -> Unit = {}
) {
    val context = LocalContext.current

    DisposableEffect(context) {
        onDispose { }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var cameraDevice: CameraDevice? = null
                    private var captureSession: CameraCaptureSession? = null

                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        onTextureViewReady(this@apply)
                        openRearCamera(ctx, surface) { cam, session ->
                            cameraDevice = cam
                            captureSession = session
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        try {
                            captureSession?.close()
                            cameraDevice?.close()
                        } catch (_: Exception) {
                        }
                        captureSession = null
                        cameraDevice = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        }
    )
}

@SuppressLint("MissingPermission")
private fun openRearCamera(
    context: Context,
    surfaceTexture: SurfaceTexture,
    onOpened: (CameraDevice, CameraCaptureSession?) -> Unit
) {
    try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            val chars = manager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull() ?: return

        surfaceTexture.setDefaultBufferSize(1280, 720)
        val previewSurface = Surface(surfaceTexture)
        val mainHandler = Handler(Looper.getMainLooper())

        manager.openCamera(
            cameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurface)
                            set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                        }
                        @Suppress("DEPRECATION")
                        camera.createCaptureSession(
                            listOf(previewSurface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    try {
                                        session.setRepeatingRequest(requestBuilder.build(), null, mainHandler)
                                        onOpened(camera, session)
                                    } catch (_: Exception) {
                                        onOpened(camera, null)
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    onOpened(camera, null)
                                }
                            },
                            mainHandler
                        )
                    } catch (_: Exception) {
                        onOpened(camera, null)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    try {
                        camera.close()
                    } catch (_: Exception) {
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    try {
                        camera.close()
                    } catch (_: Exception) {
                    }
                }
            },
            mainHandler
        )
    } catch (_: Exception) {
    }
}
