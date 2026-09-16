package io.github.ydonghao.{{appPackageSegment}}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.ydonghao.{{appPackageSegment}}.core.designsystem.theme.{{appClassName}}Theme
import io.github.ydonghao.{{appPackageSegment}}.navigation.RootNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            {{appClassName}}Theme {
                RootNavHost()
            }
        }
    }
}
