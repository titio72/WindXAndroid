package com.aboni.n2kRouter

import kotlin.math.roundToInt

/** The one "no value" string: a dash is "I don't know", a zero would be a value. */
const val NO_VALUE = "-"

/** Step sizes picked with the precision slider, smallest first. */
val INCREMENTS = listOf(1, 2, 5, 10, 25, 50, 100, 250)
const val DEFAULT_INCREMENT_INDEX = 3

/** Offset, speed adjustment and score threshold never move by more than this per tap. */
const val MAX_SETTING_STEP = 10
const val SMOOTHING_STEP = 5
const val ADC_MAX = 4095

/** Apparent wind angle side-signed: `S45` to starboard, `P120` to port. */
fun sideAngle(deg: Double): String {
    val d = ((deg % 360.0) + 360.0) % 360.0
    return if (d > 180.0) "P${(360.0 - d).roundToInt()}" else "S${d.roundToInt()}"
}

fun IntValue.text(): String = if (valid) value.toString() else NO_VALUE
fun DoubleValue.text(decimals: Int): String = if (valid) "%.${decimals}f".format(value) else NO_VALUE
fun signedDegrees(v: IntValue): String = if (v.valid) "%+d".format(v.value) else NO_VALUE

/** Stored as a percentage, shown as a factor (0.85). */
fun percentAsFactor(v: IntValue): String = if (v.valid) "%.2f".format(v.value / 100.0) else NO_VALUE

fun vaneTypeName(v: IntValue): String = when {
    !v.valid -> NO_VALUE
    v.value == 0L -> "ST50"
    v.value == 1L -> "ST60"
    else -> "Unknown"
}

/** The value after one tap of [step] (negative = down), kept in [range]; null when the current value is unknown. */
fun stepped(v: IntValue, step: Int, range: IntRange): Int? =
    if (v.valid) (v.value + step).toInt().coerceIn(range) else null

/** The calibration range with one bound moved by [step]; out-of-range bounds read from the device start at the extremes. */
fun steppedRange(d: Data, bound: RangeBound, step: Int): CalibrationRange {
    fun low(v: IntValue) = if (v.value in 0..ADC_MAX.toLong()) v.value.toInt() else 0
    fun high(v: IntValue) = if (v.value in 0..ADC_MAX.toLong()) v.value.toInt() else ADC_MAX
    val r = CalibrationRange(low(d.iSinLow), high(d.iSinHigh), low(d.iCosLow), high(d.iCosHigh))
    when (bound) {
        RangeBound.SinLow -> r.sinLow = (r.sinLow + step).coerceIn(0, ADC_MAX)
        RangeBound.SinHigh -> r.sinHigh = (r.sinHigh + step).coerceIn(0, ADC_MAX)
        RangeBound.CosLow -> r.cosLow = (r.cosLow + step).coerceIn(0, ADC_MAX)
        RangeBound.CosHigh -> r.cosHigh = (r.cosHigh + step).coerceIn(0, ADC_MAX)
    }
    return r
}

enum class RangeBound { SinLow, SinHigh, CosLow, CosHigh }

/** Commit only makes sense for a valid manual calibration; auto-calibration commits on its own. */
fun canCommitCalibration(d: Data?): Boolean =
    d != null && d.autoCalibration.valid && d.autoCalibration.value == 0L && d.calibValid.asBoolean() == true

/** The sensor's alerts, in plain words. */
fun alerts(d: Data): List<String> = buildList {
    if (d.errorAngle.valid && d.errorAngle.value == 2L) add("Off calibration")
    else if (d.errorAngle.valid && d.errorAngle.value > 0L) add("Transducer error")
    if (d.n2kBusError.valid && d.n2kBusError.value > 0L) add("NMEA 2000 bus error")
}

/** The firmware keeps the name in 16 bytes, terminator included. */
const val MAX_DEVICE_NAME_BYTES = 15

/** Null when [name] can be sent, else why not. */
fun deviceNameError(name: String): String? = when {
    name.isBlank() -> "Enter a name"
    name.toByteArray(Charsets.UTF_8).size > MAX_DEVICE_NAME_BYTES -> "At most $MAX_DEVICE_NAME_BYTES characters"
    else -> null
}
