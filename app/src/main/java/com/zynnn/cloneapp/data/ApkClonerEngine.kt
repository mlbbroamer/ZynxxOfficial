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
     * Clones base.apk + all split APKs (if any) into [outputDir]:
     * - Preserves original Java/Kotlin component class names (expands relative ".Foo" -> "oldPkg.Foo")
     * - Rewrites only manifest package, provider authorities, and custom permissions to [newPackageName]
     * - Signs every APK with v1+v2 ApkSigner
     */
    fun createClonedApks(
        context: Context,
        sourceApkPath: String,
        splitApkPaths: List<String>,
        originalPackage: String,
        newPackageName: String,
        outputDir: File
    ): List<File> {
        outputDir.mkdirs()
        val allSources = listOf(sourceApkPath) + splitApkPaths
        val (privateKey, cert) = getOrCreateSigningKey(context)
        val signerConfig = ApkSigner.SignerConfig.Builder(
            "ZynnnCloneKey",
            privateKey,
            listOf(cert)
        ).build()

        return allSources.mapIndexed { idx, srcPath ->
            val name = if (idx == 0) "base.apk" else "split_$idx.apk"
            val unsignedApk = File(outputDir, "unsigned_$name")
            val signedApk = File(outputDir, name)

            ZipFile(srcPath).use { zipIn ->
                ZipOutputStream(FileOutputStream(unsignedApk)).use { zipOut ->
                    val entries = zipIn.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryName = entry.name
                        if (entryName.startsWith("META-INF/") &&
                            (entryName.endsWith(".SF") || entryName.endsWith(".RSA") ||
                                entryName.endsWith(".DSA") || entryName == "META-INF/MANIFEST.MF")
                        ) {
                            continue
                        }
                        val rawBytes = zipIn.getInputStream(entry).readBytes()
                        val outBytes = if (entryName == "AndroidManifest.xml") {
                            patchManifestAxml(rawBytes, originalPackage, newPackageName)
                        } else {
                            rawBytes
                        }
                        val newEntry = ZipEntry(entryName)
                        if (entry.method == ZipEntry.STORED && entryName != "AndroidManifest.xml") {
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

            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(signedApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(false)
                .build()
                .sign()

            unsignedApk.delete()
            signedApk
        }
    }

    fun installClonedApks(context: Context, apkFiles: List<File>) {
        if (apkFiles.isEmpty()) return
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apkFiles.forEachIndexed { idx, apkFile ->
                val writeName = if (idx == 0) "base.apk" else "split_$idx.apk"
                session.openWrite(writeName, 0, apkFile.length()).use { out ->
                    apkFile.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
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
     * Attribute-aware binary AXML patcher:
     * 1. Walks XML_START_ELEMENT chunks to find exact string indices used for:
     *    - <manifest package="..."> -> [newPkg]
     *    - <provider android:authorities="..."> -> replaces [oldPkg] with [newPkg]
     *    - <application/activity/service/receiver/provider android:name=".Foo"> -> expands to "[oldPkg].Foo"
     *      so Android does NOT throw ClassNotFoundException when package is changed!
     * 2. Appends modified strings to the StringPool and updates attribute rawValue + typedValue.data
     *    without corrupting any Java class names!
     */
    private fun patchManifestAxml(axml: ByteArray, oldPkg: String, newPkg: String): ByteArray {
        if (axml.size < 16) return axml
        val work = axml.copyOf()
        val buf = ByteBuffer.wrap(work).order(ByteOrder.LITTLE_ENDIAN)
        if ((buf.getShort(0).toInt() and 0xFFFF) != 0x0003) return axml

        val spOffset = 8
        if ((buf.getShort(spOffset).toInt() and 0xFFFF) != 0x0001) return axml
        val spOldSize = buf.getInt(spOffset + 4)
        val stringCount = buf.getInt(spOffset + 8)
        val styleCount = buf.getInt(spOffset + 12)
        val flags = buf.getInt(spOffset + 16)
        val stringsStart = buf.getInt(spOffset + 20)
        val stylesStart = buf.getInt(spOffset + 24)
        val isUtf8 = (flags and (1 shl 8)) != 0

        val offsetsBase = spOffset + 28
        val dataBase = spOffset + stringsStart
        val strings = ArrayList<String>(stringCount)

        for (i in 0 until stringCount) {
            val strOff = buf.getInt(offsetsBase + i * 4)
            var p = dataBase + strOff
            val str = if (isUtf8) {
                val b1 = work[p].toInt() and 0xFF
                p += if ((b1 and 0x80) != 0) 2 else 1
                val b2 = work[p].toInt() and 0xFF
                val byteLen = if ((b2 and 0x80) != 0) {
                    val v = ((b2 and 0x7F) shl 8) or (work[p + 1].toInt() and 0xFF)
                    p += 2
                    v
                } else {
                    p += 1
                    b2
                }
                String(work, p, byteLen, Charsets.UTF_8)
            } else {
                var cLen = buf.getShort(p).toInt() and 0xFFFF
                p += 2
                if ((cLen and 0x8000) != 0) {
                    cLen = ((cLen and 0x7FFF) shl 16) or (buf.getShort(p).toInt() and 0xFFFF)
                    p += 2
                }
                String(work, p, cLen * 2, Charsets.UTF_16LE)
            }
            strings.add(str)
        }

        val componentTags = setOf("application", "activity", "activity-alias", "service", "receiver", "provider")

        fun getOrAddString(value: String): Int {
            val existing = strings.indexOf(value)
            if (existing >= 0) return existing
            strings.add(value)
            return strings.size - 1
        }

        // Walk chunks after StringPool (ResourceMap + XML tree)
        var pos = spOffset + spOldSize
        while (pos + 8 <= work.size) {
            val chunkType = buf.getShort(pos).toInt() and 0xFFFF
            val chunkSize = buf.getInt(pos + 4)
            if (chunkSize <= 0 || pos + chunkSize > work.size) break

            if (chunkType == 0x0102) { // RES_XML_START_ELEMENT_TYPE
                val nameIdx = buf.getInt(pos + 20)
                val tagName = strings.getOrNull(nameIdx) ?: ""
                val attrStart = buf.getShort(pos + 24).toInt() and 0xFFFF
                val attrSize = buf.getShort(pos + 26).toInt() and 0xFFFF
                val attrCount = buf.getShort(pos + 28).toInt() and 0xFFFF
                val attrsBase = pos + 16 + attrStart // ResXMLTree_node(16) + attrStart

                for (a in 0 until attrCount) {
                    val aOff = attrsBase + a * attrSize
                    if (aOff + 20 > work.size) break
                    val attrNameIdx = buf.getInt(aOff + 4)
                    val attrName = strings.getOrNull(attrNameIdx) ?: ""
                    val rawValIdx = buf.getInt(aOff + 8)
                    val valType = work[aOff + 15].toInt() and 0xFF
                    if (valType == 0x03 && rawValIdx in strings.indices) { // TYPE_STRING
                        val curVal = strings[rawValIdx]
                        var newVal: String? = null
                        when {
                            tagName == "manifest" && attrName == "package" -> {
                                newVal = newPkg
                            }
                            tagName == "provider" && attrName == "authorities" -> {
                                newVal = curVal.split(";").joinToString(";") { auth ->
                                    if (auth.contains(oldPkg)) auth.replace(oldPkg, newPkg) else "$newPkg.$auth"
                                }
                            }
                            tagName in componentTags && attrName == "name" -> {
                                // Expand relative ".MainActivity" to "oldPkg.MainActivity" so class resolution succeeds!
                                if (curVal.startsWith(".")) {
                                    newVal = oldPkg + curVal
                                } else if (!curVal.contains(".")) {
                                    newVal = "$oldPkg.$curVal"
                                }
                            }
                            (tagName == "permission" || tagName == "uses-permission") && attrName == "name" -> {
                                if (curVal.startsWith("$oldPkg.")) {
                                    newVal = newPkg + curVal.removePrefix(oldPkg)
                                }
                            }
                        }
                        if (newVal != null && newVal != curVal) {
                            val newIdx = getOrAddString(newVal)
                            buf.putInt(aOff + 8, newIdx)  // rawValue
                            buf.putInt(aOff + 16, newIdx) // typedValue.data
                        }
                    }
                }
            }
            pos += chunkSize
        }

        // Rebuild StringPool with updated strings
        val newStringCount = strings.size
        val poolData = ByteArrayOutputStream()
        val newOffsets = IntArray(newStringCount)
        for (i in 0 until newStringCount) {
            newOffsets[i] = poolData.size()
            val s = strings[i]
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
        val headerSize = 28 + (newStringCount + styleCount) * 4
        val newStringsStart = headerSize
        val newStylesStart = if (stylesStart > 0) newStringsStart + poolData.size() else 0
        val newSpSize = headerSize + poolData.size() + stylesSize

        val out = ByteArrayOutputStream()
        val hdr = ByteBuffer.allocate(8 + headerSize).order(ByteOrder.LITTLE_ENDIAN)
        hdr.putShort(0x0003.toShort())
        hdr.putShort(8.toShort())
        hdr.putInt(0)
        hdr.putShort(0x0001.toShort())
        hdr.putShort(28.toShort())
        hdr.putInt(newSpSize)
        hdr.putInt(newStringCount)
        hdr.putInt(styleCount)
        hdr.putInt(flags)
        hdr.putInt(newStringsStart)
        hdr.putInt(newStylesStart)
        for (o in newOffsets) hdr.putInt(o)
        for (i in 0 until styleCount) {
            hdr.putInt(buf.getInt(offsetsBase + stringCount * 4 + i * 4))
        }
        out.write(hdr.array())
        out.write(poolData.toByteArray())
        if (stylesSize > 0) {
            out.write(work, spOffset + stylesStart, stylesSize)
        }
        val restStart = spOffset + spOldSize
        out.write(work, restStart, work.size - restStart)

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
