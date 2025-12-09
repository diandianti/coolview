package com.example.coolview.ui.screens.viewer

import kotlin.random.Random

data class TileSpec(
    val colSpan: Int = 1,
    val rowSpan: Float = 1.0f,
    val isSpacer: Boolean = false
)

fun generateTileSpecs(mode: Int, cols: Int, chaos: Float): List<TileSpec> {
    val list = mutableListOf<TileSpec>()
    val count = 1000

    when (mode) {
        0 -> repeat(count) { list.add(TileSpec(1, 1f)) }
        3 -> {
            val effectiveCols = cols
            repeat(count / (effectiveCols * 2)) {
                repeat(effectiveCols) { list.add(TileSpec(2, 1f)) }
                list.add(TileSpec(1, 1f))
                repeat(effectiveCols - 1) { list.add(TileSpec(2, 1f)) }
                list.add(TileSpec(1, 1f))
            }
        }
        1, 2 -> {
            repeat(count) {
                if (Random.nextFloat() < chaos) {
                    list.add(TileSpec(1, 2.0f))
                } else {
                    list.add(TileSpec(1, 1.0f))
                }
            }
        }
        4 -> {
            repeat(count) {
                if (Random.nextFloat() < chaos) {
                    val h = if (Random.nextBoolean()) 2.0f else 3.0f
                    list.add(TileSpec(1, h))
                } else {
                    list.add(TileSpec(1, 1.0f))
                }
            }
        }
        7 -> {
            repeat(100) {
                repeat(cols) { list.add(TileSpec(1, 1f)) }
                list.add(TileSpec(colSpan = cols, rowSpan = 2.0f))
                repeat(cols * 2) { list.add(TileSpec(1, 1f)) }
            }
        }
        else -> repeat(count) { list.add(TileSpec(1, 1f)) }
    }
    return list
}