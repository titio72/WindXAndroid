package com.aboni.n2kRouter.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aboni.marine.ui.theme.Marine
import com.aboni.marine.ui.theme.StatusTone
import com.aboni.n2kRouter.ADC_MAX
import com.aboni.n2kRouter.Data
import com.aboni.n2kRouter.NO_VALUE
import com.aboni.n2kRouter.SMOOTHING_STEP
import com.aboni.n2kRouter.WindCommand
import com.aboni.n2kRouter.alerts
import com.aboni.n2kRouter.percentAsFactor
import com.aboni.n2kRouter.sideAngle
import com.aboni.n2kRouter.stepped
import com.aboni.n2kRouter.text
import com.aboni.n2kRouter.vaneTypeName

/** The main card: the angle sent to the bus, the dial, speed, alerts and the smoothing factors. */
@Composable
fun ColumnScope.WindCard(d: Data?, send: (WindCommand) -> Unit) {
    val wind = d?.wind?.takeIf { it.valid }
    Row(Modifier.fillMaxWidth()) {
        LabeledValue("AWA (°)", d?.windOutput?.takeIf { it.valid }?.let { sideAngle(it.value) } ?: NO_VALUE,
            Modifier.weight(1f), style = Marine.type.hero)
        LabeledValue("AWS (kn)", d?.speed?.text(1) ?: NO_VALUE, style = Marine.type.hero, alignEnd = true)
    }
    WindDial(
        raw = wind?.value,
        smooth = d?.windSmooth?.takeIf { wind != null && it.valid }?.value,
        output = d?.windOutput?.takeIf { wind != null && it.valid }?.value,
        ellipse = d?.ellipse?.takeIf { it.valid }?.value,
        calibration = d?.calibrationProgress?.takeIf { it.valid },
        modifier = Modifier.weight(1f).fillMaxWidth(),
    )
    d?.let { alerts(it) }.orEmpty().forEach {
        Text("${StatusTone.Error.symbol} $it", style = Marine.type.body, color = Marine.colors.error)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        LabeledValue("Ellipse", d?.ellipse?.text(2) ?: NO_VALUE, Modifier.weight(1f))
        LabeledValue("Vane", d?.let { vaneTypeName(it.vaneType) } ?: NO_VALUE, Modifier.weight(1f))
    }
    SensorBars(d)
    Stepper(
        "Speed 𝛂", d?.let { percentAsFactor(it.speedSmoothing) } ?: NO_VALUE,
        onDown = d?.let { stepped(it.speedSmoothing, -SMOOTHING_STEP, 0..100) }?.let { { send(WindCommand.SpeedSmoothing(it)) } },
        onUp = d?.let { stepped(it.speedSmoothing, SMOOTHING_STEP, 0..100) }?.let { { send(WindCommand.SpeedSmoothing(it)) } },
    )
    Stepper(
        "Direction 𝛂", d?.let { percentAsFactor(it.angleSmoothing) } ?: NO_VALUE,
        onDown = d?.let { stepped(it.angleSmoothing, -SMOOTHING_STEP, 0..100) }?.let { { send(WindCommand.DirectionSmoothing(it)) } },
        onUp = d?.let { stepped(it.angleSmoothing, SMOOTHING_STEP, 0..100) }?.let { { send(WindCommand.DirectionSmoothing(it)) } },
    )
}

/** Sin and cos readings against their calibrated ranges. */
@Composable
fun SensorBars(d: Data?) {
    BarRow("Sin", d?.iSin?.takeIf { it.valid }?.value?.toInt(), d?.iSinLow?.takeIf { it.valid }?.value?.toInt(),
        d?.iSinHigh?.takeIf { it.valid }?.value?.toInt())
    BarRow("Cos", d?.iCos?.takeIf { it.valid }?.value?.toInt(), d?.iCosLow?.takeIf { it.valid }?.value?.toInt(),
        d?.iCosHigh?.takeIf { it.valid }?.value?.toInt())
}

@Composable
private fun BarRow(label: String, value: Int?, low: Int?, high: Int?) {
    Text(label, style = Marine.type.label, color = Marine.colors.label)
    RangeBar(value, low, high, ADC_MAX, Modifier.fillMaxWidth().height(14.dp))
    Spacer(Modifier.height(4.dp))
}
