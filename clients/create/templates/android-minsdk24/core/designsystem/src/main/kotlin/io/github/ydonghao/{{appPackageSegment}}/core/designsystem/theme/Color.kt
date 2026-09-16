package io.github.ydonghao.{{appPackageSegment}}.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// 设计 token 唯一落点（contract/clients/android.md 四-3）：色值字面量只允许出现在本文件，
// feature 模块出现 Color(0x...) 即缺陷（机检点）。
val BrandPrimary = Color(0xFF2E5BFF)
val BrandPrimaryDark = Color(0xFF9DB0FF)
val BrandSecondary = Color(0xFF0FB5A6)
val BrandSecondaryDark = Color(0xFF6FE0D5)
val SurfaceLight = Color(0xFFF7F8FA)
val SurfaceDark = Color(0xFF16181D)
