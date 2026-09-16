package yarch

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

/** :app 壳模块约定：targetSdk 硬线断言 + 三重机检接线（ktlint / detekt / Lint）。 */
class YarchAndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            pluginManager.apply("io.gitlab.arturbosch.detekt")
            extensions.configure<ApplicationExtension> {
                defaultConfig {
                    // targetSdk >= 36 是 Play 上架硬线（2026-08-31 起），改低直接构建失败（android.md 六-1）
                    check(AndroidSdk.targetSdk >= 36) { "targetSdk must be >= 36 (Google Play policy since 2026-08-31)" }
                    targetSdk = AndroidSdk.targetSdk
                }
                lint {
                    abortOnError = true
                }
                configureKotlinAndroid(this)
            }
        }
    }
}
