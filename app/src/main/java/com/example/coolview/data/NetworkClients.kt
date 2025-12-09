package com.example.coolview.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.coolview.model.ImageItem
import com.example.coolview.model.SourceConfig
import com.example.coolview.model.SourceType
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthentication
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Properties

object ClientFactory {
    // 限制缓存处理的最大尺寸，防止加载超大图导致 OOM
    private const val CACHE_MAX_DIMENSION = 1920
    private const val CACHE_COMPRESS_QUALITY = 80
    private const val TAG = "ClientFactory"

    suspend fun scanImages(context: Context, config: SourceConfig): List<ImageItem> {
        return when (config.type) {
            SourceType.LOCAL -> scanLocal(context, config)
            SourceType.SMB -> scanSmb(config)
            SourceType.WEBDAV -> scanWebDav(config)
        }
    }

    suspend fun fetchImageData(context: Context, item: ImageItem): Any? = withContext(Dispatchers.IO) {
        try {
            when (item.sourceConfig.type) {
                SourceType.LOCAL -> {
                    if (item.uri.startsWith("content://")) Uri.parse(item.uri)
                    else File(item.uri)
                }
                else -> {
                    val cacheKey = CacheManager.generateKey(item.uri)
                    // 1. 检查缓存
                    if (CacheManager.hasFile(context, cacheKey)) {
                        val file = CacheManager.getFile(context, cacheKey)
                        if (isValidImageFile(file)) return@withContext file
                        else file.delete()
                    }
                    // 2. 下载并转码（优化版）
                    val downloadedFile = downloadAndTranscodeOptimized(context, item, cacheKey)
                    if (downloadedFile != null && isValidImageFile(downloadedFile)) downloadedFile
                    else {
                        downloadedFile?.delete()
                        null
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isValidImageFile(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            BitmapFactory.decodeFile(file.absolutePath, options)
            return options.outWidth > 0 && options.outHeight > 0
        } catch (e: Exception) { return false }
    }

    /**
     * OOM 修复核心方法：
     * 1. 流式下载到临时文件 (Raw)
     * 2. 读取尺寸计算采样率 (inSampleSize)
     * 3. 加载缩小的 Bitmap
     * 4. 压缩保存并回收内存
     */
    private fun downloadAndTranscodeOptimized(context: Context, item: ImageItem, key: String): File? {
        val tempRawFile = File(context.cacheDir, "${key}_raw.tmp")
        val tempProcessedFile = File(context.cacheDir, "${key}_processed.tmp")
        var bitmap: Bitmap? = null

        try {
            // 步骤 1: 网络流 -> 本地临时文件 (避免内存持有完整字节流)
            getStream(item)?.use { input ->
                FileOutputStream(tempRawFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempRawFile.exists() || tempRawFile.length() == 0L) return null

            // 步骤 2: 只解码边界，计算采样率
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempRawFile.absolutePath, options)

            options.inSampleSize = calculateInSampleSize(options, CACHE_MAX_DIMENSION, CACHE_MAX_DIMENSION)
            options.inJustDecodeBounds = false
            // 使用 RGB_565 减少一半内存占用（如果不需要透明度）
            options.inPreferredConfig = Bitmap.Config.RGB_565

            // 步骤 3: 根据采样率加载 Bitmap
            bitmap = BitmapFactory.decodeFile(tempRawFile.absolutePath, options)

            if (bitmap != null) {
                // 步骤 4: 压缩并写入处理后的临时文件
                FileOutputStream(tempProcessedFile).use { outStream ->
                    val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    }
                    bitmap?.compress(format, CACHE_COMPRESS_QUALITY, outStream)
                }

                // 关键：立即回收 Bitmap
                bitmap?.recycle()
                bitmap = null

                // 步骤 5: 移交给 CacheManager (原子重命名)
                return CacheManager.saveFile(context, key, tempProcessedFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcode failed: ${e.message}")
            e.printStackTrace()
        } finally {
            // 清理资源
            bitmap?.recycle()
            if (tempRawFile.exists()) tempRawFile.delete()
            // 如果处理失败，清理产生的临时文件；如果成功，saveFile 内部会处理 rename，这里再次检查删除防残留
            if (tempProcessedFile.exists()) tempProcessedFile.delete()
        }
        return null
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun getStream(item: ImageItem): InputStream? {
        return when (item.sourceConfig.type) {
            SourceType.SMB -> {
                val config = item.sourceConfig
                val props = Properties().apply {
                    setProperty("jcifs.smb.client.responseTimeout", "10000")
                    setProperty("jcifs.smb.client.soTimeout", "10000")
                }
                val baseContext = BaseContext(PropertyConfiguration(props))
                val auth = NtlmPasswordAuthentication(baseContext, null, config.user, config.password)
                val ctx = baseContext.withCredentials(auth)
                SmbFile(item.uri, ctx).inputStream
            }
            SourceType.WEBDAV -> {
                val config = item.sourceConfig
                val sardine: Sardine = OkHttpSardine()
                sardine.setCredentials(config.user, config.password)
                sardine.get(item.uri)
            }
            else -> null
        }
    }

    // --- 扫描逻辑 (保持原有不变) ---

    private fun scanLocal(context: Context, config: SourceConfig): List<ImageItem> {
        val images = mutableListOf<ImageItem>()
        val pathStr = config.path
        if (pathStr.startsWith("content://")) {
            try {
                val treeUri = Uri.parse(pathStr)
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.isDirectory) {
                    scanDocumentRecursive(rootDoc, config, images, config.recursive)
                }
            } catch (e: Exception) { Log.e("LocalScan", "URI error: ${e.message}") }
        } else {
            val root = File(pathStr)
            if (root.exists()) {
                if (config.recursive) {
                    root.walkTopDown().forEach { file ->
                        if (file.isFile && isImage(file.name)) images.add(ImageItem(file.absolutePath, config))
                    }
                } else {
                    root.listFiles()?.forEach { file ->
                        if (file.isFile && isImage(file.name)) images.add(ImageItem(file.absolutePath, config))
                    }
                }
            }
        }
        return images
    }

    private fun scanDocumentRecursive(dir: DocumentFile, config: SourceConfig, list: MutableList<ImageItem>, recursive: Boolean) {
        dir.listFiles().forEach { file ->
            if (file.isDirectory) {
                if (recursive) scanDocumentRecursive(file, config, list, true)
            } else if (file.name != null && isImage(file.name!!)) {
                list.add(ImageItem(file.uri.toString(), config))
            }
        }
    }

    private suspend fun scanSmb(config: SourceConfig): List<ImageItem> {
        val images = mutableListOf<ImageItem>()
        try {
            val props = Properties().apply {
                setProperty("jcifs.smb.client.responseTimeout", "5000")
                setProperty("jcifs.smb.client.soTimeout", "5000")
            }
            val baseContext = BaseContext(PropertyConfiguration(props))
            val auth = NtlmPasswordAuthentication(baseContext, null, config.user, config.password)
            val ctx = baseContext.withCredentials(auth)
            val safeHost = config.host.removeSuffix("/")
            val safeShare = config.share.removePrefix("/").removeSuffix("/")
            val safePath = config.path.removePrefix("/").removeSuffix("/")
            val url = "smb://$safeHost/$safeShare/$safePath/"

            scanSmbRecursive(url, ctx, config, images, config.recursive)
        } catch (e: Exception) { e.printStackTrace() }
        return images
    }

    private fun scanSmbRecursive(url: String, ctx: CIFSContext, config: SourceConfig, list: MutableList<ImageItem>, recursive: Boolean) {
        try {
            val dir = SmbFile(url, ctx)
            dir.listFiles()?.forEach { f ->
                try {
                    val fileName = f.name.removeSuffix("/")
                    if (f.isDirectory) {
                        if (recursive) scanSmbRecursive(f.path, ctx, config, list, true)
                    } else if (isImage(fileName)) {
                        list.add(ImageItem(f.path, config))
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
    }

    private fun scanWebDav(config: SourceConfig): List<ImageItem> {
        val images = mutableListOf<ImageItem>()
        try {
            val sardine = OkHttpSardine()
            sardine.setCredentials(config.user, config.password)
            val host = config.host.removeSuffix("/")
            val path = config.path.removePrefix("/").removeSuffix("/")
            val fullUrl = "$host/$path/"

            if (config.recursive) {
                scanWebDavRecursive(sardine, fullUrl, host, config, images)
            } else {
                val resources = sardine.list(fullUrl)
                resources.forEach { res ->
                    if (!res.isDirectory && isImage(res.name)) {
                        val imgUrl = if (res.path.toString().startsWith("http")) res.path.toString() else "$host${res.path}"
                        images.add(ImageItem(imgUrl, config))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return images
    }

    private fun scanWebDavRecursive(sardine: Sardine, url: String, hostBase: String, config: SourceConfig, list: MutableList<ImageItem>) {
        try {
            val resources = sardine.list(url)
            resources.forEach { res ->
                val resPath = res.path.toString()
                val fullResUrl = if (resPath.startsWith("http")) resPath else "$hostBase$resPath"
                val isSelf = fullResUrl.trimEnd('/') == url.trimEnd('/')

                if (!isSelf) {
                    if (res.isDirectory) {
                        scanWebDavRecursive(sardine, fullResUrl, hostBase, config, list)
                    } else if (isImage(res.name)) {
                        list.add(ImageItem(fullResUrl, config))
                    }
                }
            }
        } catch (e: Exception) { }
    }

    private fun isImage(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "bmp", "webp", "gif")
    }
}