plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // 不再需要 org.jetbrains.kotlin.android —— AGP 9 已内置 Kotlin 支持
}

android {
    namespace = "com.vongz.holler"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vongz.holler"
        // 领克 08 车机为 Flyme Auto（基于安卓 11 = API 30）。
        // ⚠ 此值尚未经 adb 实测确认；若 `getprop ro.build.version.sdk` 不是 30，
        //    必须改回对应值，否则 APK 直接装不上。
        minSdk = 30
        targetSdk = 37
        versionCode = 4
        versionName = "0.3.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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

    debugImplementation("androidx.compose.ui:ui-tooling")
}
