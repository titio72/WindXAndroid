package com.aboni.n2kRouter

import kotlin.experimental.and

class Calibration(): BaseValue() {

    constructor(d: ByteArray, n: Int, s: Int): this() {
        if (n==0) throw RuntimeException("no samples")
        if (n>(d.size*8)) throw RuntimeException("too many samples")

        score = s
        nSamples = n
        data = d.copyOf()
        valid = true
    }

    constructor(c: Calibration) : this() {
        copyFrom(c)
    }

    private var data: ByteArray? = null
    private var nSamples: Int = 0
    var score: Int = 0
    val intervalBit: Double
        get() = if (nSamples==0) 0.0 else 360.0 / nSamples

    private fun isSampleOk(bitN: Int): Boolean {
        if (data==null) return false

        val byteN = bitN / 8
        val byteBit = bitN % 8
        return data!![byteN + 1].and((1 shl byteBit).toByte()).toInt() !=0
    }

    fun isAngleOk(angle: Double): Boolean {
        if (nSamples==0) return false

        val bitN = (angle / intervalBit).toInt()
        return isSampleOk(bitN)
    }

    override fun clone(): BaseValue {
        return Calibration(this)
    }

    override fun copyFrom(v: BaseValue) {
        if (v !is Calibration) throw RuntimeException("wrong type")
        score = v.score
        nSamples = v.nSamples
        data = if (v.data==null) null else v.data!!.copyOf()
        valid = nSamples>0
    }

    override fun parseValue(data: ByteArray, offset: Int): Int {
        nSamples = data[offset].toInt()
        if (nSamples==0) {
            valid = false
            score = 0
            this.data = null
            return offset + 1
        } else {
            val calibBufferSize = nSamples / 8 + (if (nSamples % 8 > 0) 1 else 0)
            this.data = ByteArray(calibBufferSize + 1)
            for (i in 0..calibBufferSize) {
                this.data!![i] = data[offset + i]
            }
            score = data[offset + calibBufferSize + 1].toInt()
            valid = true
            return offset + calibBufferSize + 2
        }
    }
}