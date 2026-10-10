# Holler 混淆规则

# app_process 入口类：通过 shell 以高权限调用，类名/方法名不能被混淆
-keep class com.vongzh.holler.audio.shell.** { *; }

# 广播接收器：系统按清单里的类名实例化，混淆后需保留
-keep class com.vongzh.holler.audio.HollerCommandReceiver { *; }

# 服务：同样由系统按类名实例化
-keep class com.vongzh.holler.audio.*Service { *; }

# 音频与 DSP 层：JNI / 反射调用面，保守全保留（目前无 JNI，为未来原生实现留位置）
-keep class com.vongzh.holler.audio.** { *; }
