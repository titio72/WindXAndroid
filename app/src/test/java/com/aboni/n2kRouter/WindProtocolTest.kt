package com.aboni.n2kRouter

import com.aboni.marine.link.Inbound
import com.aboni.marine.link.Packet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WindProtocolTest {

    private fun packet(wind: Short = 450, speedSmoothing: Byte = 30): ByteArray {
        val b = ByteBuffer.allocate(45).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(wind).putShort(wind).putShort(wind).putShort(45).putInt(2048).put(0)
        repeat(6) { b.putShort(100) }
        b.putShort(56).putShort(1234).put(0).putShort(0).put(100).put(5).put(20).put(speedSmoothing)
        b.put(80).put(0).put(0).put(1).put(1).put(0).put(0)
        b.put(0).put(0) // no calibration samples, score
        return b.array()
    }

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
}
