package com.zynnn.cloneapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zynnn.cloneapp.data.UiStrings
import com.zynnn.cloneapp.model.InstalledAppInfo
import com.zynnn.cloneapp.ui.components.AppIconBadge
import com.zynnn.cloneapp.ui.components.LiquidGlassCard
import com.zynnn.cloneapp.ui.theme.LocalLiquidGlass

@Composable
fun AddAppScreen(
    installedApps: List<InstalledAppInfo>,
    hideSystemApps: Boolean,
    defaultCopyApk: Boolean,
    strings: UiStrings,
    onToggleHideSystem: (Boolean) -> Unit,
    onCloneBatch: (List<InstalledAppInfo>, Boolean) -> Unit,
    onShowToast: (String) -> Unit
) {
    val glass = LocalLiquidGlass.current
    var search by remember { mutableStateOf("") }
    var tabIndex by remember { mutableStateOf(0) } // 0=All, 1=Hot Apps, 2=Hot Games
    var copyApk by remember { mutableStateOf(defaultCopyApk) }
    val selected = remember { mutableStateListOf<InstalledAppInfo>() }

    val filtered = remember(installedApps, hideSystemApps, search, tabIndex) {
        installedApps.filter { app ->
            val passSystem = !hideSystemApps || !app.isSystemApp
            val passTab = when (tabIndex) {
                1 -> app.isHotApp
                2 -> app.isHotGame
                else -> true
            }
            val passSearch = search.isBlank() || app.appName.contains(search, true) || app.packageName.contains(search, true)
            passSystem && passTab && passSearch
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(10.dp))
            Text(strings.deviceApps, color = glass.textPrimary, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))

            // Search
            LiquidGlassCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, null, tint = glass.textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = glass.textPrimary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (search.isEmpty()) Text(strings.searchApps, color = glass.textSecondary, fontSize = 14.sp)
                            inner()
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Tabs: All, Hot Apps, Hot Games
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(strings.allApps, strings.hotApps, strings.hotGames).forEachIndexed { idx, label ->
                    val active = tabIndex == idx
                    LiquidGlassCard(
                        shape = RoundedCornerShape(14.dp),
                        onClick = { tabIndex = idx },
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (active) glass.accentCyan.copy(alpha = 0.25f) else Color.Transparent)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (active) glass.accentCyan else glass.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Copy APK & Hide System Toggles
            LiquidGlassCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(strings.copyApk, color = glass.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (copyApk) strings.copyApkDesc else strings.shareApkDesc,
                                color = glass.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(checked = copyApk, onCheckedChange = { copyApk = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.hideSystemApps, color = glass.textPrimary, fontSize = 13.sp)
                        Switch(checked = hideSystemApps, onCheckedChange = onToggleHideSystem)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 160.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val isChecked = selected.any { it.packageName == app.packageName }
                    LiquidGlassCard(
                        shape = RoundedCornerShape(18.dp),
                        onClick = {
                            if (isChecked) {
                                selected.removeAll { it.packageName == app.packageName }
                            } else if (selected.size >= 9) {
                                onShowToast(strings.max9Apps)
                            } else {
                                selected.add(app)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconBadge(packageName = app.packageName, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, color = glass.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${app.packageName} • ${"%.1f".format(app.sizeMb)} MB", color = glass.textSecondary, fontSize = 11.sp)
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (!checked) {
                                        selected.removeAll { it.packageName == app.packageName }
                                    } else if (selected.size >= 9) {
                                        onShowToast(strings.max9Apps)
                                    } else {
                                        selected.add(app)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            Button(
                onClick = {
                    val toClone = selected.toList()
                    selected.clear()
                    onCloneBatch(toClone, copyApk)
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = glass.accentCyan),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Rounded.Add, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(strings.cloneSelected(selected.size), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
