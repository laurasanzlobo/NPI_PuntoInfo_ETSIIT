package com.example.npi_puntoinfo_etsiit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = UgrRed,
    onPrimary = Color.White,
    primaryContainer = UgrRedLight,
    onPrimaryContainer = UgrRedDark,
    secondary = EtsiitNavy,
    onSecondary = Color.White,
    tertiary = CampusTeal,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder
)

// Para mantener consistencia con la identidad visual corporativa ETSIIT (fondos claros),
// garantizamos contraste legible en cualquier modo del sistema
private val DarkColorScheme = LightColorScheme.copy()

@Composable
fun NPI_PuntoInfo_ETSIITTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}