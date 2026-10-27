package com.aboni.n2kRouter

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DataTest {

    @Test
    fun testIsCalibrating() {
        val data = Data()
        // Initially, calibrationProgress has no samples, so isCalibrating should be false
        assertFalse("isCalibrating should be false when calibrationProgress is empty", data.isCalibrating)

        // Simulate starting calibration
        // We'll use a minimal valid byte array for the Calibration object
        val calibData = byteArrayOf(8, 0) // Represents a minimal Calibration object
        data.calibrationProgress.copyFrom(Calibration(calibData, 8, 0))

        // Now, isCalibrating should be true
        assertTrue("isCalibrating should be true when calibrationProgress has samples", data.isCalibrating)
    }

    @Test
    fun testCopyFrom() {
        val sourceData = Data()
        val targetData = Data()

        // Populate sourceData with some non-default values
        sourceData.wind.value = 15.5
        sourceData.ellipse.value = 0.123
        sourceData.heap.value = 1024
        sourceData.errorAngle.value = 2
        sourceData.iSin.value = 100
        sourceData.iSinLow.value = 50
        sourceData.iSinHigh.value = 150
        sourceData.iCos.value = 200
        sourceData.iCosLow.value = 150
        sourceData.iCosHigh.value = 250
        sourceData.speed.value = 8.2
        sourceData.errorSpeed.value = 1
        sourceData.angleOffset.value = -5
        sourceData.speedAdjustment.value = 95

        // Perform the copy
        targetData.copyFrom(sourceData)

        // Assert that all values have been copied correctly
        assertEquals("wind value should be copied", sourceData.wind.value, targetData.wind.value, 0.0)
        assertEquals("err value should be copied", sourceData.ellipse.value, targetData.ellipse.value, 0.0)
        assertEquals("heap value should be copied", sourceData.heap.value, targetData.heap.value)
        assertEquals("error value should be copied", sourceData.errorAngle.value, targetData.errorAngle.value)
        assertEquals("iSin value should be copied", sourceData.iSin.value, targetData.iSin.value)
        assertEquals("iSinLow value should be copied", sourceData.iSinLow.value, targetData.iSinLow.value)
        assertEquals("iSinHigh value should be copied", sourceData.iSinHigh.value, targetData.iSinHigh.value)
        assertEquals("iCos value should be copied", sourceData.iCos.value, targetData.iCos.value)
        assertEquals("iCosLow value should be copied", sourceData.iCosLow.value, targetData.iCosLow.value)
        assertEquals("iCosHigh value should be copied", sourceData.iCosHigh.value, targetData.iCosHigh.value)
        assertEquals("speed value should be copied", sourceData.speed.value, targetData.speed.value, 0.0)
        assertEquals("errorSpeed value should be copied", sourceData.errorSpeed.value, targetData.errorSpeed.value)
        assertEquals("angleOffset value should be copied", sourceData.angleOffset.value, targetData.angleOffset.value)
        assertEquals("speedAdjustment value should be copied", sourceData.speedAdjustment.value, targetData.speedAdjustment.value)
    }

    @Test
    fun testParse() {
        val data = Data()
        val buffer = ByteBuffer.allocate(45).order(ByteOrder.LITTLE_ENDIAN)

        // Populate the buffer with sample data according to the class structure
        buffer.putShort(123)       // wind (12.3)
        buffer.putShort(120)       // windSmooth (12.0)
        buffer.putShort(121)       // windOutput (12.1)
        buffer.putShort(45)        // err (0.045)
        buffer.putInt(2048)        // heap
        buffer.put(3)              // errorAngle
        buffer.putShort(111)       // iSin
        buffer.putShort(100)       // iSinLow
        buffer.putShort(122)       // iSinHigh
        buffer.putShort(222)       // iCos
        buffer.putShort(200)       // iCosLow
        buffer.putShort(244)       // iCosHigh
        buffer.putShort(56)        // speed (5.6)
        buffer.putShort(1234)      // frequency (12.34)
        buffer.put(7)              // errorSpeed
        buffer.putShort(-10)       // angleOffset
        buffer.put(98)             // speedAdjustment
        buffer.put(5)              // n2kSource
        buffer.put(2)              // angleSmoothing
        buffer.put(3)              // speedSmoothing
        buffer.put(80)             // calibrationThreshold
        buffer.put(0)              // autoCalibration
        buffer.put(0)              // n2kBusError
        buffer.put(1)              // vaneType
        buffer.put(1)              // calibValid
        buffer.put(0)              // calibInScore
        buffer.put(0)              // debug
        buffer.put(0)              // calibration nSamples = 0 (no calibration data)
        buffer.put(0)              // calibration score

        // Call the parse method
        data.parse(buffer.array())

        // Assert that the values were parsed correctly
        assertEquals("Parsed wind value is incorrect", 12.3, data.wind.value, 0.01)
        assertEquals("Parsed err value is incorrect", 0.045, data.ellipse.value, 0.0001)
        assertEquals("Parsed heap value is incorrect", 2048, data.heap.value)
        assertEquals("Parsed error value is incorrect", 3, data.errorAngle.value)
        assertEquals("Parsed iSin value is incorrect", 111, data.iSin.value)
        assertEquals("Parsed iSinLow value is incorrect", 100, data.iSinLow.value)
        assertEquals("Parsed iSinHigh value is incorrect", 122, data.iSinHigh.value)
        assertEquals("Parsed iCos value is incorrect", 222, data.iCos.value)
        assertEquals("Parsed iCosLow value is incorrect", 200, data.iCosLow.value)
        assertEquals("Parsed iCosHigh value is incorrect", 244, data.iCosHigh.value)
        assertEquals("Parsed speed value is incorrect", 5.6, data.speed.value, 0.01)
        assertEquals("Parsed frequency value is incorrect", 12.34, data.frequency.value, 0.001)
        assertEquals("Parsed errorSpeed value is incorrect", 7, data.errorSpeed.value)
        assertEquals("Parsed angleOffset value is incorrect", -10, data.angleOffset.value)
        assertEquals("Parsed speedAdjustment value is incorrect", 98, data.speedAdjustment.value)
        assertEquals("Parsed calibrationThreshold value is incorrect", 80, data.calibrationThreshold.value)
        assertEquals("Parsed vaneType value is incorrect", 1, data.vaneType.value)
        assertEquals("Parsed calibValid value is incorrect", true, data.calibValid.asBoolean())
        assertFalse("calibrationProgress should be invalid when nSamples is 0", data.calibrationProgress.valid)
    }
}
