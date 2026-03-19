package ru.matveyb9.diy.thermometerapp.ui.chart

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.shreyaspatil.capturable.capturable
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import ru.matveyb9.diy.thermometerapp.data.model.*
import ru.matveyb9.diy.thermometerapp.ui.common.TemperatureUnitToggle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(viewModel: ChartViewModel = hiltViewModel()) {
    val context         = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isRecording     by viewModel.isRecording.collectAsStateWithLifecycle()
    val history         by viewModel.history.collectAsStateWithLifecycle()
    val unit            by viewModel.unit.collectAsStateWithLifecycle()
    val isExporting     by viewModel.isExporting.collectAsStateWithLifecycle()

    val isConnected       = connectionState is ConnectionState.Connected
    val captureController = rememberCaptureController()
    var lastBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showExportSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.exportResult.collect { result ->
            if (result is ExportResult.Success) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = result.format.mimeType
                    putExtra(Intent.EXTRA_STREAM, result.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, "Экспорт ${result.format.label}")
                )
            }
        }
    }

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            runCatching { lastBitmap = captureController.captureAsync().await() }
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
        // Мини-температура + кнопка экспорта
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
                onClick = { showExportSheet = true },
                enabled = history.isNotEmpty() && !isExporting,
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.FileDownload, contentDescription = "Экспорт")
                }
            }
        }

        // График
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

// ── График через Canvas ───────────────────────────────────────────────────────

@Composable
private fun TemperatureChart(
    history: List<TemperatureRecord>,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    val lineColor  = MaterialTheme.colorScheme.primary
    val gridColor  = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface

    // Конвертируем все значения в нужные единицы
    val values = remember(history, unit) {
        history.map { it.temperatureCelsius.toDisplayTemp(unit) }
    }
    val times = remember(history) { history.map { it.elapsedSeconds } }

    val minVal = values.minOrNull() ?: 0f
    val maxVal = values.maxOrNull() ?: 1f
    val valRange = if (maxVal - minVal < 1f) 1f else maxVal - minVal

    val minTime = times.firstOrNull() ?: 0f
    val maxTime = times.lastOrNull() ?: 1f
    val timeRange = if (maxTime - minTime < 1f) 1f else maxTime - minTime

    Canvas(modifier = modifier.padding(start = 48.dp, end = 8.dp, top = 8.dp, bottom = 32.dp)) {
        val w = size.width
        val h = size.height

        // Сетка — 5 горизонтальных линий
        val gridPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(80, 128, 128, 128)
            strokeWidth = 1.dp.toPx()
        }
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
        }

        for (i in 0..4) {
            val y = h * i / 4f
            drawContext.canvas.nativeCanvas.drawLine(-44.dp.toPx(), y, w, y, gridPaint)
            val label = "%.1f".format(maxVal - (valRange * i / 4f))
            drawContext.canvas.nativeCanvas.drawText(label, -4.dp.toPx(), y + 4.dp.toPx(), textPaint)
        }

        // Ось X — временны́е метки
        val xLabelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        for (i in 0..4) {
            val x = w * i / 4f
            val timeSec = minTime + timeRange * i / 4f
            val label = if (timeSec < 60) "${timeSec.toInt()}s"
                        else "${(timeSec / 60).toInt()}m${(timeSec % 60).toInt()}s"
            drawContext.canvas.nativeCanvas.drawText(label, x, h + 20.dp.toPx(), xLabelPaint)
        }

        // Линия графика
        if (values.size < 2) return@Canvas
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = w * (times[index] - minTime) / timeRange
            val y = h * (1f - (value - minVal) / valRange)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path  = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx()),
        )

        // Последняя точка
        val lastX = w * (times.last() - minTime) / timeRange
        val lastY = h * (1f - (values.last() - minVal) / valRange)
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
    }
}

// ── Остальные компоненты ─────────────────────────────────────────────────────

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
            text       = text,
            fontSize   = 28.sp,
            fontWeight = FontWeight.Light,
            color      = if (isLoss) MaterialTheme.colorScheme.error
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
            Button(
                onClick  = onStart,
                enabled  = isConnected,
                modifier = Modifier.weight(1f),
            ) { Text("Старт") }
        } else {
            OutlinedButton(
                onClick  = onStop,
                modifier = Modifier.weight(1f),
            ) { Text("Стоп") }
        }
        OutlinedButton(
            onClick  = onClear,
            enabled  = hasHistory && !isRecording,
            modifier = Modifier.weight(1f),
        ) { Text("Сброс") }
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
                modifier = if (enabled)
                    Modifier.fillMaxWidth().clickable { onSelect(format) }
                else
                    Modifier.fillMaxWidth(),
                colors = if (!enabled)
                    ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.outline)
                else
                    ListItemDefaults.colors(),
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
