package com.enmooy.deepseno.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.enmooy.deepseno.data.local.entity.CaptureItemEntity
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.service.CaptureQueue
import com.enmooy.deepseno.ui.screen.common.EmptyStateView
import com.enmooy.deepseno.ui.screen.common.StatusBadge
import com.enmooy.deepseno.ui.theme.AccentRed
import com.enmooy.deepseno.ui.theme.BgPrimary
import com.enmooy.deepseno.ui.theme.BgSecondary
import com.enmooy.deepseno.ui.theme.TextPrimary
import com.enmooy.deepseno.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private fun iconForType(type: String): ImageVector = when (type.lowercase()) {
    "image", "images" -> Icons.Default.Image
    "video" -> Icons.Default.Videocam
    "audio", "voice" -> Icons.Default.AudioFile
    "text" -> Icons.Default.TextSnippet
    else -> Icons.Default.Description
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueManagementScreen(
    captureQueue: CaptureQueue,
    onDismiss: () -> Unit,
) {
    val t = LocalStrings.current
    val scope = rememberCoroutineScope()
    val items by captureQueue.getItems().collectAsState(initial = emptyList())
    var showMenu by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        TopAppBar(
            title = {
                Text(
                    t.uploadQueue,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = t.cancel, tint = TextPrimary)
                }
            },
            actions = {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = TextPrimary)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(t.retryAllFailed) },
                        onClick = {
                            showMenu = false
                            scope.launch { captureQueue.retryAndProcess() }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(t.clearAll, color = AccentRed) },
                        onClick = {
                            showMenu = false
                            scope.launch { captureQueue.clearAll() }
                        },
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary),
        )

        if (items.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Description,
                title = t.queueEmpty,
                subtitle = t.noPendingUploads,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    QueueItemRow(item)
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(item: CaptureItemEntity) {
    val t = LocalStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSecondary, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = iconForType(item.type),
            contentDescription = item.type,
            tint = TextSecondary,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.type.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                if (item.retries > 0) {
                    Text(
                        text = " · ${item.retries}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        StatusBadge(status = item.status)
    }
}
