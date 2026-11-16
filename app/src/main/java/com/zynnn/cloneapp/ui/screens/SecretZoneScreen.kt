package com.zynnn.cloneapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zynnn.cloneapp.data.UiStrings
import com.zynnn.cloneapp.model.ClonedApp
import com.zynnn.cloneapp.ui.components.AppIconBadge
import com.zynnn.cloneapp.ui.components.LiquidGlassCard
import com.zynnn.cloneapp.ui.components.PatternLockCanvas
import com.zynnn.cloneapp.ui.theme.LocalLiquidGlass

@Composable
fun SecretZoneScreen(
    secretClones: List<ClonedApp>,
    savedPattern: String,
    strings: UiStrings,
    onSavePattern: (String) -> Unit,
    onLaunch: (ClonedApp) -> Unit,
    onRemoveFromSecret: (ClonedApp) -> Unit,
    onShowToast: (String) -> Unit
) {
    val glass = LocalLiquidGlass.current
    var isUnlocked by remember { mutableStateOf(false) }
    var isModifying by remember { mutableStateOf(savedPattern.isBlank()) }
    var firstDraftPattern by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(strings.secretZoneTitle, color = glass.textPrimary, style = MaterialTheme.typography.headlineMedium)
                Text(strings.secretZoneDesc, color = glass.textSecondary, fontSize = 12.sp)
            }
            if (isUnlocked && savedPattern.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { isModifying = true; firstDraftPattern = null }) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = strings.modifyGesturePassword, tint = glass.accentCyan)
                    }
                    IconButton(onClick = { isUnlocked = false }) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Lock", tint = glass.accentViolet)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (!isUnlocked || isModifying) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LiquidGlassCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.padding(12.dp)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.Lock, null, tint = glass.accentViolet, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(10.dp))
                        val promptText = when {
                            isModifying && firstDraftPattern == null -> strings.drawPattern
                            isModifying && firstDraftPattern != null -> strings.drawPatternAgain
                            else -> strings.drawPattern
                        }
                        Text(
                            text = promptText,
                            color = if (isError) glass.accentRose else glass.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))
                        PatternLockCanvas(isError = isError) { code ->
                            val count = code.split("-").size
                            if (count < 4) {
                                isError = true
                                onShowToast(strings.connectAtLeast4)
                                return@PatternLockCanvas
                            }
                            isError = false
                            if (isModifying) {
                                if (firstDraftPattern == null) {
                                    firstDraftPattern = code
                                } else if (firstDraftPattern == code) {
                                    onSavePattern(code)
                                    firstDraftPattern = null
                                    isModifying = false
                                    isUnlocked = true
                                    onShowToast(strings.patternSaved)
                                } else {
                                    isError = true
                                    firstDraftPattern = null
                                    onShowToast(strings.patternMismatch)
                                }
                            } else {
                                if (code == savedPattern) {
                                    isUnlocked = true
                                    onShowToast(strings.unlockedSuccess)
                                } else {
                                    isError = true
                                    onShowToast(strings.wrongPattern)
                                }
                            }
                        }
                    }
                }
            }
        } else if (secretClones.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LiquidGlassCard(shape = RoundedCornerShape(24.dp)) {
                    Text(
                        text = strings.emptySecret,
                        color = glass.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(28.dp)
                    )
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
                items(secretClones, key = { it.id }) { clone ->
                    LiquidGlassCard(
                        shape = RoundedCornerShape(22.dp),
                        onClick = { onLaunch(clone) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AppIconBadge(clone.packageName, clone.customEmoji, clone.customIconColorHex, clone.spaceId, clone.isRunning)
                            Spacer(Modifier.height(8.dp))
                            Text(clone.customName, color = glass.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { onRemoveFromSecret(clone) }) {
                                Text("Unhide", fontSize = 11.sp, color = glass.accentCyan)
                            }
                        }
                    }
                }
            }
        }
    }
}
