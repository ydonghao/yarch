package yarch

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Compose 约定（contract/clients/android.md 四）：开启 compose 构建特性。
 * Compose 依赖（含 BOM platform）由各模块 build.gradle.kts 声明——依赖声明属模块层职责，
 * platform() 访问器在 .kts 脚本内天然可用，不进 convention 源。
 * 扩展按具体类型配置（ApplicationExtension / LibraryExtension）——Gradle 扩展查找不认
 * CommonExtension 接口型 publicType（星投影 getByType 必失败），withPlugin 兼容两种 apply 顺序。
 */
class YarchAndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Kotlin 2.0+ compose 必须配编译器插件（版本随 kotlin 走）
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> {
                    buildFeatures { compose = true }
                }
            }
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> {
                    buildFeatures { compose = true }
                }
            }
        }
    }
}
