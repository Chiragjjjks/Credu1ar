package io.github.chiragjjjks.credu1ar.debug

import java.time.Instant
import java.time.ZoneId

/**
 * Builds genuine 3GPP SMS-DELIVER PDUs (GSM 7-bit, or UCS-2 when the text needs it) so the production receiver is
 * exercised exactly as Android would call it, including multi-part messages. Debug builds only.
 */
object SmsPdu {
    private val basic: Map<Char, Int> = buildMap {
        val table = "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u0000ÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
        table.forEachIndexed { i, c -> if (c != '\u0000') put(c, i) }
    }

    fun deliverPdus(sender: String, body: String, atMillis: Long): List<ByteArray> {
        val gsm7 = body.all { it in basic }
        val ref = (atMillis and 0xFF).toInt()
        val chunks: List<String> = if (gsm7) {
            if (body.length <= 160) listOf(body) else body.chunked(153)
        } else {
            if (body.length <= 70) listOf(body) else body.chunked(67)
        }
        return chunks.mapIndexed { i, chunk -> pdu(sender, chunk, gsm7, atMillis, if (chunks.size > 1) Triple(ref, chunks.size, i + 1) else null) }
    }

    private fun pdu(sender: String, text: String, gsm7: Boolean, atMillis: Long, concat: Triple<Int, Int, Int>?): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(0x00) // no SMSC
        out.write(if (concat != null) 0x44 else 0x04) // SMS-DELIVER (+ UDH indicator)
        // Originating address: numeric for phone numbers, alphanumeric (type 0xD0) for bank headers like VM-HDFCBK-S.
        val numeric = sender.trimStart('+').all(Char::isDigit)
        if (numeric) {
            val digits = sender.trimStart('+')
            out.write(digits.length); out.write(0x91)
            val padded = if (digits.length % 2 == 1) digits + "F" else digits
            padded.chunked(2).forEach { out.write((Character.digit(it[1], 16) shl 4) or Character.digit(it[0], 16)) }
        } else {
            val septets = sender.take(11).map { basic[it] ?: basic['X']!! }
            val packed = packSeptets(septets, 0)
            out.write((septets.size * 7 + 3) / 4); out.write(0xD0)
            out.write(packed)
        }
        out.write(0x00) // PID
        out.write(if (gsm7) 0x00 else 0x08) // DCS
        val t = Instant.ofEpochMilli(atMillis).atZone(ZoneId.of("Asia/Kolkata"))
        listOf(t.year % 100, t.monthValue, t.dayOfMonth, t.hour, t.minute, t.second).forEach { out.write(((it % 10) shl 4) or (it / 10)) }
        out.write(0x22) // +5h30m in quarter hours (22 -> swapped nibbles)
        val udh = concat?.let { byteArrayOf(0x05, 0x00, 0x03, it.first.toByte(), it.second.toByte(), it.third.toByte()) }
        if (gsm7) {
            val septets = text.map { basic.getValue(it) }
            val packed = packSeptets(septets, if (udh != null) 1 else 0)
            out.write((if (udh != null) 7 else 0) + septets.size)
            if (udh != null) out.write(udh)
            out.write(packed)
        } else {
            val bytes = text.toByteArray(Charsets.UTF_16BE)
            out.write((udh?.size ?: 0) + bytes.size)
            if (udh != null) out.write(udh)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    /** Packs 7-bit values little-endian into octets, starting [fillBits] bits in (1 after a 6-byte UDH). */
    private fun packSeptets(septets: List<Int>, fillBits: Int): ByteArray {
        val out = ByteArray((fillBits + septets.size * 7 + 7) / 8)
        var bit = fillBits
        for (s in septets) for (b in 0 until 7) {
            if ((s shr b) and 1 == 1) out[bit / 8] = (out[bit / 8].toInt() or (1 shl (bit % 8))).toByte()
            bit++
        }
        return out
    }
}
