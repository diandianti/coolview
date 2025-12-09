package com.example.coolview.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.example.coolview.data.CacheManager
import com.example.coolview.data.ClientFactory
import com.example.coolview.model.ImageItem
import com.example.coolview.model.Scene
import com.example.coolview.model.SourceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedDeque

@Serializable
data class VisualSettings(
    val modeIndex: Int = 0,
    val colCount: Int = 3,
    val rowCount: Int = 4,
    val gridSpacing: Int = 2,
    val breathScale: Float = 1.1f,
    val breathDuration: Long = 10000L,
    val brightness: Float = 1.0f,
    val refreshInterval: Long = 5000L,
    val layoutChaos: Float = 0.2f,
    val autoScrollSpeed: Float = 2.0f,
    // [新增] 屏幕常亮设置，默认为开启
    val keepScreenOn: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _allImages = MutableStateFlow<List<ImageItem>>(emptyList())
    val allImages = _allImages.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _visualSettings = MutableStateFlow(VisualSettings())
    val visualSettings = _visualSettings.asStateFlow()

    private val _scenes = MutableStateFlow<List<Scene>>(emptyList())
    val scenes = _scenes.asStateFlow()

    private val _currentSceneId = MutableStateFlow<String>("")
    val currentSceneId = _currentSceneId.asStateFlow()

    private val imageQueue = ConcurrentLinkedDeque<ImageItem>()
    private val MIN_BUFFER_SIZE = 10

    private val prefs by lazy {
        application.getSharedPreferences("coolview_prefs", Context.MODE_PRIVATE)
    }
    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadData()
        loadSettings()
        startPrefetchLoop()
    }

    private fun loadData() {
        val savedScenesJson = prefs.getString("saved_scenes", null)

        if (savedScenesJson != null) {
            try {
                val list = json.decodeFromString<List<Scene>>(savedScenesJson)
                _scenes.value = list
                if (list.isNotEmpty()) {
                    _currentSceneId.value = list.first().id
                } else {
                    createDefaultScene()
                }
            } catch (e: Exception) { createDefaultScene() }
        } else {
            val oldSourcesJson = prefs.getString("saved_sources", "[]") ?: "[]"
            try {
                val oldSources = json.decodeFromString<List<SourceConfig>>(oldSourcesJson)
                val defaultScene = Scene(name = "Default", sources = oldSources)
                _scenes.value = listOf(defaultScene)
                _currentSceneId.value = defaultScene.id
                saveScenesLocally()
            } catch (e: Exception) {
                createDefaultScene()
            }
        }
    }

    private fun createDefaultScene() {
        val newScene = Scene(name = "Default", sources = emptyList())
        _scenes.value = listOf(newScene)
        _currentSceneId.value = newScene.id
        saveScenesLocally()
    }

    private fun saveScenesLocally() {
        prefs.edit().putString("saved_scenes", json.encodeToString(_scenes.value)).apply()
    }

    fun selectScene(sceneId: String) {
        if (_scenes.value.any { it.id == sceneId }) {
            _currentSceneId.value = sceneId
        }
    }

    fun addScene(name: String) {
        val newScene = Scene(name = name)
        _scenes.value = _scenes.value + newScene
        _currentSceneId.value = newScene.id
        saveScenesLocally()
    }

    fun removeScene(sceneId: String) {
        if (_scenes.value.size <= 1) return
        val newList = _scenes.value.filter { it.id != sceneId }
        _scenes.value = newList
        if (_currentSceneId.value == sceneId) {
            _currentSceneId.value = newList.first().id
        }
        saveScenesLocally()
    }

    fun getCurrentSceneSources(): List<SourceConfig> {
        return _scenes.value.find { it.id == _currentSceneId.value }?.sources ?: emptyList()
    }

    fun addSourceToCurrentScene(config: SourceConfig) {
        val currentId = _currentSceneId.value
        _scenes.value = _scenes.value.map { scene ->
            if (scene.id == currentId) {
                scene.copy(sources = scene.sources + config)
            } else scene
        }
        saveScenesLocally()
    }

    fun removeSourceFromCurrentScene(config: SourceConfig) {
        val currentId = _currentSceneId.value
        _scenes.value = _scenes.value.map { scene ->
            if (scene.id == currentId) {
                scene.copy(sources = scene.sources.filter { it.id != config.id })
            } else scene
        }
        saveScenesLocally()
    }

    fun updateSourceInCurrentScene(config: SourceConfig) {
        val currentId = _currentSceneId.value
        _scenes.value = _scenes.value.map { scene ->
            if (scene.id == currentId) {
                scene.copy(sources = scene.sources.map { if (it.id == config.id) config else it })
            } else scene
        }
        saveScenesLocally()
    }

    private fun startPrefetchLoop() {
        repeat(2) {
            viewModelScope.launch(Dispatchers.IO) {
                val context = getApplication<Application>().applicationContext
                while (isActive) {
                    val currentList = _allImages.value
                    if (currentList.isNotEmpty() && imageQueue.size < MIN_BUFFER_SIZE) {
                        val nextItem = currentList.random()
                        val result = ClientFactory.fetchImageData(context, nextItem)
                        if (result != null) {
                            val request = ImageRequest.Builder(context).data(result).build()
                            context.imageLoader.enqueue(request)
                            imageQueue.add(nextItem)
                        } else {
                            delay(200)
                        }
                    } else {
                        delay(1000)
                    }
                }
            }
        }
    }

    fun getNextBufferedImage(): ImageItem? {
        return imageQueue.poll() ?: if (_allImages.value.isNotEmpty()) _allImages.value.random() else null
    }

    fun getRandomCachedImage(context: Context): ImageItem? {
        val list = _allImages.value
        if (list.isEmpty()) return null
        repeat(20) {
            val item = list.random()
            val key = CacheManager.generateKey(item.uri)
            if (CacheManager.hasFile(context, key)) {
                return item
            }
        }
        return list.random()
    }

    private fun loadSettings() {
        val jsonStr = prefs.getString("visual_settings", null)
        if (jsonStr != null) {
            try { _visualSettings.value = json.decodeFromString(jsonStr) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateSettings(newSettings: VisualSettings) {
        _visualSettings.value = newSettings
        prefs.edit().putString("visual_settings", json.encodeToString(newSettings)).apply()
    }

    fun startSession() {
        val currentSources = getCurrentSceneSources()

        viewModelScope.launch {
            _isScanning.value = true
            imageQueue.clear()
            val foundImages = mutableListOf<ImageItem>()
            val context = getApplication<Application>().applicationContext

            withContext(Dispatchers.IO) {
                currentSources.forEach { config ->
                    try {
                        val result = ClientFactory.scanImages(context, config)
                        foundImages.addAll(result)
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }

            _allImages.value = foundImages.shuffled()
            _isScanning.value = false
        }
    }
}