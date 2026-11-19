package com.wellsey.burrow.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wellsey.burrow.data.FileEntry
import com.wellsey.burrow.data.FileThumb
import com.wellsey.burrow.data.hasThumbnail
import com.wellsey.burrow.util.formatShortDate
import com.wellsey.burrow.util.formatSize
import com.wellsey.burrow.util.typeStyle

/** The file's type icon, replaced by a real thumbnail for photos, videos, music and APKs. */
@Composable
fun FileIcon(entry: FileEntry, size: Dp, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    val style = typeStyle(entry)
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(style.tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(style.icon, null, tint = style.tint, modifier = Modifier.size(size * 0.55f))
        if (hasThumbnail(entry)) {
            AsyncImage(
                model = FileThumb(entry.path, entry.lastModified),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun subtitle(entry: FileEntry, showPath: Boolean = false): String {
    val main = if (entry.isDir) {
        when (entry.childCount) {
            -1 -> formatShortDate(entry.lastModified)
            0 -> "Empty"
            1 -> "1 item"
            else -> "${entry.childCount} items"
        }
    } else "${formatSize(entry.size)} · ${formatShortDate(entry.lastModified)}"
    return if (showPath) "$main · ${entry.file.parent?.substringAfter("/emulated/0") ?: ""}" else main
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showPath: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box {
            FileIcon(entry, 44.dp)
            if (selectionMode) SelectMark(selected, Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle(entry, showPath),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileCell(
    entry: FileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            FileIcon(entry, size = 400.dp, modifier = Modifier.fillMaxSize(), corner = 14.dp)
            if (selectionMode) SelectMark(selected, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Text(
            entry.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(34.dp),
        )
    }
}

@Composable
fun SelectMark(selected: Boolean, modifier: Modifier = Modifier) {
    Icon(
        if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
        contentDescription = if (selected) "Selected" else "Not selected",
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .size(20.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
    )
}
