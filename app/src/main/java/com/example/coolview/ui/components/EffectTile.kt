package com.example.coolview.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.coolview.data.ClientFactory
import com.example.coolview.model.ImageItem
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

@Composable
fun EffectTile(
    imageProvider: () -> ImageItem?,
    cachedImageProvider: () -> ImageItem?,
    refreshIntervalBase: Long = 5000,
    maxScale: Float = 1.15f,
    breathDuration: Long = 15000L,
    brightness: Float = 1.0f,
    isHero: Boolean = false
) {
    val context = LocalContext.current
    var currentImage by remember { mutableStateOf<ImageItem?>(null) }

    // 图片轮播与预加载逻辑
    LaunchedEffect(Unit) {
        var initial = imageProvider()
        if (initial == null) initial = cachedImageProvider()

        if (initial != null) {
            ClientFactory.fetchImageData(context, initial)
            currentImage = initial
        }

        while (isActive) {
            val randomOffset = if (isHero) 0 else Random.nextLong(0, 3000)
            val displayDuration = (refreshIntervalBase + randomOffset).coerceAtLeast(1000)

            // 异步准备下一张
            val nextImageJob = async {
                val item = imageProvider()
                if (item != null) {
                    val file = ClientFactory.fetchImageData(context, item)
                    if (file != null) item else null
                } else null
            }

            delay(displayDuration)

            // 获取结果，允许少量超时等待
            val timeoutLimit = (displayDuration * 0.05).toLong()
            var nextItem = withTimeoutOrNull(timeoutLimit) {
                nextImageJob.await()
            }

            // 失败兜底
            if (nextItem == null) {
                nextItem = cachedImageProvider()
                if (nextItem != null) {
                    ClientFactory.fetchImageData(context, nextItem)
                }
            }

            if (nextItem != null) {
                currentImage = nextItem
            }
        }
    }

    // 呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "breath")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(breathDuration.toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    AnimatedContent(
        targetState = currentImage,
        transitionSpec = {
            if (isHero) {
                fadeIn(tween(1500)) togetherWith fadeOut(tween(1500))
            } else {
                val dice = Math.random()
                when {
                    dice < 0.25 -> fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    dice < 0.50 -> slideInVertically { height -> height } + fadeIn() togetherWith slideOutVertically { height -> -height } + fadeOut()
                    dice < 0.75 -> scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
                    else -> slideInHorizontally { width -> -width } togetherWith slideOutHorizontally { width -> width }
                }
            }
        }, label = "imageSwitch"
    ) { imgItem ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clip(RectangleShape)
        ) {
            val density = LocalDensity.current
            // 关键优化：限制请求尺寸，且不超过 1920px
            val requestWidth = with(density) { (maxWidth.toPx() * maxScale).toInt().coerceAtMost(1920) }
            val requestHeight = with(density) { (maxHeight.toPx() * maxScale).toInt().coerceAtMost(1920) }

            if (imgItem != null) {
                var imageModel by remember(imgItem) { mutableStateOf<Any?>(null) }

                LaunchedEffect(imgItem) {
                    // 此时文件已下载好，获取本地 File 对象
                    imageModel = ClientFactory.fetchImageData(context, imgItem)
                }

                if (imageModel != null && requestWidth > 0 && requestHeight > 0) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageModel)
                            .crossfade(true)
                            .size(requestWidth, requestHeight) // 限制内存占用
                            .precision(Precision.EXACT) // 强制裁剪，不加载完整原图
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(scale)
                            .alpha(brightness)
                    )
                }
            }
        }
    }
}