package com.aboni.n2kRouter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindFormatTest {

    private fun int(v: Long, valid: Boolean = true, size: Int = 2) =
        IntValue(size, true).apply { value = v; this.valid = valid }

    @Test fun sideAngles() {
        assertEquals("S0", sideAngle(0.0))
        assertEquals("S45", sideAngle(45.2))
        assertEquals("S180", sideAngle(180.0))
        assertEquals("P90", sideAngle(270.0))
        assertEquals("P1", sideAngle(359.4))
        assertEquals("P10", sideAngle(-10.0))
    }

    @Test fun unknownValuesShowTheDash() {
        assertEquals(NO_VALUE, int(5, valid = false).text())
        assertEquals(NO_VALUE, signedDegrees(int(5, valid = false)))
        assertEquals(NO_VALUE, percentAsFactor(int(5, valid = false)))
        assertEquals(NO_VALUE, vaneTypeName(int(0, valid = false, size = 1)))
    }

    @Test fun formats() {
        assertEquals("+5", signedDegrees(int(5)))
        assertEquals("-12", signedDegrees(int(-12)))
        assertEquals("ST50", vaneTypeName(int(0, size = 1)))
        assertEquals("ST60", vaneTypeName(int(1, size = 1)))
        assertEquals("Unknown", vaneTypeName(int(7, size = 1)))
    }

    @Test fun steppingStaysInRange() {
        assertEquals(15, stepped(int(10), 5, 0..100))
        assertEquals(100, stepped(int(98), 5, 0..100))
        assertEquals(0, stepped(int(3), -5, 0..100))
        assertNull(stepped(int(3, valid = false), 5, 0..100))
    }

    @Test fun rangeStepMovesOneBound() {
        val d = Data()
        d.iSinLow.value = 100; d.iSinHigh.value = 4000; d.iCosLow.value = 200; d.iCosHigh.value = 3900
        val r = steppedRange(d, RangeBound.CosHigh, 250)
        assertEquals(listOf(100, 4000, 200, ADC_MAX), listOf(r.sinLow, r.sinHigh, r.cosLow, r.cosHigh))
        assertEquals(0, steppedRange(d, RangeBound.SinLow, -250).sinLow)
    }

    @Test fun outOfRangeBoundsStartAtTheExtremes() {
        val d = Data()
        d.iSinLow.value = 65535; d.iSinHigh.value = 65535; d.iCosLow.value = -1; d.iCosHigh.value = 5000
        val r = steppedRange(d, RangeBound.SinLow, 1)
        assertEquals(listOf(1, ADC_MAX, 0, ADC_MAX), listOf(r.sinLow, r.sinHigh, r.cosLow, r.cosHigh))
    }

    @Test fun commitNeedsAValidManualCalibration() {
        val d = Data()
        assertFalse(canCommitCalibration(null))
        assertFalse(canCommitCalibration(d))
        d.autoCalibration.apply { value = 0; valid = true }
        d.calibValid.apply { value = 1; valid = true }
        assertTrue(canCommitCalibration(d))
        d.autoCalibration.value = 1
        assertFalse(canCommitCalibration(d))
    }

    @Test fun alertsInWords() {
        val d = Data()
        assertTrue(alerts(d).isEmpty())
        d.errorAngle.apply { value = 2; valid = true }
        d.n2kBusError.apply { value = 1; valid = true }
        assertEquals(listOf("Off calibration", "NMEA 2000 bus error"), alerts(d))
        d.errorAngle.value = 1
        assertEquals("Transducer error", alerts(d).first())
    }

    @Test fun deviceNames() {
        assertEquals("Enter a name", deviceNameError("  "))
        assertNull(deviceNameError("Wind"))
        assertNull(deviceNameError("A".repeat(15)))
        assertEquals("At most 15 characters", deviceNameError("A".repeat(16)))
    }
}
