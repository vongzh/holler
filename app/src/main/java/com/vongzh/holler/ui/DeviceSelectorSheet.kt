package com.vongzh.holler.ui

import android.media.AudioDeviceInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vongzh.holler.audio.AudioRouteResolver
import com.vongzh.holler.ui.theme.HollerTheme

/**
 * 车外音频通道选择专属弹窗 (Device Selector Sheet)。
 * 严谨整洁的弹窗卡片，弹簧进出场，高质感选中态（告别刺眼实色蓝条），无黑色遮罩。
 */
@Composable
fun DeviceSelectorSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    currentRoute: AudioRouteResolver.RouteInfo?,
    availableDevices: List<AudioDeviceInfo>,
    preferredDeviceId: Int?,
    onSelectDevice: (Int?) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.35f,
            transformOrigin = TransformOrigin(pivotFractionX = 0.85f, pivotFractionY = 0f),
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f)
        ) + fadeIn(tween(160)),
        exit = scaleOut(
            targetScale = 0.35f,
            transformOrigin = TransformOrigin(pivotFractionX = 0.85f, pivotFractionY = 0f),
            animationSpec = tween(140)
        ) + fadeOut(tween(140)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.TopEnd
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 24.dp, top = 62.dp)
                    .width(460.dp)
                    .heightIn(min = 400.dp, max = 560.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(26.dp),
                        ambientColor = Color(0x351C2C40),
                        spotColor = Color(0x501C2C40)
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(HollerTheme.colors.BrandElevated)
                    .border(
                        BorderStroke(1.dp, HollerTheme.colors.Stroke),
                        RoundedCornerShape(26.dp)
                    )
                    .padding(horizontal = 22.dp, vertical = 20.dp)
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(HollerTheme.colors.Accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = null,
                                    tint = HollerTheme.colors.AccentText,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "车外广播输出设备",
                                    fontSize = 17.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HollerTheme.colors.TextPrimary
                                )
                                Text(
                                    text = "定向选择车外广播物理扬声器通道",
                                    fontSize = 12.sp,
                                    color = HollerTheme.colors.TextSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(HollerTheme.colors.Brand)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭",
                                tint = HollerTheme.colors.TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // 设备列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. 智能自动匹配
                        val isAutoSelected = preferredDeviceId == null
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isAutoSelected) HollerTheme.colors.BrandElevated else HollerTheme.colors.Brand)
                                    .border(
                                        BorderStroke(
                                            if (isAutoSelected) 1.5.dp else 1.dp,
                                            if (isAutoSelected) HollerTheme.colors.Accent.copy(alpha = 0.75f) else HollerTheme.colors.Stroke.copy(alpha = 0.6f)
                                        ),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectDevice(null)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isAutoSelected) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.5.dp)
                                                .height(24.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(HollerTheme.colors.Accent)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isAutoSelected) HollerTheme.colors.Accent.copy(alpha = 0.15f) else HollerTheme.colors.BrandElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                            contentDescription = null,
                                            tint = if (isAutoSelected) HollerTheme.colors.AccentText else HollerTheme.colors.TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "智能自动匹配通道 (推荐)",
                                            fontSize = 14.5.sp,
                                            fontWeight = if (isAutoSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = HollerTheme.colors.TextPrimary
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "优先定向车外扬声器，未检测到时使用默认通道",
                                            fontSize = 11.5.sp,
                                            color = HollerTheme.colors.TextSecondary
                                        )
                                    }
                                }

                                if (isAutoSelected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(HollerTheme.colors.Accent.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "当前使用",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = HollerTheme.colors.AccentText
                                        )
                                    }
                                }
                            }
                        }

                        // 2. 硬件设备列表
                        items(availableDevices) { dev ->
                            val isThisSelected = preferredDeviceId == dev.id
                            val devIcon = when (dev.type) {
                                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> Icons.Rounded.Bluetooth
                                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> Icons.Rounded.Usb
                                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> Icons.Rounded.Headphones
                                else -> Icons.Rounded.Speaker
                            }
                            val devTypeName = when (dev.type) {
                                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙车载设备"
                                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 外置音频"
                                AudioDeviceInfo.TYPE_BUS -> "车载总线扬声器"
                                else -> "车载物理扬声器"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isThisSelected) HollerTheme.colors.BrandElevated else HollerTheme.colors.Brand)
                                    .border(
                                        BorderStroke(
                                            if (isThisSelected) 1.5.dp else 1.dp,
                                            if (isThisSelected) HollerTheme.colors.Accent.copy(alpha = 0.75f) else HollerTheme.colors.Stroke.copy(alpha = 0.6f)
                                        ),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectDevice(dev.id)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isThisSelected) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.5.dp)
                                                .height(24.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(HollerTheme.colors.Accent)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isThisSelected) HollerTheme.colors.Accent.copy(alpha = 0.15f) else HollerTheme.colors.BrandElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = devIcon,
                                            contentDescription = null,
                                            tint = if (isThisSelected) HollerTheme.colors.AccentText else HollerTheme.colors.TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dev.productName.toString().ifBlank { "未知音频设备" },
                                            fontSize = 14.sp,
                                            fontWeight = if (isThisSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = HollerTheme.colors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "$devTypeName · ID: ${dev.id}",
                                            fontSize = 11.5.sp,
                                            color = HollerTheme.colors.TextSecondary
                                        )
                                    }
                                }

                                if (isThisSelected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(HollerTheme.colors.Accent.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "当前使用",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = HollerTheme.colors.AccentText
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 底栏：高级系统音频设置入口
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(HollerTheme.colors.Brand)
                            .border(BorderStroke(1.dp, HollerTheme.colors.Stroke.copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
                            .clickable {
                                onDismiss()
                                onOpenSettings()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = HollerTheme.colors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "高级系统音频通路与混音策略",
                                fontSize = 12.sp,
                                color = HollerTheme.colors.TextSecondary
                            )
                        }
                        Text(
                            text = "前往设置 ›",
                            fontSize = 11.5.sp,
                            color = HollerTheme.colors.AccentText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
