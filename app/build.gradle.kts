// 顶部 import：Kotlin DSL 里 `java` 已被 JavaPluginExtension 占用，
// 必须显式 import java.util.Properties 才能用 Properties()
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // 不再需要 org.jetbrains.kotlin.android —— AGP 9 已内置 Kotlin 支持
}

// ---------------------------------------------------------------------------
// 可选正式签名：仅在提供了密钥库时才启用，否则 release 保持未签名
// （assembleRelease 产出 app-release-unsigned.apk，本地调试构建完全不受影响）。
//   · CI：密钥库 base64 存进 GitHub Secrets，workflow 解码后经
//     HOLLER_KEYSTORE_PATH / HOLLER_KEYSTORE_PASSWORD / HOLLER_KEY_ALIAS /
//     HOLLER_KEY_PASSWORD 四个环境变量传入。
//   · 本地：在 local.properties 写 holler.keystore.path / holler.keystore.password /
//     holler.key.alias / holler.key.password（该文件已被 .gitignore 忽略，不会入库）。
// ---------------------------------------------------------------------------
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(propName)?.takeIf { it.isNotBlank() }

val releaseKeystoreFile = signingValue("HOLLER_KEYSTORE_PATH", "holler.keystore.path")
    ?.let { file(it) }
    ?.takeIf { it.exists() }
val hasReleaseSigning = releaseKeystoreFile != null

android {
    namespace = "com.vongzh.holler"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vongzh.holler"
        // 领克 08 车机为 Flyme Auto（基于安卓 11 = API 30）。
        // ⚠ 此值尚未经 adb 实测确认；若 `getprop ro.build.version.sdk` 不是 30，
        //    必须改回对应值，否则 APK 直接装不上。
        minSdk = 30
        targetSdk = 37
        versionCode = 7
        versionName = "0.3.4"
    }

    signingConfigs {
        if (releaseKeystoreFile != null) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = signingValue("HOLLER_KEYSTORE_PASSWORD", "holler.keystore.password")
                keyAlias = signingValue("HOLLER_KEY_ALIAS", "holler.key.alias")
                keyPassword = signingValue("HOLLER_KEY_PASSWORD", "holler.key.password")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 没密钥库时保持 AGP 默认的「未签名」，绝不因为缺少 secrets 而构建失败
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// AGP 9：android.kotlinOptions{} 已移除，统一改用 kotlin.compilerOptions{}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
