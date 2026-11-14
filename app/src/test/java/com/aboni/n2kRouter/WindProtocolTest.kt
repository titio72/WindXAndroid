package com.aboni.n2kRouter

import com.aboni.marine.link.Inbound
import com.aboni.marine.link.Packet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WindProtocolTest {

    private fun packet(wind: Short = 450, speedSmoothing: Byte = 30, tail: ByteArray = ByteArray(0)): ByteArray {
        val b = ByteBuffer.allocate(45 + tail.size).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(wind).putShort(wind).putShort(wind).putShort(45).putInt(2048).put(0)
        repeat(6) { b.putShort(100) }
        b.putShort(56).putShort(1234).put(0).putShort(0).put(100).put(5).put(20).put(speedSmoothing)
        b.put(80).put(0).put(0).put(1).put(1).put(0).put(0)
        b.put(0).put(0) // no calibration samples, score
        b.put(tail)
        return b.array()
    }

    /** The correction status the sensor appends after the variable-length calibration part. */
    private fun tail(id: Long, enabled: Int, corrected: Int, awa: Int, aws: Int): ByteArray =
        ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(id.toInt()).put(enabled.toByte()).put(corrected.toByte()).putShort(awa.toShort()).putShort(aws.toShort())
            .array()

    private val table = CorrectionTable(
        true,
        listOf(CorrectionRow(60, listOf(Anchor(45, -60, 0), Anchor(95, -40, 0), Anchor(150, -20, 0)))),
    )

    private fun decodeData(p: Packet, current: Data? = null) = (WindProtocol.decode(p, current) as Inbound.Data).value

    @Test fun decodesTheDataChannel() {
        val r = WindProtocol.decode(Packet(CHANNEL_DATA, packet()), null)
        assertTrue(r is Inbound.Data)
        val d = (r as Inbound.Data).value
        assertEquals(45.0, d.wind.value, 0.01)
        assertEquals(30L, d.speedSmoothing.value)
        assertEquals(1L, d.vaneType.value)
    }

    @Test fun eachPacketIsANewValue() {
        val a = WindProtocol.decode(Packet(CHANNEL_DATA, packet()), null) as Inbound.Data
        val b = WindProtocol.decode(Packet(CHANNEL_DATA, packet()), a.value) as Inbound.Data
        assertTrue(a.value !== b.value)
    }

    @Test fun ignoresOtherChannels() = assertNull(WindProtocol.decode(Packet(CHANNEL_CMD, packet()), null))

    @Test fun dropsATruncatedPacket() =
        assertNull(WindProtocol.decode(Packet(CHANNEL_DATA, packet().copyOf(20)), null))

    @Test fun commandsAreTextOnTheCommandChannel() {
        val p = WindProtocol.encode(7, WindCommand.Offset(-12))
        assertEquals(CHANNEL_CMD, p.channel)
        assertEquals("O-12", p.bytes.toString(Charsets.UTF_8))
    }

    @Test fun commandTexts() {
        assertEquals("H", WindCommand.Heartbeat.text)
        assertEquals("NWind2", WindCommand.DeviceName("Wind2").text)
        assertEquals("P", WindCommand.ToggleAutoCalibration.text)
        assertEquals("Q25", WindCommand.DirectionSmoothing(25).text)
        assertEquals("W30", WindCommand.SpeedSmoothing(30).text)
        assertEquals("K105", WindCommand.SpeedAdjustment(105).text)
        assertEquals("T60", WindCommand.CalibrationScoreThreshold(60).text)
        assertEquals("S10|4000|20|3990", WindCommand.SetCalibration(CalibrationRange(10, 4000, 20, 3990)).text)
        assertEquals("R", WindCommand.CommitCalibration.text)
        assertEquals("A", WindCommand.CancelCalibration.text)
        assertEquals("D1", WindCommand.Debug(true).text)
        assertEquals("D0", WindCommand.Debug(false).text)
        assertEquals("V1", WindCommand.VaneType(1).text)
    }

    @Test fun theSensorIsNeverPaired() {
        assertEquals(com.aboni.marine.link.ble.PairingPolicy.OnDemand, WIND_PROFILE.pairing)
        assertTrue(WIND_PROFILE.securedWrites.isEmpty())
    }

    @Test fun decodesTheCorrectionStatus() {
        val d = decodeData(Packet(CHANNEL_DATA, packet(tail = tail(0x5330A1DE, 1, 1, 382, 152))))
        assertEquals(0x5330A1DEL, d.tableId.value)
        assertEquals(true, d.correctionEnabled.asBoolean())
        assertEquals(true, d.corrected.asBoolean())
        assertEquals(382L, d.correctedAwa.value)
        assertEquals(152L, d.correctedAws.value)
    }

    @Test fun anUnavailableCorrectedWindIsInvalid() {
        val d = decodeData(Packet(CHANNEL_DATA, packet(tail = tail(0x5330A1DE, 1, 0, 0xFFFF, 0xFFFF))))
        assertTrue(d.tableId.valid)
        assertFalse(d.correctedAwa.valid)
        assertFalse(d.correctedAws.valid)
    }

    @Test fun olderFirmwareHasNoCorrectionFields() {
        val d = decodeData(Packet(CHANNEL_DATA, packet()))
        assertEquals(45.0, d.wind.value, 0.01)
        assertFalse(d.tableId.valid)
        assertFalse(d.corrected.valid)
    }

    @Test fun aTablePacketIsMergedIntoTheCurrentData() {
        val current = decodeData(Packet(CHANNEL_DATA, packet(tail = tail(table.id, 1, 1, 382, 152))))
        val merged = decodeData(Packet(CHANNEL_TABLE, table.encode()), current)
        assertEquals(table, merged.table)
        assertEquals(table.id, merged.table!!.id)
        assertEquals(45.0, merged.wind.value, 0.01)
        assertEquals(table.id, merged.tableId.value)
        assertTrue(merged !== current)
    }

    @Test fun theNextDataPacketKeepsTheTable() {
        val first = decodeData(Packet(CHANNEL_DATA, packet(tail = tail(table.id, 1, 1, 382, 152))))
        val withTable = decodeData(Packet(CHANNEL_TABLE, table.encode()), first)
        val next = decodeData(Packet(CHANNEL_DATA, packet(wind = 900, tail = tail(table.id, 1, 1, 382, 152))), withTable)
        assertEquals(table, next.table)
        assertEquals(90.0, next.wind.value, 0.01)
    }

    @Test fun aTablePacketWithABadCrcIsDropped() {
        val bad = table.encode().also { it[12] = (it[12] + 1).toByte() }
        assertNull(WindProtocol.decode(Packet(CHANNEL_TABLE, bad), null))
    }

    @Test fun aTablePacketBeforeAnyDataStillWorks() {
        val d = decodeData(Packet(CHANNEL_TABLE, table.encode()))
        assertEquals(table, d.table)
        assertFalse(d.tableId.valid)
    }

    @Test fun tableCommandTexts() {
        val row = CorrectionRow(100, listOf(Anchor(40, -35, 0), Anchor(95, -10, 0), Anchor(150, 20, 0)))
        assertEquals("C1", WindCommand.TableEnable(true).text)
        assertEquals("C0", WindCommand.TableEnable(false).text)
        assertEquals("U10.0,40,-3.5,0.0,95,-1.0,0.0,150,2.0,0.0", WindCommand.TableUpsert(row).text)
        assertEquals("Z10.0", WindCommand.TableDelete(100).text)
        assertEquals("Y", WindCommand.TableClear.text)
    }

    @Test fun aNegativeFractionKeepsItsSign() {
        val row = CorrectionRow(5, listOf(Anchor(40, -5, -1), Anchor(95, 5, 0), Anchor(150, 0, 0)))
        assertEquals("U0.5,40,-0.5,-0.1,95,0.5,0.0,150,0.0,0.0", WindCommand.TableUpsert(row).text)
    }

    @Test fun tableCommandsUseADotWhateverTheLocale() {
        val saved = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        try {
            assertEquals("Z10.5", WindCommand.TableDelete(105).text)
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test fun theTableCharacteristicIsReadOnDemand() {
        assertEquals(
            mapOf(java.util.UUID.fromString("b0ff19bd-7c2d-4215-8cc0-2a6db52cdf4f") to CHANNEL_TABLE),
            WIND_PROFILE.onDemand,
        )
        assertEquals(2, CHANNEL_TABLE)
    }
}
