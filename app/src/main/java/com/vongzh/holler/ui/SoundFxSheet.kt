package com.vongzh.holler.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vongzh.holler.audio.HollerEngine
import com.vongzh.holler.ui.theme.HollerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 趣味车外音效专属弹窗 (Sound Fx Sheet)。
 * 独立功能面板，大宽屏双列卡片布局，无黑遮罩，带弹性进出场。
 */
@Composable
fun SoundFxSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    engine: HollerEngine,
    scope: CoroutineScope,
    onStatusChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(initialScale = 0.90f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f)) + fadeIn(tween(200)),
        exit = scaleOut(targetScale = 0.92f, animationSpec = tween(160)) + fadeOut(tween(160)),
        modifier = modifier.fillMaxSize()
    ) {
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
                    .widthIn(min = 760.dp, max = 920.dp)
                    .fillMaxWidth(0.88f)
                    .heightIn(min = 480.dp, max = 620.dp)
                    .fillMaxHeight(0.86f)
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
                    // 顶栏：标题与关闭
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
                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = null,
                                    tint = HollerTheme.colors.AccentText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "趣味车外音效库",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HollerTheme.colors.TextPrimary
                                )
                                Text(
                                    text = "定向至车外高音扬声器广播 · 礼让行人与逗趣互动提醒",
                                    fontSize = 12.sp,
                                    color = HollerTheme.colors.TextSecondary
                                )
                            }
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

                    // 音效网格列表
                    val soundEffects: List<Pair<Triple<String, String, ImageVector>, suspend () -> Unit>> = listOf(
                        Triple("优雅叮咚", "清脆双音节，温和礼让行人", Icons.Rounded.Notifications) to { engine.playChime() },
                        Triple("喊话鸭鸣", "逗趣鸭叫声，互动避让提示", Icons.Rounded.AutoAwesome) to { engine.playPcm(engine.duckPcm()) },
                        Triple("呼哨互动", "欢快互动口哨，趣味友好打招呼", Icons.Rounded.MusicNote) to { engine.playPcm(engine.whistlePcm()) },
                        Triple("短促鸣笛", "0.2s 柔和短按，提醒前车起步", Icons.Rounded.Campaign) to { engine.playPcm(engine.hornPcm()) },
                        Triple("倒车蜂鸣", "间歇低频蜂鸣，提示后方行人", Icons.Rounded.GraphicEq) to { engine.playPcm(engine.reverseWarningPcm()) },
                        Triple("警示长鸣", "连续防险警报音，紧急避险提醒", Icons.Rounded.Warning) to { engine.playPcm(engine.sirenPcm()) },
                        Triple("基准校音", "440Hz 纯正正弦波，声道快速测试", Icons.AutoMirrored.Rounded.VolumeUp) to { engine.playTone() },
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(soundEffects.chunked(2)) { rowPair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowPair.forEach { (info, action) ->
                                    val (name, desc, iconVector) = info
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(HollerTheme.colors.Brand)
                                            .border(BorderStroke(1.dp, HollerTheme.colors.Stroke.copy(alpha = 0.8f)), RoundedCornerShape(18.dp))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onStatusChange("正在播放车外音效：$name")
                                                scope.launch { action() }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(HollerTheme.colors.BrandElevated),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = iconVector,
                                                contentDescription = name,
                                                tint = HollerTheme.colors.TextPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = name,
                                                color = HollerTheme.colors.TextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                text = desc,
                                                color = HollerTheme.colors.TextSecondary,
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "播放",
                                            tint = HollerTheme.colors.AccentText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                if (rowPair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
