package com.aboni.n2kRouter

import android.content.Context
import android.util.Log
import kotlin.math.abs

class CalibrationRange {
    constructor(sL: Int, sH: Int, cL: Int, cH: Int) {
        sinLow = sL
        sinHigh = sH
        cosLow = cL
        cosHigh = cH
    }

    var sinLow: Int = 0
    var sinHigh: Int = 4095
    var cosLow: Int = 0
    var cosHigh: Int = 5095
}


fun byteToInt(v: ByteArray, offset: Int, size: Int): Long? {
    if ((size+offset)>v.size) return null
    val bytes = v.copyOfRange(offset, offset + size)
    var result = 0L
    var shift = 0
    val negative = bytes[bytes.size-1] < 0
    for (byte in bytes) {
        var ub: UByte = byte.toUByte()
        if (negative) ub = ub.inv()
        result = result or ((ub.toInt() shl shift).toLong())
        shift += 8
    }
    return if (negative)
        -(result+1)
    else
        result
}

fun appendLog(message: String)
{
    Log.i("ABN2K", message)
}
fun noValueStr(context: Context): String {
    return context.getString(R.string.NO_VALUE_STRING)
}

fun formatValue(context: Context, formatId: Int, vararg args: Any?): String {
    return context.getString(formatId).format(*args)
}