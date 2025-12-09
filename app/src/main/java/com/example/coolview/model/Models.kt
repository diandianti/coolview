package com.example.coolview.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class SourceType { LOCAL, SMB, WEBDAV }

@Serializable
data class SourceConfig(
    val id: String = UUID.randomUUID().toString(),
    val type: SourceType,
    val path: String = "",
    val host: String = "",
    val share: String = "",
    val user: String = "",
    val password: String = "",
    // [新增] 是否递归扫描子文件夹
    val recursive: Boolean = true
)

// [新增] 场景模型，用于保存不同的配置集合
@Serializable
data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sources: List<SourceConfig> = emptyList()
)

data class ImageItem(
    val uri: String,
    val sourceConfig: SourceConfig
)