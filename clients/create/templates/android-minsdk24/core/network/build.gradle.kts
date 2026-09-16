plugins {
    id("yarch.android.library")
    id("yarch.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.ydonghao.{{appPackageSegment}}.core.network"
}

dependencies {
    api(project(":core:common"))
    api(libs.yarch.client.android)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.converter)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
}
