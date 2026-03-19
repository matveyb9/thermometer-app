package ru.matveyb9.diy.thermometerapp.ui.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.matveyb9.diy.thermometerapp.data.model.*
import ru.matveyb9.diy.thermometerapp.ui.common.TemperatureUnitToggle // FIX #3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    // FIX #1: viewModelStoreOwner = Activity, чтобы использовался тот же экземпляр,
    // что создаётся в MainActivity.dashboardViewModel (by viewModels()).
    // Без этого MainActivity и DashboardScreen имели бы два разных ViewModel-объекта,
    // и onDeviceAttached() никогда не доходил бы до UI.
    viewModel: DashboardViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
) {
    val state           by viewModel.connectionState.collectAsStateWithLifecycle()
    val unit            by viewModel.unit.collectAsStateWithLifecycle()
    val connectionMode  by viewModel.connectionMode.collectAsStateWithLifecycle()
    val baudRateMode    by viewModel.baudRateMode.collectAsStateWithLifecycle()
    val manualBaudRate  by viewModel.manualBaudRate.collectAsStateWithLifecycle()
    val connectPrompt   by viewModel.connectPrompt.collectAsStateWithLifecycle()
    val showPicker      by viewModel.showDevicePicker.collectAsStateWithLifecycle()
    val pickerDevices   by viewModel.pickerDevices.collectAsStateWithLifecycle()
    val isPickerLoading by viewModel.isPickerLoading.collectAsStateWithLifecycle()

    val isConnected = state is ConnectionState.Connected
    val isBusy      = state is ConnectionState.Scanning
            || state is ConnectionState.RequestingPermission
            || state is ConnectionState.DetectingBaudRate

    // ── Диалог авто-обнаружения (только авто-флейвор) ────────────────────────
    if (viewModel.isAutoScanSupported) {
        connectPrompt?.let { prompt ->
            ConnectPromptDialog(
                device    = prompt.device,
                onConfirm = viewModel::confirmConnect,
                onDismiss = viewModel::dismissConnectPrompt,
            )
        }
    }

    // ── BottomSheet ручного выбора ────────────────────────────────────────────
    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissDevicePicker,
            sheetMaxWidth    = 600.dp,
        ) {
            DevicePickerSheet(
                devices     = pickerDevices,
                isLoading   = isPickerLoading,
                onRefresh   = viewModel::refreshPickerDevices,
                onSelect    = viewModel::connectToDevice,
                onDismiss   = viewModel::dismissDevicePicker,
            )
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text     = "Arduino Thermometer",
                style    = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            TemperatureDisplay(state = state, unit = unit)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // FIX #3: импорт из ui/common
                TemperatureUnitToggle(selected = unit, onSelect = viewModel::setUnit)

                HorizontalDivider()

                if (!isConnected && !isBusy) {
                    SettingsSection(
                        connectionMode         = connectionMode,
                        baudRateMode           = baudRateMode,
                        manualBaudRate         = manualBaudRate,
                        onConnectionModeChange = viewModel::setConnectionMode,
                        onBaudRateModeChange   = viewModel::setBaudRateMode,
                        onManualBaudRateChange = viewModel::setManualBaudRate,
                    )
                }

                // FIX #5: передаём isAutoScan чтобы корректно отображать статус
                ConnectionStatusChip(state = state, isAutoScan = viewModel.isAutoScanSupported)

                when {
                    isConnected ->
                        OutlinedButton(onClick = viewModel::disconnect) { Text("Отключить") }

                    isBusy ->
                        OutlinedButton(onClick = {}, enabled = false) { Text("Подключение...") }

                    connectionMode == ConnectionMode.MANUAL ->
                        Button(onClick = viewModel::openDevicePicker) { Text("Выбрать устройство") }

                    viewModel.isAutoScanSupported ->
                        // Авто-флейвор: сканирование уже идёт в фоне — кнопка пассивная
                        OutlinedButton(onClick = {}, enabled = false) { Text("Поиск устройств...") }

                    else ->
                        // Ручной флейвор + AUTO mode
                        Button(onClick = viewModel::openDevicePicker) { Text("Подключить") }
                }
            }
        }
    }
}

// ─────────────────────────── Device Picker Sheet ─────────────────────────────

@Composable
private fun DevicePickerSheet(
    devices: List<UsbDeviceInfo>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSelect: (UsbDeviceInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text      = "Выбор устройства",
                style     = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier  = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Обновить список")
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider()

        if (!isLoading && devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text      = "Поддерживаемые устройства не найдены.\nПроверь USB-подключение и нажми «Обновить».",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
            ) {
                items(devices, key = { it.device.deviceId }) { device ->
                    DeviceListItem(device = device, onClick = { onSelect(device) })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick  = onDismiss,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp),
        ) { Text("Отмена") }
    }
}

@Composable
private fun DeviceListItem(device: UsbDeviceInfo, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector  = Icons.Outlined.Usb,
                contentDescription = null,
                tint         = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = {
            Text(text = device.displayName, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text  = device.manufacturerName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeviceInfoChip("PORT", device.portPath)
                    DeviceInfoChip("VID",  device.vendorId)
                    DeviceInfoChip("PID",  device.productId)
                }
            }
        },
        trailingContent = {
            Text(
                text  = device.driverName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        },
    )
}

@Composable
private fun DeviceInfoChip(label: String, value: String) {
    Row(
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────── Connect Prompt Dialog ───────────────────────────

@Composable
private fun ConnectPromptDialog(
    device: UsbDeviceInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon             = { Icon(Icons.Outlined.Usb, contentDescription = null) },
        title            = { Text("Обнаружено устройство") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(device.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(device.manufacturerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabeledValue("Порт", device.portPath)
                    LabeledValue("VID",  device.vendorId)
                    LabeledValue("PID",  device.productId)
                }
                HorizontalDivider()
                Text("Подключиться к этому устройству?", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton  = { Button(onClick = onConfirm) { Text("Подключить") } },
        dismissButton  = { OutlinedButton(onClick = onDismiss) { Text("Пропустить") } },
    )
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodySmall,  fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────── Settings Section ────────────────────────────────

@Composable
private fun SettingsSection(
    connectionMode: ConnectionMode,
    baudRateMode: BaudRateMode,
    manualBaudRate: BaudRate,
    onConnectionModeChange: (ConnectionMode) -> Unit,
    onBaudRateModeChange: (BaudRateMode) -> Unit,
    onManualBaudRateChange: (BaudRate) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Выбор устройства", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        SingleChoiceSegmentedButtonRow {
            ConnectionMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    shape    = SegmentedButtonDefaults.itemShape(index, ConnectionMode.entries.size),
                    selected = connectionMode == mode,
                    onClick  = { onConnectionModeChange(mode) },
                    label    = { Text(if (mode == ConnectionMode.AUTO) "Автоматически" else "Вручную") },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Text("Скорость порта", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        SingleChoiceSegmentedButtonRow {
            BaudRateMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    shape    = SegmentedButtonDefaults.itemShape(index, BaudRateMode.entries.size),
                    selected = baudRateMode == mode,
                    onClick  = { onBaudRateModeChange(mode) },
                    label    = { Text(if (mode == BaudRateMode.AUTO) "Авто" else "Вручную") },
                )
            }
        }

        if (baudRateMode == BaudRateMode.MANUAL) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BaudRate.entries) { baud ->
                    FilterChip(
                        selected = manualBaudRate == baud,
                        onClick  = { onManualBaudRateChange(baud) },
                        label    = { Text("${baud.value}") },
                    )
                }
            }
        }
    }
}

// ─────────────────────────── Temperature Display ─────────────────────────────

@Composable
private fun TemperatureDisplay(state: ConnectionState, unit: TemperatureUnit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val s = state) {
            is ConnectionState.Connected -> {
                val temp = s.temperature?.toDisplayTemp(unit)
                Text(
                    text       = if (temp != null) "%.2f${unit.symbol()}".format(temp) else "--.-°",
                    fontSize   = 72.sp,
                    fontWeight = FontWeight.Thin,
                    color      = if (temp == null) MaterialTheme.colorScheme.outline
                                 else MaterialTheme.colorScheme.onSurface,
                )
                if (s.packetLoss) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = "⚠ Нет данных более 5 секунд",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            is ConnectionState.Error -> {
                Text("--.-°", fontSize = 72.sp, fontWeight = FontWeight.Thin, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            is ConnectionState.DetectingBaudRate -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(20.dp))
                Text("Определение скорости порта...", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text("${s.currentBaud} bps (${s.attempt}/${s.totalAttempts})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
            is ConnectionState.Scanning,
            is ConnectionState.RequestingPermission -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(20.dp))
                Text(
                    text  = when (s) {
                        is ConnectionState.RequestingPermission -> "Запрос разрешения USB..."
                        else -> "Поиск устройств..."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> {
                Text("--.-°", fontSize = 72.sp, fontWeight = FontWeight.Thin, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ─────────────────────────── Status Chip ─────────────────────────────────────

@Composable
private fun ConnectionStatusChip(
    state: ConnectionState,
    isAutoScan: Boolean,  // FIX #5
) {
    val (label, color) = when (state) {
        is ConnectionState.Connected            -> "Подключено"          to Color(0xFF4CAF50)
        is ConnectionState.Scanning             -> "Сканирование"        to Color(0xFFFFA726)
        is ConnectionState.DetectingBaudRate    -> "Определение baud"    to Color(0xFFFFA726)
        is ConnectionState.RequestingPermission -> "Ожидание разрешения" to Color(0xFFFFA726)
        is ConnectionState.Error                -> "Ошибка"              to MaterialTheme.colorScheme.error
        // FIX #5: в ручном флейворе нет никакого "поиска" — показываем "Не подключено"
        is ConnectionState.Disconnected         ->
            (if (isAutoScan) "Поиск устройств..." else "Не подключено") to MaterialTheme.colorScheme.outline
    }
    SuggestionChip(
        onClick = {},
        label   = { Text(label) },
        icon    = { Box(Modifier.size(8.dp).background(color, CircleShape)) },
    )
}
