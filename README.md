# Holler

领克 08 车机「车外喊话」应用。**Kotlin + Jetpack Compose**，界面参考 **Flyme Auto** 设计语言。
技术路线来自对 CoAuto **Hey! v1.2.5** 的逆向分析（见 `docs/HEY_CAPABILITY.md`）。

## 核心问题

把音频**定向输出到车外扬声器**，而不是车内音响。

```
按住录音 (AudioRecord)  →  DSP 变声  →  松开回放 (AudioTrack + setPreferredDevice)  →  车外响
```

难点不在播放，而在**定位车外喇叭对应哪个音频设备 / usage**。

## 从 Hey! 逆向出的关键信息

| 发现 | 内容 |
|---|---|
| 包名 / 技术栈 | `com.kooo.hey`，Kotlin + Compose，自有类仅 13 个 |
| 音频输出 | 标准 `AudioTrack` + `AudioDeviceInfo.setPreferredDevice` |
| 路由定位 | 读 `/vendor\|/odm\|/system/etc/car_audio_configuration.xml` + `CarAudioManager` |
| usage 遍历 | `UsageSweepWorker` 逐个 usage 播 800ms 特征音，人工听车外 |
| 提权技巧 | `app_process /system/bin com.kooo.hey.audio.UsageSweepWorker` |
| 变声实现 | `libvoiceeffect.so`，5 个 JNI 方法，5 种效果 |
| APK 规模 | dex 1.29MB，10928 个方法，1304 个类（含框架引用） |

参考素材：`../hey/reference/`（APK + 分析产物）。

> 与 Hey! 的逐项差异（设置 / 界面 / 配置 24 项对照）见 [`docs/GAP_VS_HEY.md`](docs/GAP_VS_HEY.md)。

## 能力对照表（Holler vs Hey!）

| # | Hey! 能力 | Holler 实现 | 状态 |
|---|---|---|---|
| 1 | 按住说话（Push-to-Talk） | `HollerEngine` 录音→DSP→定向回放 | ✅ |
| 2 | 变声 5 种（原声/大叔/机器人/电音/萝莉） | `DspChain` 纯 Kotlin 等价实现 | ✅ |
| 3 | 音量增益 + AGC | `DspChain.agc` 对齐 Hey! 实测参数 | ✅ |
| 4 | usage 遍历定位车外喇叭 | `UsageSweeper` | ✅ |
| 5 | 音频配置文件解析 | `AudioRouteResolver.parseCarAudioConfig` | ✅ |
| 6 | 悬浮窗喊话按钮 | `FloatingTalkService` + `FloatingTalkView` | ✅ |
| 7 | 悬浮键单击播快捷语 | 同上，位置/透明度持久化 | ✅ |
| 8 | TTS 文字转语音 | `PhraseSpeaker`（合成到文件→定向播放） | ✅ |
| 9 | tts_items 快捷语管理 | `PhraseStore`（增删改 + 预置 7 条） | ✅ |
| 10 | 语速 / 音调可调 | `PhraseStore.speechRate / pitch` | ✅ |
| 11 | 外部命令接收器 | `HollerCommandReceiver`（3 个 action） | ✅ |
| 12 | 日志关键词触发 | `LogcatTriggerService` | ✅ |
| 13 | 前台保活 | `HollerForegroundService` | ✅ |
| 14 | 音乐播放服务 | — | ⬜ 排后 |
| 15 | ADB 自连接提权 | `shell/DeviceProbe.kt` 占位 | ⬜ 待车机验证 |
| 16 | **手动指定音频设备** | `PhraseStore.outputDeviceId` + 设置页「选用」 | ✅ v0.3.1 |
| 17 | **录音倒计时环** | `ui/CountdownRingView.kt` | ✅ v0.3.1 |
| 18 | **浮窗透明度 / 尺寸** | 设置页两个滑块 + `FloatingTalkService.refreshAppearance` | ✅ v0.3.1 |
| 19 | **启动自动播报** | `PhraseStore.launchAutoPlay` | ✅ v0.3.1 |

> 第 16–19 项是 v0.3.1 补齐的缺口，详见 [`docs/GAP_VS_HEY.md` §6](docs/GAP_VS_HEY.md)。

## 目录结构

```
app/src/main/java/com/vongz/holler/
  audio/
    AudioRouteResolver.kt        # 车外喇叭定位：设备名匹配 + 配置解析 + 兜底
    HollerEngine.kt              # 引擎单例：录音 + DSP + 回放 + Listener
    PhraseStore.kt               # 快捷语持久化 + PhraseSpeaker(TTS) + WavReader
    UsageSweeper.kt              # usage 遍历测试（对齐 Hey! UsageSweepWorker）
    FloatingTalkService.kt       # 悬浮窗服务（对齐 Hey! FloatingWindowService）
    LogcatTriggerService.kt      # 日志关键词触发（对齐 Hey! LogcatTriggerService）
    HollerCommandReceiver.kt     # 外部 Intent 触发（对齐 Hey! HeyCommandReceiver）
    HollerForegroundService.kt   # 保活前台服务
    dsp/
      VoiceEffect.kt             # 5 种变声效果枚举
      DspChain.kt                # 纯 Kotlin DSP 链（替代 libvoiceeffect.so）
      VoiceEffectBridge.kt       # 桥接层，接口对齐 Hey! 的 JNI 语义
    shell/DeviceProbe.kt         # app_process 提权入口（预留）
  ui/
    MainActivity.kt              # 三页导航主界面
    Controls.kt                  # Flyme Auto 风格控件库
    CountdownRingView.kt         # 录音倒计时环（对齐 Hey! CountdownRingView）
    FloatingTalkView.kt          # 悬浮按钮自绘 View
    WaveformView.kt              # 声波可视化
    theme/Theme.kt               # 色彩 / 形状 / 间距令牌
docs/
  DESIGN.md                      # 设计规范（Flyme Auto 参考）
  ENVIRONMENT.md                 # 环境准备（JDK + Android SDK）
  ADB_PROBE.md                   # 车机探测指南
  HEY_CAPABILITY.md              # Hey! v1.2.5 完整能力清单（逆向产物）
  GAP_VS_HEY.md                  # 与 Hey! 的逐项差异（24 项配置键对照）
tools/
  check.py                       # 静态自检（无需 JDK/SDK：括号配平 / 死导入 / 重名函数）
```

## 界面布局（v0.3.1 三页导航）

```
┌──────────────────────────────────────────────┐
│  Holler                          待命 ●       │  顶栏（状态点常驻）
├──────────────────────────────────────────────┤
│  ┌────────────────────────────────────────┐  │
│  │         ╭─────────────╮                │  │
│  │         │  ◎ 声波  12 │  ← 倒计时环    │  │
│  │         ╰─────────────╯                │  │  喊话页
│  │            按住喊话                     │  │
│  │            准备就绪                     │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │ 变声效果                    原声        │  │
│  │ [原声][大叔][机器人][电音][萝莉]        │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │ 音量与增益                ────●── 100%  │  │
│  │ 自动增益 (AGC)                     [ON] │  │
│  └────────────────────────────────────────┘  │
├──────────────────────────────────────────────┤
│      喊话      │      语音      │    设置     │  DOCK 栏
└──────────────────────────────────────────────┘
```

**语音页**：播报参数（语速/音调滑块）+ 新增/修改快捷语 + 条目列表（播放/改名/设为快捷/删除）+ 恢复预置语料 + 测试音。
**设置页**：悬浮按钮开关 · **透明度/尺寸滑块** · **启动自动播报** · 日志触发（开关 · 关键词含自定义 · 冷却可调）· 音频路由诊断 · usage 遍历 · 外部触发命令 · **音频设备清单（可「选用」= 手动指定设备）** · 车机音频配置 · 关于。

## 当前进度（v0.3.1 · 已首次编译通过 ✅）

> **2026-10-08 里程碑**：工具链升到 **AGP 9.4.1 / Gradle 9.8.1 / Kotlin 2.4.20 /
> compileSdk 37**（当日前序版本首编译通过，随后再升级至最新并复编译通过），
> 5036 行代码**首次真实编译通过**（零 warning），
> 产出 `app/build/outputs/apk/debug/app-debug.apk`（约 29–30 MB）。
> 版本约束与 AGP 9 的三个坑详见 [`docs/ENVIRONMENT.md`](docs/ENVIRONMENT.md)。

- [x] Gradle 工程骨架（Kotlin 2.4.20 / AGP 9.4.1 / Gradle 9.8.1 / Compose BOM 2026.09 / compileSdk 37）
- [x] **首次真实编译通过**（2026-10-08）
- [x] 音频路由解析器（四路径：手动指定 / 设备名 / 配置 / 兜底）
- [x] 一键喊话（按住录音 → DSP → 松开发送）
- [x] 纯 Kotlin 变声 DSP（5 效果 + AGC + 噪声门 + 软限幅）
- [x] TTS 快捷语（合成到文件 → 定向播放，绕开 TTS 直出路由）
- [x] 悬浮喊话按钮（单击/长按/拖动/双击收起）
- [x] 外部 Intent 触发（方控按键 / 自动化工具）
- [x] 日志关键词触发（含 READ_LOGS 权限降级）
- [x] usage 遍历测试（定位车外喇叭）
- [x] 三页导航 UI（Flyme Auto 风格控件库）
- [x] 录音倒计时环（显示剩余秒数，最后 3 秒转红）
- [x] 手动指定音频设备（车外喇叭定位的兜底手段）
- [x] 浮窗透明度 / 尺寸可调 · 启动自动播报 · 冷却可调 · 关键词自定义
- [x] 「系统 usage」异常保护（`USAGE_SAFETY` 等 1000–1003 会抛异常，已反射兼容）
- [ ] **待验证**：车机 API 等级（决定 `minSdk 30` 是否正确，错了装不上）
- [ ] **待验证**：车外喇叭的真实设备/usage（需车机 dump）
- [ ] **待验证**：UsageSweeper 是否触发 native crash（若触发需升级为子进程）
- [ ] app_process 提权 `DeviceProbe`（依赖车机实测结论）
- [ ] 音乐 / 音频文件播放
- [ ] 方控按键实测联动

## 风险边界

**只做座舱信息娱乐域**：车外喊话、音频外放、TTS。
**不碰**：行驶控制、刹车/转向/动力、安全气囊、ADAS 参数、车门锁远程控制。

## 权限说明

| 权限 | 用途 | 获取方式 |
|---|---|---|
| `RECORD_AUDIO` | 录音 | 运行时申请 |
| `MODIFY_AUDIO_SETTINGS` | 音频路由 | 安装即得 |
| `SYSTEM_ALERT_WINDOW` | 悬浮按钮 | 系统设置手动授予 |
| `READ_LOGS` | 读系统日志触发 | **adb 一次性**：`adb shell pm grant com.vongz.holler android.permission.READ_LOGS` |
| `FOREGROUND_SERVICE*` | 后台保活 | 安装即得 |

未授予 `READ_LOGS` 时，日志触发服务自动降级为「仅读本应用日志」，不会崩溃。

## 构建

```bash
cd D:/Code/Holler

# 系统 PATH 里没有 java，需显式指定 Studio 自带的 JDK 21
export JAVA_HOME="C:/Users/vongz/.jdks/jbr-21.0.11"

./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk（约 30 MB）

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> ⚠ **装车前务必先确认车机 API 等级**：`adb shell getprop ro.build.version.sdk`。
> 项目当前 `minSdk = 30` 是按「Flyme Auto = 安卓 11」**推断**的，未经实测；
> 若车机不是 API 30，会直接 `INSTALL_FAILED_OLDER_SDK`。改回实际值只需一行。

## 命名

项目 **Holler** ｜ 包名 `com.vongz.holler` ｜ 代号来自 "holler"（大声喊）
