// 顶层构建文件：只声明插件版本，不在此处添加模块依赖
//
// ⚠ AGP 9 内置 Kotlin 编译器（AGP 9.4.1 自带 KGP 2.2.10），由此带来两条硬规则：
//   1) org.jetbrains.kotlin.android 插件【必须移除】，否则报
//      "Cannot add extension with name 'kotlin', as there is an extension already registered"
//   2) 想把 Kotlin 升到 2.4.20，只改下面 plugins 块里的版本号是【无效】的
//      （它只作用于 Compose 插件，编译器本身纹丝不动，会得到一个
//      「编译通过但新语法报错」的假象）—— 必须在 buildscript classpath 里显式声明 KGP。

buildscript {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
    dependencies {
        // 向上覆盖 AGP 内置的 KGP 2.2.10 → 2.4.20
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    // Compose 编译器插件：版本必须与 Kotlin 版本一致
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
