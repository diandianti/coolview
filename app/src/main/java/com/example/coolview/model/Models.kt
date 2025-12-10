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
    val recursive: Boolean = true
)

@Serializable
data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sources: List<SourceConfig> = emptyList()
)

// [修改] 增加文件属性字段，用于生成精确的缓存 Key，但不包含场景名以共享缓存
data class ImageItem(
    val uri: String,
    val sourceConfig: SourceConfig,
    val lastModified: Long = 0,    // 新增：文件最后修改时间
    val fileSize: Long = 0         // 新增：文件大小
)