package com.aboni.n2kRouter

import kotlin.experimental.and
import kotlin.math.roundToInt

class Calibration(val data: ByteArray) {
    val interval = 360 / (data.size - 1)
    val size = data.size - 1

    val intervalBit = 360.0 / ((data.size - 1) * 8)

    val calibValues = BooleanArray(size) { false }

    var valid = false

    init {
        var v = true;
        for (i in 0..<size) {
            calibValues[i] = (data[i+1].toInt() == -1)
            v = v and calibValues[i]
        }
        valid = v
    }

    fun isAngleOk(angle: Double): Boolean {
        /*val a = angle / intervalBit
        val bitN = a.roundToInt()
        val byteN = bitN / 8
        val byteBit = bitN % 8
        return data[byteN + 1].and((1 shl byteBit).toByte()).toInt() !=0*/


        val d = (angle.roundToInt() + 360) % 360
        val bucket = (d / interval)
        return calibValues[bucket]
    }

}