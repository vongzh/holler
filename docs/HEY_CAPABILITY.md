# Hey! v1.2.5 能力清单（逆向实测）

> 数据来源：`D:/Code/hey/reference/Hey-v1.2.5-release.apk`（2.87 MB）
> 逆向方式：DEX 字符串池（8841 条）+ 二进制 AndroidManifest 字符串池（95 条）+ `libvoiceeffect.so` 导出符号
> 说明：本清单为**实测事实**，非猜测。所有条目均可回溯到 APK 中的具体符号。

---

## 0. 包体事实

| 项目 | 值 |
|---|---|
| 包名 | `com.kooo.hey` |
| 版本 | v1.2.5 release |
| APK 大小 | 2,936,720 B（2.87 MB） |
| classes.dex | 1,291,188 B |
| DEX 规模 | 8841 字符串 / 10928 方法 / 1304 类 / 2083 类型 |
| 原生库 | `lib/arm64-v8a/libvoiceeffect.so`（290,952 B）、`lib/x86_64/...`（292,312 B） |
| 资源 | resources.arsc（531,248 B）+ 大量 res/ 单字母混淆名 |
| 混淆 | R8 全量混淆，类名/资源名被压缩为单字母 |

**关键结论**：Hey! 只有 **1 个 dex、13 个自有类**。所谓「功能很多」不在代码量，而在**每个类里的深度系统集成**。1304 个类里绝大多数是 Android/Compose/AppCompat 框架引用，自有代码极少。

---

## 1. 自有类清单（13 个，全部反混淆还原）

| 类 | 职责 |
|---|---|
| `com.kooo.hey.MainActivity` | 主界面 |
| `com.kooo.hey.DebugActivity` | 调试面板（扬声器上电/断电、usage 遍历、音量组查询） |
| `com.kooo.hey.FloatingWindowService` | **悬浮喊话按钮**（悬浮窗服务） |
| `com.kooo.hey.LogcatTriggerService` | **日志关键词触发喊话**（监听系统日志） |
| `com.kooo.hey.MusicPlaybackService` | 音乐播放前台服务 |
| `com.kooo.hey.HeyCommandReceiver` | 外部命令接收器（`START_TALK` / `STOP_TALK` / `TOGGLE_TALK`） |
| `com.kooo.hey.audio.UsageSweepWorker` | **app_process 提权：usage 遍历 worker** |
| `com.kooo.hey.audio.VolumeShellMain` | **app_process 提权：音量控制入口** |
| `com.kooo.hey.audio.WaveformView` | 声波可视化 |
| `com.kooo.hey.audio.dsp.VoiceEffectBridge` | **原生变声桥（JNI）** |
| `com.kooo.hey.ui.AudioGlowView` | 主按钮光晕动效 |
| `com.kooo.hey.ui.CountdownRingView` | 倒计时环（录音剩余时长） |
| `com.kooo.hey.ui.GainSliderView` | 增益滑块 |

---

## 2. 能力矩阵（逐项证据）

### 2.1 核心喊话
- **Push-to-Talk 按住说话**：`AudioRecord` + 独立线程 + 缓冲复用（`复用 AudioRecord`、`取消延迟释放，复用音频资源`、`停止 drain，复用已有 AudioRecord`）
- **一键喊话**：`com.kooo.hey.action.START_TALK` / `STOP_TALK` / `TOGGLE_TALK` 三个外部 Intent action，可被方控/第三方 App/宏触发
- **悬浮按钮**：`FloatingWindowService`，Manifest 描述原文 `Floating overlay talk button for quick access`
- **录音参数**：`采样率`、`通道数`、`缓冲区: record=`、`首帧: read=`
- **误触保护**：录音过短直接丢弃（Holler 已实现 `<0.25s` 判定）

### 2.2 变声（原生 DSP）
- **JNI 类**：`VoiceEffectBridge`，5 个原生方法
  ```
  Java_com_kooo_hey_audio_dsp_VoiceEffectBridge_nativeCreate
  Java_com_kooo_hey_audio_dsp_VoiceEffectBridge_nativeDestroy
  Java_com_kooo_hey_audio_dsp_VoiceEffectBridge_nativeFlush
  Java_com_kooo_hey_audio_dsp_VoiceEffectBridge_nativeProcess
  Java_com_kooo_hey_audio_dsp_VoiceEffectBridge_nativeSetEffect
  ```
- **5 种效果**：`原声` / `大叔` / `机器人` / `电音` / `萝莉`
- **运行模式**：`dsp_mode`、`DSP 模式已设置为 `、`无法设置 workmode (需要系统签名或root)，将使用软件DSP处理`
  → 即：**能拿 root 就走厂商 HAL 硬 DSP，拿不到就走自研软件 DSP 兜底**
- **实时参数**（日志可见）：`变声切换: `、`变声效果: `、`频移=`、`混响=`、`带通=`、`噪声门=`

### 2.3 音频增强链（自研，非系统 API）
- **AGC 自动增益**：`AGC限幅: target=0.45, minAtten=0.05, attack=0.15, release=0.02`、`最终agcAtten=`、`enable_agc`
- **三件套开关**：`回声消除` / `噪音抑制` / `唤醒优化`
- **增益**：`volume_gain`、`getGain` / `setGain` / `GainSliderView`

### 2.4 音频路由（本类 App 的胜负手）
- **usage 遍历**：`UsageSweepWorker` + `── Usage 遍历测试 ──` + `范围: 0 ~ 100, 每个播放 800ms` → 逐个 usage 试播，人工听哪个从车外喇叭出来
- **设备级路由**：`setPreferredDevice(id=`、`指定输出设备 id=`、`指定输入设备 id=`、`input_device_id` / `output_device_id` / `output_device`
- **设备白名单**（已枚举到的物理设备名）：`内置扬声器` / `内置麦克风` / `功放` / `听筒` / `有线耳机` / `USB耳机` / `蓝牙A2DP` / `蓝牙SCO` / `AUX线路` / `FM调谐器` / `USB设备` / `IP网络` / `BUS总线` / `遥控混音`
- **音量组自动匹配**：`══ 自动匹配音量组 ══`、`getVolumeGroupIdForUsage`、`getVolumeGroupCount`、`getGroupVolume` / `setGroupVolume`、`volume_group_id`、`匹配成功: usage `
- **配置降级链**：`car_audio_config_not_found` → `failed_to_get_car_audio_manager` → `, 跳过 usage 路由, 使用 DEFAULT(0)` → `, 使用 USAGE_UNKNOWN(0) 跳过 usage 路由`

### 2.5 app_process 提权（为什么必须 ADB）
- `app_process` 子进程管道：`启动 app_process 管道 (root): source=`、`✓ app_process 管道已建立 (`
- `VolumeShellMain` → 控制系统音量组（普通 App 无权限）
- `UsageSweepWorker` → 枚举普通 App **看不到**的音频设备
- `HotwordMicWorker stream` → 常驻独占麦克风（`(麦克风保持占用)`）
- **进程隔离**：`══ Usage 遍历测试 (进程隔离) ══`、`⚠ 子进程超时 (卡在 usage `、`→ ✗ NATIVE CRASH (进程被杀)` → 遍历放在独立进程，崩了不拖垮主 App

### 2.6 ADB 自连接（用户无感）
- **内置 ADB 客户端**：`尝试连接 ADB (端口 5555)...`、`adb tcpip 5555`、`ADB 需要认证...`、`发送公钥，请在设备上确认 USB 调试授权...`、`✓ 认证成功（已知密钥）`、`✓ 认证成功（用户已授权）`、`✗ 无法连接到 ADB (所有地址均失败)`、`重置 ADB 连接...`
- **连接指引文案**（说明作者预期用户操作路径）：
  ```
  1. 开发者选项中已开启 USB 调试
  2. 通过 PC 执行: adb tcpip 5555
  3. 设备已连接 WiFi（部分设备需要）
  ```

### 2.7 热词唤醒
- `HotwordMicWorker` 子进程 + `adb root 失败，无法获取 HOTWORD 权限`
- `[hotword] 进程结束`、`: 手柄`（方控/手柄联动）

### 2.8 日志关键词触发
- 服务：`LogcatTriggerService`，Manifest 描述原文 `Monitors system logcat for keyword to trigger talk`
- 文案：`监听系统日志关键词以触发车外喊话`、`日志关键词触发喊话监听中`
- 配置：`enable_logcat_trigger`、`logcat_trigger_keyword`、`logcat_trigger`
- 权限：`android.permission.READ_LOGS`（需 `adb shell pm grant`）+ 指引文案 `请通过 ADB 授权日志读取权限：` / `adb shell pm grant `
- **用途推测**：车机系统在倒车/碰撞预警时打日志 → 抓关键词 → 自动喊话（如"车辆正在倒车，请保持安全距离"）

### 2.9 预设语音条目（TTS 化）
已提取的**全部中文喊话语料**（这就是它的「语音包」）：
```
前方施工，请减速慢行
文明行车，跟车行驶，请关闭远光灯，谢谢！
时速低于100请勿占用最左侧车道，谢谢！
窄路会车，请减速慢行，保持安全距离
路口礼让行人，请耐心等待
车辆临时停靠，请小心避让
车辆正在倒车，请保持安全距离
```
- **TTS 可编辑**：`tts_items`、`请输入文本`、`点击选择语音条目…`、`还没有语音条目，请先在主界面添加`、`已添加` / `已重命名` / `重命名失败`
- **TTS 参数**：`tts_pitch`（音调）、`tts_speech_rate`（语速）、`tts_volume`
- **开机自动播报**：`launch_auto_play`、`launch_tts_index`、`选择启动时播放的语音`

### 2.10 音乐/音频文件播放
- 服务：`MusicPlaybackService`（前台服务，`FOREGROUND_SERVICE_MEDIA_PLAYBACK`）
- **目录扫描**：`音乐文件夹为空: `、`文件夹, `、`文件夹`、`内部存储`、`读取媒体音频`（`READ_MEDIA_AUDIO`）
- 文案：`点击选择音乐曲目…`、`选择启动时播放的音乐`、`音乐: `、`当前模式: `、`music_play_mode`、`music_volume`
- 格式：dex 中可见 `audio/mpeg`

### 2.11 悬浮窗
- `SYSTEM_ALERT_WINDOW` + `INTERNAL_SYSTEM_WINDOW`（后者是系统级权限）
- 配置：`enable_floating`、`floating_x` / `floating_y` / `floating_alpha` / `floating_size`
- 文案：`⚠ 需要悬浮窗权限，正在打开设置…`、`请授予悬浮窗权限`、`✓ 悬浮窗已启动` / `已更新` / `已关闭`、`悬浮喊话按钮运行中`、`dp, 透明度`

### 2.12 保活与权限（Manifest 实测）
```
android.car.permission.CAR_CONTROL_AUDIO_SETTINGS
android.car.permission.CAR_CONTROL_AUDIO_VOLUME
android.permission.MODIFY_AUDIO_ROUTING
android.permission.MODIFY_AUDIO_SETTINGS
android.permission.RECORD_AUDIO
android.permission.READ_LOGS
android.permission.DUMP
android.permission.SYSTEM_ALERT_WINDOW
android.permission.INTERNAL_SYSTEM_WINDOW
android.permission.WAKE_LOCK
android.permission.READ_MEDIA_AUDIO
android.permission.READ_EXTERNAL_STORAGE
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_MICROPHONE
android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
android.permission.FOREGROUND_SERVICE_SPECIAL_USE
```
> **注意**：`CAR_CONTROL_AUDIO_SETTINGS` / `CAR_CONTROL_AUDIO_VOLUME` / `INTERNAL_SYSTEM_WINDOW` / `MODIFY_AUDIO_ROUTING` 是**系统签名级权限**，普通签名 App 申请了也不生效——这正是 Hey! 要靠 `app_process` 绕过的原因。

---

## 3. 能力全景图（按「用户可感知功能」归类）

| # | 功能 | 实现 | Holler 现状 |
|---|---|---|---|
| 1 | 按住说话 · 松手车外喊 | AudioRecord + AudioTrack + setPreferredDevice | ✅ v0.2 已实现 |
| 2 | 一键喊话（外部触发） | `START_TALK`/`STOP_TALK`/`TOGGLE_TALK` | ⬜ 待做 |
| 3 | 悬浮喊话按钮 | FloatingWindowService | ⬜ 待做 |
| 4 | 变声（5 种） | VoiceEffectBridge + libvoiceeffect.so | ⬜ 待做（用户已排后） |
| 5 | AGC / 回声消除 / 降噪 | 自研 DSP 链 | ⬜ 待做 |
| 6 | 音频路由探测 | UsageSweepWorker + app_process | 🟡 已有 AudioRouteResolver（待车机校准） |
| 7 | 系统音量组控制 | VolumeShellMain + app_process | ⬜ 待做 |
| 8 | ADB 自连接 | 内置 ADB 客户端 | ⬜ 待做 |
| 9 | 热词唤醒 | HotwordMicWorker | ⬜ 待做 |
| 10 | 日志关键词触发 | LogcatTriggerService | ⬜ 待做 |
| 11 | TTS 语音条目 | 系统 TTS + 可编辑列表 | ⬜ 待做（用户已排后） |
| 12 | 音乐播放 | MusicPlaybackService | ⬜ 待做（用户已排后） |
| 13 | 开机自动播报 | launch_auto_play | ⬜ 待做 |
| 14 | 声波 / 光晕 / 倒计时动效 | WaveformView / AudioGlowView / CountdownRingView | 🟡 已有 WaveformView |
| 15 | 调试面板 | DebugActivity | 🟡 Holler 已有 DiagnosticsPanel |

**统计**：Hey! 15 项能力，Holler 已覆盖 1 项完整 + 3 项部分，待做 11 项。

---

## 4. 对 Holler 的三条硬结论

1. **Hey! 的技术护城河只有两道**：① 车外喇叭 usage 的正确取值（靠车机实测，不是靠猜）；② `app_process` 提权 + ADB 自连接。其余都是常规 Android 开发，工作量可控。
2. **变声不必自研 DSP**（除非要做「大叔/机器人」这种特色音效）。用户已明确排在后面，正确。
3. **优先级建议**：先把 ②「一键喊话 + 悬浮按钮 + 外部 Intent 触发」打通并跑在真车上验证喇叭，再考虑 ①之后的一切。**喇叭没响，其他都是空谈。**

---

## 5. 尚未提取到的信息（诚实标注）

| 项 | 状态 | 原因 |
|---|---|---|
| `car_audio_configuration.xml` 实际 usage 值 | ❌ 未获取 | 车规配置文件在**车机**上，不在 APK 内 |
| 每个 usage 对应的音量组 ID | ❌ 未获取 | 同上，需车机 `dumpsys` |
| 变声算法的具体实现 | ❌ 未获取 | 原生 .so 为编译后机器码，未反汇编 |
| 日志触发关键词的完整列表 | ❌ 未获取 | 关键词为用户自定义（`logcat_trigger_keyword`），非硬编码 |
| Compose UI 的具体布局 | ❌ 未获取 | Compose 布局编译为字节码，反编译成本高 |

**下一步依赖**：在车机上执行 `docs/ADB_PROBE.md` 里的 5 组只读命令，把输出回传。
