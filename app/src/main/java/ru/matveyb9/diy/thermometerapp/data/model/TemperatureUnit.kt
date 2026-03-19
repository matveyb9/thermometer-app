package ru.matveyb9.diy.thermometerapp.data.model

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

fun Float.toDisplayTemp(unit: TemperatureUnit): Float = when (unit) {
    TemperatureUnit.CELSIUS    -> this
    TemperatureUnit.FAHRENHEIT -> this * 9f / 5f + 32f
}

fun TemperatureUnit.symbol(): String = when (this) {
    TemperatureUnit.CELSIUS    -> "°C"
    TemperatureUnit.FAHRENHEIT -> "°F"
}
