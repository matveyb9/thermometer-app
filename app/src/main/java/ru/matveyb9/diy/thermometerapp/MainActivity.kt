package ru.matveyb9.diy.thermometerapp

import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import ru.matveyb9.diy.thermometerapp.ui.MainScreen
import ru.matveyb9.diy.thermometerapp.ui.dashboard.DashboardViewModel
import ru.matveyb9.diy.thermometerapp.ui.theme.ThermometerAppTheme

// FIX #1: DashboardViewModel создаётся здесь (Activity scope).
// DashboardScreen получает тот же экземпляр через
//   hiltViewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
// Таким образом onDeviceAttached() и UI используют один и тот же объект.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    val dashboardViewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThermometerAppTheme {
                MainScreen()
            }
        }
    }

    // Кабель воткнули пока приложение уже открыто
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            dashboardViewModel.onDeviceAttached()
        }
    }
}
