package com.zynnn.cloneapp.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zynnn.cloneapp.MainActivity
import com.zynnn.cloneapp.model.AppSettings
import com.zynnn.cloneapp.model.ClonedApp
import com.zynnn.cloneapp.model.DeviceIdentity
import com.zynnn.cloneapp.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom

class VirtualSpaceManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("zynnn_clone_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val random = SecureRandom()

    private val _clonedApps = MutableStateFlow<List<ClonedApp>>(emptyList())
    val clonedApps: StateFlow<List<ClonedApp>> = _clonedApps.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val hotAppPackages = setOf(
        "com.whatsapp", "com.instagram.android", "com.zhiliaoapp.musically",
        "com.tiktok.lite.go", "org.telegram.messenger", "com.facebook.katana",
        "com.facebook.orca", "com.snapchat.android", "com.openai.chatgpt",
        "com.microsoft.copilot", "com.instagram.barcelona", "com.squareup.cash"
    )

    private val hotGamePackages = setOf(
        "com.roblox.client", "com.mobile.legends", "com.dts.freefireth",
        "com.tencent.ig", "com.scopely.monopolygo", "com.dreamgames.royalmatch",
        "com.dreamgames.royalkingdom", "com.supercell.clashofclans"
    )

    init {
        loadState()
    }

    private fun loadState() {
        val clonesJson = prefs.getString("cloned_apps", null)
        if (!clonesJson.isNullOrBlank()) {
            val type = object : TypeToken<List<ClonedApp>>() {}.type
            _clonedApps.value = runCatching { gson.fromJson<List<ClonedApp>>(clonesJson, type) }.getOrDefault(emptyList())
        }
        val settingsJson = prefs.getString("app_settings", null)
        if (!settingsJson.isNullOrBlank()) {
            _settings.value = runCatching { gson.fromJson(settingsJson, AppSettings::class.java) }.getOrDefault(AppSettings())
        }
    }

    private fun persistClones(list: List<ClonedApp>) {
        _clonedApps.value = list
        prefs.edit().putString("cloned_apps", gson.toJson(list)).apply()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit().putString("app_settings", gson.toJson(updated)).apply()
    }

    suspend fun refreshInstalledApps() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val result = packages.mapNotNull { pkgInfo ->
            val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
            if (pkgInfo.packageName == context.packageName) return@mapNotNull null
            if (pkgInfo.packageName.startsWith("zynnn.s")) return@mapNotNull null
            pm.getLaunchIntentForPackage(pkgInfo.packageName) ?: return@mapNotNull null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val label = appInfo.loadLabel(pm).toString()
            val apkFile = File(appInfo.sourceDir)
            val sizeMb = if (apkFile.exists()) apkFile.length() / (1024f * 1024f) else 0f
            InstalledAppInfo(
                packageName = pkgInfo.packageName,
                appName = label,
                versionName = pkgInfo.versionName ?: "1.0",
                isSystemApp = isSystem,
                isHotApp = pkgInfo.packageName in hotAppPackages,
                isHotGame = pkgInfo.packageName in hotGamePackages,
                apkPath = appInfo.sourceDir,
                sizeMb = sizeMb
            )
        }.sortedWith(compareByDescending<InstalledAppInfo> { it.isHotApp || it.isHotGame }.thenBy { it.appName.lowercase() })
        _installedApps.value = result
    }

    suspend fun cloneAppsBatch(
        apps: List<InstalledAppInfo>,
        copyApk: Boolean,
        toSecretZone: Boolean = false
    ): Result<List<ClonedApp>> = withContext(Dispatchers.IO) {
        runCatching {
            val current = _clonedApps.value.toMutableList()
            val added = mutableListOf<ClonedApp>()
            for (app in apps.take(9)) {
                val existingSpaces = current.filter { it.packageName == app.packageName }.map { it.spaceId }
                val nextSpaceId = (1..99).firstOrNull { it !in existingSpaces } ?: (existingSpaces.size + 1)
                val isolatedPkg = ApkClonerEngine.buildIsolatedPackageName(app.packageName, nextSpaceId)
                val spaceDir = File(context.filesDir, "spaces/space_${nextSpaceId}/${app.packageName}")
                val outApk = File(spaceDir, "cloned_signed.apk")

                if (app.apkPath.isNotBlank()) {
                    ApkClonerEngine.createClonedApk(
                        context = context,
                        sourceApkPath = app.apkPath,
                        originalPackage = app.packageName,
                        newPackageName = isolatedPkg,
                        outputApk = outApk
                    )
                }

                val clone = ClonedApp(
                    packageName = app.packageName,
                    clonedPackageName = isolatedPkg,
                    originalName = app.appName,
                    customName = "${app.appName} (S$nextSpaceId)",
                    spaceId = nextSpaceId,
                    copyApkMode = copyApk,
                    copiedApkPath = if (outApk.exists()) outApk.absolutePath else null,
                    isInstalledClone = isPackageInstalled(isolatedPkg),
                    isSecretZone = toSecretZone,
                    deviceIdentity = originalDeviceIdentity()
                )
                current.add(0, clone)
                added.add(clone)

                if (outApk.exists() && ApkClonerEngine.canRequestPackageInstalls(context)) {
                    ApkClonerEngine.installClonedApk(context, outApk)
                }
            }
            persistClones(current)
            added
        }
    }

    fun isPackageInstalled(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        return runCatching {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)
    }

    /**
     * Launches the truly isolated cloned package ([ClonedApp.clonedPackageName]) which has
     * its own /data/data/<clonedPackageName> directory. If not yet installed, triggers
     * PackageInstaller (or opens Unknown Sources permission) instead of launching the original app.
     */
    fun launchClonedApp(clone: ClonedApp): LaunchStatus {
        val targetPkg = clone.clonedPackageName.ifBlank {
            ApkClonerEngine.buildIsolatedPackageName(clone.packageName, clone.spaceId)
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            context.startActivity(launchIntent)
            persistClones(_clonedApps.value.map {
                if (it.id == clone.id) it.copy(isRunning = true, isInstalledClone = true) else it
            })
            return LaunchStatus.LAUNCHED
        }

        if (!ApkClonerEngine.canRequestPackageInstalls(context)) {
            ApkClonerEngine.openInstallPermissionSettings(context)
            return LaunchStatus.NEEDS_INSTALL_PERMISSION
        }

        val apkFile = clone.copiedApkPath?.let { File(it) }
        if (apkFile != null && apkFile.exists()) {
            ApkClonerEngine.installClonedApk(context, apkFile)
            return LaunchStatus.INSTALLING
        }
        return LaunchStatus.APK_MISSING
    }

    fun forceStopClone(cloneId: String) {
        val target = _clonedApps.value.find { it.id == cloneId } ?: return
        val pkg = target.clonedPackageName.ifBlank { target.packageName }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        runCatching { am?.killBackgroundProcesses(pkg) }
        persistClones(_clonedApps.value.map { if (it.id == cloneId) it.copy(isRunning = false) else it })
    }

    fun clearCache(cloneId: String) {
        val clone = _clonedApps.value.find { it.id == cloneId } ?: return
        val cacheDir = File(context.cacheDir, "spaces/space_${clone.spaceId}/${clone.packageName}")
        if (cacheDir.exists()) cacheDir.deleteRecursively()
        persistClones(_clonedApps.value.map { if (it.id == cloneId) it.copy(cacheSizeMb = 0f) else it })
    }

    fun clearStorage(cloneId: String) {
        val clone = _clonedApps.value.find { it.id == cloneId } ?: return
        val dataDir = File(context.filesDir, "spaces/space_${clone.spaceId}/${clone.packageName}/data")
        if (dataDir.exists()) dataDir.deleteRecursively()
        persistClones(_clonedApps.value.map {
            if (it.id == cloneId) it.copy(cacheSizeMb = 0f, storageSizeMb = 0f, isRunning = false) else it
        })
    }

    fun renameClone(cloneId: String, newName: String) {
        if (newName.isBlank()) return
        persistClones(_clonedApps.value.map { if (it.id == cloneId) it.copy(customName = newName.trim()) else it })
    }

    fun updateCloneIcon(cloneId: String, emoji: String?, colorHex: Long?) {
        persistClones(_clonedApps.value.map {
            if (it.id == cloneId) it.copy(customEmoji = emoji, customIconColorHex = colorHex) else it
        })
    }

    fun toggleSecretZone(cloneId: String) {
        persistClones(_clonedApps.value.map {
            if (it.id == cloneId) it.copy(isSecretZone = !it.isSecretZone) else it
        })
    }

    fun deleteClone(cloneId: String) {
        val target = _clonedApps.value.find { it.id == cloneId }
        if (target != null) {
            if (target.clonedPackageName.isNotBlank() && isPackageInstalled(target.clonedPackageName)) {
                val uninstallIntent = Intent(
                    Intent.ACTION_DELETE,
                    Uri.parse("package:${target.clonedPackageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(uninstallIntent) }
            }
            if (target.copiedApkPath != null) {
                runCatching { File(target.copiedApkPath).parentFile?.deleteRecursively() }
            }
        }
        persistClones(_clonedApps.value.filterNot { it.id == cloneId })
    }

    fun randomizeDeviceIdentity(cloneId: String) {
        val spoofed = DeviceIdentity(
            androidId = generateRandomHex(16),
            bluetoothMac = generateRandomMac(),
            serial = "ZYN${generateRandomHex(9).uppercase()}",
            isSpoofed = true
        )
        persistClones(_clonedApps.value.map { if (it.id == cloneId) it.copy(deviceIdentity = spoofed) else it })
    }

    fun resetDeviceIdentity(cloneId: String) {
        val orig = originalDeviceIdentity()
        persistClones(_clonedApps.value.map { if (it.id == cloneId) it.copy(deviceIdentity = orig) else it })
    }

    @SuppressLint("HardwareIds")
    fun originalDeviceIdentity(): DeviceIdentity {
        val aid = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull() ?: "0000000000000000"
        return DeviceIdentity(
            androidId = aid,
            bluetoothMac = "02:00:00:00:00:00",
            serial = android.os.Build.MODEL.replace(" ", "_").uppercase(),
            isSpoofed = false
        )
    }

    fun createPinnedShortcut(clone: ClonedApp): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("launch_clone_id", clone.id)
        }
        val iconDrawable = runCatching { context.packageManager.getApplicationIcon(clone.packageName) }.getOrNull()
        val iconCompat = if (iconDrawable != null) {
            IconCompat.createWithBitmap(drawableToBitmap(iconDrawable))
        } else {
            IconCompat.createWithResource(context, android.R.drawable.sym_def_app_icon)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, "clone_${clone.id}")
            .setShortLabel(clone.customName)
            .setIcon(iconCompat)
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val w = drawable.intrinsicWidth.coerceAtLeast(128)
        val h = drawable.intrinsicHeight.coerceAtLeast(128)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }

    private fun generateRandomHex(len: Int): String {
        val chars = "0123456789abcdef"
        return (1..len).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    private fun generateRandomMac(): String {
        val bytes = ByteArray(6)
        random.nextBytes(bytes)
        bytes[0] = (bytes[0].toInt() and 0xfe or 0x02).toByte()
        return bytes.joinToString(":") { "%02X".format(it) }
    }
}

enum class LaunchStatus {
    LAUNCHED,
    INSTALLING,
    NEEDS_INSTALL_PERMISSION,
    APK_MISSING
}
