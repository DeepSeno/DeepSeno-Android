package com.enmooy.deepseno.ui.screen.capture

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.*
import java.io.File

@Composable
fun MultiPhotoCaptureScreen(
    onDone: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalStrings.current
    val capturedPhotos = remember { mutableStateListOf<String>() }

    // TODO: Integrate CameraX preview and image capture
    // For now, show a placeholder with camera permission request

    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // Camera preview area (placeholder)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (!hasCameraPermission) {
                Text(
                    t.cameraRequired,
                    color = TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "CameraX Preview",
                    color = TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                // TODO: Add CameraX PreviewView here
            }
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close", tint = TextPrimary)
            }

            if (capturedPhotos.isNotEmpty()) {
                Text(
                    "${capturedPhotos.size} ${t.photoCount}",
                    color = TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }

            if (capturedPhotos.isNotEmpty()) {
                IconButton(onClick = { onDone(capturedPhotos.toList()) }) {
                    Icon(Icons.Default.Check, "Done", tint = AccentGreen)
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Thumbnail strip
            if (capturedPhotos.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(capturedPhotos) { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, BgTertiary, RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }

            // Capture button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(3.dp, TextPrimary, CircleShape)
                    .clickable {
                        // TODO: Capture photo via CameraX ImageCapture
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(TextPrimary),
                )
            }
        }
    }
}
