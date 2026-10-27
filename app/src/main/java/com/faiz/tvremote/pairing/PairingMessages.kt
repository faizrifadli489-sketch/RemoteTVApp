package com.faiz.tvremote.pairing

import com.faiz.tvremote.proto.ProtoCodec
import java.io.ByteArrayOutputStream

/**
 * Builder pesan pairing (pairingmessage.proto).
 * Mirror dari PairingMessageManager.js.
 *
 * PairingMessage { protocol_version=1, status=2, request_case=3,
 *   pairing_request=10, pairing_request_ack=11, pairing_option=20,
 *   pairing_configuration=30, pairing_configuration_ack=31,
 *   pairing_secret=40, pairing_secret_ack=41 }
 */
object PairingMessages {
    const val STATUS_OK = 200

    fun pairingRequest(serviceName: String, clientName: String): ByteArray {
        val req = ByteArrayOutputStream().apply {
            ProtoCodec.encodeStringField(1, serviceName, this)   // service_name
            ProtoCodec.encodeStringField(2, clientName, this)     // client_name
        }.toByteArray()
        val msg = ByteArrayOutputStream().apply {
            ProtoCodec.encodeVarintField(1, 2, this)              // protocol_version
            ProtoCodec.encodeVarintField(2, STATUS_OK.toLong(), this)
            ProtoCodec.encodeMessageField(10, req, this)          // pairing_request
        }.toByteArray()
        return ProtoCodec.delimited(msg)
    }

    fun pairingOption(): ByteArray {
        // PairingEncoding { type=1 HEXADECIMAL(3), symbol_length=2 (6) }
        val enc = ByteArrayOutputStream().apply {
            ProtoCodec.encodeVarintField(1, 3, this)
            ProtoCodec.encodeVarintField(2, 6, this)
        }.toByteArray()
        // PairingOption { input_encodings=1 [enc], preferred_role=3 INPUT(1) }
        val opt = ByteArrayOutputStream().apply {
            ProtoCodec.encodeMessageField(1, enc, this)
            ProtoCodec.encodeVarintField(3, 1, this)
        }.toByteArray()
        val msg = ByteArrayOutputStream().apply {
            ProtoCodec.encodeVarintField(1, 2, this)
            ProtoCodec.encodeVarintField(2, STATUS_OK.toLong(), this)
            ProtoCodec.encodeMessageField(20, opt, this)
        }.toByteArray()
        return ProtoCodec.delimited(msg)
    }

    fun pairingConfiguration(): ByteArray {
        val enc = ByteArrayOutputStream().apply {
            ProtoCodec.encodeVarintField(1, 3, this)
            ProtoCodec.encodeVarintField(2, 6, this)
        }.toByteArray()
        // PairingConfiguration { encoding=1, client_role=2 INPUT(1) }
        val cfg = ByteArrayOutputStream().apply {
            ProtoCodec.encodeMessageField(1, enc, this)
            ProtoCodec.encodeVarintField(2, 1, this)
        }.toByteArray()
        val msg = ByteArrayOutputStream().apply {
            ProtoCodec.encodeVarintField(1, 2, this)
            ProtoCodec.encodeVarintField(2, STATUS_OK.toLong(), this)
            ProtoCodec.encodeMessageField(30, cfg, this)
        }.toByteArray()
        return ProtoCodec.delimited(msg)
    }

    fun pairingSecret(secret: ByteArray): ByteArray {
        // PairingSecret { secret=1 bytes }
        val s = ByteArrayOutputStream().apply {
            ProtoCodec.encodeBytesField(1, secret, this)
        }.toByteArray()
        val msg = ByteArrayOutputStream().apply {
            ProtoCodec.encodeVarintField(1, 2, this)
            ProtoCodec.encodeVarintField(2, STATUS_OK.toLong(), this)
            ProtoCodec.encodeMessageField(40, s, this)
        }.toByteArray()
        return ProtoCodec.delimited(msg)
    }

    data class Parsed(
        val status: Int,
        val hasRequestAck: Boolean,
        val hasOption: Boolean,
        val hasConfigAck: Boolean,
        val hasSecretAck: Boolean,
    )

    fun parse(buf: ByteArray): Parsed {
        val r = ProtoCodec.Reader(buf)
        var status = 0
        var reqAck = false; var opt = false; var cfgAck = false; var secAck = false
        while (r.remaining > 0) {
            val tag = r.readVarint()
            val field = (tag shr 3).toInt()
            val wire = (tag and 7).toInt()
            when (field) {
                2 -> if (wire == 0) status = r.readVarint().toInt() else r.skipField(wire)
                11 -> { reqAck = true; r.skipField(wire) }
                20 -> { opt = true; r.skipField(wire) }
                31 -> { cfgAck = true; r.skipField(wire) }
                41 -> { secAck = true; r.skipField(wire) }
                else -> r.skipField(wire)
            }
        }
        return Parsed(status, reqAck, opt, cfgAck, secAck)
    }
}
