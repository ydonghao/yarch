plugins {
    id("yarch.android.library")
    id("yarch.android.compose")
    id("yarch.android.hilt")
}

android {
    namespace = "io.github.ydonghao.{{appPackageSegment}}.feature.users"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
