plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val supportedAndroidAbi = "arm64-v8a"

val resolvedVersionCode: Int =
    (findProperty("versionCode") ?: property("pocketglance.versionCode")).toString().toInt()
val resolvedVersionName: String = property("pocketglance.versionName").toString()

android {
    namespace = "com.opencapture.pocketglance"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.opencapture.pocketglance"
        minSdk = 29
        targetSdk = 36
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += supportedAndroidAbi }
    }

    ndkVersion = "28.2.13676358"

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // 预置 libOpenPocketCineAndroid.so（见 app/src/main/jniLibs）。
    sourceSets {
        getByName("main").jniLibs.srcDirs("src/main/jniLibs")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // SoftAP 会话 + 解码 + 权限门；经 settings includeBuild 指到 ../OpenCine。
    implementation("com.opencapture.opencine:dji:0.1.0-SNAPSHOT")
    implementation("com.opencapture.opencine:decoder:0.1.0-SNAPSHOT")
    implementation("com.opencapture.opencine:media:0.1.0-SNAPSHOT")
    implementation("com.opencapture.opencine:core:0.1.0-SNAPSHOT")
    implementation("com.opencapture.opencine:platform:0.1.0-SNAPSHOT")
    implementation("com.opencapture.opencine:native-runtime:0.1.0-SNAPSHOT")
    implementation("com.opencapture.opencine:appkit:0.1.0-SNAPSHOT")

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
