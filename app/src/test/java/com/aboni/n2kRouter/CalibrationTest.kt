package com.aboni.n2kRouter

import org.junit.Assert.*
import org.junit.Test

class CalibrationTest {
    @Test
    fun testCalibrationNoWastedBits() {
        val testData = byteArrayOf(16, 2, 4) // 2 bytes, 16 bits, hence a sample every 360/16=22.5 deg
        val c = Calibration(testData, 16, 0)
        assertFalse(c.isAngleOk(9.0))
        assertTrue(c.isAngleOk(40.0))
        assertFalse(c.isAngleOk(100.0))
        assertTrue(c.isAngleOk(245.0))
        assertFalse(c.isAngleOk(280.0))
        assertTrue(c.valid)
    }

    @Test
    fun testCalibrationWastedBits() {
        val testData = byteArrayOf(12, 2, 1) // 2 bytes, 16 bits, but only 12 samples, so one every 360/12=30 deg - 6 bits are "wasted"
        val c = Calibration(testData, 12, 0)
        assertFalse(c.isAngleOk(9.0))
        assertTrue(c.isAngleOk(40.0))
        assertFalse(c.isAngleOk(100.0))
        assertTrue(c.isAngleOk(245.0))
        assertFalse(c.isAngleOk(350.0))
        assertTrue(c.valid)
    }

    @Test
    fun testCalibrationValid() {
        val testData = byteArrayOf(12, 0xFF.toByte(), 0xFF.toByte()) // 2 bytes, 16 bits, but only 12 samples, so one every 360/12=30 deg - 6 bits are "wasted"
        val c = Calibration(testData, 12, 0)
        assertTrue(c.valid)
    }
}
