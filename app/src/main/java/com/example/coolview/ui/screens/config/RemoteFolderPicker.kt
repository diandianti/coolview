package com.example.coolview.ui.screens.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.coolview.data.RemoteEntry
import com.example.coolview.model.SourceConfig
import com.example.coolview.viewmodel.MainViewModel

@Composable
fun RemoteFolderPicker(
    viewModel: MainViewModel,
    initialConfig: SourceConfig,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // 确保路径以 / 结尾，且默认为 /
    var currentPath by remember { mutableStateOf(initialConfig.path.ifBlank { "/" }) }
    var folderList by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val accentColor = Color(0xFF00E5FF)

    // 加载文件夹列表
    LaunchedEffect(initialConfig, currentPath) {
        isLoading = true
        errorMessage = null
        try {
            // 使用临时配置和当前路径去获取列表
            folderList = viewModel.fetchRemoteFolders(initialConfig, currentPath)
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unknown error"
            folderList = emptyList()
        } finally {
            isLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // --- Header Area ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252525))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Select Remote Folder",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    // Path Display Bar
                    Surface(
                        color = Color(0xFF121212),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = currentPath,
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Divider(color = Color(0xFF333333))

                // --- List Content ---
                Box(modifier = Modifier.weight(1f).background(Color(0xFF181818))) {
                    if (isLoading) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = accentColor)
                            Spacer(Modifier.height(16.dp))
                            Text("Fetching folders...", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else if (errorMessage != null) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.Close, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Connection Failed",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = errorMessage ?: "",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            // ".." Back Item
                            // [修改] 只有当路径绝对为根路径 "/" 时才隐藏返回按钮
                            // 移除了对 initialConfig.path 的比较，允许在编辑模式下跳出当前目录
                            val isRootPath = currentPath == "/"

                            if (!isRootPath) {
                                item {
                                    FolderItem(
                                        name = ".. (Parent Directory)",
                                        isUpNavigation = true,
                                        onClick = {
                                            // 导航到父目录逻辑
                                            val trimmedPath = currentPath.trimEnd('/')
                                            val parent = trimmedPath.substringBeforeLast('/', "")
                                            currentPath = if (parent.isEmpty()) "/" else "$parent/"
                                        }
                                    )
                                }
                            }

                            if (folderList.isEmpty() && !isRootPath) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("No subfolders found", color = Color.DarkGray, fontSize = 12.sp)
                                    }
                                }
                            }

                            items(folderList) { entry ->
                                FolderItem(
                                    name = entry.name,
                                    isUpNavigation = false,
                                    onClick = {
                                        val newPath = if (currentPath.endsWith("/")) "${currentPath}${entry.name}/" else "${currentPath}/${entry.name}/"
                                        currentPath = newPath
                                    }
                                )
                            }
                        }
                    }
                }

                Divider(color = Color(0xFF333333))

                // --- Footer Actions ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252525))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { onConfirm(currentPath.trimEnd('/')) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Outlined.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Select Current", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderItem(
    name: String,
    isUpNavigation: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isUpNavigation) Icons.Default.ArrowBack else Icons.Default.Folder,
            contentDescription = null,
            tint = if (isUpNavigation) Color(0xFF00E5FF) else Color(0xFFFFC107),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = name,
            color = if (isUpNavigation) Color(0xFF00E5FF) else Color.White,
            fontSize = 16.sp,
            fontWeight = if (isUpNavigation) FontWeight.Bold else FontWeight.Normal
        )
    }
}