package com.example.coolview.ui.screens.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coolview.model.SourceConfig
import com.example.coolview.model.SourceType

@Composable
fun SourcesList(
    sources: List<SourceConfig>,
    editingId: String?,
    onEdit: (SourceConfig) -> Unit,
    onDelete: (SourceConfig) -> Unit
) {
    if (sources.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No sources in this scene.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sources) { source ->
                SourceItem(source, source.id == editingId, { onEdit(source) }, { onDelete(source) })
            }
        }
    }
}

@Composable
fun SourceItem(source: SourceConfig, isEditing: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isEditing) Color(0x5500E5FF) else Color(0x33000000), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when (source.type) {
            SourceType.LOCAL -> "📂"
            SourceType.SMB -> "📁"
            SourceType.WEBDAV -> "🌐"
        }
        Text(icon, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.type.name, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (source.recursive) {
                    Spacer(Modifier.width(8.dp))
                    Text("RECURSIVE", color = Color.Green, fontSize = 10.sp)
                }
            }
            val desc = when (source.type) {
                SourceType.LOCAL -> source.path
                SourceType.SMB -> "${source.host} / ${source.share}"
                SourceType.WEBDAV -> source.host
            }
            Text(desc, color = Color.White, fontSize = 14.sp, maxLines = 1)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = Color.LightGray) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Close, "Remove", tint = Color(0xFFFF4444)) }
    }
}