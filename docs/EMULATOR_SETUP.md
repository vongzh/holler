# 车机系统镜像安装（手动，绕过 SDK Manager）

> 2026-10-08 实测通过。适用于「国内下载慢 / SDK Manager 识别不到手动解压的镜像」场景。

## 一、下载：用腾讯云镜像，别直连 Google

**Google 直连实测只有 29 KB/s**（1.46 GB 要下 14 小时），且 ghproxy.net /
gh-proxy.com / ghfast.top 等 GitHub 代理类**全部不通**（HTTP 000）。

**✅ 腾讯云镜像站是完整的 Android SDK 仓库镜像**，路径与 Google 官方完全一致，
只需替换域名：

| 源 | 地址 |
|---|---|
| ❌ Google 官方 | `https://dl.google.com/android/repository/` |
| ✅ **腾讯云镜像** | `https://mirrors.cloud.tencent.com/AndroidSDK/` |

例如本项目的车机镜像：

```
https://mirrors.cloud.tencent.com/AndroidSDK/sys-img/android-automotive/x86_64-35-ext15_playstore_r03.zip
```

**其他镜像站实测结论（均无此路径）**：阿里云 `mirrors.aliyun.com/android-sdk`、
清华 TUNA、中科大 USTC、南大、北外、华为云 —— 全部 404 或 401。

> **备选：多线程下载确实有效。** Google 直连单线程 29 KB/s，8 并发聚合可达 **781 KB/s**（27 倍）。
> 若镜像站也没有目标文件，用 `aria2c -x16 -s16 <url>` 或 NDM 开多线程。

## 二、为什么手动解压后 adb 还提示下载？

**SDK Manager / adb 不扫描目录，而是读注册表文件**
`<SDK>/.knownPackages`（二进制格式）。
手动解压的镜像**没有在注册表里登记**，所以界面判定「未安装」，
于是继续提示下载 —— 而它提示的路径，正是文件已经存在的位置。

```
# 典型症状
adb / SDK Manager 提示：
  需要下载到 ...\system-images\android-35-ext15\android-automotive-playstore\x86_64
实际：
  该目录下 system.img / vendor.img / kernel-ranchu 等文件全部齐全
```

**排查确认文件是否真的完整**（对比一个已知可用的镜像）：

```bash
# 逐项对比文件清单，确认无缺失
comm -23 <(ls "<SDK>/system-images/<已知可用镜像>/x86_64/" | sort) \
         <(ls "<SDK>/system-images/<待检查镜像>/x86_64/" | sort)
```

## 三、解决：手工建 AVD，完全绕过 SDK Manager

**关键认知**：AVD 的本质只是两个文件；`emulator` 直接读目录，**不需要任何注册**。

### 1. 创建 `<AVD名>.ini`

`C:\Users\<用户>\.android\avd\Automotive_API_35.ini`

```ini
avd.ini.encoding=UTF-8
path=C:\Users\<用户>\.android\avd\Automotive_API_35.avd
path.rel=avd\Automotive_API_35.avd
target=android-35-ext15
```

### 2. 创建 `<AVD名>.avd\config.ini`

`C:\Users\<用户>\.android\avd\Automotive_API_35.avd\config.ini`

**最关键的是 `image.sysdir.1`** —— 它指向镜像目录（相对 `<SDK>`，用反斜杠 + 结尾反斜杠）：

```ini
image.sysdir.1=system-images\android-35-ext15\android-automotive-playstore\x86_64\
```

完整车机版配置：

```ini
AvdId=Automotive_API_35
PlayStore.enabled=true
abi.type=x86_64
avd.ini.displayname=Automotive API 35 ext15
avd.ini.encoding=UTF-8
disk.dataPartition.size=4G
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.device.manufacturer=Google
hw.device.name=automotive_1024p_landscape
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.initialOrientation=landscape
hw.keyboard=yes
hw.lcd.density=160
hw.lcd.height=768
hw.lcd.width=1024
hw.ramSize=2048
image.sysdir.1=system-images\android-35-ext15\android-automotive-playstore\x86_64\
showDeviceFrame=no
skin.dynamic=yes
skin.name=1024x768
skin.path=1024x768
tag.display=Automotive,Google Play
tag.id=android-automotive,google_apis_playstore
tag.ids=android-automotive,google_apis_playstore
target=android-35-ext15
vm.heapSize=256
```

### 3. 验证

```bash
export ANDROID_SDK_ROOT="<SDK路径>"
export ANDROID_AVD_HOME="C:/Users/<用户>/.android/avd"
cd "<SDK>/emulator"

./emulator.exe -list-avds        # 应出现 Automotive_API_35
./emulator.exe -avd Automotive_API_35
```

**启动成功的日志标志**：

```
INFO | Found systemPath <SDK>\system-images\android-35-ext15\android-automotive-playstore\x86_64\
INFO | Hypervisor compatibility to run avd: `Automotive_API_35` are met
INFO | System requirements to run avd: `Automotive_API_35` are met
INFO | Disk space requirements to run avd: `Automotive_API_35` are met
```

## 四、重要提醒：车机镜像 ≠ 车机

用到 **Automotive 系统镜像**只是比手机镜像更接近，但**仍无法验证车外喇叭**：

| 能验证 | 不能验证 |
|---|---|
| UI 渲染、布局、交互 | 车外喇叭是否存在 / 真响 |
| 录音 → DSP → 回放链路 | usage 遍历的真实结果（全打同一内置扬声器） |
| TTS 合成与播放 | `car_audio_configuration.xml`（不存在） |
| 权限流程、悬浮窗 | `CarAudioManager`（无车机音频服务） |
| 启动不崩、无 native crash | 真实音频路由策略 |

镜像里有 `ro.product.system.model=car_generic` 和 vehicle HAL 桩，
但**音频设备枚举结果与真车不同**。音频路由的最终结论必须在真车上验证。
