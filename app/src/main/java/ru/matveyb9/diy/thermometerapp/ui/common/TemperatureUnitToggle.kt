package ru.matveyb9.diy.thermometerapp.ui.common

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ru.matveyb9.diy.thermometerapp.data.model.TemperatureUnit

// FIX #3: компонент перенесён из ui/dashboard/DashboardScreen.kt в ui/common/,
// т.к. используется на обеих вкладках (Dashboard и Chart)
@Composable
fun TemperatureUnitToggle(
    selected: TemperatureUnit,
    onSelect: (TemperatureUnit) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        TemperatureUnit.entries.forEachIndexed { index, unit ->
            SegmentedButton(
                shape    = SegmentedButtonDefaults.itemShape(index, TemperatureUnit.entries.size),
                selected = selected == unit,
                onClick  = { onSelect(unit) },
                label    = {
                    Text(if (unit == TemperatureUnit.CELSIUS) "°C  Цельсий" else "°F  Фаренгейт")
                },
            )
        }
    }
}
