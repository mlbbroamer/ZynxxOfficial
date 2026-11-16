package com.zynnn.cloneapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.zynnn.cloneapp.data.VirtualSpaceManager
import com.zynnn.cloneapp.data.getStrings
import com.zynnn.cloneapp.ui.components.AuroraGlassBackdrop
import com.zynnn.cloneapp.ui.components.LiquidGlassCard
import com.zynnn.cloneapp.ui.screens.AddAppScreen
import com.zynnn.cloneapp.ui.screens.SecretZoneScreen
import com.zynnn.cloneapp.ui.screens.SettingsScreen
import com.zynnn.cloneapp.ui.screens.SpacesHomeScreen
import com.zynnn.cloneapp.ui.theme.CloneAppByZynnnTheme
import com.zynnn.cloneapp.ui.theme.LocalLiquidGlass
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val manager = VirtualSpaceManager(applicationContext)

        // Handle pinned shortcut launch
        intent?.getStringExtra("launch_clone_id")?.let { cloneId ->
            manager.clonedApps.value.find { it.id == cloneId }?.let { manager.launchClonedApp(it) }
        }

        setContent {
            val settings by manager.settings.collectAsState()
            val clones by manager.clonedApps.collectAsState()
            val installedApps by manager.installedApps.collectAsState()
            val strings = remember(settings.language) { getStrings(settings.language) }
            val scope = rememberCoroutineScope()
            val ctx = LocalContext.current

            var currentTab by remember { mutableStateOf(0) } // 0=Spaces, 1=Secret, 2=Add, 3=Settings
            var searchQuery by remember { mutableStateOf("") }
            var isCloning by remember { mutableStateOf(false) }
            var qrResultText by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                manager.refreshInstalledApps()
            }

            val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
                if (!result.contents.isNullOrBlank()) {
                    qrResultText = result.contents
                }
            }

            val launchQrScanner = {
                val opts = ScanOptions().apply {
                    setPrompt(strings.qrScanner)
                    setBeepEnabled(false)
                    setOrientationLocked(true)
                }
                qrLauncher.launch(opts)
            }

            val toast: (String) -> Unit = { msg ->
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }

            CloneAppByZynnnTheme(
                themeMode = settings.themeMode,
                glassIntensity = settings.glassIntensity,
                wallpaperIndex = settings.wallpaperIndex
            ) {
                AuroraGlassBackdrop {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentTab) {
                            0 -> SpacesHomeScreen(
                                clones = clones,
                                strings = strings,
                                searchQuery = searchQuery,
                                onSearchChange = { searchQuery = it },
                                onLaunch = { clone -> manager.launchClonedApp(clone) },
                                onForceStop = { clone ->
                                    manager.forceStopClone(clone.id)
                                    toast(strings.forceStopped)
                                },
                                onClearCache = { clone ->
                                    manager.clearCache(clone.id)
                                    toast(strings.cacheCleared)
                                },
                                onClearStorage = { clone ->
                                    manager.clearStorage(clone.id)
                                    toast(strings.storageCleared)
                                },
                                onRename = { clone, name -> manager.renameClone(clone.id, name) },
                                onChangeIcon = { clone, emoji, color -> manager.updateCloneIcon(clone.id, emoji, color) },
                                onCreateShortcut = { clone ->
                                    if (manager.createPinnedShortcut(clone)) toast(strings.shortcutCreated)
                                },
                                onRandomizeId = { clone -> manager.randomizeDeviceIdentity(clone.id) },
                                onResetId = { clone -> manager.resetDeviceIdentity(clone.id) },
                                onToggleSecret = { clone -> manager.toggleSecretZone(clone.id) },
                                onDelete = { clone -> manager.deleteClone(clone.id) },
                                onAddClick = { currentTab = 2 },
                                onQrScanClick = launchQrScanner
                            )

                            1 -> SecretZoneScreen(
                                secretClones = clones.filter { it.isSecretZone },
                                savedPattern = settings.gesturePattern,
                                strings = strings,
                                onSavePattern = { pat -> manager.updateSettings { it.copy(gesturePattern = pat) } },
                                onLaunch = { clone -> manager.launchClonedApp(clone) },
                                onRemoveFromSecret = { clone -> manager.toggleSecretZone(clone.id) },
                                onShowToast = toast
                            )

                            2 -> AddAppScreen(
                                installedApps = installedApps,
                                hideSystemApps = settings.hideSystemApps,
                                defaultCopyApk = settings.defaultCopyApk,
                                strings = strings,
                                onToggleHideSystem = { hide -> manager.updateSettings { it.copy(hideSystemApps = hide) } },
                                onCloneBatch = { apps, copyApk ->
                                    scope.launch {
                                        isCloning = true
                                        val added = manager.cloneAppsBatch(apps, copyApk)
                                        isCloning = false
                                        added.firstOrNull()?.let { toast(strings.clonedToSpace(it.spaceId)) }
                                        currentTab = 0
                                    }
                                },
                                onShowToast = toast
                            )

                            3 -> SettingsScreen(
                                settings = settings,
                                strings = strings,
                                onUpdateSettings = { manager.updateSettings(it) },
                                onQrScanClick = launchQrScanner,
                                onShowToast = toast
                            )
                        }

                        // Floating Liquid Glass Pill Bottom Nav
                        LiquidGlassBottomNav(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it },
                            items = listOf(
                                NavItem(strings.navSpaces, Icons.Rounded.GridView),
                                NavItem(strings.navSecretZone, Icons.Rounded.Lock),
                                NavItem(strings.navAddApp, Icons.Rounded.AddCircle),
                                NavItem(strings.navSettings, Icons.Rounded.Tune)
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }

                    if (isCloning) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text(strings.cloneNow) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(16.dp))
                                    Text("Copying app data…")
                                }
                            },
                            confirmButton = {}
                        )
                    }

                    qrResultText?.let { content ->
                        AlertDialog(
                            onDismissRequest = { qrResultText = null },
                            title = { Text(strings.scanResult) },
                            text = { Text(content) },
                            confirmButton = {
                                if (content.startsWith("http://") || content.startsWith("https://")) {
                                    TextButton(onClick = {
                                        runCatching {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(content)))
                                        }
                                        qrResultText = null
                                    }) { Text(strings.openLink) }
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("QR", content))
                                    toast("Copied")
                                    qrResultText = null
                                }) { Text(strings.copyText) }
                            }
                        )
                    }
                }
            }
        }
    }
}

data class NavItem(val label: String, val icon: ImageVector)

@Composable
private fun LiquidGlassBottomNav(
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    items: List<NavItem>,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlass.current
    LiquidGlassCard(
        shape = RoundedCornerShape(30.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { idx, item ->
                val selected = currentTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (selected) glass.accentCyan.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { onTabSelected(idx) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (selected) glass.accentCyan else glass.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            color = if (selected) glass.accentCyan else glass.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
