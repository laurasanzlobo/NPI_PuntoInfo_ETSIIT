package com.example.npi_puntoinfo_etsiit.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : Screen("home", "Inicio", Icons.Default.Home)
    data object Schedule : Screen("schedule", "Horario", Icons.Default.DateRange)
    data object Navigation : Screen("navigation", "Guiado", Icons.Default.Navigation)
    data object Dining : Screen("dining", "Comedores", Icons.Default.Restaurant)
    data object Professors : Screen("professors", "Profesores", Icons.Default.School)
    data object Profile : Screen("profile", "Mi TUI", Icons.Default.AccountCircle)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Schedule,
    Screen.Navigation,
    Screen.Dining,
    Screen.Professors
)
