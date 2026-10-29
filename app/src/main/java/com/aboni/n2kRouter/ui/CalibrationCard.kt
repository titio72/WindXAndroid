package com.aboni.n2kRouter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aboni.marine.ui.theme.Marine
import com.aboni.n2kRouter.Data
import com.aboni.n2kRouter.NO_VALUE
import com.aboni.n2kRouter.WindCommand
import com.aboni.n2kRouter.canCommitCalibration
import com.aboni.n2kRouter.sideAngle
import com.aboni.n2kRouter.stepped
import com.aboni.n2kRouter.text

private const val THRESHOLD_STEP = 5

/** Calibration progress: coverage of the 360°, score, auto-calibration, commit or cancel. */
@Composable
fun ColumnScope.CalibrationCard(d: Data?, send: (WindCommand) -> Unit) {
    val wind = d?.wind?.takeIf { it.valid }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        LabeledValue("AWA raw (°)", wind?.let { sideAngle(it.value) } ?: NO_VALUE, Modifier.weight(1f))
        LabeledValue("AWS (kn)", d?.speed?.text(1) ?: NO_VALUE, Modifier.weight(1f))
        LabeledValue("Freq (Hz)", d?.frequency?.text(1) ?: NO_VALUE, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        LabeledValue("Ellipse", d?.ellipse?.text(2) ?: NO_VALUE, Modifier.weight(1f))
        LabeledValue("Score", d?.calibrationProgress?.takeIf { it.valid }?.score?.toString() ?: NO_VALUE, Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
    }
    Text("Coverage", style = Marine.type.label, color = Marine.colors.label)
    CalibrationStrip(
        calibration = d?.calibrationProgress?.takeIf { it.valid },
        angle = wind?.value,
        inScore = d?.calibInScore?.asBoolean() == true,
        modifier = Modifier.fillMaxWidth().height(40.dp),
    )
    Spacer(Modifier.height(8.dp))
    SensorBars(d)
    SwitchRow(
        "Auto calibration",
        d?.autoCalibration?.takeIf { it.valid }?.let { d.isAutocalibrating },
        onToggle = { send(WindCommand.ToggleAutoCalibration) },
    )
    Stepper(
        "Score threshold", d?.calibrationThreshold?.text() ?: NO_VALUE,
        onDown = d?.let { stepped(it.calibrationThreshold, -THRESHOLD_STEP, 0..100) }
            ?.let { { send(WindCommand.CalibrationScoreThreshold(it)) } },
        onUp = d?.let { stepped(it.calibrationThreshold, THRESHOLD_STEP, 0..100) }
            ?.let { { send(WindCommand.CalibrationScoreThreshold(it)) } },
    )
    Spacer(Modifier.weight(1f))
    // Cancel is the escape action: always enabled, and kept apart from Commit.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(onClick = { send(WindCommand.CancelCalibration) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Cancel")
        }
        Button(
            onClick = { send(WindCommand.CommitCalibration) },
            enabled = canCommitCalibration(d),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Commit")
        }
    }
}
