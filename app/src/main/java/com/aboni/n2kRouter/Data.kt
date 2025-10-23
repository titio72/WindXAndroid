package com.aboni.n2kRouter


class Data: BaseData() {

    fun copyFrom(data: Data) {
        wind.copyFrom(data.wind)
        err.copyFrom(data.err)
        heap.copyFrom(data.heap)
        error.copyFrom(data.error)
        iSin.copyFrom(data.iSin)
        iSinLow.copyFrom(data.iSinLow)
        iSinHigh.copyFrom(data.iSinHigh)
        iCos.copyFrom(data.iCos)
        iCosLow.copyFrom(data.iCosLow)
        iCosHigh.copyFrom(data.iCosHigh)
        speed.copyFrom(data.speed)
        errorSpeed.copyFrom(data.errorSpeed)
        angleOffset.copyFrom(data.angleOffset)
        speedAdjustment.copyFrom(data.speedAdjustment)
    }

    val wind = DoubleValue(2, false, 0.1)
    val err = DoubleValue(2, false, 0.001)
    val heap = IntValue(4, false)
    val error = IntValue(4, true)
    val iSin = IntValue(2, false)
    val iSinLow = IntValue(2, false)
    val iSinHigh = IntValue(2, false)
    val iCos = IntValue(2, false)
    val iCosLow = IntValue(2, false)
    val iCosHigh = IntValue(2, false)
    val speed = DoubleValue(2, false, 0.1)
    val errorSpeed = IntValue(4, true)
    val angleOffset = IntValue(4, true)
    val speedAdjustment = IntValue(2, false)

    var calibrationProgress: Calibration? = null

    val isCalibrating: Boolean
        get() = calibrationProgress!=null

    override fun parse(data: ByteArray) {
        /*var s = "->"
        for (b in data) s = "$s $b"
        appendLog("received data: $s")*/
        var offset = 0;
        offset = wind.parseValue(data, offset)
        offset = err.parseValue(data, offset)
        offset = heap.parseValue(data, offset)
        offset = error.parseValue(data, offset)
        offset = iSin.parseValue(data, offset)
        offset = iSinLow.parseValue(data, offset)
        offset = iSinHigh.parseValue(data, offset)
        offset = iCos.parseValue(data, offset)
        offset = iCosLow.parseValue(data, offset)
        offset = iCosHigh.parseValue(data, offset)
        offset = speed.parseValue(data, offset)
        offset = errorSpeed.parseValue(data, offset)
        offset = angleOffset.parseValue(data, offset)
        offset = speedAdjustment.parseValue(data, offset);

        val calibBufferSize = data[offset].toInt()
        if (calibBufferSize == 0) {
            calibrationProgress = null
        } else {
            val bb = ByteArray(calibBufferSize + 1)
            for (i in 0..calibBufferSize) {
                bb[i] = data[offset + i]
            }
            calibrationProgress = Calibration(bb)
        }
    }
}