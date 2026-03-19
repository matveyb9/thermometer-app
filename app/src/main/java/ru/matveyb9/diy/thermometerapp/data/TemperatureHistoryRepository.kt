package ru.matveyb9.diy.thermometerapp.data

import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import ru.matveyb9.diy.thermometerapp.data.model.ConnectionState
import ru.matveyb9.diy.thermometerapp.data.model.TemperatureRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemperatureHistoryRepository @Inject constructor(
    private val usbRepository: UsbThermometerRepository,
) {
    companion object {
        private const val MAX_POINTS = 1_800  // 30 минут при 1 пакете/сек
    }

    // FIX #9: используем ProcessLifecycleOwner.lifecycleScope вместо CoroutineScope(SupervisorJob())
    // Scope привязан к жизни процесса, отменяется корректно при завершении приложения
    private val scope = ProcessLifecycleOwner.get().lifecycleScope

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _history = MutableStateFlow<List<TemperatureRecord>>(emptyList())
    val history: StateFlow<List<TemperatureRecord>> = _history.asStateFlow()

    private var recordingStartTime = 0L

    init {
        scope.launch(Dispatchers.Default) {
            usbRepository.connectionState
                .filterIsInstance<ConnectionState.Connected>()
                .distinctUntilChangedBy { it.lastPacketAt }  // только реальные новые пакеты
                .collect { state ->
                    if (!_isRecording.value) return@collect
                    val temp = state.temperature ?: return@collect
                    if (state.packetLoss) return@collect

                    val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000f
                    val record  = TemperatureRecord(
                        elapsedSeconds     = elapsed,
                        temperatureCelsius = temp,
                        timestamp          = System.currentTimeMillis(),
                    )
                    val updated = _history.value + record
                    _history.value = if (updated.size > MAX_POINTS) {
                        updated.drop(updated.size - MAX_POINTS)
                    } else updated
                }
        }
    }

    fun startRecording() {
        if (_isRecording.value) return
        recordingStartTime = System.currentTimeMillis()
        _isRecording.value = true
    }

    fun stopRecording() {
        _isRecording.value = false
    }

    fun clearHistory() {
        _isRecording.value = false
        _history.value = emptyList()
    }
}
