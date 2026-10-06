package com.snothin.ghostsam.data.channel

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AdbConnection
import io.github.muntashirakon.adb.PairingConnectionCtx
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class AdbBridge(context: Context) {

    companion object {
        const val DEVICE_NAME = "GhostSam"
        private const val KEY_FILE = "adb_key.p8"
        private const val CERT_FILE = "adb_cert.der"
        private const val CONNECT_TIMEOUT_MS = 15_000L
    }

    private val keyFile: File = File(context.filesDir, KEY_FILE)
    private val certFile: File = File(context.filesDir, CERT_FILE)

    @Synchronized
    fun loadOrCreateKeyPair(): Pair<PrivateKey, Certificate> {
        if (keyFile.exists() && certFile.exists()) {
            try {
                return loadKeyPair()
            } catch (_: Exception) {
                keyFile.delete()
                certFile.delete()
            }
        }
        val result = X509SelfSigned.generate()
        keyFile.writeBytes(result.privateKey.encoded)
        certFile.writeBytes(result.certificate.encoded)
        return Pair(result.privateKey, result.certificate)
    }

    private fun loadKeyPair(): Pair<PrivateKey, Certificate> {
        val keyBytes = keyFile.readBytes()
        val certBytes = certFile.readBytes()
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certBytes))
        return Pair(privateKey, cert)
    }

    fun pair(host: String, port: Int, code: String) {
        val (privateKey, certificate) = loadOrCreateKeyPair()
        val ctx = PairingConnectionCtx(
            host, port,
            code.toByteArray(Charsets.UTF_8),
            privateKey, certificate,
            DEVICE_NAME,
        )
        try {
            ctx.start()
        } finally {
            // Workaround: libadb 3.1.1 close() NPEs on mid-pairing failure, masking the real error.
            try {
                ctx.close()
            } catch (_: Exception) {
            }
        }
    }

// Wireless-debug channel upgrades to TLS automatically (A_STLS); classic 127.0.0.1:5555 TCP mode stays plaintext (A_AUTH).
    fun connect(
        host: String,
        port: Int,
        timeoutMs: Long = CONNECT_TIMEOUT_MS,
        allowFirstTimeAuthorisation: Boolean = false,
    ): AdbConnection {
        val (privateKey, certificate) = loadOrCreateKeyPair()
        val conn = AdbConnection.Builder(host, port)
            .setPrivateKey(privateKey)
            .setCertificate(certificate)
            .setApi(Build.VERSION.SDK_INT)
            .build()
        return try {
            if (conn.connect(timeoutMs, TimeUnit.MILLISECONDS, !allowFirstTimeAuthorisation)) {
                conn
            } else {
                throw IOException("ADB connect timeout")
            }
        } catch (error: Throwable) {
            // Failure must reap: libadb never closes on handshake/auth errors; retries leak socket/fd.
            runCatching { conn.close() }
            throw error
        }
    }
}

private object X509SelfSigned {

    class Result(val privateKey: PrivateKey, val certificate: Certificate)

    private const val CN = "GhostSam"

    fun generate(): Result {
        try {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048, SecureRandom())
            val kp = kpg.generateKeyPair()

            val pubEncoded = kp.public.encoded

            val sha256WithRsaOid = byteArrayOf(
                0x2a, 0x86.toByte(), 0x48, 0x86.toByte(),
                0xf7.toByte(), 0x0d, 0x01, 0x01, 0x0b,
            )
            val cnOid = byteArrayOf(0x55, 0x04, 0x03)

            val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val notBefore = fmt.format(Date(System.currentTimeMillis() - 0x5265c00L)) // -1 day (notBefore backdate)
            val notAfter = fmt.format(Date(System.currentTimeMillis() + 0x496cebb800L)) // +10 years (notAfter)

            val versionExplicit = tlv(0xa0, tlv(0x02, byteArrayOf(0x02)))
            val serial = tlv(0x02, BigInteger.probablePrime(128, SecureRandom()).toByteArray())
            val algSeq = tlv(
                0x30,
                concat(tlv(0x06, sha256WithRsaOid), tlv(0x05, ByteArray(0))),
            )
            // X.500 Name: SEQUENCE { SET { SEQUENCE { OID, value } } }; the SET layer is required.
            val issuerSeq = tlv(
                0x30,
                tlv(
                    0x31,
                    tlv(0x30, concat(tlv(0x06, cnOid), tlv(0x0c, CN.toByteArray(Charsets.UTF_8)))),
                ),
            )
            val validity = tlv(
                0x30,
                concat(
                    tlv(0x17, notBefore.toByteArray(Charsets.US_ASCII)),
                    tlv(0x17, notAfter.toByteArray(Charsets.US_ASCII)),
                ),
            )
            // PublicKey.getEncoded() is already a complete SPKI DER; use as-is, do not wrap again.
            val spki = pubEncoded

            val tbs = tlv(
                0x30,
                concat(versionExplicit, serial, algSeq, issuerSeq, validity, issuerSeq, spki),
            )

            val sig = Signature.getInstance("SHA256withRSA")
            sig.initSign(kp.private)
            sig.update(tbs)
            val sigBitString = tlv(0x03, concat(byteArrayOf(0), sig.sign()))

            val certDer = tlv(0x30, concat(tbs, algSeq, sigBitString))

            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate

            return Result(kp.private, cert)
        } catch (e: Exception) {
            throw IllegalStateException("X509 self-signed certificate generation failed", e)
        }
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val out = ByteArray(arrays.sumOf { it.size })
        var pos = 0
        for (a in arrays) {
            System.arraycopy(a, 0, out, pos, a.size)
            pos += a.size
        }
        return out
    }

    private fun derLen(len: Int): ByteArray {
        if (len < 0x80) return byteArrayOf(len.toByte())
        val tmp = ByteArray(4)
        var n = 0
        var v = len
        while (v > 0) {
            tmp[n++] = (v and 0xff).toByte()
            v = v ushr 8
        }
        val out = ByteArray(n + 1)
        out[0] = (n or 0x80).toByte()
        for (i in 0 until n) out[1 + i] = tmp[n - 1 - i]
        return out
    }

    private fun tlv(tag: Int, content: ByteArray): ByteArray =
        concat(byteArrayOf(tag.toByte()), derLen(content.size), content)
}
