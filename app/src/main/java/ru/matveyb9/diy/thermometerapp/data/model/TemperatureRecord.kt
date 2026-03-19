package ru.matveyb9.diy.thermometerapp.data.model

data class TemperatureRecord(
    val elapsedSeconds: Float,        // ось X на графике
    val temperatureCelsius: Float,    // всегда хранится в °C, конвертируется при отображении
    val timestamp: Long,
)
