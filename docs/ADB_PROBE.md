# 车机探测指南

在写任何音频代码之前，必须先搞清楚：**你的车机上，车外扬声器是哪个设备？**

以下命令均为**只读**，不修改车机任何状态，可放心执行。

## 1. 音频输出设备清单（最重要）

```bash
adb shell dumpsys media.audio_policy | sed -n '/Output devices/,/Input devices/p'
```

看输出里有没有类似 `external_speaker` / `bus0_...` / 厂商自定义名的条目。
**这是判断能否纯 App 实现的关键。**

## 2. 车机音频配置文件（路由权威来源）

```bash
adb shell cat /vendor/etc/car_audio_configuration.xml
adb shell cat /odm/etc/car_audio_configuration.xml
adb shell cat /system/etc/car_audio_configuration.xml
```

这个文件定义了「哪个 usage → 哪个物理设备」。典型内容：

```xml
<device name="bus0_external" address="...">
  <context name="NAVIGATION"/>
  <context name="SYSTEM"/>
</device>
```

**把整个文件内容发出来**，即可确定：
- 车外喇叭的设备名
- 它承接的 usage（Holler 的 `CANDIDATE_USAGES` 要据此调整）

## 3. 完整音频服务信息

```bash
adb shell dumpsys audio | head -200
```

## 4. 权限与身份确认

```bash
adb shell id                        # shell 还是 root
adb shell getprop ro.product.model  # 车型
adb shell getprop ro.build.version.release  # Android 版本
adb shell getprop ro.build.display.id       # 系统版本号
```

## 5. 参考样本（可选，很有价值）

若车机上装了 Hey!：

```bash
adb shell pm path com.kooo.hey              # 定位安装路径
adb shell dumpsys activity services com.kooo.hey   # 看它起了哪些服务
adb shell dumpsys audio | grep -i kooo      # 看它的音频占用
```

## 探测结果怎么用

| 探测发现 | 对策 |
|---|---|
| 输出设备里**有**车外喇叭（含 external/outside 等字样） | 直接改 `AudioRouteResolver.looksExternal()` 匹配它，走 `setPreferredDevice` |
| 输出设备里**没有**，但配置文件里**有** external 设备 + usage | 用对应 usage 构造 `AudioAttributes`，看路由是否自动生效 |
| 两者都看不到 | 需要 `app_process` 提权探测，或调 `vendor.ecarx.xma.audiocontrol` HAL |

## 记录模板

把结果按这个格式贴回来即可：

```
车型: 领克08 EM-P
Android: 
系统版本: 
adb 身份: 

[输出设备]
...dumpsys media.audio_policy 的相关片段...

[car_audio_configuration.xml]
...完整内容...

[其他发现]
...
```
