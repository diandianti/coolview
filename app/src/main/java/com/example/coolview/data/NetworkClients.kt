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

// [新增] 用于文件夹选择器的数据类
data class RemoteEntry(val name: String, val isDirectory: Boolean)

object ClientFactory {
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

    // [新增] 用于列出远程文件夹
    suspend fun listRemoteFolders(context: Context, config: SourceConfig, relativePath: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val list = mutableListOf<RemoteEntry>()
        try {
            when (config.type) {
                SourceType.SMB -> {
                    val props = Properties().apply {
                        setProperty("jcifs.smb.client.responseTimeout", "5000")
                        setProperty("jcifs.smb.client.soTimeout", "5000")
                    }
                    val baseContext = BaseContext(PropertyConfiguration(props))
                    val auth = NtlmPasswordAuthentication(baseContext, null, config.user, config.password)
                    val ctx = baseContext.withCredentials(auth)

                    // 构造 URL: smb://host/share/path/
                    val safeHost = config.host.removeSuffix("/")
                    val safeShare = config.share.removePrefix("/").removeSuffix("/")
                    // relativePath 通常以 / 开头，这里去掉
                    val safePath = relativePath.removePrefix("/").removeSuffix("/")

                    val url = if (safeShare.isNotEmpty()) {
                        if (safePath.isNotEmpty()) "smb://$safeHost/$safeShare/$safePath/"
                        else "smb://$safeHost/$safeShare/"
                    } else {
                        "smb://$safeHost/" // 只有主机名，可能列出共享
                    }

                    val dir = SmbFile(url, ctx)
                    dir.listFiles()?.forEach { f ->
                        if (f.isDirectory) {
                            val name = f.name.removeSuffix("/")
                            list.add(RemoteEntry(name, true))
                        }
                    }
                }
                SourceType.WEBDAV -> {
                    val sardine = OkHttpSardine()
                    sardine.setCredentials(config.user, config.password)
                    val host = config.host.removeSuffix("/")
                    val path = relativePath.removePrefix("/").removeSuffix("/")
                    val fullUrl = if (path.isNotEmpty()) "$host/$path/" else "$host/"

                    val resources = sardine.list(fullUrl)
                    resources.forEach { res ->
                        // 过滤掉当前目录本身
                        val resPath = res.path.toString()
                        val fullResUrl = if (resPath.startsWith("http")) resPath else "$host$resPath"
                        val isSelf = fullResUrl.trimEnd('/') == fullUrl.trimEnd('/')

                        if (!isSelf && res.isDirectory) {
                            list.add(RemoteEntry(res.name, true))
                        }
                    }
                }
                else -> { /* Local not supported here */ }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e // 抛出异常供 UI 显示
        }
        list.sortedBy { it.name }
    }

    suspend fun fetchImageData(context: Context, item: ImageItem): Any? = withContext(Dispatchers.IO) {
        try {
            when (item.sourceConfig.type) {
                SourceType.LOCAL -> {
                    if (item.uri.startsWith("content://")) Uri.parse(item.uri)
                    else File(item.uri)
                }
                else -> {
                    // [修改] 使用包含属性的新 Key 生成逻辑
                    val cacheKey = CacheManager.generateKey(item)

                    if (CacheManager.hasFile(context, cacheKey)) {
                        val file = CacheManager.getFile(context, cacheKey)
                        if (isValidImageFile(file)) return@withContext file
                        else file.delete()
                    }

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

    private fun downloadAndTranscodeOptimized(context: Context, item: ImageItem, key: String): File? {
        val tempRawFile = File(context.cacheDir, "${key}_raw.tmp")
        val tempProcessedFile = File(context.cacheDir, "${key}_processed.tmp")
        var bitmap: Bitmap? = null

        try {
            getStream(item)?.use { input ->
                FileOutputStream(tempRawFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempRawFile.exists() || tempRawFile.length() == 0L) return null

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempRawFile.absolutePath, options)

            options.inSampleSize = calculateInSampleSize(options, CACHE_MAX_DIMENSION, CACHE_MAX_DIMENSION)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            bitmap = BitmapFactory.decodeFile(tempRawFile.absolutePath, options)

            if (bitmap != null) {
                FileOutputStream(tempProcessedFile).use { outStream ->
                    val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    }
                    bitmap?.compress(format, CACHE_COMPRESS_QUALITY, outStream)
                }

                bitmap?.recycle()
                bitmap = null
                return CacheManager.saveFile(context, key, tempProcessedFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcode failed: ${e.message}")
            e.printStackTrace()
        } finally {
            bitmap?.recycle()
            if (tempRawFile.exists()) tempRawFile.delete()
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

    // --- 扫描逻辑 ---

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
                        if (file.isFile && isImage(file.name)) {
                            // [修改] 捕获文件属性
                            images.add(ImageItem(file.absolutePath, config, file.lastModified(), file.length()))
                        }
                    }
                } else {
                    root.listFiles()?.forEach { file ->
                        if (file.isFile && isImage(file.name)) {
                            // [修改] 捕获文件属性
                            images.add(ImageItem(file.absolutePath, config, file.lastModified(), file.length()))
                        }
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
                // DocumentFile 获取属性较慢，这里尽量获取
                list.add(ImageItem(file.uri.toString(), config, file.lastModified(), file.length()))
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
                        // [修改] 捕获 SMB 文件属性
                        list.add(ImageItem(f.path, config, f.lastModified, f.length()))
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
                        // [修改] 捕获 WebDAV 文件属性
                        val lastMod = res.modified?.time ?: 0L
                        val size = res.contentLength ?: 0L
                        images.add(ImageItem(imgUrl, config, lastMod, size))
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
                        // [修改] 递归时捕获属性
                        val lastMod = res.modified?.time ?: 0L
                        val size = res.contentLength ?: 0L
                        list.add(ImageItem(fullResUrl, config, lastMod, size))
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