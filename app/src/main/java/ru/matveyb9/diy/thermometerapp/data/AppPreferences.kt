package ru.matveyb9.diy.thermometerapp.data

import ru.matveyb9.diy.thermometerapp.data.model.TemperatureUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton — единый источник единиц измерения для обеих вкладок.
 * Изменение на любой вкладке мгновенно отражается на другой.
 */
@Singleton
class AppPreferences @Inject constructor() {
    private val _unit = MutableStateFlow(TemperatureUnit.CELSIUS)
    val unit: StateFlow<TemperatureUnit> = _unit.asStateFlow()

    fun setUnit(unit: TemperatureUnit) {
        _unit.value = unit
    }
}
