package com.aboni.n2kRouter

import kotlin.math.abs

private const val ANCHORS = 3

/** The first row of an empty table: 10 kn, no corrections, anchors at typical upwind, reaching and downwind angles. */
val DEFAULT_ROW = CorrectionRow(100, listOf(Anchor(45, 0, 0), Anchor(95, 0, 0), Anchor(150, 0, 0)))
private const val NEW_ROW_TWS_STEP = 5   // 0.5 kn

/** The firmware's slope rule: a change of [dv] over [dx] (both in 0.1 units) is at most 0.5 per unit. */
private fun slopeOk(dv: Int, dx: Int) = 2 * abs(dv) <= dx

/**
 * Null when the firmware would accept [rows] as a table, else the first problem in plain words. Mirrors the firmware's
 * `correction_table_valid`, because it never replies to a rejected edit.
 */
fun validateRows(rows: List<CorrectionRow>): String? {
    if (rows.size > MAX_ROWS) return "The table can hold at most $MAX_ROWS rows"
    for ((i, row) in rows.withIndex()) {
        rowProblem(row)?.let { return it }
        if (i > 0) {
            val prev = rows[i - 1]
            if (row.tws == prev.tws) return "Two rows have the same TWS"
            if (row.tws < prev.tws) return "Rows must be in order of TWS"
            for (j in 0 until ANCHORS) {
                if (!slopeOk(row.anchors[j].dS - prev.anchors[j].dS, row.tws - prev.tws)) {
                    return "dS changes too fast between rows (at most 0.5 kn per knot of TWS)"
                }
            }
        }
    }
    return null
}

private fun rowProblem(row: CorrectionRow): String? {
    if (row.tws < 0 || row.tws > MAX_TWS) return "TWS must be between 0.0 and ${MAX_TWS / 10}.0 kn"
    if (row.anchors.size != ANCHORS) return "Each row needs $ANCHORS anchors"
    var prevAngle = 0
    var prevDa = 0   // the correction tapers from 0 at the bow
    for (a in row.anchors) {
        if (a.angle <= prevAngle || a.angle >= 180) return "Anchor angles must increase, between 1° and 179°"
        if (abs(a.dA) > MAX_DA) return "dA can't be more than ±${MAX_DA / 10}.0°"
        if (abs(a.dS) > MAX_DS) return "dS can't be more than ±${MAX_DS / 10}.0 kn"
        if (!slopeOk(a.dA - prevDa, 10 * (a.angle - prevAngle))) {
            return "dA changes too fast along the row (at most 0.5° per degree of angle)"
        }
        if (!slopeOk(a.dS, row.tws)) return "dS can't be more than half the row's TWS"
        prevAngle = a.angle
        prevDa = a.dA
    }
    // ... and back to 0 at the stern
    if (!slopeOk(prevDa, 10 * (180 - prevAngle))) return "dA changes too fast towards the stern (at most 0.5° per degree)"
    return null
}

enum class Field { Tws, Angle, DA, DS }

/** The value being edited: a row (by its TWS), one of its anchors (0..2, ignored for [Field.Tws]) and the field. */
data class Selection(val rowTws: Int, val anchor: Int, val field: Field)

/** The outcome of an edit: the new rows (and the selection to keep), or why the firmware would refuse them. */
sealed interface Adjusted {
    /** [selection] is null when no row is left. */
    data class Ok(val rows: List<CorrectionRow>, val selection: Selection?) : Adjusted
    data class Blocked(val reason: String) : Adjusted
}

private fun checked(rows: List<CorrectionRow>, selection: Selection?): Adjusted =
    validateRows(rows)?.let { Adjusted.Blocked(it) } ?: Adjusted.Ok(rows, selection)

/** Moves the selected value by [delta], in the field's own unit (0.1 for TWS, dA and dS; whole degrees for angle). */
fun adjustRows(rows: List<CorrectionRow>, sel: Selection, delta: Int): Adjusted {
    val index = rows.indexOfFirst { it.tws == sel.rowTws }
    if (index < 0) return Adjusted.Blocked("There is no row at that TWS")
    val row = rows[index]
    val edited = if (sel.field == Field.Tws) {
        row.copy(tws = row.tws + delta)
    } else {
        val a = row.anchors.getOrNull(sel.anchor) ?: return Adjusted.Blocked("There is no such anchor")
        val moved = when (sel.field) {
            Field.Angle -> a.copy(angle = a.angle + delta)
            Field.DA -> a.copy(dA = a.dA + delta)
            else -> a.copy(dS = a.dS + delta)
        }
        row.copy(anchors = row.anchors.toMutableList().also { it[sel.anchor] = moved })
    }
    val next = rows.toMutableList().also { it[index] = edited }.sortedBy { it.tws }
    return checked(next, sel.copy(rowTws = edited.tws))
}

/** A copy of the row at [tws], at the next free TWS above it. */
fun addRowLike(rows: List<CorrectionRow>, tws: Int): Adjusted {
    if (rows.size >= MAX_ROWS) return Adjusted.Blocked("The table already has $MAX_ROWS rows")
    val source = rows.firstOrNull { it.tws == tws } ?: return Adjusted.Blocked("There is no row at that TWS")
    var free = tws + NEW_ROW_TWS_STEP
    while (rows.any { it.tws == free }) free += NEW_ROW_TWS_STEP
    if (free > MAX_TWS) return Adjusted.Blocked("There is no free TWS above this row")
    val next = (rows + source.copy(tws = free)).sortedBy { it.tws }
    return checked(next, Selection(free, 0, Field.Tws))
}

/**
 * Removes the row at [tws]. Removing a row can't break the firmware's rules (its slope limits only get easier), so
 * the only way to be blocked is to name a row that isn't there.
 */
fun deleteRow(rows: List<CorrectionRow>, tws: Int): Adjusted {
    val index = rows.indexOfFirst { it.tws == tws }
    if (index < 0) return Adjusted.Blocked("There is no row at that TWS")
    val next = rows.filterIndexed { i, _ -> i != index }
    val selected = next.getOrNull(index) ?: next.lastOrNull()
    return Adjusted.Ok(next, selected?.let { Selection(it.tws, 0, Field.Tws) })
}
