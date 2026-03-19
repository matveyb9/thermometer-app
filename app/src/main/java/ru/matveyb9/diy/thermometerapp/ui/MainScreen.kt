package ru.matveyb9.diy.thermometerapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import ru.matveyb9.diy.thermometerapp.ui.chart.ChartScreen
import ru.matveyb9.diy.thermometerapp.ui.dashboard.DashboardScreen
import ru.matveyb9.diy.thermometerapp.ui.navigation.AppDestination
import ru.matveyb9.diy.thermometerapp.ui.navigation.navItems

@Composable
fun MainScreen() {
    val navController   = rememberNavController()
    val backStackEntry  by navController.currentBackStackEntryAsState()
    val currentDest     = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEach { item ->
                    // FIX #4: hasRoute<T>() вместо сравнения строк
                    val selected = when (item.destination) {
                        is AppDestination.Dashboard -> currentDest?.hasRoute<AppDestination.Dashboard>() == true
                        is AppDestination.Chart     -> currentDest?.hasRoute<AppDestination.Chart>() == true
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick  = {
                            if (!selected) {
                                navController.navigate(item.destination) {
                                    popUpTo(AppDestination.Dashboard) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        },
                        icon  = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        // FIX #4: composable<T> вместо composable(route: String)
        NavHost(
            navController    = navController,
            startDestination = AppDestination.Dashboard,
            modifier         = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable<AppDestination.Dashboard> { DashboardScreen() }
            composable<AppDestination.Chart>     { ChartScreen() }
        }
    }
}
