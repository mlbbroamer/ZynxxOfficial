package com.zynnn.cloneapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zynnn.cloneapp.data.UiStrings
import com.zynnn.cloneapp.model.AppLanguage
import com.zynnn.cloneapp.model.AppSettings
import com.zynnn.cloneapp.model.GlassIntensity
import com.zynnn.cloneapp.model.ThemeMode
import com.zynnn.cloneapp.ui.components.LiquidGlassCard
import com.zynnn.cloneapp.ui.theme.LocalLiquidGlass

@Composable
fun SettingsScreen(
    settings: AppSettings,
    strings: UiStrings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onQrScanClick: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val glass = LocalLiquidGlass.current
    val wallpaperPreview = listOf(
        listOf(Color(0xFF0EA5E9), Color(0xFF8B5CF6)),
        listOf(Color(0xFF10B981), Color(0xFF06B6D4)),
        listOf(Color(0xFFEC4899), Color(0xFF6366F1)),
        listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text(strings.navSettings, color = glass.textPrimary, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        // Premium Unlocked Banner
        LiquidGlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.WorkspacePremium, null, tint = glass.accentEmerald, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Clone app By Zynnn PRO", color = glass.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(strings.premiumActive, color = glass.accentEmerald, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Theme & Glass UI
        LiquidGlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.themeSetting, color = glass.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeMode.DARK to strings.darkMode,
                        ThemeMode.LIGHT to strings.lightMode,
                        ThemeMode.FOLLOW_SYSTEM to strings.followSystem
                    ).forEach { (mode, label) ->
                        val active = settings.themeMode == mode
                        FilterChip(
                            selected = active,
                            onClick = { onUpdateSettings { it.copy(themeMode = mode) } },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Text(strings.glassIntensity, color = glass.textSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassIntensity.entries.forEach { intensity ->
                        val label = if (settings.language == AppLanguage.INDONESIAN) intensity.labelId else intensity.labelEn
                        FilterChip(
                            selected = settings.glassIntensity == intensity,
                            onClick = { onUpdateSettings { it.copy(glassIntensity = intensity) } },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Text(strings.wallpaper, color = glass.textSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    wallpaperPreview.forEachIndexed { idx, colors ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(colors))
                                .border(
                                    width = if (settings.wallpaperIndex == idx) 2.5.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { onUpdateSettings { it.copy(wallpaperIndex = idx) } }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Language
        LiquidGlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.language, color = glass.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { onUpdateSettings { it.copy(language = lang) } },
                            label = { Text(lang.displayName, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Tools & Info
        LiquidGlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                ListItem(
                    headlineContent = { Text(strings.qrScanner, color = glass.textPrimary) },
                    leadingContent = { Icon(Icons.Rounded.QrCodeScanner, null, tint = glass.accentCyan) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onQrScanClick)
                )
                ListItem(
                    headlineContent = { Text(strings.checkUpdates, color = glass.textPrimary) },
                    leadingContent = { Icon(Icons.Rounded.SystemUpdate, null, tint = glass.accentEmerald) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onShowToast(strings.upToDate) }
                )
                ListItem(
                    headlineContent = { Text(strings.privacyPolicy, color = glass.textPrimary) },
                    leadingContent = { Icon(Icons.Rounded.PrivacyTip, null, tint = glass.accentViolet) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onShowToast("Local isolated storage • Zero tracking") }
                )
                ListItem(
                    headlineContent = { Text(strings.contactUs, color = glass.textPrimary) },
                    leadingContent = { Icon(Icons.Rounded.SupportAgent, null, tint = glass.textSecondary) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onShowToast("Clone app By Zynnn v1.0.0") }
                )
            }
        }

        Spacer(Modifier.height(110.dp))
    }
}
