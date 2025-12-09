package com.example.coolview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.coolview.ui.screens.AboutScreen
import com.example.coolview.ui.screens.ConfigScreen
import com.example.coolview.ui.screens.ViewerScreen
import com.example.coolview.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全屏模式
        // 让内容延伸到系统窗口区域
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 获取 WindowInsetsController 来控制系统栏
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // 设置系统栏的行为，例如在滑动时临时显示
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // 隐藏状态栏和导航栏
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        val viewModel: MainViewModel by viewModels()

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "config") {
                    composable("config") {
                        ConfigScreen(
                            viewModel = viewModel,
                            onStartClicked = {
                                viewModel.startSession()
                                navController.navigate("viewer")
                            },
                            // [新增] 处理关于按钮点击
                            onAboutClicked = {
                                navController.navigate("about")
                            }
                        )
                    }
                    composable("viewer") {
                        ViewerScreen(viewModel = viewModel)
                    }
                    // [新增] 关于页面路由
                    composable("about") {
                        AboutScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}