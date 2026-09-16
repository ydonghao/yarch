package yarch

/**
 * 基线常量（contract/clients/android.md 六，本档 = 24 扩展档）：
 * 26+ API 调用必须 Build.VERSION guard（lint NewApi fatal 兜底），core library desugaring 开启。
 */
object AndroidSdk {
    const val compileSdk = 36
    const val targetSdk = 36
    const val minSdk = 24
    const val coreLibraryDesugaring = true
}
