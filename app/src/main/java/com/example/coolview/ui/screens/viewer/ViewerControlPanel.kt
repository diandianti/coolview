package com.example.coolview.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coolview.viewmodel.VisualSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerControlPanel(
    settings: VisualSettings,
    onSettingsChanged: (VisualSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .width(40.dp)
                .height(4.dp)
                .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                .align(Alignment.CenterHorizontally)
        )

        Text("Display Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF), modifier = Modifier.padding(bottom = 16.dp))

        // [新增] 屏幕常亮开关
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Keep Screen On", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), color = Color.White)
            Switch(
                checked = settings.keepScreenOn,
                onCheckedChange = { onSettingsChanged(settings.copy(keepScreenOn = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00E5FF),
                    checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                )
            )
        }

        Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(bottom = 16.dp))

        Text("Layout Pattern", style = MaterialTheme.typography.labelLarge)
        val modes = listOf("Grid", "Mosaic", "Mondrian", "Brick Wall", "Pillars", "Waterfall", "Alternate", "Hero Focus")
        var expanded by remember { mutableStateOf(false) }

        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(modes.getOrElse(settings.modeIndex) { "Unknown" })
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF222222))
            ) {
                modes.forEachIndexed { index, name ->
                    DropdownMenuItem(
                        text = { Text(name, color = Color.White) },
                        onClick = {
                            onSettingsChanged(settings.copy(modeIndex = index))
                            expanded = false
                        }
                    )
                }
            }
        }

        if (settings.modeIndex in listOf(1, 2, 4)) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chaos: ${(settings.layoutChaos * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            }
            Slider(
                value = settings.layoutChaos,
                onValueChange = { onSettingsChanged(settings.copy(layoutChaos = it)) },
                valueRange = 0f..0.8f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
            )
        }

        if (settings.modeIndex == 5 || settings.modeIndex == 6) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Scroll Speed", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            }
            Slider(
                value = settings.autoScrollSpeed,
                onValueChange = { onSettingsChanged(settings.copy(autoScrollSpeed = it)) },
                valueRange = -5f..5f,
                steps = 9,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Breathing Animation", style = MaterialTheme.typography.labelLarge, color = Color(0xFF00E5FF))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Amplitude: ${(settings.breathScale * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
        Slider(
            value = settings.breathScale,
            onValueChange = { onSettingsChanged(settings.copy(breathScale = it)) },
            valueRange = 1.0f..1.5f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cycle Time: ${settings.breathDuration / 1000}s", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
        Slider(
            value = settings.breathDuration.toFloat(),
            onValueChange = { onSettingsChanged(settings.copy(breathDuration = it.toLong())) },
            valueRange = 5000f..30000f,
            steps = 4,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Columns: ${settings.colCount}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        }
        Slider(
            value = settings.colCount.toFloat(),
            onValueChange = { onSettingsChanged(settings.copy(colCount = it.toInt())) },
            valueRange = 1f..8f,
            steps = 6,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
        )

        if (settings.modeIndex != 5 && settings.modeIndex != 6) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rows: ${settings.rowCount}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            }
            Slider(
                value = settings.rowCount.toFloat(),
                onValueChange = { onSettingsChanged(settings.copy(rowCount = it.toInt())) },
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Switch Speed: ${settings.refreshInterval / 1000}s", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = settings.refreshInterval.toFloat(),
            onValueChange = { onSettingsChanged(settings.copy(refreshInterval = it.toLong())) },
            valueRange = 2000f..30000f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
        )

        Text("Spacing: ${settings.gridSpacing}dp", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = settings.gridSpacing.toFloat(),
            onValueChange = { onSettingsChanged(settings.copy(gridSpacing = it.toInt())) },
            valueRange = 0f..16f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
        )
    }
}