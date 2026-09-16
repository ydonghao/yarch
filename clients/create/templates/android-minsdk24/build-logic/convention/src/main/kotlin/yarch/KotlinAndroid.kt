package yarch

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** 共享 Kotlin/Android 编译配置：JDK 17 工具链统一在此，业务模块禁散装（contract/clients/android.md 三-2）。 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = AndroidSdk.compileSdk
        defaultConfig {
            minSdk = AndroidSdk.minSdk
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            // minsdk24 档：java.time 等经 desugaring 下探（contract/clients/android.md 六-2 差异条文）
            isCoreLibraryDesugaringEnabled = AndroidSdk.coreLibraryDesugaring
        }
        testOptions {
            unitTests.all { it.useJUnitPlatform() }
        }
    }
    if (AndroidSdk.coreLibraryDesugaring) {
        dependencies.add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk-libs").get())
    }
    tasks.withType(KotlinCompile::class.java).configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            // M3 实验性 API（TopAppBar 等）全工程放行——模板 UI 代码不逐文件 OptIn
            optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        }
    }
}

internal val Project.libs
    get(): org.gradle.api.artifacts.VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
