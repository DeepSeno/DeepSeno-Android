package com.enmooy.deepseno.ui.screen.capture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.*

enum class MediaPickerOption {
    CAMERA,
    CHOOSE_IMAGES,
    RECORD_VIDEO,
    CHOOSE_VIDEO,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPickerSheet(
    onDismiss: () -> Unit,
    onOptionSelected: (MediaPickerOption) -> Unit,
) {
    val t = LocalStrings.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgSecondary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            MediaPickerItem(
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AccentGreen) },
                label = t.camera,
                onClick = {
                    onOptionSelected(MediaPickerOption.CAMERA)
                    onDismiss()
                },
            )

            MediaPickerItem(
                icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = AccentBlue) },
                label = t.chooseImages,
                onClick = {
                    onOptionSelected(MediaPickerOption.CHOOSE_IMAGES)
                    onDismiss()
                },
            )

            MediaPickerItem(
                icon = { Icon(Icons.Default.Videocam, contentDescription = null, tint = AccentAmber) },
                label = t.recordVideo,
                onClick = {
                    onOptionSelected(MediaPickerOption.RECORD_VIDEO)
                    onDismiss()
                },
            )

            MediaPickerItem(
                icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = AccentBlue) },
                label = t.chooseVideo,
                onClick = {
                    onOptionSelected(MediaPickerOption.CHOOSE_VIDEO)
                    onDismiss()
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            MediaPickerItem(
                icon = { Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary) },
                label = t.cancel,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun MediaPickerItem(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
        )
    }
}
