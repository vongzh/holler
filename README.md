# Holler

<p align="left">
  <b>车外喊话 · 车载外放广播</b><br/>
  Kotlin + Jetpack Compose ｜ 面向 Flyme Auto 车机（领克 08 等）｜ Apache-2.0
</p>

Holler 把音频流**定向路由到车外扬声器**，让你在车里对车外说话：礼让行人、倒车提醒、狭窄会车喊话 —— 一按即说，车外即响。

界面深度参考 **Flyme Auto** 智能座舱设计语言，完整适配**浅色 / 深色**双主题。

---

## 核心实现机制

```
按住录音 (AudioRecord) → 实时 DSP 变声处理 → 定向音频路由回放 (AudioTrack + setPreferredDevice) → 车外扬声器发声
```

车载音频的难点在于不同车机型号、不同底层 HAL 对车外扬声器的挂载差异。Holler 提供分层容灾的路由决策：

1. **设备名匹配**：枚举音频输出设备，匹配车外扬声器关键字；
2. **车载配置解析**：动态读取 `/vendor|/odm|/system/etc/` 下的 `car_audio_configuration.xml`；
3. **Usage 遍历测试**：逐个音频用途播放短音频特征流，快速锁定车外喇叭真实 usage 通道；
4. **手动指定兜底**：在设置页设备清单中直接绑定目标设备 ID。

---

## 功能特性

| 功能模块 | 说明 | 状态 |
|---|---|---|
| **按住说话（Push-to-Talk）** | Ø184dp 大热区圆钮，按住录音、松开发送；上滑就地填充取消 | ✅ |
| **变声处理链** | 纯 Kotlin DSP：原声 + 大叔 / 萝莉 / 机器人 / 电音 / 怪兽，无 native SO 依赖 | ✅ |
| **音量增益与 AGC** | 硬件级自动增益控制与软限幅，避免啸叫破音 | ✅ |
| **车外音频路由探测** | 设备名匹配、车载配置解析、Usage 遍历，四层容灾 | ✅ |
| **悬浮喊话按钮** | 全局悬浮球：单击快捷播报 / 长按说话 / 拖动贴边 / 双击收起 | ✅ |
| **TTS 快捷语管理** | 内置车外礼让语料，增删改查、语速音调调节、★ 常用标记 | ✅ |
| **录音倒计时环** | 15s 上限环形进度，常态青绿、**最后 3 秒转红**预警 | ✅ |
| **广播命令联动** | 标准 Intent 接收器，方控按键 / Tasker 自动化可直连 | ✅ |
| **系统日志关键词触发** | 命中倒车 / 碰撞等系统日志自动播报（带冷却防抖） | ✅ |
| **浅色 / 深色主题** | 全令牌化色彩系统，文本色单独分档保证浅色座舱可读性 | ✅ |
| **开机自启与前台保活** | 随车通电常驻待命，通知栏快捷控制 | ✅ |

---

## 界面与设计（Flyme Auto 风格）

单屏聚焦 + 底部 DOCK 的座舱式布局，所有触控目标 ≥ 44dp：

```
┌────────────────────────────────────────────────────────┐
│      ● 车载喊话系统已就绪          🔊 Car on x86_…      │ ← 状态胶囊（定宽防挤压）
│                                                        │
│                    ╭─────────────╮                     │
│                 ╾─┤  ▮▮▮ 声波柱   ├─╾                  │
│                  │   松开 发送     │   ← Ø184dp 主按钮   │
│                 ╾─┤   ↑ 上滑取消  ├─╾   + Ø196dp 倒计时环│
│                    ╰─────────────╯                     │
│                      按 住 喊 话                        │   ← 待机态（呼吸动效）
│                                                        │
│  ⚙                                                     │
│             [ 变声 ]  [ 快捷语音 ]  [ 车外音效 ]          │ ← 底部 DOCK
└────────────────────────────────────────────────────────┘
```

- **主按钮四态**：待机（呼吸麦克风）→ 录音（声波柱 + 就地提示）→ 上滑取消（圆底珊瑚填充渐满）→ 播报（绿色律动）
- **色彩纪律**：绿色 = 工作 / 就绪，红色 = 危险 / 时间压力（仅末 3 秒与取消态），不做全程常红
- **底栏语义**：面板展开 = 浅底高亮；持久状态（变声已启用）= 右上小圆点，两套反馈互不干扰

设计令牌、间距与状态机的完整定义见 [`docs/DESIGN.md`](docs/DESIGN.md)；2026-10 的逐项视觉审计见 [`docs/AUDIT-2026-10-10.md`](docs/AUDIT-2026-10-10.md)。

---

## 目录结构

```
app/src/main/java/com/vongzh/holler/
  audio/
    AudioRouteResolver.kt        # 车外扬声器定位：设备名匹配 + 配置解析 + 兜底策略
    HollerEngine.kt              # 音频引擎：录音流 + DSP 链 + AudioTrack 定向回放
    PhraseStore.kt               # 快捷语与配置持久化 + TTS 播放器（PhraseSpeaker）
    UsageSweeper.kt              # Usage 遍历测试工具
    FloatingTalkService.kt       # 全局桌面悬浮窗服务
    HollerForegroundService.kt   # 前台保活与通知栏快捷控制服务
    LogcatTriggerService.kt      # 系统日志关键词触发服务
    HollerCommandReceiver.kt     # 方控 / 第三方自动化广播接收器
    BootReceiver.kt              # 开机自启
    shell/DeviceProbe.kt         # 提权设备探测（app_process）
    dsp/
      VoiceEffect.kt             # 变声枚举
      DspChain.kt                # 纯 Kotlin 实时 DSP 链（音调漂移 / 混响 / 滤波 / AGC）
      VoiceEffectBridge.kt       # 变声处理桥接模块
  ui/
    MainActivity.kt              # 主界面：主按钮状态机 + DOCK + 设置弹层
    Controls.kt                  # Flyme Auto 风格车规控件库（卡片/开关/滑块/芯片/按钮）
    CountdownRingView.kt         # 录音倒计时环（末 3 秒转红）
    AudioEqualizerBars.kt        # 律动声波柱
    AudioGlowRippleView.kt       # 主按钮外扩声波涟漪
    FloatingTalkView.kt          # 自绘轻量级悬浮球控件（支持深浅色）
    theme/Theme.kt               # 色彩令牌 / 形状 / 间距 / 深浅色系统
```

---

## 构建与运行

### 环境要求

| 项 | 版本 |
|---|---|
| Android Studio | Ladybug (2024.2)+ |
| JDK | 21 |
| Gradle | 9.8.1（AGP 9） |
| Android SDK | compileSdk 37 / minSdk 30（Android 11，对应 Flyme Auto 车机） |

### 本地编译与安装

```bash
# 编译 Debug APK
./gradlew assembleDebug

# 安装到车机或模拟器（需先授权麦克风，喊话依赖录音）
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.vongzh.holler android.permission.RECORD_AUDIO
adb shell am start -n com.vongzh.holler/.ui.MainActivity
```

> 车机上若识别不到车外喇叭，进「设置 → 音频路由 → Usage 遍历测试」逐个试听，锁定真实通道后在设备清单里手动绑定。

---

## 声明与边界

- 本项目仅面向**座舱信息娱乐域**（车外扬声器语音广播、TTS 播报）。
- 本项目不涉及、亦不篡改任何车辆行驶、制动、动力、安全气囊或底层控制指令。
- 使用车外喊话功能时，请遵守道路交通安全法律法规，文明用车，切勿在禁鸣区域或夜间扰民。
- 界面语言参考 Flyme Auto，本项目**不包含**其任何源码或专有素材。

---

## 作者

**vongzh** · [github.com/vongzh/holler](https://github.com/vongzh/holler)

## License

本项目基于 [Apache License 2.0](LICENSE) 开源。

```
Copyright 2026 vongzh

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

**衍生作品必须标注来源**：转载、修改或再分发时，请保留 `LICENSE` 与 [`NOTICE`](NOTICE) 中的版权与许可声明，并在你改动过的文件上注明修改（Apache-2.0 §4(b)）。第三方组件清单见 `NOTICE`。
