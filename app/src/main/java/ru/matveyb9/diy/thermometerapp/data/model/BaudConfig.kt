package ru.matveyb9.diy.thermometerapp.data.model

sealed interface BaudConfig {
    data object Auto : BaudConfig
    data class Manual(val baudRate: BaudRate) : BaudConfig
}
