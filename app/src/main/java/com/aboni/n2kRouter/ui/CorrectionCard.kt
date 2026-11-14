package com.aboni.n2kRouter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aboni.marine.ui.LocalMarineSnackbar
import com.aboni.marine.ui.theme.Marine
import com.aboni.n2kRouter.Adjusted
import com.aboni.n2kRouter.CorrectionController
import com.aboni.n2kRouter.CorrectionRow
import com.aboni.n2kRouter.CorrectionUi
import com.aboni.n2kRouter.DEFAULT_ROW
import com.aboni.n2kRouter.Data
import com.aboni.n2kRouter.Field
import com.aboni.n2kRouter.NO_VALUE
import com.aboni.n2kRouter.Selection
import com.aboni.n2kRouter.Sync
import com.aboni.n2kRouter.correctingText
import com.aboni.n2kRouter.correctionSteps
import com.aboni.n2kRouter.selectionLabel
import com.aboni.n2kRouter.selectionValueText
import com.aboni.n2kRouter.stepFor
import com.aboni.n2kRouter.stepText
import com.aboni.n2kRouter.syncText
import com.aboni.n2kRouter.tenths
import kotlinx.coroutines.launch

private const val TWS_WEIGHT = 1.2f
private val ANCHORS = 0..2

/**
 * The sensor's wind-correction table: every row at once, with the value to edit picked by tapping it and changed with
 * the stepper at the bottom. What is shown is what the sensor reports, plus the user's edits marked as pending.
 * [onImport] is null until an import screen exists, which hides the button.
 */
@Composable
fun ColumnScope.CorrectionCard(
    d: Data?,
    ui: CorrectionUi,
    controller: CorrectionController,
    connected: Boolean,
    stepIndex: Int,
    onStepIndex: (Int) -> Unit,
    onImport: (() -> Unit)?,
) {
    val snackbar = LocalMarineSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    fun tell(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }
    LaunchedEffect(ui.message) { ui.message?.let { tell(it) } }

    var selection by remember { mutableStateOf<Selection?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val table = ui.table
    val rows = table?.rows.orEmpty()
    // A selection whose row has gone (deleted, or its TWS moved by someone else) is dropped.
    val sel = selection?.takeIf { s -> rows.any { it.tws == s.rowTws } }
    val editable = connected && (ui.sync == Sync.InSync || ui.sync == Sync.Pending)
    fun applied(r: Adjusted) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        when (r) {
            is Adjusted.Ok -> selection = r.selection
            is Adjusted.Blocked -> tell(r.reason)
        }
    }

    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        SwitchRow(
            "Correction",
            table?.enabled,
            onToggle = table?.takeIf { editable }?.let { t -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); controller.setEnabled(!t.enabled) } },
        )
        Text(
            syncText(ui),
            style = Marine.type.body,
            color = if (ui.sync == Sync.OutOfSync || ui.sync == Sync.Unavailable) Marine.colors.error else Marine.colors.onSurface,
        )
        Text(correctingText(d), style = Marine.type.body, color = Marine.colors.label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (onImport != null) TextButton(onClick = onImport, enabled = editable) { Text("Import…") }
            TextButton(onClick = { confirmClear = true }, enabled = editable && rows.isNotEmpty()) { Text("Clear…") }
        }
        HorizontalDivider(color = Marine.colors.divider)
        if (table != null) {
            Header()
            if (rows.isEmpty()) {
                Text("The table is empty. Add a row.", style = Marine.type.body, color = Marine.colors.label)
            }
            rows.forEach { row -> TableRow(row, sel, row.tws in ui.pendingTws) { selection = it } }
        }
    }

    HorizontalDivider(color = Marine.colors.divider)
    val field = sel?.field ?: Field.DA
    val step = stepFor(field, stepIndex)
    val current = sel?.let { s -> rows.firstOrNull { it.tws == s.rowTws }?.let { selectionValueText(it, s) } }
    Stepper(
        label = sel?.let(::selectionLabel) ?: "Tap a value above",
        value = current ?: NO_VALUE,
        onDown = sel?.takeIf { editable }?.let { s -> { applied(controller.adjust(s, -step)) } },
        onUp = sel?.takeIf { editable }?.let { s -> { applied(controller.adjust(s, step)) } },
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val steps = correctionSteps(field)
        val chosen = stepIndex.coerceIn(0, steps.lastIndex)
        steps.forEachIndexed { i, s ->
            SegmentedButton(
                selected = i == chosen,
                onClick = { onStepIndex(i) },
                shape = SegmentedButtonDefaults.itemShape(i, steps.size),
            ) { Text(stepText(field, s)) }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(
            onClick = { sel?.let { applied(controller.deleteRow(it.rowTws)) } },
            enabled = editable && sel != null,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Delete row") }
        OutlinedButton(
            onClick = {
                if (rows.isEmpty()) controller.importRows(listOf(DEFAULT_ROW)) else sel?.let { applied(controller.addRow(it.rowTws)) }
            },
            enabled = editable && (rows.isEmpty() || sel != null),
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Add row") }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the table?") },
            text = { Text("Every row is removed from the sensor.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; controller.clear() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth()) {
        HeaderCell("TWS", TWS_WEIGHT)
        repeat(ANCHORS.count()) {
            HeaderCell("∠", 1f)
            HeaderCell("dA", 1f)
            HeaderCell("dS", 1f)
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        style = Marine.type.label,
        color = Marine.colors.label,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(weight),
    )
}

/** One row of the table: the TWS, then the three anchors as angle, dA and dS. */
@Composable
private fun TableRow(row: CorrectionRow, sel: Selection?, pending: Boolean, onSelect: (Selection) -> Unit) {
    fun picked(anchor: Int, field: Field) = sel != null && sel.rowTws == row.tws && sel.field == field && (field == Field.Tws || sel.anchor == anchor)
    Row(Modifier.fillMaxWidth()) {
        val tws = Selection(row.tws, 0, Field.Tws)
        Cell(tenths(row.tws), "Row ${tenths(row.tws)} kn TWS", picked(0, Field.Tws), pending, TWS_WEIGHT) { onSelect(tws) }
        row.anchors.forEachIndexed { k, a ->
            val n = k + 1
            Cell("${a.angle}°", "Row ${tenths(row.tws)} kn anchor $n angle ${a.angle} degrees", picked(k, Field.Angle), pending, 1f) {
                onSelect(Selection(row.tws, k, Field.Angle))
            }
            Cell(tenths(a.dA, signed = true), "Row ${tenths(row.tws)} kn anchor $n dA ${tenths(a.dA)} degrees", picked(k, Field.DA), pending, 1f) {
                onSelect(Selection(row.tws, k, Field.DA))
            }
            Cell(tenths(a.dS, signed = true), "Row ${tenths(row.tws)} kn anchor $n dS ${tenths(a.dS)} knots", picked(k, Field.DS), pending, 1f) {
                onSelect(Selection(row.tws, k, Field.DS))
            }
        }
    }
}

/**
 * One number of the table. Ten numbers share the card's width, so a cell is narrower than 48dp but 48dp tall;
 * selecting only highlights, and the real controls (the stepper buttons) are large.
 */
@Composable
private fun RowScope.Cell(text: String, description: String, selected: Boolean, pending: Boolean, weight: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(weight)
            .heightIn(min = 48.dp)
            .background(if (selected) Marine.colors.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Marine.type.caption.copy(fontFamily = FontFamily.Monospace),
            color = when {
                selected -> Marine.colors.onPrimary
                pending -> Marine.colors.warn
                else -> Marine.colors.onSurface
            },
            maxLines = 1,
        )
    }
}
