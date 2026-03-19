package ru.matveyb9.diy.thermometerapp.ui.chart

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomCartesianAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartCartesianAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import dev.shreyaspatil.capturable.capturable
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.matveyb9.diy.thermometerapp.data.model.*
import ru.matveyb9.diy.thermometerapp.ui.common.TemperatureUnitToggle // FIX #3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(viewModel: ChartViewModel = hiltViewModel()) {
    val context         = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isRecording     by viewModel.isRecording.collectAsStateWithLifecycle()
    val history         by viewModel.history.collectAsStateWithLifecycle()
    val unit            by viewModel.unit.collectAsStateWithLifecycle()
    val isExporting     by viewModel.isExporting.collectAsStateWithLifecycle()

    val isConnected     = connectionState is ConnectionState.Connected
    val captureController = rememberCaptureController()
    var lastBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showExportSheet by remember { mutableStateOf(false) }

    // Обработка результата экспорта
    LaunchedEffect(Unit) {
        viewModel.exportResult.collect { result ->
            if (result is ExportResult.Success) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = result.format.mimeType
                    putExtra(Intent.EXTRA_STREAM, result.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Экспорт ${result.format.label}"))
            }
        }
    }

    // Захват bitmap при каждом новом пакете данных
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            runCatching {
                lastBitmap = captureController.captureAsync().await()
            }
        }
    }

    if (showExportSheet) {
        ModalBottomSheet(onDismissRequest = { showExportSheet = false }) {
            ExportFormatPicker(
                hasHistory  = history.isNotEmpty(),
                hasChart    = lastBitmap != null,
                isExporting = isExporting,
                onSelect    = { format ->
                    showExportSheet = false
                    viewModel.export(format, lastBitmap)
                },
                onDismiss = { showExportSheet = false },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Мини-температура + кнопка экспорта ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniTemperatureHeader(
                state    = connectionState,
                unit     = unit,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick  = { showExportSheet = true },
                enabled  = history.isNotEmpty() && !isExporting,
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.FileDownload, contentDescription = "Экспорт")
                }
            }
        }

        // ── График ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .capturable(captureController),
            contentAlignment = Alignment.Center,
        ) {
            if (history.isEmpty()) {
                EmptyChartPlaceholder(isConnected = isConnected, isRecording = isRecording)
            } else {
                TemperatureChart(
                    history  = history,
                    unit     = unit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // FIX #3: импорт из ui/common
        TemperatureUnitToggle(selected = unit, onSelect = viewModel::setUnit)

        RecordingControls(
            isConnected = isConnected,
            isRecording = isRecording,
            hasHistory  = history.isNotEmpty(),
            onStart     = viewModel::startRecording,
            onStop      = viewModel::stopRecording,
            onClear     = viewModel::clearHistory,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiniTemperatureHeader(
    state: ConnectionState,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    val text = when (state) {
        is ConnectionState.Connected ->
            state.temperature?.toDisplayTemp(unit)
                ?.let { "%.2f${unit.symbol()}".format(it) } ?: "--.-°"
        else -> "--.-°"
    }
    val isLoss = state is ConnectionState.Connected && state.packetLoss

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text      = text,
            fontSize  = 28.sp,
            fontWeight = FontWeight.Light,
            color     = if (isLoss) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
        )
        if (isLoss) {
            Text(
                text  = "⚠ нет данных",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TemperatureChart(
    history: List<TemperatureRecord>,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(history, unit) {
        withContext(Dispatchers.Default) {
            modelProducer.runTransaction {
                lineSeries {
                    series(
                        x = history.map { it.elapsedSeconds },
                        y = history.map { it.temperatureCelsius.toDisplayTemp(unit) },
                    )
                }
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis  = rememberStartCartesianAxis(),
            bottomAxis = rememberBottomCartesianAxis(),
        ),
        modelProducer = modelProducer,
        scrollState   = rememberVicoScrollState(scrollEnabled = true),
        modifier      = modifier,
    )
}

@Composable
private fun EmptyChartPlaceholder(isConnected: Boolean, isRecording: Boolean) {
    Text(
        text  = when {
            !isConnected -> "Подключите Arduino\nчтобы начать запись"
            !isRecording -> "Нажмите «Старт» для записи"
            else         -> "Ожидание данных..."
        },
        style     = MaterialTheme.typography.bodyLarge,
        color     = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RecordingControls(
    isConnected: Boolean,
    isRecording: Boolean,
    hasHistory: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        if (!isRecording) {
            Button(onClick = onStart, enabled = isConnected, modifier = Modifier.weight(1f)) {
                Text("Старт")
            }
        } else {
            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                Text("Стоп")
            }
        }
        OutlinedButton(
            onClick  = onClear,
            enabled  = hasHistory && !isRecording,
            modifier = Modifier.weight(1f),
        ) {
            Text("Сброс")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFormatPicker(
    hasHistory: Boolean,
    hasChart: Boolean,
    isExporting: Boolean,
    onSelect: (ExportFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            text       = "Экспорт данных",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(vertical = 16.dp),
        )

        ExportFormat.entries.forEach { format ->
            val enabled = when (format) {
                ExportFormat.CSV -> hasHistory && !isExporting
                ExportFormat.PNG -> hasChart && !isExporting
                ExportFormat.PDF -> hasHistory && !isExporting
            }
            val description = when (format) {
                ExportFormat.CSV -> "Таблица: время, секунды, температура"
                ExportFormat.PNG -> "Снимок графика как изображение"
                ExportFormat.PDF -> "Документ: метаданные + снимок графика"
            }
            ListItem(
                headlineContent   = { Text(format.label) },
                supportingContent = {
                    Text(description, color = MaterialTheme.colorScheme.outline)
                },
                modifier = if (enabled) Modifier.fillMaxWidth().clickableItem { onSelect(format) }
                           else Modifier.fillMaxWidth(),
                colors   = if (!enabled) ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.outline)
                           else ListItemDefaults.colors(),
            )
            HorizontalDivider()
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick  = onDismiss,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Отмена") }
    }
}

private fun Modifier.clickableItem(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
