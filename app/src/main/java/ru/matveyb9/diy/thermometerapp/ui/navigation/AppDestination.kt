package ru.matveyb9.diy.thermometerapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// FIX #4: type-safe навигация — @Serializable объекты вместо строковых маршрутов
@Serializable
sealed interface AppDestination {
    @Serializable data object Dashboard : AppDestination
    @Serializable data object Chart     : AppDestination
}

/** Метаданные для NavigationBar */
data class NavItem(
    val destination: AppDestination,
    val label: String,
    val icon: ImageVector,
)

val navItems = listOf(
    NavItem(AppDestination.Dashboard, "Температура", Icons.Outlined.DeviceThermostat),
    NavItem(AppDestination.Chart,     "График",      Icons.Outlined.ShowChart),
)
