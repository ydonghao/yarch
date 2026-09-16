package yarch

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Compose 约定（contract/clients/android.md 四）：开启 compose 构建特性。
 * Compose 依赖（含 BOM platform）由各模块 build.gradle.kts 声明——依赖声明属模块层职责，
 * platform() 访问器在 .kts 脚本内天然可用，不进 convention 源。
 */
class YarchAndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<CommonExtension<*, *, *, *, *, *>> {
                buildFeatures {
                    compose = true
                }
            }
        }
    }
}
