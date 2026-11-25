package com.opencapture.pocketglance.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun MonitorBackButton(onClick: () -> Unit, label: String = "返回监视界面") {
    TextButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = GlanceColors.azure, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, color = GlanceColors.azure)
    }
}
