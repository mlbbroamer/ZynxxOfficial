package com.zynnn.cloneapp.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zynnn.cloneapp.ui.theme.LocalLiquidGlass

@Composable
fun AuroraGlassBackdrop(content: @Composable BoxScope.() -> Unit) {
    val glass = LocalLiquidGlass.current
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift"
    )

    val palettesDark = listOf(
        Triple(Color(0xFF050814), Color(0xFF0EA5E9), Color(0xFF8B5CF6)),
        Triple(Color(0xFF040D12), Color(0xFF10B981), Color(0xFF06B6D4)),
        Triple(Color(0xFF120514), Color(0xFFEC4899), Color(0xFF6366F1)),
        Triple(Color(0xFF090B10), Color(0xFFF59E0B), Color(0xFFEF4444))
    )
    val palettesLight = listOf(
        Triple(Color(0xFFE0F2FE), Color(0xFF7DD3FC), Color(0xFFC4B5FD)),
        Triple(Color(0xFFECFDF5), Color(0xFF6EE7B7), Color(0xFF67E8F9)),
        Triple(Color(0xFFFDF2F8), Color(0xFFF9A8D4), Color(0xFFA5B4FC)),
        Triple(Color(0xFFFFFBEB), Color(0xFFFCD34D), Color(0xFFFCA5A5))
    )
    val idx = glass.wallpaperIndex.coerceIn(0, 3)
    val (base, orb1, orb2) = if (glass.isDark) palettesDark[idx] else palettesLight[idx]

    Box(modifier = Modifier.fillMaxSize().background(base)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb1.copy(alpha = if (glass.isDark) 0.34f else 0.48f), Color.Transparent),
                    center = Offset(w * (0.2f + 0.3f * shift), h * 0.18f),
                    radius = w * 0.85f
                ),
                radius = w * 0.85f,
                center = Offset(w * (0.2f + 0.3f * shift), h * 0.18f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb2.copy(alpha = if (glass.isDark) 0.30f else 0.42f), Color.Transparent),
                    center = Offset(w * (0.85f - 0.25f * shift), h * 0.72f),
                    radius = w * 0.90f
                ),
                radius = w * 0.90f,
                center = Offset(w * (0.85f - 0.25f * shift), h * 0.72f)
            )
        }
        content()
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalLiquidGlass.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
        label = "glassScale"
    )

    val bgBrush = Brush.linearGradient(
        colors = listOf(
            glass.glassTint.copy(alpha = glass.glassAlpha + 0.06f),
            glass.glassTint.copy(alpha = glass.glassAlpha * 0.65f)
        )
    )
    val borderBrush = Brush.linearGradient(
        colors = listOf(glass.borderStart, glass.borderEnd)
    )

    val clickableMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(bgBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(clickableMod),
        content = content
    )
}

@Composable
fun AppIconBadge(
    packageName: String,
    customEmoji: String? = null,
    customColorHex: Long? = null,
    spaceId: Int? = null,
    isRunning: Boolean = false,
    size: Dp = 54.dp
) {
    val context = LocalContext.current
    val glass = LocalLiquidGlass.current
    val appBitmap = remember(packageName) {
        runCatching {
            val d = context.packageManager.getApplicationIcon(packageName)
            val w = d.intrinsicWidth.coerceAtLeast(96)
            val h = d.intrinsicHeight.coerceAtLeast(96)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = AndroidCanvas(bmp)
            d.setBounds(0, 0, w, h)
            d.draw(c)
            bmp.asImageBitmap()
        }.getOrNull()
    }

    Box(modifier = Modifier.size(size)) {
        if (!customEmoji.isNullOrBlank()) {
            val bg = Color(customColorHex ?: 0xFF0EA5E9)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(bg, bg.copy(alpha = 0.65f))))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = customEmoji, fontSize = (size.value * 0.48f).sp)
            }
        } else if (appBitmap != null) {
            Image(
                bitmap = appBitmap,
                contentDescription = packageName,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(glass.accentCyan.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text("📱", fontSize = 24.sp)
            }
        }

        if (spaceId != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(glass.accentCyan, glass.accentViolet)))
                    .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "S$spaceId",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isRunning) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(glass.accentEmerald)
                    .border(1.5.dp, Color.White, CircleShape)
            )
        }
    }
}
