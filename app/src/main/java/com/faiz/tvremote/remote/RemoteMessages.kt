package com.faiz.tvremote.remote

import com.faiz.tvremote.proto.ProtoCodec
import java.io.ByteArrayOutputStream

/**
 * Builder pesan remote (remotemessage.proto).
 * Mirror dari RemoteMessageManager.js.
 *
 * RemoteMessage { remote_configure=1? ... } — nomor field sesuai pemakaian di JS:
 *  configure dipakai via create(payload{remoteConfigure}) -> field nomor sesuai proto asli.
 *
 * Nomor field RemoteMessage (dari AOSP + pemakaian JS):
 *  remote_key_inject=8, remote_app_link_launch_request=15,
 *  remote_configure=2? -> lihat createRemoteConfigure di JS memakai key "remoteConfigure".
 * Nomor field yang benar (protokol atvtv): configure=... dipakai handshake awal.
 */
object RemoteMessages {

    // Nomor field di RemoteMessage (dari remotemessage.proto)
    private const val F_CONFIGURE = 1
    private const val F_SET_ACTIVE = 2
    private const val F_PING_REQUEST = 8
    private const val F_PING_RESPONSE = 9
    private const val F_KEY_INJECT = 10
    private const val F_IME_KEY_INJECT = 20
    private const val F_START = 40
    private const val F_SET_VOLUME = 50
    private const val F_APP_LINK = 90

    fun configure(): ByteArray {
        // RemoteConfigure { code1=622(1), device_info=?(lihat JS: hanya code1 yang dipakai di verify) }
        // JS mengirim { remoteConfigure: { code1: 622, deviceInfo: {...} } }.
        // Field RemoteConfigure: code1 field 1 (int), device_info field 2.
        val deviceInfo = ByteArrayOutputStream().apply {
            ProtoCodec.encodeStringField(1, android.os.Build.MODEL ?: "?", this)      // model
            ProtoCodec.encodeStringField(2, android.os.Build.MANUFACTURER ?: "?", this) // vendor
            ProtoCodec.encodeVarintField(3, 1, this)                                   // unknown1
            ProtoCodec.encodeStringField(4, "1", this)                                 // unknown2
            ProtoCodec.encodeStringField(5, "androitv-remote", this)                   // packageName
            ProtoCodec.encodeStringField(6, "1.0.0", this)                             // appVersion
        }.toByteArray()
        val cfg = ByteArrayOutputStream().apply {
            ProtoCodec.encodeVarintField(1, 622, this)
            ProtoCodec.encodeMessageField(2, deviceInfo, this)
        }.toByteArray()
        val msg = ByteArrayOutputStream().apply {
            ProtoCodec.encodeMessageField(F_CONFIGURE, cfg, this)
        }.toByteArray()
        return ProtoCodec.delimited(msg)
    }

    fun setActive(active: Int = 622): ByteArray {
        // RemoteSetActive { active=1 int32 }
        val body = ByteArrayOutputStream().apply {
            ProtoCodec.encodeVarintField(1, active.toLong(), this)
        }.toByteArray()
        val msg = ByteArrayOutputStream().apply {
            ProtoCodec.encodeMessageField(F_SET_ACTIVE, body, this)
        }.toByteArray()
        return ProtoCodec.delimited(msg)
    }

    fun pingResponse(val1: Int): ByteArray {
        val body = ByteArrayOutputStream().apply {
            ProtoCodec.encodeVarintField(1, val1.toLong(), this)
        }.toByteArray()
        val msg = ByteArrayOutputStream().apply {
            ProtoCodec.encodeMessageField(F_PING_RESPONSE, body, this)
        }.toByteArray()
        return ProtoCodec.delimited(msg)
    }

    fun keyInject(keyCode: Int, direction: Int): ByteArray {
        // RemoteKeyInject { key_code=1, direction=2 }
        val body = ByteArrayOutputStream().apply {
            ProtoCodec.encodeVarintField(1, keyCode.toLong(), this)
            ProtoCodec.encodeVarintField(2, direction.toLong(), this)
        }.toByteArray()
        val msg = ByteArrayOutputStream().apply {
            ProtoCodec.encodeMessageField(F_KEY_INJECT, body, this)
        }.toByteArray()
        return ProtoCodec.delimited(msg)
    }

    fun appLinkLaunch(appLink: String): ByteArray {
        val body = ByteArrayOutputStream().apply {
            ProtoCodec.encodeStringField(1, appLink, this)
        }.toByteArray()
        val msg = ByteArrayOutputStream().apply {
            ProtoCodec.encodeMessageField(F_APP_LINK, body, this)
        }.toByteArray()
        return ProtoCodec.delimited(msg)
    }

    data class Parsed(
        val hasConfigure: Boolean = false,
        val hasSetActive: Boolean = false,
        val hasPingRequest: Boolean = false,
        val pingVal: Int = 0,
        val hasStart: Boolean = false,
        val started: Boolean = false,
        val hasVolume: Boolean = false,
        val volumeLevel: Int = 0,
        val volumeMax: Int = 0,
        val volumeMuted: Boolean = false,
        val currentApp: String? = null,
    )

    fun parse(buf: ByteArray): Parsed {
        val r = ProtoCodec.Reader(buf)
        var cfg = false; var active = false
        var ping = false; var pingVal = 0
        var start = false; var started = false
        var vol = false; var level = 0; var max = 0; var muted = false
        var app: String? = null
        while (r.remaining > 0) {
            val tag = r.readVarint()
            val field = (tag shr 3).toInt()
            val wire = (tag and 7).toInt()
            when (field) {
                2 -> { cfg = true; r.skipField(wire) }          // remote_configure
                8 -> {                                           // remote_ping_request { val1=1 }
                    ping = true
                    if (wire == 2) {
                        val inner = ProtoCodec.Reader(r.readBytes())
                        while (inner.remaining > 0) {
                            val t = inner.readVarint()
                            if ((t shr 3).toInt() == 1 && (t and 7).toInt() == 0) {
                                pingVal = inner.readVarint().toInt()
                            } else inner.skipField((t and 7).toInt())
                        }
                    }
                }
                40 -> {                                          // remote_start { started=1 }
                    start = true
                    if (wire == 2) {
                        val inner = ProtoCodec.Reader(r.readBytes())
                        while (inner.remaining > 0) {
                            val t = inner.readVarint()
                            if ((t shr 3).toInt() == 1 && (t and 7).toInt() == 0) {
                                started = inner.readVarint() != 0L
                            } else inner.skipField((t and 7).toInt())
                        }
                    }
                }
                50 -> {                                          // remote_set_volume_level
                    vol = true
                    if (wire == 2) {
                        val inner = ProtoCodec.Reader(r.readBytes())
                        while (inner.remaining > 0) {
                            val t = inner.readVarint()
                            val f = (t shr 3).toInt(); val w = (t and 7).toInt()
                            when (f) {
                                6 -> if (w == 0) max = inner.readVarint().toInt() else inner.skipField(w)
                                7 -> if (w == 0) level = inner.readVarint().toInt() else inner.skipField(w)
                                8 -> if (w == 0) muted = inner.readVarint() != 0L else inner.skipField(w)
                                else -> inner.skipField(w)
                            }
                        }
                    }
                }
                20 -> {                                          // remote_ime_key_inject { app_info=1 { app_package=12 } }
                    if (wire == 2) {
                        val inner = ProtoCodec.Reader(r.readBytes())
                        while (inner.remaining > 0) {
                            val t = inner.readVarint()
                            if ((t shr 3).toInt() == 1 && (t and 7).toInt() == 2) {
                                val ai = ProtoCodec.Reader(inner.readBytes())
                                while (ai.remaining > 0) {
                                    val at = ai.readVarint()
                                    if ((at shr 3).toInt() == 12 && (at and 7).toInt() == 2) {
                                        val len = ai.readVarint().toInt()
                                        app = String(ai.buf, ai.pos, len, Charsets.UTF_8)
                                        ai.pos += len
                                    } else ai.skipField((at and 7).toInt())
                                }
                            } else inner.skipField((t and 7).toInt())
                        }
                    }
                }
                else -> r.skipField(wire)
            }
        }
        return Parsed(cfg, active, ping, pingVal, start, started, vol, level, max, muted, app)
    }
}
