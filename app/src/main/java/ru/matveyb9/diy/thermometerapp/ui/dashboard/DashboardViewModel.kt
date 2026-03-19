package ru.matveyb9.diy.thermometerapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.matveyb9.diy.thermometerapp.data.AppPreferences
import ru.matveyb9.diy.thermometerapp.data.UsbThermometerRepository
import ru.matveyb9.diy.thermometerapp.data.model.*
import ru.matveyb9.diy.thermometerapp.scan.ScanController
import javax.inject.Inject

data class ConnectPrompt(
    val device: UsbDeviceInfo,
    val baudConfig: BaudConfig,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: UsbThermometerRepository,
    private val prefs: AppPreferences,
    private val scanController: ScanController,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Disconnected)

    val unit: StateFlow<TemperatureUnit>         = prefs.unit
    val connectPrompt: StateFlow<ConnectPrompt?> = scanController.connectPrompt

    /** Используется в UI для адаптации текста и скрытия авто-диалога */
    val isAutoScanSupported: Boolean = scanController.isAutoScanSupported

    private val _connectionMode   = MutableStateFlow(ConnectionMode.AUTO)
    private val _baudRateMode     = MutableStateFlow(BaudRateMode.AUTO)
    private val _manualBaudRate   = MutableStateFlow(BaudRate.BAUD_9600)
    private val _showDevicePicker = MutableStateFlow(false)
    private val _pickerDevices    = MutableStateFlow<List<UsbDeviceInfo>>(emptyList())
    private val _isPickerLoading  = MutableStateFlow(false)

    val connectionMode: StateFlow<ConnectionMode>     = _connectionMode.asStateFlow()
    val baudRateMode: StateFlow<BaudRateMode>         = _baudRateMode.asStateFlow()
    val manualBaudRate: StateFlow<BaudRate>           = _manualBaudRate.asStateFlow()
    val showDevicePicker: StateFlow<Boolean>          = _showDevicePicker.asStateFlow()
    val pickerDevices: StateFlow<List<UsbDeviceInfo>> = _pickerDevices.asStateFlow()
    val isPickerLoading: StateFlow<Boolean>           = _isPickerLoading.asStateFlow()

    init {
        // FIX #2: connectToDevice не передаётся — он был мёртвым параметром.
        // Авто-флейвор запускает бесконечный цикл сканирования.
        // Ручной флейвор: no-op.
        scanController.start(
            scope              = viewModelScope,
            getBaudConfig      = ::buildBaudConfig,
            getConnectionState = { connectionState.value },
            scanDevices        = { repository.scanDevices() },
        )
    }

    // ── Диалог авто-обнаружения ───────────────────────────────────────────────

    fun confirmConnect() {
        val device = scanController.connectPrompt.value?.device ?: return
        scanController.confirm()
        repository.connectToDevice(device, buildBaudConfig())
    }

    fun dismissConnectPrompt() = scanController.dismiss()

    // FIX #1: вызывается из MainActivity.onNewIntent() — единственный экземпляр ViewModel
    fun onDeviceAttached() = scanController.onDeviceAttached()

    // ── Кнопка «Выбрать устройство» (ручной флейвор / ручной режим) ──────────

    fun openDevicePicker() {
        _showDevicePicker.value = true
        refreshPickerDevices()
    }

    fun refreshPickerDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            _isPickerLoading.value = true
            _pickerDevices.value   = repository.scanDevices()
            _isPickerLoading.value = false
        }
    }

    fun connectToDevice(device: UsbDeviceInfo) {
        _showDevicePicker.value = false
        scanController.clearDismissedDevices()
        repository.connectToDevice(device, buildBaudConfig())
    }

    fun dismissDevicePicker() { _showDevicePicker.value = false }

    // ── Прочее ───────────────────────────────────────────────────────────────

    fun disconnect() {
        scanController.clearDismissedDevices()
        repository.disconnect()
    }

    fun setUnit(unit: TemperatureUnit)           = prefs.setUnit(unit)
    fun setConnectionMode(mode: ConnectionMode)  { _connectionMode.value = mode }
    fun setBaudRateMode(mode: BaudRateMode)      { _baudRateMode.value = mode }
    fun setManualBaudRate(rate: BaudRate)        { _manualBaudRate.value = rate }

    private fun buildBaudConfig(): BaudConfig = when (_baudRateMode.value) {
        BaudRateMode.AUTO   -> BaudConfig.Auto
        BaudRateMode.MANUAL -> BaudConfig.Manual(_manualBaudRate.value)
    }
}
