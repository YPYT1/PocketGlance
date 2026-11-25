package com.opencapture.pocketglance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.opencapture.pocketglance.gallery.GalleryItem
import com.opencapture.pocketglance.gallery.MediaKind

@Composable
fun GalleryScreen(
    items: List<GalleryItem>,
    filter: MediaKind?,
    onFilter: (MediaKind?) -> Unit,
    onBack: () -> Unit,
) {
    var preview by remember { mutableStateOf<GalleryItem?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlanceColors.canvas)
                .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("相册", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text("返回监视") }
        }

        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip("全部", selected = filter == null) { onFilter(null) }
            FilterChip("图片", selected = filter == MediaKind.Image) { onFilter(MediaKind.Image) }
            FilterChip("视频", selected = filter == MediaKind.Video) { onFilter(MediaKind.Video) }
            FilterChip("动图", selected = filter == MediaKind.Motion) { onFilter(MediaKind.Motion) }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.id }) { item ->
                GalleryTile(item) { preview = item }
            }
        }
    }

    preview?.let { item ->
        Dialog(onDismissRequest = { preview = null }) {
            Column(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(GlanceColors.surface)
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("预览", style = MaterialTheme.typography.titleLarge)
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                Text(GlanceCopy.kind(item.kind), style = MaterialTheme.typography.bodyMedium)
                Text(
                    when (item.kind) {
                        MediaKind.Image -> "静态图片"
                        MediaKind.Video -> "视频（只读预览）"
                        MediaKind.Motion -> "动图循环预览"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { preview = null }) { Text("关闭") }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) GlanceColors.pear else GlanceColors.surface)
                .border(1.dp, GlanceColors.hairline, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun GalleryTile(item: GalleryItem, onClick: () -> Unit) {
    val tagColor =
        when (item.kind) {
            MediaKind.Image -> GlanceColors.pear
            MediaKind.Video -> GlanceColors.azure
            MediaKind.Motion -> GlanceColors.coral
        }
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFEFE8DC))
                .clickable(onClick = onClick),
    ) {
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = GlanceColors.inkMuted,
            modifier = Modifier.align(Alignment.Center).padding(8.dp),
        )
        Text(
            GlanceCopy.kind(item.kind),
            color = GlanceColors.ink,
            style = MaterialTheme.typography.labelLarge,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tagColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
