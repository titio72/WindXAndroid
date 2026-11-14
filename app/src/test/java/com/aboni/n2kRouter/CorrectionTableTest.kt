package com.aboni.n2kRouter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Golden values come from the firmware's test_correctionprotocol. */
class CorrectionTableTest {

    private fun bytes(hex: String) = hex.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()

    private val t1Payload = bytes(
        "FE 9F 01 01 DE A1 30 53 01 02 3C 00 2D C4 FF 00 00 5F D8 FF 00 00 96 EC FF 00 00 " +
            "64 00 28 DD FF 00 00 5F F6 FF 00 00 96 14 00 05 00",
    )

    private val t1 = CorrectionTable(
        enabled = true,
        rows = listOf(
            CorrectionRow(60, listOf(Anchor(45, -60, 0), Anchor(95, -40, 0), Anchor(150, -20, 0))),
            CorrectionRow(100, listOf(Anchor(40, -35, 0), Anchor(95, -10, 0), Anchor(150, 20, 5))),
        ),
    )

    @Test fun crc32OfTheCheckString() =
        assertEquals(0xCBF43926L, crc32("123456789".toByteArray(), 0))

    @Test fun encodesTheGoldenPayload() = assertArrayEquals(t1Payload, t1.encode())

    @Test fun idsMatchTheFirmware() {
        assertEquals(0x5330A1DEL, t1.id)
        assertEquals(0xC640754BL, t1.copy(enabled = false).id)
        val empty = CorrectionTable(false, emptyList())
        assertEquals(0x41D912FFL, empty.id)
        assertEquals(10, empty.encode().size)
    }

    @Test fun decodesTheGoldenPayload() = assertEquals(t1, CorrectionTable.decode(t1Payload))

    @Test fun roundTripsTheEmptyTable() {
        val empty = CorrectionTable(false, emptyList())
        assertEquals(empty, CorrectionTable.decode(empty.encode()))
    }

    @Test fun roundTripsAFullTable() {
        val row = { tws: Int -> CorrectionRow(tws, listOf(Anchor(40, -35, 0), Anchor(95, -10, 0), Anchor(150, 20, 5))) }
        val full = CorrectionTable(true, (1..10).map { row(it * 10) })
        val payload = full.encode()
        assertEquals(180, payload.size)
        assertEquals(full, CorrectionTable.decode(payload))
    }

    @Test fun rejectsABadCrc() {
        val p = t1Payload.copyOf().also { it[12] = (it[12] + 1).toByte() }
        assertNull(CorrectionTable.decode(p))
    }

    @Test fun rejectsAChangedStoredCrc() {
        val p = t1Payload.copyOf().also { it[4] = (it[4] + 1).toByte() }
        assertNull(CorrectionTable.decode(p))
    }

    @Test fun rejectsAForeignManufacturer() {
        val p = t1Payload.copyOf().also { it[0] = 0xFD.toByte() }
        assertNull(CorrectionTable.decode(p))
    }

    @Test fun rejectsAnotherMessageTypeOrVersion() {
        assertNull(CorrectionTable.decode(t1Payload.copyOf().also { it[2] = 2 }))
        assertNull(CorrectionTable.decode(t1Payload.copyOf().also { it[3] = 2 }))
    }

    @Test fun rejectsMoreThanTenRows() {
        // A payload that claims 11 rows, with a consistent length and CRC, is still not a table.
        val body = ByteArray(2 + 11 * 17).also { it[0] = 1; it[1] = 11 }
        val p = ByteArray(8) + body
        p[0] = 0xFE.toByte(); p[1] = 0x9F.toByte(); p[2] = 1; p[3] = 1
        val crc = crc32(p, 8)
        for (i in 0..3) p[4 + i] = (crc shr (8 * i)).toByte()
        assertNull(CorrectionTable.decode(p))
    }

    @Test fun rejectsALengthThatDoesNotMatchTheRowCount() {
        assertNull(CorrectionTable.decode(t1Payload.copyOf(t1Payload.size - 1)))
        assertNull(CorrectionTable.decode(t1Payload + byteArrayOf(0)))
    }

    @Test fun rejectsAShortPayload() {
        assertNull(CorrectionTable.decode(t1Payload.copyOf(9)))
        assertNull(CorrectionTable.decode(ByteArray(0)))
        assertNotNull(CorrectionTable.decode(CorrectionTable(false, emptyList()).encode()))
    }
}
