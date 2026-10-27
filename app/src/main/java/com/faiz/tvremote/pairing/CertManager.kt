package com.faiz.tvremote.pairing

import android.content.Context
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.math.BigInteger
import java.util.Calendar
import javax.security.auth.x500.X500Principal
import java.io.ByteArrayInputStream

/**
 * Sertifikat klien untuk TLS ke TV.
 * Sama peran dengan CertificateGenerator (node-forge RSA-2048 + self-signed, valid s/d 2099).
 * Disimpan di AndroidKeyStore agar key tidak bisa diekstrak.
 */
object CertManager {
    private const val ALIAS = "tvremote_client"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    data class Cert(val keyPem: String, val certPem: String)

    fun hasCert(): Boolean = try {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        ks.containsAlias(ALIAS)
    } catch (_: Exception) { false }

    fun getOrCreate(serviceName: String = "tv-remote"): Cert {
        loadExisting()?.let { return it }
        return generate(serviceName)
    }

    fun loadExisting(): Cert? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val cert = ks.getCertificate(ALIAS) as? X509Certificate ?: return null
            val certPem = "-----BEGIN CERTIFICATE-----\n" +
                Base64.encodeToString(cert.encoded, Base64.DEFAULT) +
                "-----END CERTIFICATE-----\n"
            // Key tidak bisa diekstrak dari KeyStore; pemakai memakai KeyStore langsung.
            Cert(keyPem = "", certPem = certPem)
        } catch (_: Exception) { null }
    }

    private fun generate(serviceName: String): Cert {
        val kpg = KeyPairGenerator.getInstance("RSA", ANDROID_KEYSTORE)
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_SIGN or
                android.security.keystore.KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setCertificateSubject(X500Principal("CN=$serviceName, O=tv-remote"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(Calendar.getInstance().time)
            .apply {
                val notAfter = Calendar.getInstance().apply { set(Calendar.YEAR, 2099) }.time
                setCertificateNotAfter(notAfter)
            }
            .build()
        kpg.initialize(spec)
        kpg.generateKeyPair()
        return loadExisting() ?: error("gagal membuat sertifikat")
    }

    /** Entry key+cert untuk SSLSocket. */
    fun keyManagerEntry(): Pair<java.security.PrivateKey, Array<Certificate>> {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = ks.getKey(ALIAS, null) as java.security.PrivateKey
        return key to ks.getCertificateChain(ALIAS)
    }
}
