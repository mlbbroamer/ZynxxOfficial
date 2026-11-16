package com.zynnn.cloneapp.model

import java.util.UUID

enum class ThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK
}

enum class AppLanguage(val code: String, val displayName: String) {
    INDONESIAN("in", "Bahasa Indonesia"),
    ENGLISH("en", "English")
}

enum class GlassIntensity(val alpha: Float, val borderAlpha: Float, val labelId: String, val labelEn: String) {
    SUBTLE(0.10f, 0.22f, "Halus", "Subtle"),
    BALANCED(0.18f, 0.35f, "Seimbang", "Balanced"),
    ULTRA(0.28f, 0.50f, "Maksimal", "Ultra")
}

data class DeviceIdentity(
    val androidId: String,
    val bluetoothMac: String,
    val serial: String,
    val isSpoofed: Boolean = false
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val isHotApp: Boolean = false,
    val isHotGame: Boolean = false,
    val apkPath: String = "",
    val sizeMb: Float = 0f
)

data class ClonedApp(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val clonedPackageName: String = "",
    val originalName: String,
    val customName: String,
    val spaceId: Int,
    val customEmoji: String? = null,
    val customIconColorHex: Long? = null,
    val copyApkMode: Boolean = true,
    val copiedApkPath: String? = null,
    val isInstalledClone: Boolean = false,
    val isSecretZone: Boolean = false,
    val isRunning: Boolean = false,
    val cacheSizeMb: Float = 0f,
    val storageSizeMb: Float = 0f,
    val deviceIdentity: DeviceIdentity,
    val createdAt: Long = System.currentTimeMillis()
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.INDONESIAN,
    val hideSystemApps: Boolean = true,
    val defaultCopyApk: Boolean = true,
    val glassIntensity: GlassIntensity = GlassIntensity.BALANCED,
    val wallpaperIndex: Int = 0,
    val gesturePattern: String = "",
    val isPremiumUnlocked: Boolean = true
)

data class QrScanRecord(
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
