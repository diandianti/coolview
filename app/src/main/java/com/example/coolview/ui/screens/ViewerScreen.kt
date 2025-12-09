package com.example.coolview.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.example.coolview.ui.components.EffectTile
import com.example.coolview.ui.screens.viewer.ViewerControlPanel
import com.example.coolview.ui.screens.viewer.ViewerGridContent
import com.example.coolview.ui.screens.viewer.generateTileSpecs
import com.example.coolview.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(viewModel: MainViewModel) {
    val images by viewModel.allImages.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val settings by viewModel.visualSettings.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // [新增] 处理屏幕常亮逻辑
    DisposableEffect(settings.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            // 退出 ViewerScreen 时清除常亮标志，避免影响 Config 界面
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 1. 图片提供者
    val imageProvider = remember(viewModel) {
        { viewModel.getNextBufferedImage() }
    }

    // 2. 缓存兜底提供者
    val cachedImageProvider = remember(viewModel, context) {
        { viewModel.getRandomCachedImage(context) }
    }

    // 3. 辅助组件：统一封装 EffectTile
    val CommonEffectTile = @Composable { isHero: Boolean ->
        val effectiveInterval = if (isHero) settings.refreshInterval * 5 else settings.refreshInterval
        EffectTile(
            imageProvider = imageProvider,
            cachedImageProvider = cachedImageProvider,
            refreshIntervalBase = effectiveInterval,
            maxScale = settings.breathScale,
            breathDuration = settings.breathDuration,
            brightness = settings.brightness,
            isHero = isHero
        )
    }

    val tileSpecs = remember(settings.modeIndex, settings.colCount, settings.layoutChaos) {
        generateTileSpecs(settings.modeIndex, settings.colCount, settings.layoutChaos)
    }

    val waterfallState = rememberLazyStaggeredGridState()

    LaunchedEffect(settings.modeIndex, settings.autoScrollSpeed) {
        if (settings.modeIndex == 5 && settings.autoScrollSpeed != 0f) {
            while (isActive) {
                waterfallState.scrollBy(settings.autoScrollSpeed)
                delay(16)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { showMenu = true }) }
    ) {
        if (images.isEmpty() && isScanning) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (images.isEmpty()) {
            Text("No images found", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            ViewerGridContent(
                settings = settings,
                tileSpecs = tileSpecs,
                waterfallState = waterfallState,
                showMenu = showMenu,
                maxHeight = maxHeight,
                maxWidth = maxWidth,
                tileContent = CommonEffectTile
            )
        }

        if (showMenu) {
            ModalBottomSheet(
                onDismissRequest = { showMenu = false },
                containerColor = Color(0xDD1E1E1E),
                contentColor = Color.White
            ) {
                ViewerControlPanel(
                    settings = settings,
                    onSettingsChanged = { newSettings -> viewModel.updateSettings(newSettings) }
                )
            }
        }
    }
}