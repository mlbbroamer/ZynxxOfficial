package com.zynnn.cloneapp.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SelfSignedCertGenerator {

    fun generate(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 86400_000L)
        val notAfter = Date(now + 3650L * 86400_000L)

        val serial = derInteger(BigInteger.valueOf(now and 0x7FFFFFFFL))
        val sigAlg = derSequence(
            derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)) + // sha256WithRSAEncryption
                byteArrayOf(0x05, 0x00) // NULL
        )
        val dn = buildDn("CloneAppByZynnn")
        val validity = derSequence(derUtcTime(notBefore) + derUtcTime(notAfter))
        val spki = keyPair.public.encoded

        // TBSCertificate (v3)
        val version = derTag(0xA0, derInteger(BigInteger.valueOf(2)))
        val tbs = derSequence(version + serial + sigAlg + dn + validity + dn + spki)

        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(tbs)
        }.sign()

        val certDer = derSequence(tbs + sigAlg + derBitString(sig))
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
    }

    private fun buildDn(cn: String): ByteArray {
        val oidCn = derOid(byteArrayOf(0x55, 0x04, 0x03))
        val valCn = derTlv(0x0C, cn.toByteArray(Charsets.UTF_8))
        return derSequence(derTlv(0x31, derSequence(oidCn + valCn)))
    }

    private fun derUtcTime(date: Date): ByteArray {
        val sdf = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return derTlv(0x17, sdf.format(date).toByteArray(Charsets.US_ASCII))
    }

    private fun derInteger(v: BigInteger): ByteArray = derTlv(0x02, v.toByteArray())
    private fun derOid(oid: ByteArray): ByteArray = derTlv(0x06, oid)
    private fun derSequence(content: ByteArray): ByteArray = derTlv(0x30, content)
    private fun derTag(tag: Int, content: ByteArray): ByteArray = derTlv(tag, content)
    private fun derBitString(data: ByteArray): ByteArray = derTlv(0x03, byteArrayOf(0x00) + data)

    private fun derTlv(tag: Int, value: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = value.size
        when {
            len < 128 -> out.write(len)
            len < 256 -> {
                out.write(0x81)
                out.write(len)
            }
            else -> {
                out.write(0x82)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
        }
        out.write(value)
        return out.toByteArray()
    }
}
