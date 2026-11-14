package com.aboni.n2kRouter

import android.util.Log

fun byteToInt(v: ByteArray, offset: Int, size: Int, signed: Boolean = true): Long? {
    if ((size+offset)>v.size) return null
    val bytes = v.copyOfRange(offset, offset + size)
    var result = 0L
    var shift = 0
    val negative = signed && bytes[bytes.size-1] < 0
    for (byte in bytes) {
        var ub: UByte = byte.toUByte()
        if (negative) ub = ub.inv()
        // Shift as a Long: an Int shift of a byte of 0x80 or more by 24 would go negative and sign-extend.
        result = result or (ub.toLong() shl shift)
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
