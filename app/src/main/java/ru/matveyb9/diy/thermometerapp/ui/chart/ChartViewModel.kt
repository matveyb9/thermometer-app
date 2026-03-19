package ru.matveyb9.diy.thermometerapp.ui.chart

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.matveyb9.diy.thermometerapp.data.AppPreferences
import ru.matveyb9.diy.thermometerapp.data.ExportRepository
import ru.matveyb9.diy.thermometerapp.data.TemperatureHistoryRepository
import ru.matveyb9.diy.thermometerapp.data.UsbThermometerRepository
import ru.matveyb9.diy.thermometerapp.data.model.*
import javax.inject.Inject

@HiltViewModel
class ChartViewModel @Inject constructor(
    private val historyRepository: TemperatureHistoryRepository,
    private val usbRepository: UsbThermometerRepository,
    private val exportRepository: ExportRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = usbRepository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Disconnected)

    val isRecording: StateFlow<Boolean> = historyRepository.isRecording
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val history: StateFlow<List<TemperatureRecord>> = historyRepository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unit: StateFlow<TemperatureUnit> = prefs.unit

    private val _exportResult = MutableSharedFlow<ExportResult>(extraBufferCapacity = 1)
    val exportResult: SharedFlow<ExportResult> = _exportResult.asSharedFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    fun startRecording()  = historyRepository.startRecording()
    fun stopRecording()   = historyRepository.stopRecording()
    fun clearHistory()    = historyRepository.clearHistory()
    fun setUnit(u: TemperatureUnit) = prefs.setUnit(u)

    fun export(format: ExportFormat, chartBitmap: ImageBitmap?) {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            val result = exportRepository.export(
                format      = format,
                history     = history.value,
                unit        = unit.value,
                chartBitmap = chartBitmap,
            )
            _exportResult.emit(result)
            _isExporting.value = false
        }
    }
}
