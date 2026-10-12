# 环境准备

## 当前状态（2026-10-08 首次编译通过 ✅）

| 项目 | 状态 |
|---|---|
| JDK | ✅ `C:\Users\vongz\.jdks\jbr-21.0.11`（Studio 自动下载的 JDK 21） |
| Android Studio | ✅ 2026.2.1（`AI-262.*`），装在 `C:\Program Files\Android\Android Studio` |
| Android SDK | ✅ `C:\Users\vongz\AppData\Local\Android\Sdk`（Studio 默认路径） |
| Gradle | ✅ **9.8.1**（wrapper 自动下载到 `~/.gradle`） |
| adb | ✅ `%SDK%\platform-tools\adb.exe` |
| CPU | ✅ Intel Core Ultra 7 270K Plus，24 核 / 24 线程 |
| 虚拟化 | ✅ `HypervisorPresent = True`（模拟器可硬件加速） |
| C 盘 | ✅ 可用 **203 GB** —— SDK（4.26 GB）完全放得下 |
| D 盘 | ✅ 可用 381.8 GB |

> **首次编译已通过**：`BUILD SUCCESSFUL`，产物
> `app/build/outputs/apk/debug/app-debug.apk`（30.6 MB）。
>
> 早期记录的「C 盘仅剩 20.6 GB」**已失效**——现在有 203 GB，
> 所以 SDK 留在 Studio 默认的 C 盘路径不再有任何问题，不需要迁到 D 盘。

---

## 工具链版本矩阵（2026-10-08 升级至最新，AGP 9 路线）

| 组件 | 版本 | 约束来源 |
|---|---|---|
| **AGP** | **9.4.1** | 支持 API 37.0 及以下（上限未变） |
| **Gradle** | **9.8.1** | AGP 9.4 要求 ≥ 9.6.0；9.8.1 为安全硬化版 |
| **Kotlin** | **2.4.20** | 见下方 ⚠ 第 2 条 |
| **JDK** | **21**（jbr-21.0.11） | AGP 9.4 最低要求 17 |
| **compileSdk / targetSdk** | **37 / 37** | Compose 1.12 起强制 compileSdk 37 |
| **minSdk** | **30** | ⚠ **待车机实测确认**，见下 |
| **Compose BOM** | **2026.09.00** | → Compose 1.12.1（**已是最新**） |
| **Build Tools** | 36.0.0 | AGP 9.4 要求的最低版本（**已是最新**） |

> **升级时间线与依据（2026-10-08）**：原为 Gradle 9.3.1 / AGP 9.1.1 / Kotlin 2.4.10。
> - **Gradle 9.3.1 → 9.8.1**：9.8.1 于 2026-10-07 发布，修复 3 个高危漏洞（client↔daemon
>   未认证反序列化 ×2、SSL 失败时未禁用仓库），官方定义为安全硬化版，建议视为强制升级。
> - **AGP 9.1.1 → 9.4.1**：9.4.1 为当前稳定线；要求 Gradle ≥ 9.6.0，故必须与 Gradle 同升。
>   上限 API 仍为 37，**未影响 compileSdk**。
> - **Kotlin 2.4.10 → 2.4.20**：tooling release（2026-09-07），另修 security 相关 bug。
> - 升级后 `BUILD SUCCESSFUL`，APK 29 MB，`compileSdkVersion=37` / `targetSdkVersion=37` 不变。
> - 唯一残余告警：`Configuration.setVisible(boolean)` 弃用（**来自 AGP 内部，非项目代码**），
>   排期至 Gradle 11 才移除，当前无影响。

### ⚠ AGP 9 的三个坑（本项目全部踩过并已解决）

**1. 必须移除 `org.jetbrains.kotlin.android` 插件**

AGP 9 内置 Kotlin 支持，再应用该插件会直接报错：

```
Failed to apply plugin 'org.jetbrains.kotlin.android'.
Cannot add extension with name 'kotlin', as there is an extension already registered
```

**2. 改 Kotlin 版本必须在 `buildscript` classpath 里声明**

AGP 9 内置 KGP 2.2.10。只改 `plugins {}` 里的版本号**只影响 Compose 编译器插件**，
真正的 Kotlin 编译器纹丝不动 —— 你会得到一个「编译通过、但 2.4 语法报错」的假象，
而报错会指向你的源码，极具误导性。正确做法见根 `build.gradle.kts` 的 `buildscript` 块。

**验证方法**（`./gradlew --version` 显示的是 Gradle 内嵌 Kotlin DSL 版本，**不是项目编译器版本**，勿误判）：

```bash
./gradlew buildEnvironment | grep kotlin-gradle-plugin
# 期望输出：org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20
```

**3. `android.kotlinOptions {}` 已移除**

改用顶层 `kotlin { compilerOptions { jvmTarget = ... } }`。

### ⚠ minSdk = 30 尚未验证（装车前必做）

`minSdk 30` 是**根据资料推断**设定的（领克 08 跑 Flyme Auto = 安卓 11 = API 30），
**从未在车机上实测**。装车前先跑：

```bash
adb shell getprop ro.build.version.sdk      # 期望 30
adb shell getprop ro.build.version.release  # 期望 11
```

若不是 30，把 `app/build.gradle.kts` 的 `minSdk` 改成实际值 ——
**否则 APK 会直接装不上**（`INSTALL_FAILED_OLDER_SDK`）。一行即可改回。

---

## Android Studio 是必需的吗？

**不是。** Studio 只是 IDE 外壳，真正必需的是壳里那几个组件。
你可以完全不装 Studio，用纯命令行工具链编译 Holler。

### 必需 vs 可省略

| 组件 | 必需性 | 说明 |
|---|---|---|
| **JDK 17** | 🔴 必需 | 编译 Kotlin、运行 Gradle。**装 Studio 也需要它**（Studio 自带 JBR，命令行则要单独装） |
| **Android SDK cmdline-tools** | 🔴 必需 | 含 `sdkmanager`，用来下载 platform / build-tools |
| **SDK Platform 37** | 🔴 必需 | Holler `compileSdk = 37`（本机实测目录名为 `android-37.0`，对应 Android 17） |
| **SDK Build-Tools 36.0.0** | 🔴 必需 | 编译打包（AGP 9.4 要求的最低版本） |
| **platform-tools** | 🔴 必需 | 提供 `adb`，装 APK、连车机、看日志 |
| **Gradle** | 🔴 必需 | 用项目自带的 wrapper 即可（三件套已补齐，会自动下载） |
| Android Studio | ⚪ 可省 | 约 3.5 GB。可省的部分只有：图形编辑器、Logcat 面板、AVD 图形管理器 |
| 模拟器 + 系统镜像 | ⚪ 可选 | 3–6 GB。**若只装真机验证，完全可以不要** |

> **注意**：装了 Studio **仍然**要单独下载 SDK。所以「不装 Studio」省下的只是那 3.5 GB 外壳，
> SDK 该下还得下。

### 纯命令行路线的唯一门槛：模拟器

Android 模拟器在 Windows 上能从命令行启动，但「创建 AVD」这一步在无 Studio 时较绕：

- 要用 `avdmanager create avd` 命令，或手写 `~/.android/avd/*.ini` 配置
- 需要额外下载 `emulator` 包和对应的 system-image

功能上完全可行，只是配置成本高于 Studio 的图形界面。

> **决策点**：
> - **要跑模拟器** → 装 Studio 更省事
> - **只做命令行编译 + 装真机（车机）** → 可完全不装 Studio

---

## 路线选择（本机已由路线 A 完成）

> **实际结果**：本机装的是 **Android Studio 2026.2.1**（即路线 A），
> SDK 落在 Studio 默认的 C 盘路径，**首次编译已通过**。
> 下面三条路线保留作参考 —— 若换一台机器复现，可自行取舍。

| 路线 | 装什么 | 体积 | 适用场景 |
|---|---|---|---|
| **A. 全装 Studio** | JDK + Studio + SDK + 模拟器 | 20–27 GB | 要跑模拟器、想图形化调试、不熟悉 Android |
| **B. 纯命令行** | JDK + cmdline-tools + SDK + platform-tools | 8–13 GB | 熟悉命令行、只装真机验证、VS Code 写代码 |
| **C. 折中** | 先 B，跑通后再决定是否补 A | 起步 8–13 GB | 先用最小成本确认 APK 能编出来、能装上、喇叭能响 |

**为什么当初推荐 C**：Holler 的核心风险在**车机音频路由**，不在 IDE 好不好用。
先用命令行把 APK 编出来装上车，确认车外喇叭能响——这一步跑通后，
再决定要不要为长期开发补一个 IDE。万一某个技术路线走不通，也没白装 20 GB。

---

## 路线 A：装 Android Studio（推荐给要跑模拟器的人）

### 第 1 步：JDK 17

Android Studio 自带 JetBrains Runtime（JBR 21），若只用 Studio 的 ▶ 按钮构建，
**可以不单独装 JDK**（Studio 内部会用 JBR）。
但只要你想在终端跑 `./gradlew`，就**必须**单独装一个：

- 下载：https://adoptium.net/temurin/releases/?version=17 （选 Windows x64 .msi）
- **安装路径改到** `D:\Dev\jdk-17`
- 设置环境变量：

```powershell
# 永久生效（用户级）
[Environment]::SetEnvironmentVariable("JAVA_HOME", "D:\Dev\jdk-17", "User")
[Environment]::SetEnvironmentVariable(
  "PATH",
  "$([Environment]::GetEnvironmentVariable('PATH','User'));D:\Dev\jdk-17\bin",
  "User"
)
```

验证：新开一个终端，`java -version` 应输出 `17.x`。

> ⚠️ **避坑**：AGP 8.7.3 支持 JDK 17–21。装 JDK 25 会报
> `Unsupported class file major version`。**老老实实装 17。**

---

### 第 2 步：Android Studio

- 下载：https://developer.android.com/studio （Windows 64-bit .exe）
- 安装时**点「Custom」自定义路径**，改为 `D:\Android\Android Studio`
  （默认会装到 `C:\Program Files\Android\Android Studio`，C 盘吃不消）
- 它自带：Gradle、adb、模拟器、SDK 管理器 —— **不用另外装这些**

**装完第一件事：改 SDK 路径**

`File → Settings → Languages & Frameworks → Android SDK → SDK Location`
把 `Android SDK Location` 改成 `D:\Android\Sdk`。

> 这一步必须在下载 SDK **之前**做，否则 SDK 会落在 C 盘（8–12 GB）。

---

### 第 3 步：Android SDK 组件

在 `Settings → Android SDK` 里勾选：

| 标签页 | 必装项 | 说明 |
|---|---|---|
| SDK Platforms | **Android 17 (API 37)** | Holler 的 `compileSdk = 37` |
| SDK Tools | **Android SDK Build-Tools 36.0.0** | 编译所需 |
| SDK Tools | **Android SDK Platform-Tools** | 提供 `adb`，连车机用 |
| SDK Tools | **Android SDK Command-line Tools** | 命令行构建（可选但推荐） |

> Holler 的 `minSdk = 30`（Android 11，对应 Flyme Auto 车机基线）；`targetSdk = 37`。

**设置环境变量**（`adb` 加入 PATH）：

```powershell
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "D:\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", "D:\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable(
  "PATH",
  "$([Environment]::GetEnvironmentVariable('PATH','User'));D:\Android\Sdk\platform-tools",
  "User"
)
```

验证：`adb version` 能输出版本号。

---

### 第 4 步：模拟器镜像（可选，但强烈建议）

`Device Manager → Create Device`：

| 项 | 推荐值 | 理由 |
|---|---|---|
| 设备 | **Pixel Tablet** 或 Pixel 7a | 车机是横屏，Holler 锁 `landscape`，平板更贴近 |
| 系统 | **API 34**（Android 14） | API 36 镜像较重；34 足够跑通 UI 与音频链路 |
| ABI | **x86_64** | 你的 CPU 支持硬件加速，跑得快 |

再改一下模拟器存储位置（默认在 C 盘）：

```powershell
[Environment]::SetEnvironmentVariable("ANDROID_AVD_HOME", "D:\Android\avd", "User")
[Environment]::SetEnvironmentVariable("ANDROID_EMULATOR_HOME", "D:\Android\emulator", "User")
```

---

### 第 5 步：Gradle 缓存改到 D 盘（隐形杀手）

Gradle 默认把依赖缓存放 `C:\Users\<你>\.gradle`，**首次构建就会吃掉 3–5 GB**。

```powershell
[Environment]::SetEnvironmentVariable("GRADLE_USER_HOME", "D:\Android\gradle-home", "User")
```

> 这是最容易忽略、也最容易把 C 盘撑爆的一项。**务必设置。**

---

## ⚠️ 模拟器能验证什么，不能验证什么

**这是关键认知：模拟器是「手机」，不是「车机」。**

### ✅ 能在模拟器验证（约占 60%）

- App 能否**编译通过**、正常启动、不崩溃
- 三页导航（喊话 / 语音 / 设置）布局与交互是否正确
- 麦克风录音 → DSP 变声 → 回放链路是否跑通（能听到变声效果）
- TTS 合成、快捷语增删改、悬浮窗权限流程
- `UsageSweeper` 不崩溃、能走完 21 个 usage
- 日志触发服务的权限降级逻辑

### ❌ 无法在模拟器验证（核心 40%）

| 项 | 为什么不行 |
|---|---|
| 车外喇叭是否存在、是否真响 | 模拟器只有一个内置扬声器 |
| usage 遍历的真实结果 | 所有 usage 都路由到同一个内置扬声器 |
| `car_audio_configuration.xml` 解析 | 模拟器没有这个文件 |
| `CarAudioManager` 相关调用 | 模拟器没有车机音频服务 |
| `UsageSweeper` 是否触发 native crash | 这是车机专属风险 |
| 输入/输出设备定向 | 模拟器设备列表与你车机完全不同 |

> **结论**：音频路由这条胜负手，模拟器给不出任何有效信息。
> 但它能帮你**先排除低级错误** —— Holler 有 17 个 .kt、4440 行代码，
> 从未真编译过。先用模拟器把编译错误、崩溃、UI 问题清干净，
> 再上车测音频。否则上车后分不清「是代码 bug 还是车机不兼容」，排查成本翻倍。

---

## 构建与运行

### 已预置好的配置（实测可用，无需再动）

| 文件 | 内容 | 原因 |
|---|---|---|
| `gradlew` / `gradlew.bat` / `gradle-wrapper.jar` | **三件套齐全** | 缺任一个都无法命令行构建 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle **9.8.1** + 腾讯云镜像 | 官方源下载 140 MB 在国内易超时 |
| `settings.gradle.kts` | 阿里云 Maven 镜像 + 官方源兜底 | `google()` / `mavenCentral()` 国内常连不上 |
| `gradle.properties` | JVM 堆 4G，开 daemon / 并行 / 缓存 | 24 核机器，编译更快 |
| `local.properties` | `sdk.dir=C:\Users\vongz\AppData\Local\Android\Sdk` | 缺它会报 `SDK location not found` |

### 命令行构建（实测通过 ✅）

```bash
cd D:/Code/Holler

# 必须显式指定 JDK —— 系统 PATH 里没有 java。
# 用 Studio 自带的那份：AGP 9.4.1 要求 JDK 17+，21 完全兼容。
export JAVA_HOME="C:/Users/vongz/.jdks/jbr-21.0.11"

./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk（约 30 MB）

# 只看警告与错误（完整输出很长）
./gradlew :app:assembleDebug --console=plain 2>&1 | grep -E "^w:|^e:|BUILD"
```

首次构建要下载 Gradle 9.8.1（约 140 MB）+ 全套依赖，约 1–2 分钟；之后增量构建约 30 秒。

### 校验 APK 的 SDK 声明

```bash
AAPT="C:/Users/vongz/AppData/Local/Android/Sdk/build-tools/36.0.0/aapt2.exe"
"$AAPT" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -i sdkversion
# 期望输出：minSdkVersion:'30'   targetSdkVersion:'37'
```

### 在 Android Studio 里运行

1. `File → Open` → 选 `D:\Code\Holler`（**打开项目根目录，不是 app 目录**）
2. 等 Gradle Sync 完成（首次较慢，会下载依赖）
3. 顶部设备栏选一次 `Create Device` 建好的模拟器
4. 点绿色 ▶ Run

---

## 路线 B：纯命令行（不装 Android Studio）

### 完整步骤

#### 1) 装 JDK 17

同「第 1 步」，装到 `D:\Dev\jdk-17`，设好 `JAVA_HOME`。
**这一步无法省略** —— 没有 JDK，Gradle 跑不起来。

验证：

```bash
java -version   # 应输出 17.x
```

#### 2) 下载 Android SDK 命令行工具

- 下载页：https://developer.android.com/studio#command-line-tools-only
  （页面上找 **"Command line tools only"** 一节，下 Windows 版 zip）
- 解压后目录结构应是 `cmdline-tools\latest\bin\sdkmanager.bat`

```powershell
# 解压到 D:\Android\Sdk\cmdline-tools\latest
Expand-Archive -Path "$env:USERPROFILE\Downloads\commandlinetools-win-*.zip" `
               -DestinationPath "D:\Android\Sdk\cmdline-tools\tmp"
Move-Item "D:\Android\Sdk\cmdline-tools\tmp\cmdline-tools" "D:\Android\Sdk\cmdline-tools\latest"
Remove-Item "D:\Android\Sdk\cmdline-tools\tmp" -Recurse
```

> ⚠️ **目录名必须是 `latest`**。`sdkmanager` 通过这个固定名字定位 SDK 根目录，
> 解压出来的目录名（如 `cmdline-tools`）不改会报找不到 SDK。

#### 3) 设环境变量

```powershell
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "D:\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", "D:\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable(
  "PATH",
  "$([Environment]::GetEnvironmentVariable('PATH','User'));D:\Android\Sdk\cmdline-tools\latest\bin;D:\Android\Sdk\platform-tools",
  "User"
)
[Environment]::SetEnvironmentVariable("GRADLE_USER_HOME", "D:\Android\gradle-home", "User")
```

**改完必须新开终端**（旧终端的环境变量不会刷新）。

#### 4) 用 sdkmanager 装组件

```bash
sdkmanager --version            # 先确认能用

# 接受许可（会连续问多个，全部输入 y）
sdkmanager --licenses

# 装必需组件。
# ⚠ platform 刻意不写死版本：本机实测 compileSdk 37 对应目录名是 `android-37.0`
#   （不是 `android-37`），写死 `platforms;android-37` 可能报 package not found。
#   AGP 9.4 自带 SDK 下载器，联网构建时会按 compileSdk 自动补齐，因此这里只装
#   platform-tools 与 build-tools 即可；若想预先装 platform，先 `sdkmanager --list`
#   确认实际包名再装。
sdkmanager "platform-tools" "build-tools;36.0.0"
```

> 国内网络慢，可加代理环境变量：
> `set HTTP_PROXY=http://127.0.0.1:7890` 与 `set HTTPS_PROXY=http://127.0.0.1:7890`

列一下已装/可装：

```bash
sdkmanager --list_installed
sdkmanager --list | grep "android-36"
```

#### 5) 构建 APK

```bash
cd /d/Code/Holler
gradlew.bat :app:assembleDebug        # CMD
./gradlew :app:assembleDebug          # Git Bash
```

产物：`app\build\outputs\apk\debug\app-debug.apk`

#### 6) 装到车机

```bash
adb connect <车机IP>:5555
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

#### 7) 看日志（代替 Studio 的 Logcat 面板）

```bash
adb logcat -s Holler:*         # 只看 Holler 自己的日志
adb logcat | findstr Holler    # CMD 里过滤
```

> Holler 的日志 TAG 前缀统一是 `Holler/`，例如 `Holler/Engine`、`Holler/DSP`、
> `Holler/Route`、`Holler/Sweep`。用 `adb logcat -s Holler/Engine:*` 可精确过滤。

### 纯命令行路线放弃的东西

| 放弃 | 替代方案 | 影响 |
|---|---|---|
| 图形编辑器 / 补全 | VS Code + Kotlin 插件 | 体验略差，功能不缺 |
| Logcat 面板 | `adb logcat -s Holler:*` | 够用 |
| AVD 图形管理器 | `avdmanager create avd` + `emulator -avd` | 较绕，见下 |
| 布局预览 | 无（装真机看） | 车机项目主要看真机 |

### 若在纯命令行下仍要跑模拟器

```bash
# 1) 装模拟器与系统镜像（约 3–6 GB）
sdkmanager "emulator" "system-images;android-34;google_apis;x86_64"

# 2) 创建 AVD
avdmanager create avd -n holler_test -k "system-images;android-34;google_apis;x86_64" -d "pixel_tablet"

# 3) 启动
emulator -avd holler_test

# 4) 另开终端装 APK
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

> 这是纯命令行路线里最绕的一步。如果确定要频繁跑模拟器，**建议直接装 Studio**。

---

## 常见问题

| 问题 | 处理 |
|---|---|
| `Unsupported class file major version` | JDK 版本不对，换 17 |
| `SDK location not found` | 改 `local.properties` 的 `sdk.dir` 为你实际路径 |
| `sdkmanager` 报找不到 SDK 根目录 | cmdline-tools 的目录名不是 `latest`，见路线 B 第 2 步 |
| Gradle 下载超时 | 已配腾讯云镜像；若仍慢，手工下载 zip 放 `%GRADLE_USER_HOME%\wrapper\dists\` |
| 依赖下载超时 | 已配阿里云镜像；若个别包缺失会回退官方源（需代理） |
| `compileSdk 36` 报缺失 | `sdkmanager "platforms;android-36"` |
| C 盘爆满 | 检查 `GRADLE_USER_HOME` 是否已改（第 5 步 / 路线 B 第 3 步） |
| 改完环境变量没生效 | 必须**新开终端**；`setx` 不影响已开窗口 |
| 模拟器无法启动 | 确认 BIOS 里 VT-x 已开；本机 `HypervisorPresent = True`，理论无问题 |
| 模拟器里录音没声音 | Windows 麦克风权限：设置 → 隐私和安全性 → 麦克风 → 允许桌面应用 |
| 模拟器里没听到变声 | 模拟器音频输出有延迟，属正常；重点看日志是否走完 DSP 链 |
| Windows 上 `./gradlew` 报错 | CMD 用 `gradlew.bat`，Git Bash 用 `./gradlew` |
| 中文乱码 | `gradle.properties` 已设 `-Dfile.encoding=UTF-8`；终端也建议切 UTF-8 |
| 日志刷屏看不清 | `adb logcat -s Holler/Engine:* Holler/DSP:*` 精确过滤 |

---

## 车机侧（模拟器验证通过后再做）

```bash
adb connect <车机IP>:5555      # 或 USB 连接
adb devices                    # 确认已授权
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

车机音频探测（**决定车外喇叭真实 usage 的关键命令**，见 `ADB_PROBE.md`）：

```bash
adb shell id
adb shell getprop ro.build.display.id
adb shell dumpsys media.audio_policy
adb shell cat /vendor/etc/car_audio_configuration.xml
```

> 注意：领克 2.0.0 曾开放 ADB，2.0.1 又关闭。若你的车机当前无 ADB，
> 安装需另想办法（参照 CoAuto 社区方案）。
