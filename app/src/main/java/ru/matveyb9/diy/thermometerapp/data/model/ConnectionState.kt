package ru.matveyb9.diy.thermometerapp.data.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Scanning : ConnectionState
    data object RequestingPermission : ConnectionState

    data class DetectingBaudRate(
        val device: UsbDeviceInfo,
        val currentBaud: Int,
        val attempt: Int,
        val totalAttempts: Int,
    ) : ConnectionState

    data class Connected(
        val device: UsbDeviceInfo,
        val baudRate: Int,
        val temperature: Float?,
        val lastPacketAt: Long = 0L,
        val packetLoss: Boolean = false,
    ) : ConnectionState

    data class Error(val message: String) : ConnectionState
}

/** Утилита — используется в ScanController и DashboardViewModel */
fun ConnectionState.isActive(): Boolean =
    this is ConnectionState.Connected
        || this is ConnectionState.Scanning
        || this is ConnectionState.RequestingPermission
        || this is ConnectionState.DetectingBaudRate
