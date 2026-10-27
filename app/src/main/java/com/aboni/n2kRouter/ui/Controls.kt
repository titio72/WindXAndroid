package com.aboni.n2kRouter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aboni.marine.link.CommandResult
import com.aboni.marine.ui.LocalMarineSnackbar
import com.aboni.marine.ui.theme.Marine
import com.aboni.n2kRouter.WindCommand
import kotlinx.coroutines.launch

/** Sends a command with a haptic tick and says the outcome in the scaffold's snackbar. */
@Composable
fun rememberSender(send: suspend (WindCommand) -> CommandResult): (WindCommand) -> Unit {
    val scope = rememberCoroutineScope()
    val snackbar = LocalMarineSnackbar.current
    val haptics = LocalHapticFeedback.current
    return remember(send, snackbar, haptics) {
        { cmd ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                val r = send(cmd)
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(r.text)
            }
        }
    }
}

/** Label above value; the unit belongs in the label. */
@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Marine.type.value,
    alignEnd: Boolean = false,
) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = Marine.type.label, color = Marine.colors.label)
        Text(value, style = style, color = Marine.colors.onSurface, textAlign = if (alignEnd) TextAlign.End else TextAlign.Start)
    }
}

/**
 * A device value with − and + buttons, each tap one command. A null handler (value unknown, or at a limit) disables
 * that button.
 */
@Composable
fun Stepper(label: String, value: String, onDown: (() -> Unit)?, onUp: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        LabeledValue(label, value, Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepButton("−", "Decrease $label", onDown)
            StepButton("+", "Increase $label", onUp)
        }
    }
}

@Composable
private fun StepButton(glyph: String, description: String, onClick: (() -> Unit)?) {
    OutlinedButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(width = 64.dp, height = 48.dp).semantics { contentDescription = description },
    ) {
        Text(glyph, style = Marine.type.cardTitle)
    }
}

/** A switch showing the device's state; toggling sends a command and the switch follows the device's next data. */
@Composable
fun SwitchRow(label: String, checked: Boolean?, onToggle: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Marine.type.body, color = Marine.colors.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked == true, onCheckedChange = { onToggle?.invoke() }, enabled = checked != null && onToggle != null)
    }
}
