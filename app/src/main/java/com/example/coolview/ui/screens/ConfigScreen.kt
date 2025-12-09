package com.example.coolview.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coolview.model.SourceConfig
import com.example.coolview.model.SourceType
import com.example.coolview.viewmodel.MainViewModel
import com.example.coolview.ui.screens.config.*

@Composable
fun ConfigScreen(
    viewModel: MainViewModel,
    onStartClicked: () -> Unit,
    onAboutClicked: () -> Unit // [新增参数]
) {
    val scenes by viewModel.scenes.collectAsState()
    val currentSceneId by viewModel.currentSceneId.collectAsState()

    // 计算当前显示的源列表
    val currentSources = remember(scenes, currentSceneId) {
        scenes.find { it.id == currentSceneId }?.sources ?: emptyList()
    }

    // UI State
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var editingSourceId by remember { mutableStateOf<String?>(null) }
    var showNewSceneDialog by remember { mutableStateOf(false) }

    var pathInput by remember { mutableStateOf("") }
    var hostInput by remember { mutableStateOf("") }
    var shareInput by remember { mutableStateOf("") }
    var userInput by remember { mutableStateOf("") }
    var passInput by remember { mutableStateOf("") }
    var subPathInput by remember { mutableStateOf("/") }
    var recursiveInput by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val accentColor = Color(0xFF00E5FF)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun clearForm() {
        pathInput = ""
        hostInput = ""
        shareInput = ""
        userInput = ""
        passInput = ""
        subPathInput = "/"
        recursiveInput = true
        editingSourceId = null
    }

    fun startEditing(source: SourceConfig) {
        editingSourceId = source.id
        selectedTabIndex = when (source.type) {
            SourceType.LOCAL -> 0
            SourceType.SMB -> 1
            SourceType.WEBDAV -> 2
        }
        pathInput = source.path
        hostInput = source.host
        shareInput = source.share
        userInput = source.user
        passInput = source.password
        recursiveInput = source.recursive
        if (source.type != SourceType.LOCAL) {
            subPathInput = source.path
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            pathInput = it.toString()
        }
    }

    if (showNewSceneDialog) {
        NewSceneDialog(
            onDismiss = { showNewSceneDialog = false },
            onConfirm = { name ->
                viewModel.addScene(name)
                showNewSceneDialog = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
                )
            )
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            // [新增] 顶部标题栏和关于按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text(
                    text = "CoolView",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onAboutClicked) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About",
                        tint = accentColor
                    )
                }
            }

            SceneSelector(
                scenes = scenes,
                currentSceneId = currentSceneId,
                onSceneSelected = { viewModel.selectScene(it) },
                onAddScene = { showNewSceneDialog = true },
                onDeleteScene = { viewModel.removeScene(it) },
                accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLandscape) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Card(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF))
                        ) {
                            SourcesList(currentSources, editingSourceId, ::startEditing) { source ->
                                viewModel.removeSourceFromCurrentScene(source)
                                if (editingSourceId == source.id) clearForm()
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        StartButton(onStartClicked, currentSources.isNotEmpty(), accentColor)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        EditorPanel(
                            editingSourceId = editingSourceId,
                            accentColor = accentColor,
                            clearForm = ::clearForm,
                            selectedTabIndex = selectedTabIndex,
                            onTabSelected = { selectedTabIndex = it },
                            pathInput = pathInput, hostInput = hostInput, shareInput = shareInput,
                            userInput = userInput, passInput = passInput, subPathInput = subPathInput, recursiveInput = recursiveInput,
                            onPathChange = { pathInput = it }, onHostChange = { hostInput = it },
                            onShareChange = { shareInput = it }, onUserChange = { userInput = it },
                            onPassChange = { passInput = it }, onSubPathChange = { subPathInput = it }, onRecursiveChange = { recursiveInput = it },
                            launchPicker = { folderPicker.launch(null) },
                            onSave = {
                                val configToSave = buildConfig(selectedTabIndex, editingSourceId, pathInput, hostInput, shareInput, userInput, passInput, subPathInput, recursiveInput)
                                if (configToSave != null) {
                                    if (editingSourceId != null) viewModel.updateSourceInCurrentScene(configToSave)
                                    else viewModel.addSourceToCurrentScene(configToSave)
                                    clearForm()
                                }
                            }
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF))
                ) {
                    SourcesList(currentSources, editingSourceId, ::startEditing) { source ->
                        viewModel.removeSourceFromCurrentScene(source)
                        if (editingSourceId == source.id) clearForm()
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                    EditorPanel(
                        editingSourceId = editingSourceId,
                        accentColor = accentColor,
                        clearForm = ::clearForm,
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = { selectedTabIndex = it },
                        pathInput = pathInput, hostInput = hostInput, shareInput = shareInput,
                        userInput = userInput, passInput = passInput, subPathInput = subPathInput, recursiveInput = recursiveInput,
                        onPathChange = { pathInput = it }, onHostChange = { hostInput = it },
                        onShareChange = { shareInput = it }, onUserChange = { userInput = it },
                        onPassChange = { passInput = it }, onSubPathChange = { subPathInput = it }, onRecursiveChange = { recursiveInput = it },
                        launchPicker = { folderPicker.launch(null) },
                        onSave = {
                            val configToSave = buildConfig(selectedTabIndex, editingSourceId, pathInput, hostInput, shareInput, userInput, passInput, subPathInput, recursiveInput)
                            if (configToSave != null) {
                                if (editingSourceId != null) viewModel.updateSourceInCurrentScene(configToSave)
                                else viewModel.addSourceToCurrentScene(configToSave)
                                clearForm()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                StartButton(onStartClicked, currentSources.isNotEmpty(), accentColor)
            }
        }
    }
}

@Composable
fun StartButton(onClick: () -> Unit, enabled: Boolean, color: Color) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = Color.Gray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("🚀 START SESSION", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}