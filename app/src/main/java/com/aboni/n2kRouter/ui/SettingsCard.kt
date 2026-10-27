package com.aboni.n2kRouter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aboni.marine.ui.theme.Marine
import com.aboni.n2kRouter.Data
import com.aboni.n2kRouter.NO_VALUE
import com.aboni.n2kRouter.WindCommand
import com.aboni.n2kRouter.deviceNameError
import com.aboni.n2kRouter.text

private val VANES = listOf(0 to "ST50", 1 to "ST60")

/** Device settings: its BLE name, vane type, USB debug, and diagnostics. [deviceName] is the name the device advertises. */
@Composable
fun ColumnScope.SettingsCard(d: Data?, deviceName: String?, connected: Boolean, send: (WindCommand) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        LabeledValue("Device name", deviceName?.ifBlank { null } ?: NO_VALUE)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                singleLine = true,
                label = { Text("New name") },
                isError = error != null,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    error = deviceNameError(name.trim())
                    if (error == null) send(WindCommand.DeviceName(name.trim()))
                },
                enabled = connected,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Set") }
        }
        error?.let { Text(it, style = Marine.type.body, color = Marine.colors.error) }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Marine.colors.divider)
        Spacer(Modifier.height(8.dp))
        Text("Vane type", style = Marine.type.label, color = Marine.colors.label)
        val vane = d?.vaneType?.takeIf { it.valid }?.value?.toInt()
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            VANES.forEachIndexed { i, (type, label) ->
                SegmentedButton(
                    selected = vane == type,
                    onClick = { if (vane != type) send(WindCommand.VaneType(type)) },
                    enabled = vane != null,
                    shape = SegmentedButtonDefaults.itemShape(i, VANES.size),
                ) { Text(label) }
            }
        }
        SwitchRow(
            "USB debug log",
            d?.debug?.asBoolean(),
            onToggle = d?.debug?.asBoolean()?.let { on -> { send(WindCommand.Debug(!on)) } },
        )
        HorizontalDivider(color = Marine.colors.divider)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            LabeledValue("Free heap (bytes)", d?.heap?.text() ?: NO_VALUE, Modifier.weight(1f))
            LabeledValue("N2K source", d?.n2kSource?.text() ?: NO_VALUE, Modifier.weight(1f))
        }
    }
}
