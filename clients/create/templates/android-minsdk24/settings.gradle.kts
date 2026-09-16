pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "{{packageName}}"

// 发版前消费本仓库源码（对偶 golang replace 行）；yarch-client-android 发 Central 后删除此行改走版本依赖
includeBuild("{{yarchClientPath}}")

include(":app")
include(":core:network")
include(":core:designsystem")
include(":core:common")
include(":feature:login")
include(":feature:users")
