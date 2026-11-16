package com.zynnn.cloneapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zynnn.cloneapp.data.UiStrings
import com.zynnn.cloneapp.model.ClonedApp
import com.zynnn.cloneapp.ui.components.AppIconBadge
import com.zynnn.cloneapp.ui.components.LiquidGlassCard
import com.zynnn.cloneapp.ui.theme.LocalLiquidGlass

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpacesHomeScreen(
    clones: List<ClonedApp>,
    strings: UiStrings,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onLaunch: (ClonedApp) -> Unit,
    onForceStop: (ClonedApp) -> Unit,
    onClearCache: (ClonedApp) -> Unit,
    onClearStorage: (ClonedApp) -> Unit,
    onRename: (ClonedApp, String) -> Unit,
    onChangeIcon: (ClonedApp, String?, Long?) -> Unit,
    onCreateShortcut: (ClonedApp) -> Unit,
    onRandomizeId: (ClonedApp) -> Unit,
    onResetId: (ClonedApp) -> Unit,
    onToggleSecret: (ClonedApp) -> Unit,
    onDelete: (ClonedApp) -> Unit,
    onAddClick: () -> Unit,
    onQrScanClick: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val visibleClones = remember(clones, searchQuery) {
        clones.filter { !it.isSecretZone && (searchQuery.isBlank() || it.customName.contains(searchQuery, true)) }
    }

    var selectedClone by remember { mutableStateOf<ClonedApp?>(null) }
    var renameTarget by remember { mutableStateOf<ClonedApp?>(null) }
    var iconTarget by remember { mutableStateOf<ClonedApp?>(null) }
    var deviceIdTarget by remember { mutableStateOf<ClonedApp?>(null) }
    var spaceInfoTarget by remember { mutableStateOf<ClonedApp?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = strings.appTitle,
                    color = glass.textPrimary,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "${visibleClones.size} Active Spaces • ${strings.premiumActive}",
                    color = glass.accentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            LiquidGlassCard(
                shape = CircleShape,
                onClick = onQrScanClick,
                modifier = Modifier.size(44.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = strings.qrScanner, tint = glass.textPrimary)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Search Glass Bar
        LiquidGlassCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = glass.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = glass.textPrimary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text(strings.searchApps, color = glass.textSecondary, fontSize = 14.sp)
                        }
                        inner()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = null,
                        tint = glass.textSecondary,
                        modifier = Modifier.size(18.dp).clickable { onSearchChange("") }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (visibleClones.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LiquidGlassCard(
                    modifier = Modifier.padding(16.dp),
                    onClick = onAddClick
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.AddCircleOutline, null, tint = glass.accentCyan, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = strings.emptySpaces,
                            color = glass.textPrimary,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 110.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(visibleClones, key = { it.id }) { clone ->
                    LiquidGlassCard(
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onLaunch(clone) },
                                onLongClick = { selectedClone = clone }
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Box(Modifier.align(Alignment.Center)) {
                                    AppIconBadge(
                                        packageName = clone.packageName,
                                        customEmoji = clone.customEmoji,
                                        customColorHex = clone.customIconColorHex,
                                        spaceId = clone.spaceId,
                                        isRunning = clone.isRunning
                                    )
                                }
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "Options",
                                    tint = glass.textSecondary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(18.dp)
                                        .clickable { selectedClone = clone }
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = clone.customName,
                                color = glass.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (clone.copyApkMode) "APK" else "Share",
                                    color = glass.textSecondary,
                                    fontSize = 10.sp
                                )
                                if (clone.deviceIdentity.isSpoofed) {
                                    Icon(
                                        Icons.Rounded.VerifiedUser,
                                        contentDescription = "Spoofed",
                                        tint = glass.accentEmerald,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Clone Action Menu Dialog
    selectedClone?.let { clone ->
        CloneActionSheetDialog(
            clone = clone,
            strings = strings,
            onDismiss = { selectedClone = null },
            onOpen = { selectedClone = null; onLaunch(clone) },
            onForceStop = { selectedClone = null; onForceStop(clone) },
            onClearCache = { selectedClone = null; onClearCache(clone) },
            onClearStorage = { selectedClone = null; onClearStorage(clone) },
            onEditName = { selectedClone = null; renameTarget = clone },
            onChangeIcon = { selectedClone = null; iconTarget = clone },
            onCreateShortcut = { selectedClone = null; onCreateShortcut(clone) },
            onDeviceId = { selectedClone = null; deviceIdTarget = clone },
            onSpaceInfo = { selectedClone = null; spaceInfoTarget = clone },
            onToggleSecret = { selectedClone = null; onToggleSecret(clone) },
            onDelete = { selectedClone = null; onDelete(clone) }
        )
    }

    renameTarget?.let { clone ->
        var text by remember { mutableStateOf(clone.customName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(strings.editName) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { onRename(clone, text); renameTarget = null }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(strings.cancel) }
            }
        )
    }

    iconTarget?.let { clone ->
        IconCustomizerDialog(
            strings = strings,
            onDismiss = { iconTarget = null },
            onSelect = { emoji, color ->
                onChangeIcon(clone, emoji, color)
                iconTarget = null
            }
        )
    }

    deviceIdTarget?.let { clone ->
        val liveClone = clones.find { it.id == clone.id } ?: clone
        DeviceIdentityDialog(
            clone = liveClone,
            strings = strings,
            onDismiss = { deviceIdTarget = null },
            onRandomize = { onRandomizeId(liveClone) },
            onRestore = { onResetId(liveClone) }
        )
    }

    spaceInfoTarget?.let { clone ->
        AlertDialog(
            onDismissRequest = { spaceInfoTarget = null },
            title = { Text(strings.spaceInfo) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Space ID: ${strings.spaceLabel(clone.spaceId)}")
                    Text("Package: ${clone.packageName}")
                    Text("Mode: ${if (clone.copyApkMode) "Copy APK (Isolated)" else "Share APK"}")
                    Text("Cache: ${"%.1f".format(clone.cacheSizeMb)} MB")
                    Text("Storage: ${"%.1f".format(clone.storageSizeMb)} MB")
                }
            },
            confirmButton = {
                TextButton(onClick = { spaceInfoTarget = null }) { Text("OK") }
            }
        )
    }
}

@Composable
fun CloneActionSheetDialog(
    clone: ClonedApp,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onForceStop: () -> Unit,
    onClearCache: () -> Unit,
    onClearStorage: () -> Unit,
    onEditName: () -> Unit,
    onChangeIcon: () -> Unit,
    onCreateShortcut: () -> Unit,
    onDeviceId: () -> Unit,
    onSpaceInfo: () -> Unit,
    onToggleSecret: () -> Unit,
    onDelete: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconBadge(clone.packageName, clone.customEmoji, clone.customIconColorHex, clone.spaceId, clone.isRunning, size = 42.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(clone.customName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(strings.spaceLabel(clone.spaceId), fontSize = 12.sp, color = glass.accentCyan)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ActionRow(Icons.Rounded.PlayArrow, strings.open, glass.accentCyan, onOpen)
                ActionRow(Icons.Rounded.StopCircle, strings.forceStop, glass.textPrimary, onForceStop)
                ActionRow(Icons.Rounded.CleaningServices, strings.clearCache, glass.textPrimary, onClearCache)
                ActionRow(Icons.Rounded.DeleteSweep, strings.clearStorage, glass.textPrimary, onClearStorage)
                ActionRow(Icons.Rounded.Edit, strings.editName, glass.textPrimary, onEditName)
                ActionRow(Icons.Rounded.Palette, strings.changeIcon, glass.textPrimary, onChangeIcon)
                ActionRow(Icons.Rounded.AddToHomeScreen, strings.createShortcut, glass.textPrimary, onCreateShortcut)
                ActionRow(Icons.Rounded.Fingerprint, strings.deviceId, glass.accentEmerald, onDeviceId)
                ActionRow(Icons.Rounded.Info, strings.spaceInfo, glass.textPrimary, onSpaceInfo)
                ActionRow(
                    Icons.Rounded.Lock,
                    if (clone.isSecretZone) strings.removeFromSecret else strings.moveToSecret,
                    glass.accentViolet,
                    onToggleSecret
                )
                ActionRow(Icons.Rounded.DeleteForever, strings.deleteApp, glass.accentRose, onDelete)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = tint, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun IconCustomizerDialog(
    strings: UiStrings,
    onDismiss: () -> Unit,
    onSelect: (String?, Long?) -> Unit
) {
    val emojis = listOf("🚀", "🔥", "💎", "🦊", "🎮", "💬", "🛡️", "⚡", "🌙", "🎭", "👑", "🧊")
    val colors = listOf(0xFF0EA5E9L, 0xFF8B5CF6L, 0xFF10B981L, 0xFFF43F5EL, 0xFFF59E0BL, 0xFF6366F1L)
    var chosenColor by remember { mutableStateOf(colors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.changeIcon) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (chosenColor == hex) 2.5.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { chosenColor = hex }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    emojis.take(6).forEach { e ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(chosenColor).copy(alpha = 0.25f))
                                .clickable { onSelect(e, chosenColor) },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    emojis.drop(6).forEach { e ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(chosenColor).copy(alpha = 0.25f))
                                .clickable { onSelect(e, chosenColor) },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(null, null) }) { Text(strings.restoreOriginal) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun DeviceIdentityDialog(
    clone: ClonedApp,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onRandomize: () -> Unit,
    onRestore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.deviceId) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${strings.androidId}: ${clone.deviceIdentity.androidId}", fontSize = 13.sp)
                Text("${strings.bluetoothMac}: ${clone.deviceIdentity.bluetoothMac}", fontSize = 13.sp)
                Text("${strings.serial}: ${clone.deviceIdentity.serial}", fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(onClick = onRandomize) { Text(strings.randomizeId) }
        },
        dismissButton = {
            TextButton(onClick = onRestore) { Text(strings.restoreOriginal) }
        }
    )
}
