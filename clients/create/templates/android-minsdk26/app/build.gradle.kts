plugins {
    id("yarch.android.application")
    id("yarch.android.compose")
    id("yarch.android.hilt")
}

android {
    namespace = "io.github.ydonghao.{{appPackageSegment}}"

    defaultConfig {
        applicationId = "io.github.ydonghao.{{appPackageSegment}}"
        versionCode = 1
        versionName = "0.1.0"
    }

    // 环境由构建注入（client-shared 三-5）：base URL 禁散落在业务代码
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            defaultConfig.buildConfigField("String", "API_BASE_URL", "\"https://api.example.com/\"")
        }
        release {
            defaultConfig.buildConfigField("String", "API_BASE_URL", "\"https://api.example.com/\"")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":feature:login"))
    implementation(project(":feature:users"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
}
