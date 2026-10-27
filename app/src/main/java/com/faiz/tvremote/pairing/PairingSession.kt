package com.faiz.tvremote.pairing

import android.util.Log
import com.faiz.tvremote.proto.ProtoCodec
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * Pairing dengan TV (port 6467), mirror dari PairingManager.js.
 * Alur: request -> option -> configuration -> TV menampilkan PIN (event onPinRequest)
 * -> sendCode(PIN) -> secret -> secretAck -> selesai.
 *
 * Verifikasi PIN: SHA256(modulus_klien + exponent_klien + modulus_server +
 * exponent_server + pin), byte pertama hash harus == byte pertama PIN.
 */
class PairingSession(
    private val host: String,
    private val port: Int = 6467,
    private val serviceName: String = "tv-remote",
) {
    var onPinRequest: (() -> Unit)? = null

    @Volatile private var socket: SSLSocket? = null
    @Volatile private var cancelled = false

    fun cancel() {
        cancelled = true
        try { socket?.close() } catch (_: Exception) {}
    }

    /** Blokir sampai pairing selesai. Kembalikan true bila sukses. */
    fun run(onCodeNeeded: () -> String): Boolean {
        try {
            val cert = CertManager.getOrCreate(serviceName)
            val (key, chain) = CertManager.keyManagerEntry()
            val ks = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType()).apply {
                load(null)
                setKeyEntry("client", key, null, chain)
            }
            val kmf = javax.net.ssl.KeyManagerFactory.getInstance(
                javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm()
            ).apply { init(ks, null) }
            val trustAll = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
                    override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )
            val ctx = SSLContext.getInstance("TLS").apply {
                init(kmf.keyManagers, trustAll, null)
            }
            val s = (ctx.socketFactory.createSocket(host, port) as SSLSocket).apply {
                soTimeout = 30000
                startHandshake()
            }
            socket = s
            val input = s.inputStream
            val output = s.outputStream

            output.write(PairingMessages.pairingRequest(serviceName, android.os.Build.MODEL ?: "HP"))
            output.flush()

            while (!cancelled) {
                val buf = try {
                    ProtoCodec.readDelimited(input)
                } catch (e: Exception) {
                    Log.w(TAG, "baca pairing putus: ${e.message}")
                    return false
                }
                val m = PairingMessages.parse(buf)
                if (m.status != 0 && m.status != PairingMessages.STATUS_OK) {
                    Log.w(TAG, "pairing ditolak TV (status ${m.status})")
                    return false
                }
                when {
                    m.hasRequestAck -> {
                        output.write(PairingMessages.pairingOption()); output.flush()
                    }
                    m.hasOption -> {
                        output.write(PairingMessages.pairingConfiguration()); output.flush()
                    }
                    m.hasConfigAck -> {
                        onPinRequest?.invoke()
                        val pin = onCodeNeeded()
                        if (!sendCode(s, pin)) {
                            Log.w(TAG, "PIN salah")
                            return false
                        }
                    }
                    m.hasSecretAck -> {
                        Log.i(TAG, "pairing sukses")
                        try { s.close() } catch (_: Exception) {}
                        return true
                    }
                }
            }
            return false
        } catch (e: Exception) {
            Log.w(TAG, "pairing gagal: ${e.message}")
            return false
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun sendCode(s: SSLSocket, code: String): Boolean {
        return try {
            val sess = s.session
            val clientCert = sess.localCertificates?.firstOrNull() as? X509Certificate
                ?: return false
            val serverCert = sess.peerCertificates.firstOrNull() as? X509Certificate
                ?: return false
            val codeBytes = hexToBytes(code)
            val md = MessageDigest.getInstance("SHA-256")
            md.update(rsaModulus(clientCert))
            md.update(rsaExponent(clientCert))
            md.update(rsaModulus(serverCert))
            md.update(rsaExponent(serverCert))
            md.update(hexToBytes(code.drop(2)))
            val hash = md.digest()
            if (hash[0] != codeBytes[0]) return false
            s.outputStream.write(PairingMessages.pairingSecret(hash))
            s.outputStream.flush()
            true
        } catch (e: Exception) {
            Log.w(TAG, "sendCode gagal: ${e.message}")
            false
        }
    }

    private fun rsaModulus(c: X509Certificate): ByteArray {
        val key = c.publicKey as java.security.interfaces.RSAPublicKey
        return stripLeadingZero(key.modulus.toByteArray())
    }

    private fun rsaExponent(c: X509Certificate): ByteArray {
        val key = c.publicKey as java.security.interfaces.RSAPublicKey
        // JS: Crypto.enc.Hex.parse("0" + client_certificate.exponent.slice(2))
        // exponent dari sertifikat berupa hex dengan prefix "00" -> buang prefix, tambah "0" di kiri.
        var hex = key.publicExponent.toString(16)
        if (hex.startsWith("00")) hex = hex.drop(2)
        return hexToBytes("0$hex")
    }

    private fun stripLeadingZero(b: ByteArray): ByteArray {
        var i = 0
        while (i < b.size - 1 && b[i] == 0.toByte()) i++
        return b.copyOfRange(i, b.size)
    }

    private fun hexToBytes(s: String): ByteArray {
        val clean = s.trim()
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    companion object {
        private const val TAG = "PairingSession"
    }
}
