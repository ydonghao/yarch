plugins {
    id("yarch.android.library")
    id("yarch.android.hilt")
}

android {
    namespace = "io.github.ydonghao.{{appPackageSegment}}.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
