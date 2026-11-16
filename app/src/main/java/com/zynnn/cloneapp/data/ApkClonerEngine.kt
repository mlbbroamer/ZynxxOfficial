package com.zynnn.cloneapp.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.android.apksig.ApkSigner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (confirmIntent != null) {
                context.startActivity(confirmIntent)
            }
        }
    }
}

object ApkClonerEngine {

    fun buildIsolatedPackageName(originalPkg: String, spaceId: Int): String {
        val sanitized = originalPkg.replace(Regex("[^a-zA-Z0-9_.]"), "_")
        return "zynnn.s$spaceId.$sanitized"
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Clones the source APK by rewriting its binary AndroidManifest.xml package name &
     * provider authorities to [newPackageName], then signing with v1+v2 ApkSigner so
     * Android installs it with a 100% separate /data/data/<newPackageName> sandbox.
     */
    fun createClonedApk(
        context: Context,
        sourceApkPath: String,
        originalPackage: String,
        newPackageName: String,
        outputApk: File
    ): File {
        outputApk.parentFile?.mkdirs()
        val unsignedApk = File(outputApk.parentFile, "unsigned_${outputApk.name}")

        ZipFile(sourceApkPath).use { zipIn ->
            ZipOutputStream(FileOutputStream(unsignedApk)).use { zipOut ->
                val entries = zipIn.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name.startsWith("META-INF/") &&
                        (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") || name == "META-INF/MANIFEST.MF")
                    ) {
                        continue
                    }
                    val rawBytes = zipIn.getInputStream(entry).readBytes()
                    val outBytes = if (name == "AndroidManifest.xml") {
                        patchAxmlPackageStrings(rawBytes, originalPackage, newPackageName)
                    } else {
                        rawBytes
                    }
                    val newEntry = ZipEntry(name)
                    if (entry.method == ZipEntry.STORED && name != "AndroidManifest.xml") {
                        newEntry.method = ZipEntry.STORED
                        newEntry.size = outBytes.size.toLong()
                        newEntry.compressedSize = outBytes.size.toLong()
                        newEntry.crc = entry.crc
                    }
                    zipOut.putNextEntry(newEntry)
                    zipOut.write(outBytes)
                    zipOut.closeEntry()
                }
            }
        }

        val (privateKey, cert) = getOrCreateSigningKey(context)
        val signerConfig = ApkSigner.SignerConfig.Builder(
            "ZynnnCloneKey",
            privateKey,
            listOf(cert)
        ).build()

        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsignedApk)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .build()
            .sign()

        unsignedApk.delete()
        return outputApk
    }

    fun installClonedApk(context: Context, apkFile: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pi.intentSender)
        }
    }

    /**
     * Rebuilds the AXML StringPool chunk replacing [oldPkg] prefix with [newPkg]
     * (handles package name, provider authorities, and custom permissions) while keeping
     * exact 4-byte chunk alignment and updating ResChunk_header sizes.
     */
    private fun patchAxmlPackageStrings(axml: ByteArray, oldPkg: String, newPkg: String): ByteArray {
        if (axml.size < 16) return axml
        val buf = ByteBuffer.wrap(axml).order(ByteOrder.LITTLE_ENDIAN)
        val fileType = buf.getShort(0).toInt() and 0xFFFF
        if (fileType != 0x0003) return axml // Not AXML

        val spOffset = 8
        val spType = buf.getShort(spOffset).toInt() and 0xFFFF
        if (spType != 0x0001) return axml
        val spOldSize = buf.getInt(spOffset + 4)
        val stringCount = buf.getInt(spOffset + 8)
        val styleCount = buf.getInt(spOffset + 12)
        val flags = buf.getInt(spOffset + 16)
        val stringsStart = buf.getInt(spOffset + 20)
        val stylesStart = buf.getInt(spOffset + 24)
        val isUtf8 = (flags and (1 shl 8)) != 0

        val offsetsBase = spOffset + 28
        val dataBase = spOffset + stringsStart
        val decoded = ArrayList<String>(stringCount)

        for (i in 0 until stringCount) {
            val strOff = buf.getInt(offsetsBase + i * 4)
            var p = dataBase + strOff
            val str = if (isUtf8) {
                val b1 = axml[p].toInt() and 0xFF
                p += if ((b1 and 0x80) != 0) 2 else 1
                val b2 = axml[p].toInt() and 0xFF
                val byteLen = if ((b2 and 0x80) != 0) {
                    val v = ((b2 and 0x7F) shl 8) or (axml[p + 1].toInt() and 0xFF)
                    p += 2
                    v
                } else {
                    p += 1
                    b2
                }
                String(axml, p, byteLen, Charsets.UTF_8)
            } else {
                var cLen = buf.getShort(p).toInt() and 0xFFFF
                p += 2
                if ((cLen and 0x8000) != 0) {
                    cLen = ((cLen and 0x7FFF) shl 16) or (buf.getShort(p).toInt() and 0xFFFF)
                    p += 2
                }
                String(axml, p, cLen * 2, Charsets.UTF_16LE)
            }
            val patched = if (str == oldPkg || str.startsWith("$oldPkg.")) {
                newPkg + str.removePrefix(oldPkg)
            } else {
                str
            }
            decoded.add(patched)
        }

        // Rebuild StringPool bytes
        val poolData = ByteArrayOutputStream()
        val newOffsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            newOffsets[i] = poolData.size()
            val s = decoded[i]
            if (isUtf8) {
                val bytes = s.toByteArray(Charsets.UTF_8)
                writeLen8(poolData, s.length)
                writeLen8(poolData, bytes.size)
                poolData.write(bytes)
                poolData.write(0)
            } else {
                val bytes = s.toByteArray(Charsets.UTF_16LE)
                writeLen16(poolData, s.length)
                poolData.write(bytes)
                poolData.write(0)
                poolData.write(0)
            }
        }
        while (poolData.size() % 4 != 0) poolData.write(0)

        val stylesSize = if (stylesStart > 0) spOldSize - stylesStart else 0
        val headerSize = 28 + (stringCount + styleCount) * 4
        val newStringsStart = headerSize
        val newStylesStart = if (stylesStart > 0) newStringsStart + poolData.size() else 0
        val newSpSize = headerSize + poolData.size() + stylesSize

        val out = ByteArrayOutputStream()
        val hdr = ByteBuffer.allocate(8 + headerSize).order(ByteOrder.LITTLE_ENDIAN)
        // File header (placeholder total size)
        hdr.putShort(0x0003.toShort())
        hdr.putShort(8.toShort())
        hdr.putInt(0)
        // StringPool header
        hdr.putShort(0x0001.toShort())
        hdr.putShort(28.toShort())
        hdr.putInt(newSpSize)
        hdr.putInt(stringCount)
        hdr.putInt(styleCount)
        hdr.putInt(flags)
        hdr.putInt(newStringsStart)
        hdr.putInt(newStylesStart)
        for (o in newOffsets) hdr.putInt(o)
        // Copy style offsets if any
        for (i in 0 until styleCount) {
            hdr.putInt(buf.getInt(offsetsBase + stringCount * 4 + i * 4))
        }
        out.write(hdr.array())
        out.write(poolData.toByteArray())
        if (stylesSize > 0) {
            out.write(axml, spOffset + stylesStart, stylesSize)
        }
        // Append remaining AXML chunks after StringPool
        val restStart = spOffset + spOldSize
        out.write(axml, restStart, axml.size - restStart)

        val result = out.toByteArray()
        ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).putInt(4, result.size)
        return result
    }

    private fun writeLen8(out: ByteArrayOutputStream, len: Int) {
        if (len >= 0x80) {
            out.write((len shr 8) or 0x80)
            out.write(len and 0xFF)
        } else {
            out.write(len)
        }
    }

    private fun writeLen16(out: ByteArrayOutputStream, len: Int) {
        if (len >= 0x8000) {
            out.write((len shr 16) or 0x8000)
            out.write(len and 0xFFFF)
        } else {
            out.write(len and 0xFF)
            out.write((len shr 8) and 0xFF)
        }
    }

    private fun getOrCreateSigningKey(context: Context): Pair<PrivateKey, X509Certificate> {
        val ksFile = File(context.filesDir, "zynnn_clone_key.p12")
        val ks = KeyStore.getInstance("PKCS12")
        val pass = "zynnn123".toCharArray()
        val alias = "zynnn_key"
        if (ksFile.exists()) {
            ksFile.inputStream().use { ks.load(it, pass) }
            val key = ks.getKey(alias, pass) as PrivateKey
            val cert = ks.getCertificate(alias) as X509Certificate
            return key to cert
        }
        // Generate self-signed RSA 2048 key using AndroidKeyStore or BouncyCastle-free fallback
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val cert = SelfSignedCertGenerator.generate(kp)
        ks.load(null, pass)
        ks.setKeyEntry(alias, kp.private, pass, arrayOf(cert))
        ksFile.outputStream().use { ks.store(it, pass) }
        return kp.private to cert
    }
}
