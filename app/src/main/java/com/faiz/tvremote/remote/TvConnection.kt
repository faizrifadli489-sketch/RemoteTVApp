package com.faiz.tvremote.remote

import android.util.Log
import com.faiz.tvremote.pairing.CertManager
import com.faiz.tvremote.proto.ProtoCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Koneksi TLS ke TV (port 6466), mirror dari RemoteManager.js.
 * Handshake: tunggu remote_configure -> balas configure -> ready.
 * Ping (tiap ~5 dtk) dibalas otomatis. Reconnect tiap 5 dtk bila putus.
 */
class TvConnection(
    private val host: String,
    private val port: Int = 6466,
) {
    data class TvState(
        val connected: Boolean = false,
        val powered: Boolean? = null,
        val volumeLevel: Int? = null,
        val volumeMax: Int? = null,
        val volumeMuted: Boolean? = null,
        val currentApp: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(TvState())
    val state: StateFlow<TvState> = _state

    @Volatile private var socket: SSLSocket? = null
    @Volatile private var out: OutputStream? = null
    @Volatile private var running = false
    @Volatile private var ready = false

    val isReady: Boolean get() = ready

    fun start() {
        if (running) return
        running = true
        scope.launch { loop() }
    }

    fun stop() {
        running = false
        ready = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        _state.value = TvState()
    }

    private suspend fun loop() {
        while (running) {
            try {
                connect()
            } catch (e: Exception) {
                Log.w(TAG, "konek gagal (${e.message}), retry 5 dtk")
            }
            if (running) delay(5000)
        }
    }

    private fun connect() {
        val (key, chain) = CertManager.keyManagerEntry()
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setKeyEntry("client", key, null, chain)
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(ks, null)
        }
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<java.security.cert.X509Certificate>, a: String) {}
            override fun checkServerTrusted(c: Array<java.security.cert.X509Certificate>, a: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        val ctx = SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, trustAll, null)
        }
        val s = (ctx.socketFactory.createSocket(host, port) as SSLSocket).apply {
            soTimeout = 15000
            startHandshake()
        }
        socket = s
        out = s.outputStream
        Log.i(TAG, "TLS tersambung ke $host:$port")
        readLoop(s.inputStream)
        ready = false
        _state.value = _state.value.copy(connected = false)
        try { s.close() } catch (_: Exception) {}
    }

    private fun readLoop(input: InputStream) {
        try {
            while (running && socket?.isConnected == true) {
                val buf = ProtoCodec.readDelimited(input)
                handle(buf)
            }
        } catch (e: Exception) {
            Log.w(TAG, "read putus: ${e.message}")
        }
    }

    private fun handle(buf: ByteArray) {
        val m = RemoteMessages.parse(buf)
        when {
            m.hasConfigure -> {
                send(RemoteMessages.configure())
                send(RemoteMessages.setActive())
                ready = true
                _state.value = _state.value.copy(connected = true)
                Log.i(TAG, "TV ready")
            }
            m.hasPingRequest -> send(RemoteMessages.pingResponse(m.pingVal))
            m.hasStart -> _state.value = _state.value.copy(powered = m.started)
            m.hasVolume -> _state.value = _state.value.copy(
                volumeLevel = m.volumeLevel,
                volumeMax = m.volumeMax,
                volumeMuted = m.volumeMuted,
            )
            m.currentApp != null -> _state.value = _state.value.copy(currentApp = m.currentApp)
        }
    }

    private fun send(bytes: ByteArray) {
        try {
            synchronized(this) {
                out?.write(bytes)
                out?.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "kirim gagal: ${e.message}")
        }
    }

    fun sendKey(keyCode: Int, direction: Int = KeyDirection.SHORT) {
        send(RemoteMessages.keyInject(keyCode, direction))
    }

    fun sendKeyByName(name: String, direction: Int = KeyDirection.SHORT): Boolean {
        val code = KEY_MAP[name] ?: return false
        sendKey(code, direction)
        return true
    }

    fun sendAppLink(url: String) {
        send(RemoteMessages.appLinkLaunch(url))
    }

    fun sendPower() = sendKey(KeyCodes.POWER)

    companion object {
        private const val TAG = "TvConnection"
    }
}
