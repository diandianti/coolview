package com.example.coolview.ui.screens.config

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coolview.model.Scene

@Composable
fun SceneSelector(
    scenes: List<Scene>,
    currentSceneId: String,
    onSceneSelected: (String) -> Unit,
    onAddScene: () -> Unit,
    onDeleteScene: (String) -> Unit,
    accentColor: Color
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SCENES", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
        }
        ScrollableTabRow(
            selectedTabIndex = scenes.indexOfFirst { it.id == currentSceneId }.coerceAtLeast(0),
            containerColor = Color.Transparent,
            contentColor = Color.White,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                val index = scenes.indexOfFirst { it.id == currentSceneId }.coerceAtLeast(0)
                if (index < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[index]), color = accentColor)
                }
            },
            divider = {}
        ) {
            scenes.forEach { scene ->
                val isSelected = scene.id == currentSceneId
                Tab(
                    selected = isSelected,
                    onClick = { onSceneSelected(scene.id) },
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = scene.name,
                            color = if (isSelected) accentColor else Color.LightGray,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (scenes.size > 1 && isSelected) {
                            IconButton(onClick = { onDeleteScene(scene.id) }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                                Icon(Icons.Default.Close, "Delete", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Tab(selected = false, onClick = onAddScene) {
                Icon(Icons.Default.Add, "Add Scene", tint = Color.Gray, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
fun NewSceneDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Scene Name") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}