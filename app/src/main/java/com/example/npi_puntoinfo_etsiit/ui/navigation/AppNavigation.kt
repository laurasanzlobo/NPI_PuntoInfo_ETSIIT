package com.example.npi_puntoinfo_etsiit.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.npi_puntoinfo_etsiit.ui.screens.*
import com.example.npi_puntoinfo_etsiit.ui.theme.SurfaceWhite
import com.example.npi_puntoinfo_etsiit.ui.theme.TextPrimary
import com.example.npi_puntoinfo_etsiit.ui.theme.UgrRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Título superior dinámico según la pantalla activa
    val currentTitle = when (currentRoute) {
        Screen.Home.route -> "Punto Info ETSIIT"
        Screen.Schedule.route -> "Mi Horario"
        Screen.Navigation.route -> "Navegación ETSIIT"
        Screen.Dining.route -> "Comedores Universitarios"
        Screen.Professors.route -> "Directorio de Profesores"
        Screen.Profile.route -> "Tarjeta Universitaria (TUI)"
        else -> "ETSIIT UGR"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                actions = {
                    if (currentRoute != Screen.Profile.route) {
                        IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Perfil",
                                tint = UgrRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceWhite
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceWhite,
                tonalElevation = NavigationBarDefaults.Elevation
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = UgrRed,
                            selectedTextColor = UgrRed,
                            indicatorColor = UgrRed.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable(Screen.Schedule.route) {
                ScheduleScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable(Screen.Navigation.route) {
                NavigationScreen()
            }
            composable(Screen.Dining.route) {
                DiningScreen()
            }
            composable(Screen.Professors.route) {
                ProfessorsScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen()
            }
        }
    }
}
