package io.github.ydonghao.{{appPackageSegment}}.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.ydonghao.{{appPackageSegment}}.feature.login.LoginScreen
import io.github.ydonghao.{{appPackageSegment}}.feature.users.UsersScreen

@Composable
fun RootNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate("users") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("users") {
            UsersScreen()
        }
    }
}
