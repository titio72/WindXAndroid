package com.aboni.n2kRouter

import java.time.Instant

abstract class BaseData {

    abstract fun parse(data: ByteArray)

    class DoubleValue(sz: Int, sg: Boolean, sc: Double) {
        private var iValue = IntValue(sz, sg)
        private val scale = sc

        var valid = false
        var value = Double.NaN

        fun parseValue(data: ByteArray, offset: Int): Int {
            val newOffset = iValue.parseValue(data, offset)
            valid = iValue.valid
            if (valid) value = iValue.value * scale
            return newOffset
        }

        fun copyFrom(d: DoubleValue) {
            value = d.value
            valid = d.valid
        }
    }

    open class IntValue(sz: Int, sg: Boolean) {
        private val size = sz
        private val signed = sg
        var value: Long = 0
        var valid: Boolean = false

        fun parseValue(data: ByteArray, offset: Int): Int {
            val vv = byteToInt(data, offset, size)
            if (vv==null) {
                valid = false
            } else {
                value = vv
                valid = false
                for (i in 1..<size) {
                    valid = valid or (data[i + offset] != 0xFF.toByte())
                }
                valid = if (signed)
                    valid or (data[offset] != 0x7F.toByte())
                else
                    valid or (data[offset] != 0xFF.toByte())
            }
            return offset + size
        }

        fun copyFrom(i: IntValue) {
            value = i.value
            valid = i.valid
        }
    }

    class TimeValue : IntValue(4, false) {
        fun asTime(): Instant? {
            return if (valid) Instant.ofEpochSecond(value) else null
        }
    }
}