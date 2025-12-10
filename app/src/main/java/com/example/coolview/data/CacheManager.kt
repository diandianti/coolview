package com.example.coolview.data

import android.content.Context
import android.util.Log
import com.example.coolview.model.ImageItem
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object CacheManager {
    // 缓存上限 1GB
    private const val MAX_CACHE_SIZE = 1024 * 1024 * 1024L
    private const val TAG = "CacheManager"

    private val lruIndex = java.util.LinkedHashMap<String, Long>(0, 0.75f, true)
    private var currentCacheSize = 0L

    private val lock = ReentrantReadWriteLock()
    private var isInitialized = false

    private val cleanupExecutor = Executors.newSingleThreadExecutor()

    private fun ensureInitialized(context: Context) {
        if (isInitialized) return
        lock.write {
            if (isInitialized) return
            try {
                val cacheDir = context.cacheDir
                val files = cacheDir.listFiles() ?: return
                files.filter { !it.name.endsWith(".tmp") }
                    .sortedBy { it.lastModified() }
                    .forEach { file ->
                        lruIndex[file.name] = file.length()
                        currentCacheSize += file.length()
                    }
                isInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing cache", e)
            }
        }
    }

    // [修改] 核心逻辑：基于 URI + 文件属性 生成 Hash，不包含 sceneName
    fun generateKey(item: ImageItem): String {
        // 组合字符串：URI + LastModified + FileSize
        val rawKey = "${item.uri}|${item.lastModified}|${item.fileSize}"
        return try {
            val digest = MessageDigest.getInstance("MD5")
            digest.update(rawKey.toByteArray())
            val messageDigest = digest.digest()
            val hexString = StringBuilder()
            for (b in messageDigest) {
                hexString.append(String.format("%02x", b))
            }
            hexString.toString()
        } catch (e: Exception) {
            rawKey.hashCode().toString()
        }
    }

    // 保留旧方法以兼容（如果需要），或者主要用于纯 URI 的场景
    fun generateKey(uri: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            digest.update(uri.toByteArray())
            val messageDigest = digest.digest()
            val hexString = StringBuilder()
            for (b in messageDigest) {
                hexString.append(String.format("%02x", b))
            }
            hexString.toString()
        } catch (e: Exception) {
            uri.hashCode().toString()
        }
    }

    fun getFile(context: Context, key: String): File {
        return File(context.cacheDir, key)
    }

    fun hasFile(context: Context, key: String): Boolean {
        ensureInitialized(context)
        lock.read {
            if (lruIndex.containsKey(key)) return true
        }
        val file = getFile(context, key)
        if (file.exists()) {
            lock.write {
                lruIndex[key] = file.length()
                currentCacheSize += file.length()
            }
            return true
        }
        return false
    }

    fun saveStream(context: Context, key: String, input: InputStream): File? {
        ensureInitialized(context)
        val tempFile = File(context.cacheDir, "$key.tmp")
        try {
            tempFile.outputStream().use { output -> input.copyTo(output) }
            return saveFile(context, key, tempFile)
        } catch (e: Exception) {
            tempFile.delete()
            return null
        }
    }

    fun saveFile(context: Context, key: String, tempFile: File): File? {
        ensureInitialized(context)
        val targetFile = getFile(context, key)

        lock.write {
            if (targetFile.exists()) {
                currentCacheSize -= targetFile.length()
                targetFile.delete()
            }

            if (tempFile.renameTo(targetFile)) {
                val newSize = targetFile.length()
                lruIndex[key] = newSize
                currentCacheSize += newSize

                if (currentCacheSize > MAX_CACHE_SIZE) {
                    cleanupExecutor.submit { trimCache(context) }
                }
                return targetFile
            } else {
                try {
                    tempFile.inputStream().use { input ->
                        targetFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val newSize = targetFile.length()
                    lruIndex[key] = newSize
                    currentCacheSize += newSize
                    return targetFile
                } catch (e: Exception) {
                    return null
                } finally {
                    tempFile.delete()
                }
            }
        }
        return null
    }

    private fun trimCache(context: Context) {
        lock.write {
            if (currentCacheSize <= MAX_CACHE_SIZE) return
            val iterator = lruIndex.iterator()
            while (iterator.hasNext() && currentCacheSize > MAX_CACHE_SIZE) {
                val entry = iterator.next()
                val fileToDelete = File(context.cacheDir, entry.key)
                if (fileToDelete.delete() || !fileToDelete.exists()) {
                    currentCacheSize -= entry.value
                    iterator.remove()
                }
            }
        }
    }
}