package ru.matveyb9.diy.thermometerapp.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.matveyb9.diy.thermometerapp.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbThermometerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val ACTION_USB_PERMISSION      = "ru.matveyb9.diy.thermometerapp.USB_PERMISSION"
        private const val READ_TIMEOUT_MS            = 100
        private const val BAUD_DETECT_TIMEOUT_MS     = 3_000L
        private const val PACKET_LOSS_THRESHOLD_MS   = 5_000L
        private const val WATCHDOG_INTERVAL_MS       = 1_000L
        private const val BUFFER_MAX_SIZE            = 1_024
        private val TEMPERATURE_REGEX = Regex("""\[(-?\d+\.\d+)\]""")
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private var port: UsbSerialPort? = null
    private var ioScope: CoroutineScope? = null
    private var pendingDevice: UsbDeviceInfo? = null
    private var pendingBaudConfig: BaudConfig? = null

    // ── BroadcastReceivers ───────────────────────────────────────────────────

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val device     = pendingDevice
            val baudConfig = pendingBaudConfig
            pendingDevice     = null
            pendingBaudConfig = null
            if (granted && device != null && baudConfig != null) {
                launchConnect(device, baudConfig)
            } else {
                _state.value = ConnectionState.Error("Разрешение USB отклонено пользователем")
            }
        }
    }

    // FIX #8 — receivers зарегистрированы один раз в синглтоне (живут столько же, сколько процесс)
    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) disconnect()
        }
    }

    init {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Context.RECEIVER_NOT_EXPORTED else 0
        context.registerReceiver(permissionReceiver, IntentFilter(ACTION_USB_PERMISSION), flags)
        context.registerReceiver(detachReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED), flags)
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun scanDevices(): List<UsbDeviceInfo> =
        UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager)
            .map { driver ->
                UsbDeviceInfo(
                    device     = driver.device,
                    driverName = driver::class.simpleName ?: "Unknown",
                    portCount  = driver.ports.size,
                )
            }

    fun connectAuto(baudConfig: BaudConfig) {
        if (isAlreadyConnecting()) return
        _state.value = ConnectionState.Scanning
        val devices = scanDevices()
        if (devices.isEmpty()) {
            _state.value = ConnectionState.Error("Arduino не найден — проверь USB-кабель")
            return
        }
        requestPermissionAndConnect(devices.first(), baudConfig)
    }

    fun connectToDevice(device: UsbDeviceInfo, baudConfig: BaudConfig) {
        if (isAlreadyConnecting()) return
        requestPermissionAndConnect(device, baudConfig)
    }

    fun disconnect() {
        ioScope?.cancel()
        ioScope = null
        runCatching { port?.close() }
        port             = null
        pendingDevice    = null
        pendingBaudConfig = null
        _state.value = ConnectionState.Disconnected
    }

    // ── Permission ───────────────────────────────────────────────────────────

    private fun requestPermissionAndConnect(device: UsbDeviceInfo, baudConfig: BaudConfig) {
        if (!usbManager.hasPermission(device.device)) {
            pendingDevice     = device
            pendingBaudConfig = baudConfig
            _state.value      = ConnectionState.RequestingPermission
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            usbManager.requestPermission(device.device, pi)
        } else {
            launchConnect(device, baudConfig)
        }
    }

    private fun launchConnect(device: UsbDeviceInfo, baudConfig: BaudConfig) {
        ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ioScope!!.launch { openAndConnect(device, baudConfig) }
    }

    private suspend fun openAndConnect(device: UsbDeviceInfo, baudConfig: BaudConfig) {
        try {
            val driver = UsbSerialProber.getDefaultProber()
                .findAllDrivers(usbManager)
                .firstOrNull { it.device.deviceId == device.device.deviceId }
                ?: run { _state.value = ConnectionState.Error("Устройство исчезло до подключения"); return }

            val connection = usbManager.openDevice(driver.device)
                ?: run { _state.value = ConnectionState.Error("Не удалось открыть USB-соединение"); return }

            val newPort = driver.ports.first().also { it.open(connection) }

            val resolvedBaud: Int = when (baudConfig) {
                is BaudConfig.Auto -> {
                    detectBaudRate(newPort, device)
                        ?: run {
                            newPort.close()
                            _state.value = ConnectionState.Error(
                                "Не удалось определить скорость порта. Попробуй задать вручную.",
                            )
                            return
                        }
                }
                is BaudConfig.Manual -> {
                    newPort.setParameters(baudConfig.baudRate.value, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    baudConfig.baudRate.value
                }
            }

            port = newPort
            _state.value = ConnectionState.Connected(
                device      = device,
                baudRate    = resolvedBaud,
                temperature = null,
            )
            startReading(newPort, device, resolvedBaud)
        } catch (e: Exception) {
            _state.value = ConnectionState.Error("Ошибка подключения: ${e.message}")
        }
    }

    // ── Baud rate detection ──────────────────────────────────────────────────

    private suspend fun detectBaudRate(port: UsbSerialPort, device: UsbDeviceInfo): Int? {
        val candidates = BaudRate.AUTO_DETECT_ORDER
        val buffer = ByteArray(256)
        val sb = StringBuilder()

        candidates.forEachIndexed { index, baud ->
            _state.value = ConnectionState.DetectingBaudRate(
                device        = device,
                currentBaud   = baud.value,
                attempt       = index + 1,
                totalAttempts = candidates.size,
            )
            runCatching {
                port.setParameters(baud.value, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            }.onFailure { return@forEachIndexed }

            sb.clear()
            val deadline = System.currentTimeMillis() + BAUD_DETECT_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val len = runCatching { port.read(buffer, 100) }.getOrElse { break }
                if (len > 0) {
                    sb.append(String(buffer, 0, len, Charsets.UTF_8))
                    if (TEMPERATURE_REGEX.containsMatchIn(sb)) return baud.value
                }
            }
        }
        return null
    }

    // ── Reading loop ─────────────────────────────────────────────────────────

    private fun startReading(port: UsbSerialPort, device: UsbDeviceInfo, baudRate: Int) {
        val lastValidPacketMs = AtomicLong(System.currentTimeMillis())

        // Watchdog — следит за потерей пакетов
        ioScope!!.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                val current = _state.value as? ConnectionState.Connected ?: break
                val elapsed = System.currentTimeMillis() - lastValidPacketMs.get()
                val isLoss  = elapsed > PACKET_LOSS_THRESHOLD_MS
                if (current.packetLoss != isLoss) {
                    _state.value = current.copy(packetLoss = isLoss)
                }
            }
        }

        // Reader — основной цикл
        ioScope!!.launch {
            val buffer      = ByteArray(256)
            val accumulated = StringBuilder()

            while (isActive) {
                try {
                    val len = port.read(buffer, READ_TIMEOUT_MS)
                    if (len <= 0) continue

                    accumulated.append(String(buffer, 0, len, Charsets.UTF_8))

                    // Берём последний полный пакет (игнорируем устаревшие в буфере)
                    val lastMatch = TEMPERATURE_REGEX.findAll(accumulated).lastOrNull()
                    if (lastMatch != null) {
                        lastMatch.groupValues[1].toFloatOrNull()?.let { temp ->
                            val now     = System.currentTimeMillis()
                            lastValidPacketMs.set(now)
                            val current = _state.value
                            if (current is ConnectionState.Connected) {
                                _state.value = current.copy(
                                    temperature  = temp,
                                    lastPacketAt = now,
                                    packetLoss   = false,
                                )
                            }
                        }
                        accumulated.delete(0, lastMatch.range.last + 1)
                    }

                    // Защита от переполнения при отсутствии валидных данных
                    if (accumulated.length > BUFFER_MAX_SIZE) accumulated.clear()

                } catch (e: Exception) {
                    if (isActive) {
                        _state.value = ConnectionState.Error("Разрыв соединения: ${e.message}")
                    }
                    break
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun isAlreadyConnecting(): Boolean = _state.value.isActive()
}
