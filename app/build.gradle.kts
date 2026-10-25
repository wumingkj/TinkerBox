plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.wuming.tinkerbox"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wuming.tinkerbox"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // 包名保持固定（com.wuming.tinkerbox），仅版本号带 -debug 便于识别
            versionNameSuffix = "-debug"
        }
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.material.icons.extended)

    // SukiSU 液态玻璃毛玻璃管线（背景捕获 + 模糊 + RuntimeShader 折射）
    implementation(libs.miuix.blur)

    // miuix 全套组件库（MIUI 风格 UI/图标/Preference/Nav，主题设置界面同款）
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.nav)

    // Material You 动态配色生成（ThemeController 依据 keyColor/调色板风格/colorSpec 生成主题）
    implementation(libs.material.kolor)

    // 运行时动态开关预留性返回手势（调用隐藏 API ApplicationInfo.setEnableOnBackInvokedCallback）
    implementation(libs.hiddenapibypass)

    debugImplementation(libs.androidx.compose.ui.tooling)
}