package io.github.ydonghao.{{appPackageSegment}}.core.common.platform

import android.os.Build

/**
 * minsdk24 档示例（contract/clients/android.md 六-2）：
 * 26+ API 必须 Build.VERSION guard，lint NewApi（fatal）兜底兜不住的动态判断集中在此登记。
 */
object PlatformFeatures {

    /** notification channel 自 API 26 起可用。 */
    fun notificationChannelsSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
}
