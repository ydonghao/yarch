plugins {
    id("yarch.android.library")
    id("yarch.android.compose")
}

android {
    namespace = "io.github.ydonghao.{{appPackageSegment}}.core.designsystem"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
