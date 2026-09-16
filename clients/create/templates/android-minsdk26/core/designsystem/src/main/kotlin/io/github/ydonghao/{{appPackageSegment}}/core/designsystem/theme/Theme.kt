package io.github.ydonghao.{{appPackageSegment}}.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    secondary = BrandSecondary,
    surface = SurfaceLight
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryDark,
    secondary = BrandSecondaryDark,
    surface = SurfaceDark
)

/** 全 App 唯一主题入口：深色模式经 token 天然获得（contract/clients/android.md 四-5）。 */
@Composable
fun {{appClassName}}Theme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SampleTypography,
        content = content
    )
}
