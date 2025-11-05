package com.aboni.n2kRouter


class Data {

    fun copyFrom(data: Data) {
        wind.copyFrom(data.wind)
        windSmooth.copyFrom(data.windSmooth)
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
        n2kSource.copyFrom(data.n2kSource)
        angleSmoothing.copyFrom(data.angleSmoothing)
        speedSmoothing.copyFrom(data.speedSmoothing)
        calibrationThreshold.copyFrom(data.calibrationThreshold)
        autoCalibration.copyFrom(data.autoCalibration)
        calibrationProgress.copyFrom(data.calibrationProgress)
    }

    val wind = DoubleValue(2, 0.1)
    val windSmooth = DoubleValue(2, 0.1)
    val err = DoubleValue(2, 0.001)
    val heap = IntValue(4, false)
    val error = IntValue(4, true)
    val iSin = IntValue(2, false)
    val iSinLow = IntValue(2, false)
    val iSinHigh = IntValue(2, false)
    val iCos = IntValue(2, false)
    val iCosLow = IntValue(2, false)
    val iCosHigh = IntValue(2, false)
    val speed = DoubleValue(2, 0.1)
    val errorSpeed = IntValue(4, true)
    val angleOffset = IntValue(4, true)
    val speedAdjustment = IntValue(2, false)
    val speedSmoothing = IntValue(1, false)
    val angleSmoothing = IntValue(1, false)
    val n2kSource = IntValue(1, false)
    val autoCalibration = IntValue(1, false)
    val calibrationProgress = Calibration()
    val calibrationThreshold = IntValue(1, false)

    fun parse(data: ByteArray) {
        /*var s = "->"
        for (b in data) s = "$s $b"
        appendLog("received data: $s")*/
        var offset = 0;
        offset = wind.parseValue(data, offset)
        offset = windSmooth.parseValue(data, offset)
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
        offset = n2kSource.parseValue(data, offset)
        offset = angleSmoothing.parseValue(data, offset)
        offset = speedSmoothing.parseValue(data, offset)
        offset = calibrationThreshold.parseValue(data, offset)
        offset = autoCalibration.parseValue(data, offset)
        calibrationProgress.parseValue(data, offset)
    }

    val isAutocalibrating: Boolean
            get() = autoCalibration.valid && autoCalibration.value==1L

    val isCalibrating: Boolean
        get() = calibrationProgress.valid

    val state: Int
        get() {
            return if (calibrationProgress.valid && autoCalibration.valid && autoCalibration.value == 1L) ApplicationState.STATE_NORMAL
            else if (calibrationProgress.valid) ApplicationState.STATE_CALIBRATING
            else ApplicationState.STATE_NORMAL
        }
}