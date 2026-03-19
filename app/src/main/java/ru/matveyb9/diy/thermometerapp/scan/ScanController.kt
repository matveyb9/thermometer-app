package ru.matveyb9.diy.thermometerapp.scan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import ru.matveyb9.diy.thermometerapp.data.model.BaudConfig
import ru.matveyb9.diy.thermometerapp.data.model.ConnectionState
import ru.matveyb9.diy.thermometerapp.data.model.UsbDeviceInfo
import ru.matveyb9.diy.thermometerapp.ui.dashboard.ConnectPrompt

interface ScanController {

    /** true = авто-флейвор (непрерывный поиск + диалог), false = ручной */
    val isAutoScanSupported: Boolean

    /** Диалог авто-обнаружения. В ручном флейворе всегда null. */
    val connectPrompt: StateFlow<ConnectPrompt?>

    /**
     * Запуск логики сканирования.
     * Авто-флейвор: бесконечный цикл в фоне.
     * Ручной флейвор: no-op.
     *
     * FIX #2: параметр connectToDevice удалён — он не использовался ни в одной реализации.
     * Фактическое подключение выполняется в DashboardViewModel.confirmConnect()
     * через repository.connectToDevice().
     */
    fun start(
        scope: CoroutineScope,
        getBaudConfig: () -> BaudConfig,
        getConnectionState: () -> ConnectionState,
        scanDevices: () -> List<UsbDeviceInfo>,
    )

    /** Пользователь нажал «Подключить» в диалоге авто-обнаружения */
    fun confirm()

    /** Пользователь нажал «Пропустить» — cooldown для этого устройства */
    fun dismiss()

    /** Сброс cooldown (при отключении или ручном выборе из списка) */
    fun clearDismissedDevices()

    /** USB_DEVICE_ATTACHED пришёл пока приложение уже открыто */
    fun onDeviceAttached()
}
