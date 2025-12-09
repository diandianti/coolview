package com.example.coolview.ui.screens.viewer

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.example.coolview.viewmodel.VisualSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun ViewerGridContent(
    settings: VisualSettings,
    tileSpecs: List<TileSpec>,
    waterfallState: LazyStaggeredGridState,
    showMenu: Boolean,
    maxHeight: androidx.compose.ui.unit.Dp,
    maxWidth: androidx.compose.ui.unit.Dp,
    tileContent: @Composable (Boolean) -> Unit // CommonEffectTile
) {
    val rows = settings.rowCount.coerceAtLeast(1)
    val spacingTotal = (rows - 1) * settings.gridSpacing.dp
    val gridBaseHeight = ((maxHeight - spacingTotal) / rows).coerceAtLeast(10.dp)
    val colWidth = maxWidth / settings.colCount
    val waterfallBaseHeight = colWidth

    when (settings.modeIndex) {
        // 模式 0: 标准网格, 3: 密集网格
        0, 3 -> {
            val actualCols = if (settings.modeIndex == 3) settings.colCount * 2 else settings.colCount

            LazyVerticalGrid(
                columns = GridCells.Fixed(actualCols),
                horizontalArrangement = Arrangement.spacedBy(settings.gridSpacing.dp),
                verticalArrangement = Arrangement.spacedBy(settings.gridSpacing.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RectangleShape),
                userScrollEnabled = false
            ) {
                items(
                    count = tileSpecs.size,
                    span = { index ->
                        GridItemSpan(tileSpecs[index % tileSpecs.size].colSpan)
                    }
                ) { index ->
                    val spec = tileSpecs[index % tileSpecs.size]
                    if (spec.isSpacer) {
                        Spacer(modifier = Modifier.height(gridBaseHeight))
                    } else {
                        Box(
                            modifier = Modifier
                                .height(gridBaseHeight)
                                .fillMaxWidth()
                        ) {
                            tileContent(false)
                        }
                    }
                }
            }
        }

        // 模式 6: 纯瀑布流 (多列独立滚动)
        6 -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RectangleShape),
                horizontalArrangement = Arrangement.spacedBy(settings.gridSpacing.dp)
            ) {
                val columnStates = remember(settings.colCount) {
                    List(settings.colCount) {
                        androidx.compose.foundation.lazy.LazyListState(firstVisibleItemIndex = Int.MAX_VALUE / 2)
                    }
                }

                LaunchedEffect(settings.autoScrollSpeed, settings.colCount) {
                    if (settings.autoScrollSpeed != 0f) {
                        while (isActive) {
                            columnStates.forEachIndexed { index, state ->
                                val direction = if (index % 2 == 0) 1f else -1f
                                state.scrollBy(settings.autoScrollSpeed * direction)
                            }
                            delay(16)
                        }
                    }
                }

                repeat(settings.colCount) { colIndex ->
                    LazyColumn(
                        state = columnStates[colIndex],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(settings.gridSpacing.dp),
                        userScrollEnabled = false
                    ) {
                        items(Int.MAX_VALUE) {
                            val heightFactor = remember { Random.nextFloat() * 0.8f + 0.8f }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(waterfallBaseHeight * heightFactor)
                            ) {
                                tileContent(false)
                            }
                        }
                    }
                }
            }
        }

        // 其他模式: 交错网格 / 蒙德里安 / 瀑布流(统一滚动)
        else -> {
            val isWaterfall = settings.modeIndex == 5

            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(settings.colCount),
                horizontalArrangement = Arrangement.spacedBy(settings.gridSpacing.dp),
                verticalItemSpacing = settings.gridSpacing.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RectangleShape),
                state = if (isWaterfall) waterfallState else rememberLazyStaggeredGridState(),
                userScrollEnabled = isWaterfall || showMenu
            ) {
                items(
                    count = if (isWaterfall) Int.MAX_VALUE else tileSpecs.size,
                    span = { index ->
                        if (isWaterfall) StaggeredGridItemSpan.SingleLane
                        else {
                            val spec = tileSpecs[index % tileSpecs.size]
                            if (spec.colSpan > 1) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane
                        }
                    }
                ) { index ->
                    val spec = if (isWaterfall) TileSpec(1, Random.nextFloat() * 0.8f + 0.8f)
                    else tileSpecs[index % tileSpecs.size]

                    val itemHeight = if (isWaterfall) {
                        waterfallBaseHeight * spec.rowSpan
                    } else {
                        val spanInt = spec.rowSpan.roundToInt().coerceAtLeast(1)
                        gridBaseHeight * spanInt + (spanInt - 1) * settings.gridSpacing.dp
                    }

                    val isHeroTile = settings.modeIndex == 7 && (spec.rowSpan >= 1.5f || spec.colSpan > 1)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                    ) {
                        tileContent(isHeroTile)
                    }
                }
            }
        }
    }
}