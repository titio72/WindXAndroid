package com.aboni.n2kRouter

import org.junit.Assert.assertEquals
import org.junit.Test

class UtilsTest {

    private fun le(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test fun anUnsignedFourByteValueAboveTwoToThe31IsNotNegative() =
        assertEquals(0x8F123456L, byteToInt(le(0x56, 0x34, 0x12, 0x8F), 0, 4, signed = false))

    @Test fun theLargestUnsignedFourByteValueIsFFFFFFFF() =
        assertEquals(0xFFFFFFFEL, byteToInt(le(0xFE, 0xFF, 0xFF, 0xFF), 0, 4, signed = false))

    @Test fun aSignedFourByteValueKeepsItsSign() {
        assertEquals(-2L, byteToInt(le(0xFE, 0xFF, 0xFF, 0xFF), 0, 4, signed = true))
        assertEquals(0x7F123456L, byteToInt(le(0x56, 0x34, 0x12, 0x7F), 0, 4, signed = true))
    }

    @Test fun twoByteValuesAreUnchanged() {
        assertEquals(-10L, byteToInt(le(0xF6, 0xFF), 0, 2, signed = true))
        assertEquals(65526L, byteToInt(le(0xF6, 0xFF), 0, 2, signed = false))
    }

    @Test fun aTableIdAboveTwoToThe31ParsesAsItsCrc() {
        val d = Data()
        d.tableId.parseValue(le(0xDF, 0x7F, 0xD8, 0x8F), 0)
        assertEquals(0x8FD87FDFL, d.tableId.value)
    }
}
