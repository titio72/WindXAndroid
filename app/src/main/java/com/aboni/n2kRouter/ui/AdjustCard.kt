package com.aboni.n2kRouter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aboni.marine.ui.theme.Marine
import com.aboni.n2kRouter.Data
import com.aboni.n2kRouter.INCREMENTS
import com.aboni.n2kRouter.MAX_SETTING_STEP
import com.aboni.n2kRouter.NO_VALUE
import com.aboni.n2kRouter.RangeBound
import com.aboni.n2kRouter.WindCommand
import com.aboni.n2kRouter.percentAsFactor
import com.aboni.n2kRouter.signedDegrees
import com.aboni.n2kRouter.stepped
import com.aboni.n2kRouter.steppedRange
import com.aboni.n2kRouter.text
import kotlin.math.roundToInt

/** Manual calibration: sin/cos ranges, angle offset and speed adjustment, all moved by the chosen step. */
@Composable
fun ColumnScope.AdjustCard(d: Data?, incrementIndex: Int, onIncrement: (Int) -> Unit, send: (WindCommand) -> Unit) {
    val step = INCREMENTS[incrementIndex]
    val small = step.coerceAtMost(MAX_SETTING_STEP)
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        LabeledValue("Step", step.toString())
        Slider(
            value = incrementIndex.toFloat(),
            onValueChange = { onIncrement(it.roundToInt().coerceIn(INCREMENTS.indices)) },
            valueRange = 0f..INCREMENTS.lastIndex.toFloat(),
            steps = INCREMENTS.size - 2,
        )
        HorizontalDivider(color = Marine.colors.divider)
        Spacer(Modifier.height(4.dp))
        SensorBars(d)
        fun range(bound: RangeBound, delta: Int): (() -> Unit)? =
            d?.let { data -> { send(WindCommand.SetCalibration(steppedRange(data, bound, delta))) } }
        Stepper("Sin low", d?.iSinLow?.text() ?: NO_VALUE, range(RangeBound.SinLow, -step), range(RangeBound.SinLow, step))
        Stepper("Sin high", d?.iSinHigh?.text() ?: NO_VALUE, range(RangeBound.SinHigh, -step), range(RangeBound.SinHigh, step))
        Stepper("Cos low", d?.iCosLow?.text() ?: NO_VALUE, range(RangeBound.CosLow, -step), range(RangeBound.CosLow, step))
        Stepper("Cos high", d?.iCosHigh?.text() ?: NO_VALUE, range(RangeBound.CosHigh, -step), range(RangeBound.CosHigh, step))
        HorizontalDivider(color = Marine.colors.divider)
        Stepper(
            "Offset (°)", d?.let { signedDegrees(it.angleOffset) } ?: NO_VALUE,
            onDown = d?.let { stepped(it.angleOffset, -small, -180..180) }?.let { { send(WindCommand.Offset(it)) } },
            onUp = d?.let { stepped(it.angleOffset, small, -180..180) }?.let { { send(WindCommand.Offset(it)) } },
        )
        Stepper(
            "Speed adjustment", d?.let { percentAsFactor(it.speedAdjustment) } ?: NO_VALUE,
            onDown = d?.let { stepped(it.speedAdjustment, -small, 0..250) }?.let { { send(WindCommand.SpeedAdjustment(it)) } },
            onUp = d?.let { stepped(it.speedAdjustment, small, 0..250) }?.let { { send(WindCommand.SpeedAdjustment(it)) } },
        )
    }
}
