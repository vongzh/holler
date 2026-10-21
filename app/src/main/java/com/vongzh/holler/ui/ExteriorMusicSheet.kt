package com.vongzh.holler.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vongzh.holler.audio.media.HollerMediaEngine
import com.vongzh.holler.audio.media.MediaItem
import com.vongzh.holler.ui.theme.HollerTheme

/**
 * 车外音乐播放专属弹窗 (Boombox Exterior Music Player)。
 * 独立功能面板，大宽屏双列布局，黑胶唱机 + 播放列表 + 闪避开关。
 */
@Composable
fun ExteriorMusicSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    mediaEngine: HollerMediaEngine,
    isPlaying: Boolean,
    currentTrack: MediaItem?,
    positionMs: Long,
    durationMs: Long,
    tracks: List<MediaItem>,
    playMode: HollerMediaEngine.PlayMode,
    duckingEnabled: Boolean,
    onDuckingChange: (Boolean) -> Unit,
    onImportClick: () -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(initialScale = 0.90f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f)) + fadeIn(tween(200)),
        exit = scaleOut(targetScale = 0.92f, animationSpec = tween(160)) + fadeOut(tween(160)),
        modifier = modifier.fillMaxSize()
    ) {
        val vinylTransition = rememberInfiniteTransition(label = "vinyl_rot")
        val vinylAngle by vinylTransition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
            label = "vinyl_angle"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 840.dp, max = 1040.dp)
                    .fillMaxWidth(0.92f)
                    .heightIn(min = 520.dp, max = 660.dp)
                    .fillMaxHeight(0.90f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color(0x301C2C40),
                        spotColor = Color(0x451C2C40)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(HollerTheme.colors.BrandElevated)
                    .border(
                        BorderStroke(1.dp, HollerTheme.colors.Stroke),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 26.dp, vertical = 22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 顶栏：标题与动作
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(HollerTheme.colors.Accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = HollerTheme.colors.AccentText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "车外音乐播放 (Boombox)",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HollerTheme.colors.TextPrimary
                                )
                                Text(
                                    text = "定向至车外高音扬声器广播外放 · 驻车/行车营地多场景支持",
                                    fontSize = 12.sp,
                                    color = HollerTheme.colors.TextSecondary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(HollerTheme.colors.Brand)
                                    .border(BorderStroke(1.dp, HollerTheme.colors.Stroke), RoundedCornerShape(14.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onScanClick()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = "扫描",
                                    tint = HollerTheme.colors.TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("重新扫描", fontSize = 11.5.sp, color = HollerTheme.colors.TextSecondary)
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(HollerTheme.colors.Accent.copy(alpha = 0.15f))
                                    .border(BorderStroke(1.dp, HollerTheme.colors.Accent.copy(alpha = 0.4f)), RoundedCornerShape(14.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onImportClick()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = "导入",
                                    tint = HollerTheme.colors.AccentText,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("导入音频", fontSize = 11.5.sp, color = HollerTheme.colors.AccentText, fontWeight = FontWeight.SemiBold)
                            }

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(HollerTheme.colors.Brand)
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "关闭",
                                    tint = HollerTheme.colors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 主内容区：双列布局 (左黑胶唱机 + 右播放列表)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 左栏：播放控制台 (44% 宽度)
                        Column(
                            modifier = Modifier
                                .weight(0.44f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(22.dp))
                                .background(HollerTheme.colors.Brand)
                                .border(BorderStroke(1.dp, HollerTheme.colors.Stroke), RoundedCornerShape(22.dp))
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 黑胶旋转唱盘
                            Box(
                                modifier = Modifier
                                    .size(150.dp)
                                    .shadow(14.dp, CircleShape, ambientColor = Color(0x33000000), spotColor = Color(0x55000000))
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Color(0xFF262626), Color(0xFF141414), Color(0xFF000000))
                                        )
                                    )
                                    .border(BorderStroke(2.dp, Color(0xFF333333)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .rotate(if (isPlaying) vinylAngle else 0f)
                                ) {
                                    val c = Offset(size.width / 2f, size.height / 2f)
                                    listOf(0.35f, 0.50f, 0.65f, 0.80f).forEach { frac ->
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.08f),
                                            radius = (size.width / 2f) * frac,
                                            center = c,
                                            style = Stroke(width = 1f)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(HollerTheme.colors.Accent.copy(alpha = 0.85f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = HollerTheme.colors.OnAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // 歌曲元信息
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = currentTrack?.title ?: "未选择车外音乐",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HollerTheme.colors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "${currentTrack?.artist ?: "车载多媒体库"} · 车外通道定向",
                                    fontSize = 12.sp,
                                    color = HollerTheme.colors.TextSecondary,
                                    maxLines = 1
                                )
                            }

                            // 播放进度指示
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val progressFrac = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(HollerTheme.colors.BrandElevated)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = progressFrac)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(HollerTheme.colors.Accent)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val posSec = positionMs / 1000
                                    val durSec = durationMs / 1000
                                    Text(
                                        String.format("%02d:%02d", posSec / 60, posSec % 60),
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = HollerTheme.colors.TextSecondary
                                    )
                                    Text(
                                        String.format("%02d:%02d", durSec / 60, durSec % 60),
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = HollerTheme.colors.TextSecondary
                                    )
                                }
                            }

                            // 对称居中对齐控制台（模式胶囊、上一曲、主播放钮、下一曲、喊话避让胶囊）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 循环模式胶囊键 (匹配右侧避让键尺寸，确保整体绝对对称居中)
                                val (modeIcon, modeLabel) = when (playMode) {
                                    HollerMediaEngine.PlayMode.LIST_LOOP -> Pair(Icons.Rounded.Repeat, "循环")
                                    HollerMediaEngine.PlayMode.SINGLE_LOOP -> Pair(Icons.Rounded.RepeatOne, "单曲")
                                    HollerMediaEngine.PlayMode.SHUFFLE -> Pair(Icons.Rounded.Shuffle, "随机")
                                }
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .width(68.dp)
                                        .clip(CircleShape)
                                        .background(HollerTheme.colors.BrandElevated)
                                        .border(BorderStroke(1.dp, HollerTheme.colors.Stroke), CircleShape)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val modes = HollerMediaEngine.PlayMode.entries
                                            val next = modes[(playMode.ordinal + 1) % modes.size]
                                            mediaEngine.playMode = next
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = modeIcon,
                                            contentDescription = "播放模式",
                                            tint = HollerTheme.colors.TextSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = modeLabel,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = HollerTheme.colors.TextSecondary
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                // 上一曲
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(HollerTheme.colors.BrandElevated)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            mediaEngine.playPrevious()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipPrevious,
                                        contentDescription = "上一首",
                                        tint = HollerTheme.colors.TextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                // 主播放/暂停圆钮（绝对居中视觉锚点）
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .shadow(
                                            elevation = 10.dp,
                                            shape = CircleShape,
                                            ambientColor = HollerTheme.colors.Accent.copy(alpha = 0.25f),
                                            spotColor = HollerTheme.colors.Accent.copy(alpha = 0.55f)
                                        )
                                        .clip(CircleShape)
                                        .background(HollerTheme.colors.Accent)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            mediaEngine.togglePlayPause()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "播放/暂停",
                                        tint = HollerTheme.colors.OnAccent,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                // 下一曲
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(HollerTheme.colors.BrandElevated)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            mediaEngine.playNext()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipNext,
                                        contentDescription = "下一首",
                                        tint = HollerTheme.colors.TextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                // 车外喊话智能避让（Ducking）控制胶囊
                                val duckingBg by animateColorAsState(
                                    targetValue = if (duckingEnabled) HollerTheme.colors.Accent.copy(alpha = 0.14f) else HollerTheme.colors.BrandElevated,
                                    label = "duckingBg"
                                )
                                val duckingBorder by animateColorAsState(
                                    targetValue = if (duckingEnabled) HollerTheme.colors.Accent.copy(alpha = 0.45f) else HollerTheme.colors.Stroke,
                                    label = "duckingBorder"
                                )
                                val duckingContentColor by animateColorAsState(
                                    targetValue = if (duckingEnabled) HollerTheme.colors.AccentText else HollerTheme.colors.TextSecondary,
                                    label = "duckingContentColor"
                                )
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .width(68.dp)
                                        .clip(CircleShape)
                                        .background(duckingBg)
                                        .border(BorderStroke(1.dp, duckingBorder), CircleShape)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onDuckingChange(!duckingEnabled)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.RecordVoiceOver,
                                            contentDescription = "喊话避让",
                                            tint = duckingContentColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "避让",
                                            fontSize = 12.sp,
                                            fontWeight = if (duckingEnabled) FontWeight.SemiBold else FontWeight.Medium,
                                            color = duckingContentColor
                                        )
                                        // 状态指示圆点
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (duckingEnabled) HollerTheme.colors.Accent
                                                    else HollerTheme.colors.TextSecondary.copy(alpha = 0.35f)
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        // 右栏：车外播放列表 (56% 宽度)
                        Column(
                            modifier = Modifier
                                .weight(0.56f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(22.dp))
                                .background(HollerTheme.colors.Brand)
                                .border(BorderStroke(1.dp, HollerTheme.colors.Stroke), RoundedCornerShape(22.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "车外播报列表 (${tracks.size})",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HollerTheme.colors.TextPrimary
                                )
                                Text(
                                    text = "点击即切播",
                                    fontSize = 11.5.sp,
                                    color = HollerTheme.colors.TextSecondary
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            if (tracks.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = HollerTheme.colors.TextSecondary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            "暂无车外媒体音频，请点击右上角导入或扫描",
                                            fontSize = 12.5.sp,
                                            color = HollerTheme.colors.TextSecondary
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(tracks) { track ->
                                        val isThisPlaying = isPlaying && currentTrack?.id == track.id
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (isThisPlaying) HollerTheme.colors.BrandElevated else HollerTheme.colors.Brand)
                                                .border(
                                                    BorderStroke(
                                                        1.dp,
                                                        if (isThisPlaying) HollerTheme.colors.Accent.copy(alpha = 0.7f) else HollerTheme.colors.Stroke.copy(alpha = 0.6f)
                                                    ),
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    mediaEngine.playTrack(track)
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (isThisPlaying) {
                                                    AudioEqualizerBars(
                                                        color = HollerTheme.colors.Accent,
                                                        level = 70,
                                                        isRecording = false,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Rounded.MusicNote,
                                                        contentDescription = null,
                                                        tint = HollerTheme.colors.TextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = track.title,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = if (isThisPlaying) FontWeight.Bold else FontWeight.Medium,
                                                        color = HollerTheme.colors.TextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = track.artist,
                                                        fontSize = 11.sp,
                                                        color = HollerTheme.colors.TextSecondary,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            val sec = track.durationMs / 1000
                                            Text(
                                                String.format("%02d:%02d", sec / 60, sec % 60),
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = HollerTheme.colors.TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
