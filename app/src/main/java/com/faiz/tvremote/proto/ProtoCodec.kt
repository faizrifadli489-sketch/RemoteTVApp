package com.faiz.tvremote.proto

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Encode/decode protobuf minimal untuk pesan androidtv-remote.
 * Hanya mendukung yang dipakai: varint, length-delimited (string/bytes/embedded),
 * enum (= varint), bool (= varint), repeated.
 * Framing: tiap pesan diawali varint panjang (encodeDelimited).
 */
object ProtoCodec {

    fun encodeVarint(value: Long, out: ByteArrayOutputStream) {
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }

    fun encodeTag(field: Int, wire: Int, out: ByteArrayOutputStream) {
        encodeVarint(((field shl 3) or wire).toLong(), out)
    }

    fun encodeVarintField(field: Int, value: Long, out: ByteArrayOutputStream) {
        encodeTag(field, 0, out)
        encodeVarint(value, out)
    }

    fun encodeBytesField(field: Int, bytes: ByteArray, out: ByteArrayOutputStream) {
        encodeTag(field, 2, out)
        encodeVarint(bytes.size.toLong(), out)
        out.write(bytes)
    }

    fun encodeStringField(field: Int, s: String, out: ByteArrayOutputStream) {
        encodeBytesField(field, s.toByteArray(Charsets.UTF_8), out)
    }

    fun encodeMessageField(field: Int, msg: ByteArray, out: ByteArrayOutputStream) {
        encodeBytesField(field, msg, out)
    }

    /** Bungkus pesan dengan prefix panjang (delimited). */
    fun delimited(msg: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        encodeVarint(msg.size.toLong(), out)
        out.write(msg)
        return out.toByteArray()
    }

    // ---------- decode ----------

    class Reader(val buf: ByteArray) {
        var pos = 0
        val remaining: Int get() = buf.size - pos

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = buf[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
            }
        }

        fun readBytes(): ByteArray {
            val len = readVarint().toInt()
            val out = buf.copyOfRange(pos, pos + len)
            pos += len
            return out
        }

        fun skipField(wire: Int) {
            when (wire) {
                0 -> readVarint()
                1 -> pos += 8
                2 -> pos += readVarint().toInt()
                5 -> pos += 4
                else -> error("wire type tak dikenal: $wire")
            }
        }
    }

    /** Baca satu pesan delimited dari stream (blocking). */
    fun readDelimited(input: InputStream): ByteArray {
        var result = 0L
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) error("stream tertutup")
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        val len = result.toInt()
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = input.read(buf, off, len - off)
            if (n < 0) error("stream terpotong")
            off += n
        }
        return buf
    }
}
