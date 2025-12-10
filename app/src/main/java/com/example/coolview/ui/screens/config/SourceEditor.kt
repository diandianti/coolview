package com.example.coolview.ui.screens.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coolview.model.SourceConfig
import com.example.coolview.model.SourceType
import java.util.UUID

@Composable
fun EditorPanel(
    editingSourceId: String?, accentColor: Color, clearForm: () -> Unit,
    selectedTabIndex: Int, onTabSelected: (Int) -> Unit,
    pathInput: String, hostInput: String, shareInput: String, userInput: String, passInput: String, subPathInput: String,
    recursiveInput: Boolean,
    onPathChange: (String) -> Unit, onHostChange: (String) -> Unit, onShareChange: (String) -> Unit,
    onUserChange: (String) -> Unit, onPassChange: (String) -> Unit, onSubPathChange: (String) -> Unit,
    onRecursiveChange: (Boolean) -> Unit,
    launchPicker: () -> Unit,
    launchRemotePicker: () -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // --- 标题栏 ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (editingSourceId != null) "Editing Source" else "Add New Source",
                color = if (editingSourceId != null) accentColor else Color.LightGray,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            if (editingSourceId != null) TextButton(onClick = clearForm) { Text("Cancel", color = Color.Gray) }
        }

        // --- 标签页 (Tabs) ---
        val tabs = listOf("Local", "SMB", "WebDAV")
        TabRow(
            selectedTabIndex = selectedTabIndex, containerColor = Color.Transparent, contentColor = accentColor,
            indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]), color = accentColor) }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTabIndex == index, onClick = { onTabSelected(index) }, text = { Text(title, color = if (selectedTabIndex == index) accentColor else Color.Gray) })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 表单内容 ---
        when (selectedTabIndex) {
            0 -> {
                // [修改] Local 模式：移除外部 Button，将选择操作集成到 TextField 的 trailingIcon 中
                OutlinedTextField(
                    value = pathInput,
                    onValueChange = {}, // 本地路径通常由选择器只读填充
                    label = { Text("Local Folder Path") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true, // 禁止手动输入，防止 URI 格式错误
                    colors = textFieldColors(),
                    trailingIcon = {
                        IconButton(onClick = launchPicker) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = "Select Local Folder",
                                tint = accentColor
                            )
                        }
                    }
                )
            }
            1, 2 -> {
                // SMB / WebDAV 模式
                val isSmb = selectedTabIndex == 1
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = hostInput, onValueChange = onHostChange, label = { Text(if (isSmb) "Server IP" else "WebDAV URL") }, modifier = Modifier.weight(1f), colors = textFieldColors())
                    if (isSmb) OutlinedTextField(value = shareInput, onValueChange = onShareChange, label = { Text("Share Name") }, modifier = Modifier.weight(1f), colors = textFieldColors())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = userInput, onValueChange = onUserChange, label = { Text("Username") }, modifier = Modifier.weight(1f), colors = textFieldColors())
                    OutlinedTextField(value = passInput, onValueChange = onPassChange, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f), colors = textFieldColors())
                }

                // [修改] 远程子路径：同样使用集成式按钮风格
                OutlinedTextField(
                    value = subPathInput,
                    onValueChange = onSubPathChange,
                    label = { Text("Subfolder Path") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                    trailingIcon = {
                        IconButton(onClick = launchRemotePicker) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = "Select Remote Folder",
                                tint = accentColor
                            )
                        }
                    }
                )
            }
        }

        // --- 递归扫描选项 ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRecursiveChange(!recursiveInput) }
                .padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = recursiveInput,
                onCheckedChange = onRecursiveChange,
                colors = CheckboxDefaults.colors(checkedColor = accentColor)
            )
            Text("Scan subfolders recursively", color = Color.LightGray, fontSize = 14.sp)
        }

        // --- 保存按钮 ---
        Button(
            onClick = onSave,
            modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (editingSourceId != null) accentColor else Color(0x33FFFFFF),
                contentColor = if (editingSourceId != null) Color.Black else accentColor
            ),
            border = if (editingSourceId == null) androidx.compose.foundation.BorderStroke(1.dp, accentColor) else null
        ) {
            Text(if (editingSourceId != null) "✓ Update Source" else "+ Add to List")
        }
    }
}

@Composable
fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color(0x44FFFFFF),
    focusedLabelColor = Color(0xFF00E5FF), unfocusedLabelColor = Color.Gray,
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    cursorColor = Color(0xFF00E5FF)
)

fun buildConfig(
    selectedTabIndex: Int, editingSourceId: String?,
    pathInput: String, hostInput: String, shareInput: String,
    userInput: String, passInput: String, subPathInput: String,
    recursiveInput: Boolean
): SourceConfig? {
    val id = editingSourceId ?: UUID.randomUUID().toString()
    return when (selectedTabIndex) {
        0 -> if (pathInput.isNotBlank()) SourceConfig(
            id = id, type = SourceType.LOCAL, path = pathInput, recursive = recursiveInput
        ) else null
        1 -> if (hostInput.isNotBlank() && shareInput.isNotBlank())
            SourceConfig(
                id = id, type = SourceType.SMB, host = hostInput, share = shareInput,
                user = userInput, password = passInput, path = subPathInput, recursive = recursiveInput
            ) else null
        else -> if (hostInput.isNotBlank())
            SourceConfig(
                id = id, type = SourceType.WEBDAV, host = hostInput,
                user = userInput, password = passInput, path = subPathInput, recursive = recursiveInput
            ) else null
    }
}