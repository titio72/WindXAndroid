package com.aboni.n2kRouter

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

const val MAX_ROWS = 10
const val MAX_TWS = 1000   // 100.0 kn
const val MAX_DA = 300     // 30.0 deg
const val MAX_DS = 100     // 10.0 kn

private const val MANUFACTURER_CODE = 2046
private const val INDUSTRY_MARINE = 4
private const val MESSAGE_TYPE_TABLE = 1
private const val PAYLOAD_VERSION = 1
private const val HEADER_SIZE = 8
private const val ROW_SIZE = 17

/** One point of a row: the angle (deg) with its angle correction (0.1 deg) and speed correction (0.1 kn). */
data class Anchor(val angle: Int, val dA: Int, val dS: Int)

/** A row of the table: the true wind speed (0.1 kn) and its three anchors. */
data class CorrectionRow(val tws: Int, val anchors: List<Anchor>)

/** The sensor's true-wind correction table, as it publishes it (the PGN 130900 payload). */
data class CorrectionTable(val enabled: Boolean, val rows: List<CorrectionRow>) {

    /** The sensor's payload for this table, byte for byte. */
    fun encode(): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + 2 + rows.size * ROW_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(((MANUFACTURER_CODE and 0x7FF) or (3 shl 11) or (INDUSTRY_MARINE shl 13)).toShort())
        buf.put(MESSAGE_TYPE_TABLE.toByte()).put(PAYLOAD_VERSION.toByte())
        buf.putInt(0)
        buf.put(if (enabled) 1 else 0).put(rows.size.toByte())
        for (row in rows) {
            buf.putShort(row.tws.toShort())
            for (a in row.anchors) buf.put(a.angle.toByte()).putShort(a.dA.toShort()).putShort(a.dS.toShort())
        }
        val payload = buf.array()
        buf.putInt(4, crc32(payload, HEADER_SIZE).toInt())
        return payload
    }

    /** What the sensor reports as `table_id`: the CRC-32 of the payload from the enabled byte on. */
    val id: Long get() = crc32(encode(), HEADER_SIZE)

    companion object {
        /** Null unless [payload] is a well-formed table: framing, type, version, length and CRC all check out. */
        fun decode(payload: ByteArray): CorrectionTable? {
            if (payload.size < HEADER_SIZE + 2) return null
            val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            val head = buf.getShort(0).toInt() and 0xFFFF
            if ((head and 0x7FF) != MANUFACTURER_CODE || (head ushr 13) != INDUSTRY_MARINE) return null
            if (payload[2].toInt() != MESSAGE_TYPE_TABLE || payload[3].toInt() != PAYLOAD_VERSION) return null
            val n = payload[9].toInt() and 0xFF
            if (n > MAX_ROWS || payload.size != HEADER_SIZE + 2 + n * ROW_SIZE) return null
            if ((buf.getInt(4).toLong() and 0xFFFFFFFFL) != crc32(payload, HEADER_SIZE)) return null
            buf.position(HEADER_SIZE + 2)
            val rows = List(n) {
                val tws = buf.getShort().toInt() and 0xFFFF
                CorrectionRow(tws, List(3) {
                    val angle = buf.get().toInt() and 0xFF
                    Anchor(angle, buf.getShort().toInt(), buf.getShort().toInt())
                })
            }
            return CorrectionTable(payload[8].toInt() != 0, rows)
        }
    }
}

/** CRC-32 (IEEE 802.3, as the firmware computes it) of [bytes] from index [from] to the end, as an unsigned value. */
fun crc32(bytes: ByteArray, from: Int): Long =
    CRC32().apply { update(bytes, from, bytes.size - from) }.value
