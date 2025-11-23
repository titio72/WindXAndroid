package com.aboni.n2kRouter


class Data {

    fun copyFrom(data: Data) {
        for (i in fields.indices) fields[i].copyFrom(data.fields[i]);
    }

    val wind = DoubleValue(2, 0.1)
    val windSmooth = DoubleValue(2, 0.1)
    val windOutput = DoubleValue(2, 0.1)
    val ellipse = DoubleValue(2, 0.001)
    val heap = IntValue(4, false)
    val errorAngle = IntValue(1, true)
    val iSin = IntValue(2, false)
    val iSinLow = IntValue(2, false)
    val iSinHigh = IntValue(2, false)
    val iCos = IntValue(2, false)
    val iCosLow = IntValue(2, false)
    val iCosHigh = IntValue(2, false)
    val speed = DoubleValue(2, 0.1)
    val errorSpeed = IntValue(1, true)
    val angleOffset = IntValue(2, true)
    val speedAdjustment = IntValue(1, false)
    val speedSmoothing = IntValue(1, false)
    val angleSmoothing = IntValue(1, false)
    val n2kSource = IntValue(1, false)
    val autoCalibration = IntValue(1, false)
    val calibrationProgress = Calibration()
    val calibrationThreshold = IntValue(1, false)
    val vaneType = IntValue(1, false)
    val n2kBusError = IntValue(1, false)
    val calibValid = BooleanValue()
    val calibInScore = BooleanValue()
    val debug = BooleanValue()


    private val fields: Array<BaseValue> = arrayOf(
        wind, windSmooth, windOutput, ellipse, heap, errorAngle,
        iSin, iSinLow, iSinHigh, iCos, iCosLow, iCosHigh,
        speed, errorSpeed, angleOffset, speedAdjustment,
        n2kSource, angleSmoothing, speedSmoothing,
        calibrationThreshold, autoCalibration, calibrationProgress,
        n2kBusError, vaneType, calibValid, calibInScore, debug)


    fun dump(data: ByteArray) {
        var s = "->"
        for (b in data) s = "$s $b"
        appendLog("received data: $s")
    }

    fun parse(data: ByteArray) {
        var offset = 0
        for (f in fields) {
            offset = f.parseValue(data, offset)
        }
    }

    val isAutocalibrating: Boolean
            get() = autoCalibration.valid && autoCalibration.value==1L

    val isCalibrating: Boolean
        get() = calibrationProgress.valid

}