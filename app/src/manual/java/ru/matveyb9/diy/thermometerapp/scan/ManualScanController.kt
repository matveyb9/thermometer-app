package ru.matveyb9.diy.thermometerapp.scan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.matveyb9.diy.thermometerapp.data.model.BaudConfig
import ru.matveyb9.diy.thermometerapp.data.model.ConnectionState
import ru.matveyb9.diy.thermometerapp.data.model.UsbDeviceInfo
import ru.matveyb9.diy.thermometerapp.ui.dashboard.ConnectPrompt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManualScanController @Inject constructor() : ScanController {

    override val isAutoScanSupported = false

    // Всегда null — диалог авто-обнаружения в ручном флейворе не показывается
    private val _connectPrompt = MutableStateFlow<ConnectPrompt?>(null)
    override val connectPrompt: StateFlow<ConnectPrompt?> = _connectPrompt.asStateFlow()

    // FIX #2: параметр connectToDevice удалён из сигнатуры
    override fun start(
        scope: CoroutineScope,
        getBaudConfig: () -> BaudConfig,
        getConnectionState: () -> ConnectionState,
        scanDevices: () -> List<UsbDeviceInfo>,
    ) = Unit  // no-op

    override fun confirm()               = Unit
    override fun dismiss()               = Unit
    override fun clearDismissedDevices() = Unit
    override fun onDeviceAttached()      = Unit
}
