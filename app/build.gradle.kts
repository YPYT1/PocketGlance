plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val supportedAndroidAbi = "arm64-v8a"

val resolvedVersionCode: Int =
    (findProperty("versionCode") ?: property("pocketglance.versionCode")).toString().toInt()
val resolvedVersionName: String = property("pocketglance.versionName").toString()
// 内置 LUT 已提交在 app/src/main/assets/built-in-luts/。
// 仅当本地缺少 catalog 时，才从 Downloads 目录现场打包进 generated assets。
val committedLutCatalog = layout.projectDirectory.file("src/main/assets/built-in-luts/catalog.tsv")
val suppliedLutRoots: List<java.io.File> =
    providers.gradleProperty("pocketglance.lutPack").orNull?.let { listOf(file(it)) }
        ?: listOf(
            file("${System.getProperty("user.home")}/Downloads/Pocket4滤镜LUT"),
            file("${System.getProperty("user.home")}/Downloads/滤镜文件"),
            file("${System.getProperty("user.home")}/Downloads/2 大疆Dlog-M专用LUT【温影工坊】"),
        )
abstract class PackPreviewLuts @javax.inject.Inject constructor(
    private val executor: org.gradle.process.ExecOperations,
) : DefaultTask() {
    @get:InputFiles abstract val roots: ConfigurableFileCollection
    @get:InputFile abstract val script: RegularFileProperty
    @get:OutputDirectory abstract val output: DirectoryProperty

    @TaskAction fun pack() {
        val args = mutableListOf(
            "python3",
            script.get().asFile.absolutePath,
            output.get().asFile.absolutePath,
        )
        args += roots.files.map { it.absolutePath }
        executor.exec { commandLine(args) }
    }
}

val packPreviewLuts by tasks.registering(PackPreviewLuts::class) {
    roots.from(suppliedLutRoots)
    script.set(rootProject.file("scripts/pack-built-in-luts.py"))
    output.set(layout.buildDirectory.dir("generated/previewLutAssets"))
    onlyIf { !committedLutCatalog.asFile.exists() }
}

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

androidComponents.onVariants { variant ->
    if (!committedLutCatalog.asFile.exists()) {
        variant.sources.assets?.addGeneratedSourceDirectory(packPreviewLuts, PackPreviewLuts::output)
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
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

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
