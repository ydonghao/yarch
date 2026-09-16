plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("yarchAndroidApplication") {
            id = "yarch.android.application"
            implementationClass = "yarch.YarchAndroidApplicationConventionPlugin"
        }
        register("yarchAndroidLibrary") {
            id = "yarch.android.library"
            implementationClass = "yarch.YarchAndroidLibraryConventionPlugin"
        }
        register("yarchAndroidCompose") {
            id = "yarch.android.compose"
            implementationClass = "yarch.YarchAndroidComposeConventionPlugin"
        }
        register("yarchAndroidHilt") {
            id = "yarch.android.hilt"
            implementationClass = "yarch.YarchAndroidHiltConventionPlugin"
        }
    }
}
