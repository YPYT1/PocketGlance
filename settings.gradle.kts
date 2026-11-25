pluginManagement {
    // 兄弟仓库 OpenCine 的 Gradle 插件与版本目录。
    // 当前使用包含 Pocket 拍照状态的 OpenCine 工作树。
    val openCineRoot =
        providers.gradleProperty("opencine.root").orNull ?: "../OpenCine-tasks/pocketglance-photo"
    includeBuild(file("$openCineRoot/Android/build-logic"))
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PocketGlance"

val openCineRoot =
    providers.gradleProperty("opencine.root").orNull ?: "../OpenCine-tasks/pocketglance-photo"

// 只组合进无线监视实际需要的 OpenCine Android 模块，不拷贝整仓。
includeBuild(file("$openCineRoot/Android")) {
    name = "opencine"
    dependencySubstitution {
        substitute(module("com.opencapture.opencine:core")).using(project(":core"))
        substitute(module("com.opencapture.opencine:media")).using(project(":media"))
        substitute(module("com.opencapture.opencine:platform")).using(project(":platform"))
        substitute(module("com.opencapture.opencine:decoder")).using(project(":decoder"))
        substitute(module("com.opencapture.opencine:native-runtime")).using(project(":native-runtime"))
        substitute(module("com.opencapture.opencine:dji")).using(project(":dji"))
        substitute(module("com.opencapture.opencine:vulkan")).using(project(":vulkan"))
        substitute(module("com.opencapture.opencine:appkit")).using(project(":appkit"))
        substitute(module("com.opencapture.opencine:media3")).using(project(":media3"))
    }
}

include(":app")
