# Holler vs Hey! —— 设置 / 界面 / 配置差异清单

> 参照对象：Hey! v1.2.5（`docs/HEY_CAPABILITY.md` 逆向实测）
> 对照对象：Holler v0.3.1（当前代码，`app/src/main/java/com/vongzh/holler/`）
> 目的：把「还差什么」落成可勾选的清单，而不是凭印象。
>
> **v0.3.1 更新**：缺口 9 项中已补 4 项（见 §6）。当前覆盖 **19 / 24（79.2%）**。

---

## 0. 结论先行

| 维度 | 差距等级 | 说明 |
|---|---|---|
| **音频能力**（喊话/变声/TTS/路由） | 🟢 基本持平 | 变声 5 种、AGC、增益、TTS 条目、usage 遍历均已实现 |
| **提权能力**（app_process / ADB 自连接） | 🔴 完全缺失 | Hey! 的第二道护城河，Holler 仍是占位 |
| **设置项**（配置键覆盖） | 🟡 覆盖 19/24 | 缺 5 项，见 §6 |
| **界面**（页面 / 浮层） | 🟢 Holler 更清晰 | Hey! 2 Activity 全塞主界面；Holler 3 页按场景分层 |
| **交互手势** | 🟢 持平 | 悬浮键单击/长按/拖动/双击全部实现 |
| **音乐播放** | ⚪ 用户已明确排后 | 非缺陷，是主动取舍 |

---

## 1. 配置键逐项对照（24 项）

从 Hey! 提取到的全部配置键，逐个核对 Holler 覆盖情况：

| # | Hey! 配置键 | Holler | 说明 |
|---|---|---|---|
| 1 | `enable_floating` | ✅ 有 | 设置页开关 |
| 2 | `floating_x` | 🟡 内部存 | `PhraseStore` 有，拖动自动持久化，**无 UI 输入框**（合理，拖即是设） |
| 3 | `floating_y` | 🟡 内部存 | 同上 |
| 4 | `floating_alpha` | ✅ **v0.3.1 补** | 设置页滑块，改动即时生效（无需重启服务） |
| 5 | `floating_size` | ✅ **v0.3.1 补** | 设置页滑块，范围 96–220dp |
| 6 | `enable_logcat_trigger` | ✅ 有 | 设置页开关 |
| 7 | `logcat_trigger_keyword` | ✅ **v0.3.1 补全** | 4 个预设 chip + 「自定义…」自由输入 |
| 8 | `tts_items` | ✅ 有 | 快捷语 CRUD 完整 |
| 9 | `tts_pitch` | ✅ 有 | 语音页滑块 |
| 10 | `tts_speech_rate` | ✅ 有 | 语音页滑块 |
| 11 | `tts_volume` | 🔴 **无** | TTS 独立音量，Holler 只有全局 `volume_gain` |
| 12 | `launch_auto_play` | ✅ **v0.3.1 补** | 设置页「启动时自动播报」开关（默认关） |
| 13 | `launch_tts_index` | ✅ 有 | 语音页 ★ 按钮 |
| 14 | `music_play_mode` | ⚪ 排后 | 用户明确不做 |
| 15 | `music_volume` | ⚪ 排后 | 同上 |
| 16 | `voice_effect` | ✅ 有 | 键名改 `voice_effect_type`，值域一致（5 种） |
| 17 | `volume_gain` | ✅ 有 | 喊话页滑块 |
| 18 | `enable_agc` | ✅ 有 | 喊话页开关 |
| 19 | `echo_cancel` | 🔴 **无** | 回声消除开关（Hey! 三件套之一） |
| 20 | `noise_suppress` | 🔴 **无** | 噪音抑制开关（同上） |
| 21 | `wake_optimize` | 🔴 **无** | 唤醒优化开关（同上） |
| 22 | `dsp_mode` | 🔴 **无** | 硬 DSP / 软 DSP 模式切换（Hey! 靠这个探测能否走厂商 HAL） |
| 23 | `input_device_id` | ✅ **v0.3.1 补** | 手动指定录音设备，设备清单里点「选用」 |
| 24 | `output_device_id` | ✅ **v0.3.1 补** | 手动指定播放设备，同上 |

**统计**：覆盖 19 / 24（79.2%）。缺 5 项（含 2 项主动不做）。

**Holler 额外新增（Hey! 无）**：
- `usage_override` —— 手动锁定 usage（usage 遍历出结果后固定下来）
- `logcat_cooldown_sec` —— 触发冷却可调（Hey! 硬编码 8s）

---

## 2. 缺口分组（按「值不值得补」排序）

> ⚠️ 本节记录的是 **v0.3.0 的原始分析**，保留作为决策依据的存档。
> **v0.3.1 已补齐的项目见 §6**，括号内标注状态。

### 2.1 🔴 高优先级 —— 影响核心功能可用性

#### ① `output_device_id` / `input_device_id` 手动指定设备　`✅ v0.3.1 已补`

**这是最该补的一项。**

Hey! 的逻辑：`UsageSweepWorker` 遍历找出车外喇叭 → 用户记下 **设备 id** → 手动填入 `output_device_id` → 此后固定用这个设备。

Holler 目前只有 `AudioRouteResolver` 的自动三路径识别（设备名匹配 / 配置文件解析 / 兜底）。**自动识别失败时，用户没有任何兜底手段。**

车机上大概率会遇到：设备名不是标准中文（各厂商命名不一）、`car_audio_configuration.xml` 结构差异、多路功放无法从名字判断哪个是车外。这时「手动试 + 记住」是唯一出路。

**补法**：
- `PhraseStore` 加 `outputDeviceId: Int`（-1 = 自动）、`inputDeviceId: Int`
- 设置页「输出设备清单」每行加一个「选用」按钮 → 写入 id
- `HollerEngine` 播放时若 `outputDeviceId >= 0`，优先用该 id 构造 `AudioDeviceInfo` 并 `setPreferredDevice`

注意：`AudioDeviceInfo` 不能直接由 id 构造，需要先 `getDevices()` 找到 id 匹配的那个。而普通 App 的 `getDevices()` 可能看不到车外设备——**这正是 ② 存在的原因**。

#### ② app_process 提权（`DeviceProbe.kt` 仍是占位）　`🔴 未做（待车机实测）`

Hey! 的两道护城河之一。Holler 的 `audio/shell/DeviceProbe.kt` **目前是占位实现**，没做真正的 `app_process` 子进程。

为什么和 ① 绑在一起：`AudioDeviceInfo` 枚举能力受权限限制。普通 App 调 `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` 拿到的列表，在车机上**可能不含车外喇叭**。Hey! 用 `app_process` 以 shell/root 身份跑 `UsageSweepWorker`，才能枚举出完整设备列表。

**补法**（复杂度高，建议车机实测后再决定）：
- 写一个含 `main()` 的 Java 类，打包进 APK 的 dex
- 运行时 `Runtime.exec("app_process /system/bin com.vongzh.holler.audio.shell.DeviceProbe")`
- 通过 stdout 管道读回结果
- **必须独立进程**——Hey! 的日志里有 `⚠ 子进程超时 (卡在 usage` 和 `→ ✗ NATIVE CRASH (进程被杀)`，说明遍历会崩，放主进程会拖垮整个 App

#### ③ `echo_cancel` / `noise_suppress` / `wake_optimize` 三件套　`⚪ 主动不做`

Hey! 的日志里有这三个开关。它们的实现路径有两个：

- **系统 API 路径**：`AudioRecord` 的 `AudioSource.VOICE_COMMUNICATION` 自带 AEC/NS；或用 `AcousticEchoCanceler` / `NoiseSuppressor` 类（需 `isAvailable()` 判定）
- **自研 DSP 路径**：Hey! 声称是「自研 DSP 链」

Holler 目前的 `DspChain` 只有 AGC + 噪声门 + 软限幅，**没有回声消除**。

**实际影响评估**：Holler 用「先录后放」模式（松手才播），录音时车外喇叭不发声，**天然没有回声问题**。所以这三项对 Holler 的优先级**低于 Hey!**——Hey! 支持音乐播放，播放与录音可能并发，才需要 AEC。

**建议**：先不做。如果车机实测发现录音有明显底噪，再加 `NoiseSuppressor`（系统 API，几行代码）。

#### ④ `dsp_mode` 硬/软 DSP 切换　`⚪ 主动不做`

Hey! 靠这个探测能否走厂商 HAL：`无法设置 workmode (需要系统签名或root)，将使用软件DSP处理`。

Holler 的变声是**纯 Kotlin 软件 DSP**，没有硬 DSP 路径，所以这个开关**暂无意义**。

**建议**：不做。除非将来把 `VoiceEffectBridge` 换成真 JNI 实现对接亿咖通 HAL（`vendor.ecarx.xma.audiocontrol@1.0::IEcarxAudioControl`），那时才需要。

---

### 2.2 🟡 中优先级 —— 体验补齐

#### ⑤ `floating_alpha` / `floating_size` 缺 UI　`✅ v0.3.1 已补`

**这是「已存但用户改不了」的典型**——`PhraseStore` 里两个属性都有，`FloatingTalkService` 也在读，但设置页的浮窗卡片里只有「启用/停用」一个开关。

**实际补法**（见 §6.3）：设置页悬浮窗卡片内加两个 `HollerSlider`，
`FloatingTalkService` 新增 `refreshAppearance(context)` 静态入口 + `applyAppearance()` 私有方法，
改动即时生效（尺寸变化需要重建 `LayoutParams`，不能只刷 alpha）。

#### ⑥ `logcat_trigger_keyword` 只能从 4 个预设里选　`✅ v0.3.1 已补`

Holler 设置页给了 4 个 chip：`请倒车` / `碰撞` / `行人` / `车道偏离`。Hey! 的关键词是**用户自由输入**的。

**实际补法**（见 §6.4）：加一个「自定义…」chip，点开复用 `TextInputDialog`。

#### ⑦ `launch_auto_play` 开机自动播报总开关　`✅ v0.3.1 已补`

Holler 有 `launchIndex`（选哪条），但**没有**「开机/应用启动时自动播」这个总开关。目前 `launchIndex` 只被两处消费：悬浮键单击、日志触发命中。

**实际补法**（见 §6.5）：设置页新增「启动行为」卡片 + `launchAutoPlay` 开关。
实现放在 `HollerApp` 的启动 `LaunchedEffect` 里（延迟 1.2s 等音频通道就绪），
**未引入 `BootReceiver`** —— 开机自启需要额外权限且车机自启策略各家不同，
先做「应用启动时播报」覆盖主要场景，`BootReceiver` 留待有明确需求再加。

⚠️ 车机上开机自动喊话**可能造成打扰**（早上启动车就对外喊"前方施工"），因此**默认关闭**并在界面文案里明确提示。

#### ⑧ `tts_volume`　`⚪ 主动不做`

Hey! 有 TTS 独立音量，Holler 只有全局 `volume_gain`（作用在 DSP 链上）。

**实际影响较小**——`volume_gain` 已能覆盖音量调节需求。**建议不做**，避免出现两个音量滑块让用户困惑。

---

### 2.3 ⚪ 低优先级 / 主动不做

| 项 | 决策 | 理由 |
|---|---|---|
| `music_play_mode` / `music_volume` / `MusicPlaybackService` | 主动不做 | 用户明确排后；且音乐播放与「车外喊话」是两个场景，混在一起反而破坏信息架构 |
| 硬 DSP (`dsp_mode`) | 暂不做 | 无 JNI 实现，开关无意义 |
| AEC 回声消除 | 暂不做 | 先录后放模式天然无回声 |

---

## 3. 界面差异

### 3.1 页面对照

| | Hey! | Holler |
|---|---|---|
| Activity 数 | **2**（MainActivity + DebugActivity） | **1**（MainActivity 内三页状态切换） |
| 主界面结构 | 单页滚动，所有配置堆一起 | 三页分层（喊话 / 语音 / 设置） |
| 调试面板 | 独立 Activity | 并入设置页的「音频路由」「usage 遍历」卡片 |
| 切换方式 | Activity 跳转 | `var page by remember` + `when`（零依赖） |

**Holler 的优势**：按**使用场景**分层——行车中（喊话页，注意力稀缺）/ 停车配置（语音页）/ 极低频（设置页）。Hey! 不分场景，用户要在十几组配置里滚动找。

### 3.2 浮层与对话框

| 浮层 | Hey! | Holler |
|---|---|---|
| 悬浮喊话按钮 | ✅ `FloatingWindowService` | ✅ `FloatingTalkService` |
| 悬浮键手势 | 单击/长按/拖动/双击 | ✅ **全部实现**（`FloatingTalkView.onTouchEvent`） |
| 声波可视化 | ✅ `WaveformView` | ✅ 已实现 |
| 光晕动效 | ✅ `AudioGlowView` | 🟡 有（简化为卡片描边变色） |
| **倒计时环** | ✅ `CountdownRingView` | ✅ **v0.3.1 补**（`ui/CountdownRingView.kt`，153 行） |
| 增益滑块自绘 | ✅ `GainSliderView` | ✅ 用 `HollerSlider` 替代 |
| 文本输入 | 系统 Dialog | ✅ 系统 Dialog（车机输入法兼容） |

**倒计时环已在 v0.3.1 补齐**（见 §6.2）—— 这是 v0.3.0 唯一缺的界面元素。

### 3.3 Hey! 有、Holler 没有的界面元素

| 元素 | 价值 | 状态 |
|---|---|---|
| 倒计时环 | 中 | ✅ **v0.3.1 已补** |
| 音乐曲目选择列表 | 低 | ⚪ 排后 |
| 启动语音选择对话框 | 低 | 🟡 已有 ★ 按钮替代 |
| 独立调试 Activity | 低 | ⚪ 设置页已覆盖 |

---

## 4. 建议的补齐顺序

按「投入产出比」排序（✅ = v0.3.1 已完成）：

| 顺序 | 项 | 工作量 | 状态 |
|---|---|---|---|
| **1** | 手动指定输出设备（`output_device_id`） | 中 | ✅ **已完成** |
| **2** | 倒计时环（`CountdownRingView`） | 小 | ✅ **已完成** |
| **3** | 浮窗透明度 / 尺寸滑块 | 小 | ✅ **已完成** |
| **4** | 日志关键词自由输入 | 小 | ✅ **已完成** |
| **5** | 启动自动播报总开关 | 小 | ✅ **已完成** |
| **6** | app_process 提权 `DeviceProbe` | **大** | 🔴 待车机实测后决定 |
| **7** | AEC / NS / 唤醒优化 | 小 | ⚪ 按需 |

**核心判断**：第 1 项和第 6 项是绑在一起的——它们共同决定「车外喇叭找不找得到」。这是 Holler 目前**唯一的功能性风险点**，其余都是体验打磨。

---

## 5. 不改的部分（明确记录）

- **不做音乐播放** —— 用户主动排后，且与喊话场景正交
- **不做独立调试 Activity** —— 设置页已覆盖同等能力，多一个 Activity 多一份维护成本
- **不引 Navigation Compose** —— 三页复杂度不值得多一个依赖
- **不用 Room 存配置** —— SharedPreferences 够用，控制包体（参考 Hey! 仅 2.87 MB）
- **不引 XML 解析库** —— `car_audio_configuration.xml` 结构简单，正则够用
- **不加 `BootReceiver`** —— 开机自启各家车机策略不同且需额外权限；先用「应用启动时播报」覆盖主场景

---

## 6. v0.3.1 补齐记录

本次共补 4 项缺口 + 1 个界面元素，改动 8 个文件（1 新建）。

### 6.1 手动指定音频设备（缺口 ① / 配置键 23·24）

| 文件 | 改动 |
|---|---|
| `audio/PhraseStore.kt` | 新增 `outputDeviceId` / `inputDeviceId` / `usageOverride` 三键（-1 = 自动） |
| `audio/HollerEngine.kt` | 新增 `findOutputDevice(id)` / `findInputDevice(id)`；录音时优先用 `inputDevice` |
| `audio/AudioRouteResolver.kt` | `RouteInfo` 新增 `outputDevices` / `inputDevices`（真实对象，供 UI 选用）；`resolve()` 新增**路径 0**：手动指定设备优先级最高 |
| `ui/MainActivity.kt` | 设备清单卡改为可选列表，每行「选用」按钮；`refreshRouting()` 统一入口 |

**设计要点**：
- 手动指定走 `resolve()` 的**最高优先级路径**，但设备 id 不存在时**自动回退**到设备名匹配，不会把路由打死
- `refreshRouting()` 是**唯一的**路由刷新入口：既解析（优先手动指定）又写 UI 状态 `route` 并同步引擎，
  设置页任何改设备 / 改 usage 的动作都调它（经 `onReresolve` 回调）
- 设备清单为空时明确提示「需要提权（普通 App 枚举不到）」—— 这正是缺口 ② 的由来

### 6.2 倒计时环（界面缺口）

新建 `ui/CountdownRingView.kt`，含两个组件：

| 组件 | 用途 |
|---|---|
| `CountdownRing` | 完整环 + 圆心剩余秒数，用于 `TalkCard`（212dp，套在声波外圈） |
| `MiniRing` | 纯进度弧无文字，留给紧凑场景（DOCK / 悬浮窗） |

**视觉纪律**：
- 底环用 `Stroke` 细线（弱），进度弧用状态色（强）—— 强调色只做「点」
- 最后 3 秒进度弧转 `danger` 红，给出收尾提醒
- `HollerEngine` 新增 `recordStartedAt` / `elapsedSeconds`，UI 以 60ms 轮询驱动
- `MAX_SECONDS` 从 `private` 改为 `const val`（UI 需要读）

### 6.3 浮窗透明度 / 尺寸（缺口 ⑤）

- `ui/MainActivity.kt`：悬浮窗卡片内加两个 `HollerSlider`（透明度 0.3–1.0、尺寸 96–220dp）
- `audio/FloatingTalkService.kt`：新增 `companion.refreshAppearance(context)` 静态入口 + `applyAppearance()` 私有方法 + `ACTION_REFRESH` 动作

**关键细节**：尺寸变化必须重建 `LayoutParams.width/height`（只刷 alpha 不够），
且要尊重「收起态」（`collapsed` 时取半尺寸），否则改尺寸会把收起的按钮弹开。

### 6.4 日志关键词自由输入（缺口 ⑥）

- `ui/MainActivity.kt`：新增 `private enum class DialogKind { NONE, KEYWORD }`，
  关键词 chip 行尾加「自定义…」，复用已有的 `TextInputDialog`（系统 `AlertDialog` + `EditText`）
- 顺带把冷却时间做成可调滑块（3–60s，默认 8s 对齐 Hey!）→ `logcatCooldownSec`

### 6.5 启动自动播报（缺口 ⑦）

- `audio/PhraseStore.kt`：新增 `launchAutoPlay`（**默认 false**）
- `ui/MainActivity.kt`：设置页新增「启动行为」卡片；`HollerApp` 启动 `LaunchedEffect` 内延迟 1.2s 播报

**为什么延迟 1.2s**：应用冷启动时音频通道尚未就绪，立即播放会被系统吞掉。
**为什么默认关闭**：车机上开机对外喊话容易造成打扰，必须由用户明确开启。

### 6.6 本次全量核对

| 校验项 | 结果 |
|---|---|
| 文件数 / 总行数 | 18 个 `.kt` / **4985 行** |
| 括号配平（剥离字符串与注释后） | ✅ 全部通过 |
| 新增 API 定义（15 项关键） | ✅ 全部存在 |
| 跨文件调用点 | ✅ 全部对上 |
| 未使用导入 | ✅ 已清理 |
| 未使用参数 / 死代码 | ✅ 已清理（见下表） |
| 版本号 | `versionCode 3→4` / `versionName 0.3.0→0.3.1` |
| **真编译** | ❌ **仍未做**（本机无 JDK / SDK） |

**本轮清理的死代码**（静态脚本查不出，靠人工逻辑推演发现）：

| 项 | 原因 |
|---|---|
| `FloatingTalkService` 的实例方法 `refreshAppearance()` | 与新的 `applyAppearance()` 语义重复；后者还会重建 `LayoutParams` 宽高（前者只刷 alpha），保留两套会导致尺寸不生效 |
| `RouteInfo.allOutputs: List<String>` | 被 `outputDevices: List<AudioDeviceInfo>` 取代，已无读取方；连带 4 处构造点同步去掉位置参数 |
| `HollerEngine.applyRouting(context, store)` | 职责被 `MainActivity.refreshRouting()` 完全接管 —— 后者走的是 `resolve(preferredDeviceId = ...)` 的**路径 0**，比它调无参 `resolve()` 更正确 |
| `SettingsPage` 的 `onRoute` 参数 | `refreshRouting()` 内部已写 `route` 状态 + 同步引擎，回调变成冗余 |
| `DspChain.kt` 的 `import kotlin.math.cos` | 全文件零使用 |

**修复的功能瑕疵**：设备清单点「选用」后，原先只调 `engine.applyRouting()`，
**UI 上的「识别来源 / 当前 usage」停在旧值**；改为走 `onReresolve()` 后状态同步刷新。

> 与 v0.3.0 一样，本轮只做了静态校验。**4985 行代码从未真编译过**，
> 装好 JDK 17 + Android SDK 后跑 `gradlew.bat :app:assembleDebug` 才能确认。

### 6.7 新增自检脚本

`tools/check.py`（无需 JDK / Android SDK）—— 剥离字符串字面量、行注释、块注释（含 Kotlin 嵌套注释）
后再做括号配平，避免「注释里的括号」「全角括号」造成的假阳性：

```
python tools/check.py
```

检查三项：括号配平 / 未使用导入（白名单放行 Compose `by` 委托的 `getValue`/`setValue`）/ 同名函数重复定义。
