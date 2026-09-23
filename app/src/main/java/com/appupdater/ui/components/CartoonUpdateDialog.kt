package com.appupdater.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.appupdater.update.UpdateState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/* ============================================================
 *  动态卡通更新弹窗 —— 「小懒」音乐猫全程 Canvas 手绘动画
 *  状态流转：检测 → 发现新版本 → 下载(进度环) → 安装(火箭) → 完成(彩带)
 * ============================================================ */

// 弹窗专属配色（霓虹卡通风）
private val CuteCyan = Color(0xFF4DE3FF)
private val CutePurple = Color(0xFF8B5CF6)
private val CutePink = Color(0xFFFF5FA2)
private val CuteYellow = Color(0xFFFFD93D)
private val CuteOrange = Color(0xFFFF9F43)
private val CuteGreen = Color(0xFF3EE6A0)
private val InkDark = Color(0xFF3A2E45)
private val CreamTop = Color(0xFFFFE9BE)
private val CreamBottom = Color(0xFFFFCF8A)
private val BodyTop = Color(0xFFFFC97E)
private val BodyBottom = Color(0xFFFFB25E)

private enum class MascotMood { HAPPY, NEUTRAL, SAD }

@Composable
fun CartoonUpdateDialog(
    state: UpdateState,
    currentVersion: String,
    newVersion: String? = null,
    onStartDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onRestartApp: (() -> Unit)? = null
) {
    val info = (state as? UpdateState.Found)?.info
    val isForce = info?.forceUpdate == true

    // 入场动画：弹性缩放 + 淡入
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.78f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "dialog_scale"
    )
    val dialogAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(220),
        label = "dialog_alpha"
    )

    val mood = when (state) {
        is UpdateState.Error -> MascotMood.SAD
        is UpdateState.Found, UpdateState.DownloadReady, UpdateState.Installing, is UpdateState.Done -> MascotMood.HAPPY
        else -> MascotMood.NEUTRAL
    }

    BackHandler(enabled = true, onBack = { if (!isForce && state !is UpdateState.Installing) onDismiss() })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.66f))
            .clickable(enabled = !isForce && state !is UpdateState.Installing) { onDismiss() }
            .testTag("cartoon_update_dialog"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(336.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = dialogAlpha
                }
                .clickable(enabled = true, onClick = { /* 消费点击，阻止穿透 */ })
                .background(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF1C1B2E), Color(0xFF241A3B), Color(0xFF2B1E45))
                    ),
                    shape = RoundedCornerShape(30.dp)
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(listOf(CuteCyan.copy(alpha = 0.9f), CutePurple, CutePink)),
                    shape = RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部：动态卡通角色（Canvas 全手绘）
            MusicCatMascot(
                mood = mood,
                isDownloading = state is UpdateState.Downloading,
                modifier = Modifier
                    .size(178.dp, 156.dp)
                    .testTag("cartoon_mascot")
            )

            // 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "发现新版本",
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(CuteCyan, CutePurple, CutePink)),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                if (info?.isDemo == true) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("演示模式", fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // 版本对照条：旧版 → 新版
            VersionCompareBar(
                oldVersion = currentVersion,
                newVersion = info?.versionName ?: newVersion ?: "新版本"
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 更新说明
            ReleaseNotesBox(notes = info?.releaseNotes ?: listOf("优化使用体验，修复已知问题"))
            Spacer(modifier = Modifier.height(12.dp))

            // 状态区（下载进度 / 火箭 / 彩带 / 错误 / 权限）
            when (state) {
                is UpdateState.Downloading -> {
                    DownloadProgressSection(
                        progress = state.progress,
                        downloaded = state.bytesDownloaded,
                        total = state.totalBytes
                    )
                }

                UpdateState.Installing -> {
                    InstallingSection()
                }

                is UpdateState.Done -> {
                    DoneSection(onDone = onDone, onRestartApp = { onRestartApp?.invoke() ?: onDone() })
                }

                is UpdateState.Error -> {
                    ErrorSection(
                        message = state.message,
                        canRetry = state.canRetry,
                        onRetry = onRetry,
                        onDismiss = onDismiss,
                        showDismiss = !isForce
                    )
                }

                UpdateState.NeedInstallPermission -> {
                    NeedPermissionSection(
                        onOpenSettings = onOpenInstallSettings,
                        onInstall = onInstall,
                        onDismiss = onDismiss
                    )
                }

                UpdateState.DownloadReady -> {
                    ReadyInstallSection(onInstall = onInstall)
                }

                UpdateState.Checking -> {
                    CheckingSection()
                }

                is UpdateState.Found -> {
                    // 默认：稍后再说 / 立即更新
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!isForce) {
                            GradientButton(
                                text = "稍后再说",
                                gradient = listOf(Color(0xFF3A3A4E), Color(0xFF45455C)),
                                modifier = Modifier.weight(1f),
                                onClick = onDismiss
                            )
                        }
                        GradientButton(
                            text = "立即更新",
                            gradient = listOf(CuteCyan, CutePurple, CutePink),
                            modifier = Modifier.weight(if (isForce) 1f else 1.35f),
                            onClick = onStartDownload,
                            pulsing = true
                        )
                    }
                }

                UpdateState.Idle -> {}
            }
        }
    }
}

/* ==================== 角色绘制 ==================== */

@Composable
private fun MusicCatMascot(
    mood: MascotMood,
    isDownloading: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "mascot")

    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce"
    )
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart),
        label = "blink"
    )
    val wave by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wave"
    )
    val notePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "note_phase"
    )
    val sparklePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "sparkle_phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // 归一化画布 200x190
        val sx = w / 200f
        val sy = h / 190f
        val s = minOf(sx, sy)
        val originX = (w - 200f * s) / 2f + 2f * s
        val originY = (h - 190f * s) / 2f

        // 全体缩放 + 弹跳
        withTransformScope(s, originX, originY, bounce * 3f * s) {
            val blinkClosed = blink > 0.965f
            val eyeRy = when {
                blinkClosed -> 1.5f
                mood == MascotMood.SAD -> 5f
                else -> 13f
            }

            // ---- 耳机 ----
            drawArc(
                color = CutePurple,
                startAngle = 205f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(40f, 26f),
                size = Size(120f, 140f),
                style = Stroke(width = 11f, cap = StrokeCap.Round)
            )
            drawRoundRect(
                color = CutePurple,
                topLeft = Offset(28f, 82f),
                size = Size(22f, 42f),
                cornerRadius = CornerRadius(10f)
            )
            drawRoundRect(
                color = CutePurple,
                topLeft = Offset(150f, 82f),
                size = Size(22f, 42f),
                cornerRadius = CornerRadius(10f)
            )
            drawRoundRect(
                color = Color(0xFF4B3FA6),
                topLeft = Offset(33f, 90f),
                size = Size(12f, 16f),
                cornerRadius = CornerRadius(6f)
            )
            drawRoundRect(
                color = Color(0xFF4B3FA6),
                topLeft = Offset(155f, 90f),
                size = Size(12f, 16f),
                cornerRadius = CornerRadius(6f)
            )

            // ---- 耳朵 ----
            drawCircle(Brush.radialGradient(listOf(CreamTop, CreamBottom), center = Offset(56f, 52f), radius = 15f), radius = 15f, center = Offset(56f, 52f))
            drawCircle(Brush.radialGradient(listOf(CreamTop, CreamBottom), center = Offset(144f, 52f), radius = 15f), radius = 15f, center = Offset(144f, 52f))
            drawCircle(CutePink.copy(alpha = 0.75f), radius = 7f, center = Offset(56f, 54f))
            drawCircle(CutePink.copy(alpha = 0.75f), radius = 7f, center = Offset(144f, 54f))

            // ---- 头 ----
            drawCircle(
                brush = Brush.radialGradient(listOf(CreamTop, CreamBottom), center = Offset(100f, 84f), radius = 60f),
                radius = 56f,
                center = Offset(100f, 96f)
            )
            drawCircle(
                color = Color(0xFFE8A87C).copy(alpha = 0.55f),
                radius = 56f,
                center = Offset(100f, 96f),
                style = Stroke(width = 3f)
            )

            // ---- 眼睛 ----
            if (!blinkClosed) {
                // 眼睛高光
                drawCircle(Color.White.copy(alpha = 0.9f), radius = 3.6f, center = Offset(82f, 86f))
                drawCircle(Color.White.copy(alpha = 0.9f), radius = 3.6f, center = Offset(126f, 86f))
            }
            drawOval(
                color = InkDark,
                topLeft = Offset(68f, 94f - eyeRy),
                size = Size(20f, eyeRy * 2f)
            )
            drawOval(
                color = InkDark,
                topLeft = Offset(112f, 94f - eyeRy),
                size = Size(20f, eyeRy * 2f)
            )

            // ---- 腮红 ----
            drawOval(CutePink.copy(alpha = 0.5f), topLeft = Offset(48f, 106f), size = Size(18f, 11f))
            drawOval(CutePink.copy(alpha = 0.5f), topLeft = Offset(134f, 106f), size = Size(18f, 11f))

            // ---- 嘴巴 ----
            if (mood == MascotMood.SAD) {
                drawArc(
                    color = InkDark,
                    startAngle = 175f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(86f, 106f),
                    size = Size(28f, 20f),
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )
            } else {
                drawArc(
                    color = InkDark,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(84f, 100f),
                    size = Size(32f, 22f),
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )
            }

            // ---- 身体 ----
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(BodyTop, BodyBottom)),
                topLeft = Offset(60f, 130f),
                size = Size(80f, 52f),
                cornerRadius = CornerRadius(20f)
            )
            drawOval(Color(0xFFFFE4AE), topLeft = Offset(74f, 142f), size = Size(52f, 26f))

            // ---- 脚 ----
            drawOval(BodyBottom, topLeft = Offset(76f, 176f), size = Size(22f, 12f))
            drawOval(BodyBottom, topLeft = Offset(102f, 176f), size = Size(22f, 12f))

            // ---- 右臂（持音符）----
            drawLine(
                color = BodyTop,
                start = Offset(136f, 148f),
                end = Offset(160f, 126f),
                strokeWidth = 11f,
                cap = StrokeCap.Round
            )
            drawCircle(BodyTop, radius = 8f, center = Offset(160f, 126f))
            drawMusicNote(x = 170f, y = 108f, scale = 1.05f, color = CutePink, alpha = 1f)

            // ---- 左臂（挥手）----
            val waveDeg = wave * 22f
            rotate(waveDeg, pivot = Offset(64f, 148f)) {
                drawLine(
                    color = BodyTop,
                    start = Offset(64f, 148f),
                    end = Offset(36f, 122f),
                    strokeWidth = 11f,
                    cap = StrokeCap.Round
                )
                drawCircle(BodyTop, radius = 8f, center = Offset(36f, 122f))
            }
        }

        // ---- 漂浮音符（三枚，环绕头顶）----
        val noteColors = listOf(CuteCyan, CutePink, CuteYellow)
        repeat(3) { i ->
            val phase = (notePhase + i * 0.33f) % 1f
            val rise = phase * 56f * s
            val sway = sin((notePhase + i * 0.5f) * PI * 2f).toFloat() * 7f * s
            val alpha = when {
                phase < 0.15f -> phase / 0.15f
                phase > 0.8f -> (1f - phase) / 0.2f
                else -> 1f
            }
            val nx = originX + (if (i == 0) 30f else if (i == 1) 168f else 96f) * s + sway
            val ny = originY + (150f - rise) * s
            drawMusicNote(x = nx, y = ny, scale = (0.8f + phase * 0.4f) * s, color = noteColors[i], alpha = alpha.coerceIn(0f, 1f), rotation = phase * 40f)
        }

        // ---- 星光闪烁 ----
        val sparkleAlpha = (0.5f + 0.5f * sin(sparklePhase * PI * 2f).toFloat())
        drawSparkle(center = Offset(originX + 16f * s, originY + 28f * s), r = 6f * s, color = CuteYellow, alpha = sparkleAlpha.coerceIn(0.15f, 0.9f), rotation = sparklePhase * 90f)
        drawSparkle(center = Offset(originX + 186f * s, originY + 40f * s), r = 5f * s, color = CuteCyan, alpha = (1f - sparkleAlpha).coerceIn(0.15f, 0.9f), rotation = -sparklePhase * 90f)

        // 下载中：头顶小圆环
        if (isDownloading) {
            drawArc(
                color = Color.White.copy(alpha = 0.16f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(originX + 172f * s, originY + 6f * s),
                size = Size(26f * s, 26f * s),
                style = Stroke(width = 4f * s, cap = StrokeCap.Round)
            )
            drawArc(
                color = CuteCyan,
                startAngle = -90f, sweepAngle = 120f + bounce * 60f, useCenter = false,
                topLeft = Offset(originX + 172f * s, originY + 6f * s),
                size = Size(26f * s, 26f * s),
                style = Stroke(width = 4f * s, cap = StrokeCap.Round)
            )
        }
    }
}

/** 在归一化坐标系中绘制角色（缩放 + 平移 + 弹跳偏移） */
private fun DrawScope.withTransformScope(
    scale: Float,
    originX: Float,
    originY: Float,
    bounceDy: Float,
    block: DrawScope.() -> Unit
) {
    withTransform({
        translate(left = originX, top = originY + bounceDy)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        block()
    }
}

/** 手绘音符 ♪ */
private fun DrawScope.drawMusicNote(
    x: Float,
    y: Float,
    scale: Float,
    color: Color,
    alpha: Float = 1f,
    rotation: Float = 0f
) {
    rotate(rotation, pivot = Offset(x, y)) {
        val r = 6f * scale
        drawOval(color = color, topLeft = Offset(x - r, y - r * 2.4f), size = Size(r * 2f, r * 1.8f), alpha = alpha)
        drawRoundRect(
            color = color,
            topLeft = Offset(x + r * 0.55f, y - r * 2.4f),
            size = Size(r * 0.55f, r * 2.8f),
            cornerRadius = CornerRadius(r * 0.25f),
            alpha = alpha
        )
        val flag = Path().apply {
            moveTo(x + r * 1.0f, y - r * 2.4f)
            quadraticTo(x + r * 2.6f, y - r * 1.6f, x + r * 1.1f, y - r * 0.3f)
            lineTo(x + r * 1.05f, y - r * 0.5f)
            quadraticTo(x + r * 2.1f, y - r * 1.5f, x + r * 1.05f, y - r * 2.1f)
            close()
        }
        drawPath(flag, color, alpha = alpha)
    }
}

/** 手绘四角星芒 */
private fun DrawScope.drawSparkle(
    center: Offset,
    r: Float,
    color: Color,
    alpha: Float,
    rotation: Float
) {
    rotate(rotation, pivot = center) {
        val p = Path().apply {
            moveTo(center.x, center.y - r)
            quadraticTo(center.x + r * 0.22f, center.y - r * 0.22f, center.x + r, center.y)
            quadraticTo(center.x + r * 0.22f, center.y + r * 0.22f, center.x, center.y + r)
            quadraticTo(center.x - r * 0.22f, center.y + r * 0.22f, center.x - r, center.y)
            quadraticTo(center.x - r * 0.22f, center.y - r * 0.22f, center.x, center.y - r)
            close()
        }
        drawPath(p, color, alpha = alpha)
    }
}

/* ==================== 内容区块 ==================== */

@Composable
private fun VersionCompareBar(oldVersion: String, newVersion: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 旧版本
        Box(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = "v$oldVersion",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.55f),
                textDecoration = TextDecoration.LineThrough
            )
        }
        // 箭头
        Text("➜", color = CuteYellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        // 新版本
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(CuteCyan, CutePurple, CutePink)),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text = "v$newVersion",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ReleaseNotesBox(notes: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .heightIn(max = 108.dp)
            .verticalScroll(rememberScrollState())
    ) {
        notes.forEach { note ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Brush.linearGradient(listOf(CuteCyan, CutePink)), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = note,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

/* ==================== 状态区块 ==================== */

@Composable
private fun DownloadProgressSection(progress: Float, downloaded: Long, total: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(progress = progress, size = 92.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                val mbText = if (total > 0) {
                    String.format("%.1f MB", downloaded / 1048576.0)
                } else ""
                val mbText2 = if (total > 0) " / " + String.format("%.1f MB", total / 1048576.0) else ""
                val fullMbText = mbText + mbText2
                Text(
                    text = fullMbText,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        EqualizerBars(modifier = Modifier.size(72.dp, 20.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "正在下载新版本，请保持网络连接…",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ProgressRing(progress: Float, size: androidx.compose.ui.unit.Dp) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "ring_progress"
    )
    Canvas(modifier = Modifier.size(size)) {
        val stroke = 7.dp.toPx()
        val inset = stroke / 2f
        val minDim = minOf(this.size.width, this.size.height)
        drawCircle(
            color = Color.White.copy(alpha = 0.12f),
            radius = minDim / 2f - inset,
            center = center,
            style = Stroke(width = stroke)
        )
        drawArc(
            brush = Brush.sweepGradient(
                listOf(CuteCyan, CutePurple, CutePink, CuteCyan),
                center = center
            ),
            startAngle = -90f,
            sweepAngle = 360f * animatedProgress,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - inset * 2f, this.size.height - inset * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun EqualizerBars(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "eq_time"
    )
    Canvas(modifier = modifier) {
        val barWidth = size.width / 7f
        repeat(5) { i ->
            val phase = (t + i * 0.18f) % 1f
            val h = (0.25f + 0.55f * abs(sin(phase * PI * 2f)).toFloat()) * size.height
            val x = (i + 1) * barWidth - barWidth / 2f
            val color = when (i) {
                0, 4 -> CuteCyan
                1, 3 -> CutePurple
                else -> CutePink
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x - barWidth / 4f, size.height - h),
                size = Size(barWidth / 2f, h),
                cornerRadius = CornerRadius(barWidth / 4f)
            )
        }
    }
}

@Composable
private fun InstallingSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RocketLaunch(modifier = Modifier.size(120.dp, 96.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "正在安装新版本…（将自动替换旧版本）",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "安装完成即可体验全新功能 ✨",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun RocketLaunch(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rocket")
    val flame by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(160, easing = LinearEasing), RepeatMode.Reverse),
        label = "flame"
    )
    val shake by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(90, easing = LinearEasing), RepeatMode.Restart),
        label = "shake"
    )
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val top = 6f
        val bodyW = 34f
        val bodyH = 52f
        val shakeX = (if (shake > 0.5f) 1.6f else -1.6f)

        rotate(shakeX, pivot = Offset(cx, top + bodyH / 2f)) {
            // 火焰
            val flameLen = 14f + flame * 14f
            val flamePath = Path().apply {
                moveTo(cx - 10f, top + bodyH)
                quadraticTo(cx - 6f, top + bodyH + flameLen * 0.55f, cx, top + bodyH + flameLen)
                quadraticTo(cx + 6f, top + bodyH + flameLen * 0.55f, cx + 10f, top + bodyH)
                close()
            }
            drawPath(flamePath, Brush.linearGradient(listOf(CuteYellow, CuteOrange)))

            // 机身
            drawRoundRect(
                color = Color(0xFFE9E9F4),
                topLeft = Offset(cx - bodyW / 2f, top),
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(12f)
            )
            // 舷窗
            drawCircle(CuteCyan.copy(alpha = 0.25f), radius = 9f, center = Offset(cx, top + 22f))
            drawCircle(Color(0xFF9BE8FF), radius = 5.5f, center = Offset(cx, top + 22f))
            // 鼻锥
            val nose = Path().apply {
                moveTo(cx, top - 16f)
                lineTo(cx - bodyW / 2f, top + 6f)
                lineTo(cx + bodyW / 2f, top + 6f)
                close()
            }
            drawPath(nose, Brush.linearGradient(listOf(CutePink, CutePurple)))
            // 尾翼
            val finL = Path().apply {
                moveTo(cx - bodyW / 2f, top + bodyH - 14f)
                lineTo(cx - bodyW / 2f - 12f, top + bodyH + 2f)
                lineTo(cx - bodyW / 2f, top + bodyH)
                close()
            }
            val finR = Path().apply {
                moveTo(cx + bodyW / 2f, top + bodyH - 14f)
                lineTo(cx + bodyW / 2f + 12f, top + bodyH + 2f)
                lineTo(cx + bodyW / 2f, top + bodyH)
                close()
            }
            drawPath(finL, CuteOrange)
            drawPath(finR, CuteOrange)
        }
    }
}

@Composable
private fun DoneSection(onDone: () -> Unit, onRestartApp: () -> Unit) {
    var burst by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { burst = true }
    val progress by animateFloatAsState(
        targetValue = if (burst) 1f else 0f,
        animationSpec = tween(1400, easing = FastOutSlowInEasing),
        label = "confetti"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            ConfettiBurst(progress = progress, modifier = Modifier.size(140.dp, 72.dp))
        }
        Text(
            text = "🎉 更新完成！",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "新版本已安装成功，重启应用即可体验全新功能 ✨",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.5.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        GradientButton(
            text = "立即重启",
            gradient = listOf(CuteGreen, CuteCyan),
            modifier = Modifier.fillMaxWidth(),
            onClick = onRestartApp,
            pulsing = true
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextButtonGhost(text = "稍后再说", onClick = onDone)
    }
}

@Composable
private fun ConfettiBurst(progress: Float, modifier: Modifier = Modifier) {
    val particles = remember {
        List(24) { i ->
            val angle = Math.toRadians((i * 360f / 24f + 15f).toDouble())
            ConfettiParticle(
                angle = angle,
                speed = 60f + (i % 5) * 14f,
                size = 5f + (i % 3) * 2f,
                color = listOf(CuteCyan, CutePink, CuteYellow, CuteGreen, CutePurple, CuteOrange)[i % 6]
            )
        }
    }
    Canvas(modifier = modifier) {
        particles.forEach { p ->
            val t = progress.coerceIn(0f, 1f)
            val dist = p.speed * t
            val x = center.x + cos(p.angle).toFloat() * dist
            val y = center.y + sin(p.angle).toFloat() * dist * 0.85f + t * t * 42f
            val alpha = (1f - t).coerceIn(0f, 1f)
            rotate(t * 540f, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(x - p.size / 2f, y - p.size / 2f),
                    size = Size(p.size, p.size),
                    alpha = alpha
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val angle: Double,
    val speed: Float,
    val size: Float,
    val color: Color
)

@Composable
private fun ErrorSection(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    showDismiss: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showDismiss) {
                GradientButton(
                    text = "稍后再说",
                    gradient = listOf(Color(0xFF3A3A4E), Color(0xFF45455C)),
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                )
            }
            if (canRetry) {
                GradientButton(
                    text = "重试",
                    gradient = listOf(CuteCyan, CutePurple, CutePink),
                    modifier = Modifier.weight(1.2f),
                    onClick = onRetry,
                    pulsing = true
                )
            }
        }
    }
}

@Composable
private fun NeedPermissionSection(
    onOpenSettings: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "需要开启「允许安装未知应用」权限\n才能自动安装新版本哦～",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        GradientButton(
            text = "去开启权限",
            gradient = listOf(CuteCyan, CutePurple, CutePink),
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenSettings,
            pulsing = true
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "开启权限后回到这里，点下方按钮继续自动安装",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButtonGhost(text = "暂不更新", onClick = onDismiss, modifier = Modifier.weight(1f))
            GradientButton(
                text = "重新安装",
                gradient = listOf(CuteGreen, CuteCyan),
                modifier = Modifier.weight(1.2f),
                onClick = onInstall,
                pulsing = true
            )
        }
    }
}

@Composable
private fun ReadyInstallSection(onInstall: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "新版本已下载完成 🎁",
            color = CuteYellow,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        GradientButton(
            text = "立即安装",
            gradient = listOf(CuteGreen, CuteCyan, CutePurple),
            modifier = Modifier.fillMaxWidth(),
            onClick = onInstall,
            pulsing = true
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "安装时将自动卸载旧版本并安装新版本",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.5.sp
        )
    }
}

@Composable
private fun CheckingSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "正在检测最新版本…",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
    }
}

/* ==================== 通用组件 ==================== */

@Composable
private fun GradientButton(
    text: String,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    pulsing: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse_scale"
    )
    val scale = if (pulsing) pulse else 1f
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                brush = Brush.horizontalGradient(gradient),
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun TextButtonGhost(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 12.sp,
        modifier = modifier
            .clickable { onClick() }
            .padding(6.dp)
    )
}

/* ==================== 私有工具 ==================== */
