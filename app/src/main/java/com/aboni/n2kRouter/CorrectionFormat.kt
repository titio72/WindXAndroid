package com.aboni.n2kRouter

import java.util.Locale

/** A value in tenths as the firmware and the card write it: one decimal, always a dot (`-35` → `-3.5`). */
fun tenths(v: Int, signed: Boolean = false): String =
    String.format(Locale.ROOT, if (signed) "%+.1f" else "%.1f", v / 10.0)

fun syncText(ui: CorrectionUi): String = when (ui.sync) {
    Sync.InSync -> if (ui.tableId != null) "In sync · " + String.format(Locale.ROOT, "%08X", ui.tableId) else "In sync"
    Sync.Pending -> "Sending…"
    Sync.Reading -> "Reading the table…"
    Sync.OutOfSync -> "Out of sync"
    Sync.Unavailable -> "This sensor's firmware has no correction table"
}

/** Whether the sensor is correcting the wind it sends right now, and with what result. */
fun correctingText(d: Data?): String {
    val enabled = d?.correctionEnabled?.asBoolean() ?: return NO_VALUE
    if (!enabled) return "Off"
    if (d.corrected.asBoolean() != true) return "Enabled, not correcting (no boat speed)"
    if (!d.correctedAwa.valid || !d.correctedAws.valid) return "Correcting now"
    return "Correcting now: AWA ${tenths(d.correctedAwa.value.toInt())}° · AWS ${tenths(d.correctedAws.value.toInt())} kn"
}

/** The step sizes the stepper offers for [field], in the field's own unit (0.1 for TWS, dA and dS; degrees for angle). */
fun correctionSteps(field: Field): List<Int> = when (field) {
    Field.DA, Field.DS -> listOf(1, 5, 10)
    Field.Angle -> listOf(1, 5)
    Field.Tws -> listOf(5, 10)
}

fun stepText(field: Field, step: Int): String = if (field == Field.Angle) step.toString() else tenths(step)

/** The step for a remembered step index; fields with fewer steps use their largest. */
fun stepFor(field: Field, index: Int): Int = correctionSteps(field).let { it[index.coerceIn(0, it.lastIndex)] }

/** The selected value of [row], as the stepper shows it. */
fun selectionValueText(row: CorrectionRow, sel: Selection): String = when (sel.field) {
    Field.Tws -> tenths(row.tws)
    Field.Angle -> row.anchors[sel.anchor].angle.toString()
    Field.DA -> tenths(row.anchors[sel.anchor].dA, signed = true)
    Field.DS -> tenths(row.anchors[sel.anchor].dS, signed = true)
}

/** What the stepper is editing, with the unit in the label. */
fun selectionLabel(sel: Selection): String {
    val what = when (sel.field) {
        Field.Tws -> "TWS (kn)"
        Field.Angle -> "anchor ${sel.anchor + 1} angle (°)"
        Field.DA -> "anchor ${sel.anchor + 1} dA (°)"
        Field.DS -> "anchor ${sel.anchor + 1} dS (kn)"
    }
    return "Row ${tenths(sel.rowTws)} kn · $what"
}

/** A row on one line, for the import preview: `10.0 kn: 40° -3.5/+0.0 · 95° -1.0/+0.5 · 150° +2.0/+0.0`. */
fun rowSummary(row: CorrectionRow): String =
    "${tenths(row.tws)} kn: " + row.anchors.joinToString(" · ") { "${it.angle}° ${tenths(it.dA, true)}/${tenths(it.dS, true)}" }
