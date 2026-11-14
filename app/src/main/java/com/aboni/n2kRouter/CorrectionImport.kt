package com.aboni.n2kRouter

import kotlin.math.floor

/** A problem in imported text. [line] is 1-based; 0 means the table as a whole. */
data class ImportError(val line: Int, val reason: String)

/** [rows] are empty whenever [errors] is not: a table with a problem is never offered for sending. */
data class ImportResult(val rows: List<CorrectionRow>, val errors: List<ImportError>)

private const val FIELDS = 10

/** Rounds half away from zero, like the firmware's `lround`. */
private fun scaled(v: Double, scale: Int): Int {
    val x = v * scale
    return if (x < 0) -floor(-x + 0.5).toInt() else floor(x + 0.5).toInt()
}

/**
 * Reads the text the router script prints: one `tws,a1,dA1,dS1,a2,dA2,dS2,a3,dA3,dS3` row per line (kn and degrees,
 * `.` as decimal separator), each optionally prefixed with `U`. Blank lines and `#` comments are ignored.
 */
fun parseImport(text: String): ImportResult {
    val rows = mutableListOf<CorrectionRow>()
    val firstLineOf = mutableMapOf<Int, Int>()
    val errors = mutableListOf<ImportError>()
    for ((i, raw) in text.lines().withIndex()) {
        val n = i + 1
        var s = raw.trim()
        if (s.isEmpty() || s.startsWith("#")) continue
        if (s.startsWith("U") || s.startsWith("u")) s = s.substring(1).trim()
        val row = parseRow(s)
        if (row is RowResult.Bad) {
            errors += ImportError(n, row.reason)
            continue
        }
        row as RowResult.Good
        val same = firstLineOf[row.row.tws]
        if (same != null) {
            errors += ImportError(n, "Same TWS as line $same")
            continue
        }
        firstLineOf[row.row.tws] = n
        rows += row.row
    }
    if (errors.isEmpty()) {
        if (rows.size > MAX_ROWS) {
            errors += ImportError(0, "The table can hold at most $MAX_ROWS rows")
        } else {
            rows.sortBy { it.tws }
            validateRows(rows)?.let { errors += ImportError(0, it) }
        }
    }
    return if (errors.isEmpty()) ImportResult(rows, emptyList()) else ImportResult(emptyList(), errors)
}

private sealed interface RowResult {
    data class Good(val row: CorrectionRow) : RowResult
    data class Bad(val reason: String) : RowResult
}

private fun parseRow(s: String): RowResult {
    val parts = s.split(",").map { it.trim() }
    if (parts.size != FIELDS) {
        return RowResult.Bad("Expected $FIELDS numbers separated by commas and found ${parts.size}")
    }
    val v = parts.map { p -> p.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return RowResult.Bad("'$p' is not a number") }
    val tws = scaled(v[0], 10)
    if (tws !in 0..MAX_TWS) return RowResult.Bad("TWS must be between 0.0 and ${MAX_TWS / 10}.0 kn")
    val anchors = List(3) { k ->
        val angle = scaled(v[1 + 3 * k], 1)
        val dA = scaled(v[2 + 3 * k], 10)
        val dS = scaled(v[3 + 3 * k], 10)
        if (angle !in 0..255) return RowResult.Bad("Angle must be a whole number of degrees")
        if (dA !in -MAX_DA..MAX_DA) return RowResult.Bad("dA must be within ±${MAX_DA / 10}.0°")
        if (dS !in -MAX_DS..MAX_DS) return RowResult.Bad("dS must be within ±${MAX_DS / 10}.0 kn")
        Anchor(angle, dA, dS)
    }
    val row = CorrectionRow(tws, anchors)
    return validateRows(listOf(row))?.let { RowResult.Bad(it) } ?: RowResult.Good(row)
}
