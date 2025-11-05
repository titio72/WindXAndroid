package com.aboni.n2kRouter

import java.time.Instant

abstract class BaseValue() {
    var valid: Boolean = false

    abstract fun clone(): BaseValue

    abstract fun copyFrom(v: BaseValue)

    abstract fun parseValue(data: ByteArray, offset: Int): Int
}

class DoubleValue(sz: Int, sc: Double): BaseValue() {
    private var iValue = IntValue(sz, true)
    private var scale = sc

    var value = Double.NaN

    override fun parseValue(data: ByteArray, offset: Int): Int {
        val newOffset = iValue.parseValue(data, offset)
        valid = iValue.valid
        if (valid) value = iValue.value * scale
        return newOffset
    }

    override fun clone(): BaseValue {
        val d = DoubleValue(iValue.size, scale)
        d.value = value
        d.valid = valid
        return d
    }

    override fun copyFrom(v: BaseValue) {
        if (v !is DoubleValue) throw RuntimeException("wrong type")
        valid = v.valid
        value = v.value
        scale = v.scale
        iValue.copyFrom(v.iValue)
    }

}

open class IntValue(sz: Int, sg: Boolean): BaseValue() {
    var signed = sg
    var value: Long = 0
    val size: Int = sz
    override fun parseValue(data: ByteArray, offset: Int): Int {
        val vv = byteToInt(data, offset, size, signed)
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
}

    override fun clone(): BaseValue {
        val d = IntValue(size, signed)
        d.value = value
        d.valid = valid
        return d
    }

    override fun copyFrom(v: BaseValue) {
        if (v !is IntValue) throw RuntimeException("wrong type")
        if (v.size != size) throw RuntimeException("wrong size")
        valid = v.valid
        value = v.value
        signed = v.signed
    }
}

class TimeValue : IntValue(4, false) {
    fun asTime(): Instant? {
        return if (valid) Instant.ofEpochSecond(value) else null
    }

    override fun clone(): BaseValue {
        val d = TimeValue()
        d.value = value
        d.valid = valid
        return d
    }
}
