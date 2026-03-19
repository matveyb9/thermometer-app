package ru.matveyb9.diy.thermometerapp.scan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.matveyb9.diy.thermometerapp.data.model.BaudConfig
import ru.matveyb9.diy.thermometerapp.data.model.ConnectionState
import ru.matveyb9.diy.thermometerapp.data.model.UsbDeviceInfo
import ru.matveyb9.diy.thermometerapp.data.model.isActive
import ru.matveyb9.diy.thermometerapp.ui.dashboard.ConnectPrompt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoScanController @Inject constructor() : ScanController {

    companion object {
        private const val SCAN_INTERVAL_MS    = 3_000L
        private const val DISMISS_COOLDOWN_MS = 15_000L
    }

    override val isAutoScanSupported = true

    private val _connectPrompt = MutableStateFlow<ConnectPrompt?>(null)
    override val connectPrompt: StateFlow<ConnectPrompt?> = _connectPrompt.asStateFlow()

    private val dismissedUntil = mutableMapOf<Int, Long>()

    // FIX #2: параметр connectToDevice удалён из сигнатуры
    override fun start(
        scope: CoroutineScope,
        getBaudConfig: () -> BaudConfig,
        getConnectionState: () -> ConnectionState,
        scanDevices: () -> List<UsbDeviceInfo>,
    ) {
        scope.launch(Dispatchers.IO) {
            while (true) {
                if (!getConnectionState().isActive()) {
                    val candidate = scanDevices().firstOrNull { device ->
                        val suppressedUntil = dismissedUntil[device.device.deviceId] ?: 0L
                        System.currentTimeMillis() > suppressedUntil
                    }
                    if (candidate != null && _connectPrompt.value == null) {
                        _connectPrompt.value = ConnectPrompt(
                            device     = candidate,
                            baudConfig = getBaudConfig(),
                        )
                    }
                }
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    override fun confirm() {
        _connectPrompt.value = null
        // Фактическое подключение — в DashboardViewModel.confirmConnect()
    }

    override fun dismiss() {
        val prompt = _connectPrompt.value ?: return
        dismissedUntil[prompt.device.device.deviceId] =
            System.currentTimeMillis() + DISMISS_COOLDOWN_MS
        _connectPrompt.value = null
    }

    override fun clearDismissedDevices() {
        dismissedUntil.clear()
        _connectPrompt.value = null
    }

    override fun onDeviceAttached() {
        clearDismissedDevices()
    }
}
