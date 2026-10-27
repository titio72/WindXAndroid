package com.aboni.n2kRouter

import com.aboni.marine.link.Inbound
import com.aboni.marine.link.Packet
import com.aboni.marine.link.Protocol
import com.aboni.marine.link.Replies
import com.aboni.marine.link.ble.BleProfile
import com.aboni.marine.link.ble.PairingPolicy
import java.util.UUID

private val SERVICE_UUID = UUID.fromString("32890585-c6ee-498b-9e7a-044baefb6542")
private val CHARACTERISTIC_DATA_UUID = UUID.fromString("003d0cab-70f7-43ac-8ab9-db26466572af")
private val CHARACTERISTIC_CMD_UUID = UUID.fromString("c3fe2075-ac6c-40bf-8073-73a110453725")

const val CHANNEL_DATA = 0
const val CHANNEL_CMD = 1

/** The wind sensor over BLE: data is indicated (and read once on connect), commands are text written to one characteristic. */
val WIND_PROFILE = BleProfile(
    service = SERVICE_UUID,
    notify = mapOf(CHARACTERISTIC_DATA_UUID to CHANNEL_DATA),
    readOnConnect = mapOf(CHARACTERISTIC_DATA_UUID to CHANNEL_DATA),
    write = mapOf(CHANNEL_CMD to CHARACTERISTIC_CMD_UUID),
    // The firmware's data buffer is up to 128 bytes.
    mtu = 128,
    // The sensor never asks for a passkey: no secured channel, so it is never paired.
    pairing = PairingPolicy.OnDemand,
)

/** Commands understood by the sensor firmware: one letter, then the argument. */
sealed class WindCommand(val text: String) {
    data object Heartbeat : WindCommand("H")
    data class DeviceName(val name: String) : WindCommand("N$name")
    data object ToggleAutoCalibration : WindCommand("P")
    data class DirectionSmoothing(val percent: Int) : WindCommand("Q$percent")
    data class SpeedSmoothing(val percent: Int) : WindCommand("W$percent")
    data class Offset(val degrees: Int) : WindCommand("O$degrees")
    data class SpeedAdjustment(val percent: Int) : WindCommand("K$percent")
    data class CalibrationScoreThreshold(val score: Int) : WindCommand("T$score")
    data class SetCalibration(val range: CalibrationRange) :
        WindCommand("S${range.sinLow}|${range.sinHigh}|${range.cosLow}|${range.cosHigh}")
    data object CommitCalibration : WindCommand("R")
    data object CancelCalibration : WindCommand("A")
    data class Debug(val on: Boolean) : WindCommand("D${if (on) 1 else 0}")
    data class VaneType(val type: Int) : WindCommand("V$type")
}

/** Decodes the sensor's data packet; the sensor does not reply to commands. */
object WindProtocol : Protocol<Data, WindCommand, Nothing> {
    override fun decode(p: Packet, current: Data?): Inbound<Data, Nothing>? {
        if (p.channel != CHANNEL_DATA) return null
        val d = Data()
        return try {
            d.parse(p.bytes)
            Inbound.Data(d)
        } catch (e: RuntimeException) {
            appendLog("Dropped malformed data packet (${p.bytes.size} bytes): ${e.message}")
            null
        }
    }

    override fun encode(seq: Int, cmd: WindCommand) = Packet(CHANNEL_CMD, cmd.text.toByteArray(Charsets.UTF_8))

    override val replies: Replies<Nothing>? = null
}
