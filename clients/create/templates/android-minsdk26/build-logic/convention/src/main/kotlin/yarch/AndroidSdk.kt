package yarch

/**
 * 基线常量（contract/clients/android.md 六）：compileSdk 跟随 stable、targetSdk >= 36 是
 * Google Play 上架硬线（2026-08-31 起生效），minSdk 由模板档决定（本档 = 26 默认）。
 */
object AndroidSdk {
    const val compileSdk = 36
    const val targetSdk = 36
    const val minSdk = 26
    const val coreLibraryDesugaring = false
}
