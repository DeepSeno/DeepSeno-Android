package com.enmooy.deepseno.ui.screen.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.service.CorrectionState
import com.enmooy.deepseno.service.TranscriptSegment
import com.enmooy.deepseno.ui.theme.*
import com.enmooy.deepseno.ui.viewmodel.AppState
import com.enmooy.deepseno.ui.viewmodel.CaptureViewModel

private val GlassBorder = Color.White.copy(alpha = 0.06f)

@Composable
fun CaptureScreen(
    appState: AppState = hiltViewModel(),
    captureViewModel: CaptureViewModel = hiltViewModel(),
) {
    val t = LocalStrings.current
    val context = LocalContext.current

    // Record-audio permission guard
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) captureViewModel.toggleRecording(savedLabel = t.recordingSaved)
    }

    val isConnected by appState.connectionActive.collectAsStateWithLifecycle()
    val host by appState.connectionHost.collectAsStateWithLifecycle()
    val isRecording by captureViewModel.recorder.isRecording.collectAsStateWithLifecycle()
    val isPaused by captureViewModel.recorder.isPaused.collectAsStateWithLifecycle()
    val durationMs by captureViewModel.recorder.durationMs.collectAsStateWithLifecycle()
    val audioLevel by captureViewModel.recorder.audioLevel.collectAsStateWithLifecycle()
    val showTextMemo by captureViewModel.showTextMemo.collectAsStateWithLifecycle()
    val showFilePicker by captureViewModel.showFilePicker.collectAsStateWithLifecycle()
    val errorMessage by captureViewModel.errorMessage.collectAsStateWithLifecycle()
    val pendingCount by appState.captureQueue.pendingCount.collectAsStateWithLifecycle(initialValue = 0)
    val failedCount by appState.captureQueue.failedCount.collectAsStateWithLifecycle(initialValue = 0)
    val isProcessing by appState.captureQueue.isProcessing.collectAsStateWithLifecycle()
    val bookmarks by captureViewModel.recorder.bookmarks.collectAsStateWithLifecycle()
    val bookmarkFeedback by captureViewModel.bookmarkFeedback.collectAsStateWithLifecycle()
    val isStreaming by captureViewModel.streamer.isStreaming.collectAsStateWithLifecycle()
    val liveText by captureViewModel.streamer.liveText.collectAsStateWithLifecycle()
    val transcriberActive by captureViewModel.transcriber.isActive.collectAsStateWithLifecycle()
    val transcriberSegments by captureViewModel.transcriber.segments.collectAsStateWithLifecycle()
    val toastMessage by captureViewModel.toastMessage.collectAsStateWithLifecycle()

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            captureViewModel.errorMessage.value = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // -- Top bar --
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionStatus(isConnected = isConnected, host = host)
                if (transcriberActive) {
                    TranscriberBadge()
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // -- Recording stage (central area) --
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Waveform
                AnimatedVisibility(
                    visible = isRecording,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    AudioWaveform(
                        level = audioLevel,
                        isPaused = isPaused,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(horizontal = 20.dp),
                    )
                }

                // Timer
                TimerDisplay(durationMs = durationMs, isRecording = isRecording)

                // Bookmark count
                if (bookmarks.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = AccentAmber,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = "${bookmarks.size}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = AccentAmber,
                        )
                    }
                }

                // Pause indicator
                if (isPaused) {
                    Text(
                        text = t.paused,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp,
                            fontSize = 11.sp,
                        ),
                        color = AccentAmber,
                    )
                }

                // Live transcript segments
                AnimatedVisibility(
                    visible = transcriberActive && transcriberSegments.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    LiveTranscriptList(segments = transcriberSegments)
                }

                // Bookmark feedback
                AnimatedVisibility(
                    visible = bookmarkFeedback,
                    enter = scaleIn(initialScale = 0.8f) + fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = AccentAmber,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = t.bookmarkAdded,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = AccentAmber,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // -- Control buttons --
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Pause/Resume button
                AnimatedVisibility(
                    visible = isRecording,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BgTertiary.copy(alpha = 0.8f))
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable { captureViewModel.togglePause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) t.a11yResumeRecording else t.a11yPauseRecording,
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                RecordButton(
                    isRecording = isRecording,
                    onClick = {
                        if (isRecording) {
                            // Stopping — no permission needed
                            captureViewModel.toggleRecording(savedLabel = t.recordingSaved)
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            captureViewModel.toggleRecording(savedLabel = t.recordingSaved)
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )

                // Bookmark button
                AnimatedVisibility(
                    visible = isRecording,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BgTertiary.copy(alpha = 0.8f))
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable { captureViewModel.addBookmark() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = t.bookmark,
                            tint = AccentAmber,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // -- Action buttons --
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(
                    icon = { Icon(Icons.Default.Add, contentDescription = t.camera, tint = TextSecondary, modifier = Modifier.size(20.dp)) },
                    label = t.camera,
                    onClick = { captureViewModel.showFilePicker.value = true },
                )
                ActionButton(
                    icon = { Icon(Icons.Default.TextSnippet, contentDescription = t.memo, tint = TextSecondary, modifier = Modifier.size(20.dp)) },
                    label = t.memo,
                    onClick = { captureViewModel.showTextMemo.value = true },
                )
                ActionButton(
                    icon = { Icon(Icons.Default.NoteAdd, contentDescription = t.importFile, tint = TextSecondary, modifier = Modifier.size(20.dp)) },
                    label = t.importFile,
                    onClick = { /* File import handled via Activity result */ },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // -- Error message --
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                errorMessage?.let { error ->
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .background(
                                AccentRed.copy(alpha = 0.08f),
                                RoundedCornerShape(8.dp),
                            )
                            .border(
                                1.dp,
                                AccentRed.copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = AccentRed,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // -- Upload queue --
            if (pendingCount > 0 || failedCount > 0 || isProcessing) {
                UploadQueueBar(
                    pendingCount = pendingCount,
                    failedCount = failedCount,
                    isProcessing = isProcessing,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // -- Toast overlay --
        AnimatedVisibility(
            visible = toastMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 50.dp),
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
        ) {
            toastMessage?.let { msg ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .background(
                            BgTertiary.copy(alpha = 0.8f),
                            RoundedCornerShape(20.dp),
                        )
                        .border(
                            1.dp,
                            GlassBorder,
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        ),
                        color = TextPrimary,
                    )
                }
            }
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Bottom sheets
    if (showTextMemo) {
        TextMemoDialog(
            viewModel = captureViewModel,
            onDismiss = { captureViewModel.showTextMemo.value = false },
        )
    }

    if (showFilePicker) {
        MediaPickerSheet(
            onDismiss = { captureViewModel.showFilePicker.value = false },
            onOptionSelected = { option ->
                // Media picker results handled via Activity result callbacks
            },
        )
    }
}

// MARK: - AudioWaveform

@Composable
private fun AudioWaveform(
    level: Float,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    val barCount = 48
    val levels = remember { mutableStateListOf(*Array(barCount) { 0f }) }

    LaunchedEffect(level) {
        if (!isPaused) {
            levels.removeAt(0)
            levels.add(level)
        }
    }

    val barColor = if (isPaused) TextSecondary else AccentGreen

    Canvas(modifier = modifier) {
        val totalBarWidth = size.width / barCount
        val barWidth = totalBarWidth * 0.65f
        val gap = totalBarWidth * 0.35f
        val midY = size.height / 2

        for (i in 0 until barCount) {
            val intensity = levels[i]
            val barHeight = (intensity * size.height * 0.85f).coerceAtLeast(2f)
            val x = i * totalBarWidth + gap / 2
            val alpha = 0.4f + intensity * 0.6f
            drawRoundRect(
                color = barColor.copy(alpha = alpha),
                topLeft = Offset(x, midY - barHeight / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

@Composable
private fun ConnectionStatus(isConnected: Boolean, host: String?) {
    val t = LocalStrings.current
    Row(
        modifier = Modifier
            .background(
                color = if (isConnected) AccentGreen.copy(alpha = 0.1f) else AccentRed.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp),
            )
            .border(
                1.dp,
                if (isConnected) AccentGreen.copy(alpha = 0.2f) else AccentRed.copy(alpha = 0.2f),
                RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isConnected) AccentGreen else AccentRed),
        )
        Text(
            text = if (isConnected) (host ?: t.connected) else t.disconnected,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            ),
            color = if (isConnected) AccentGreen else AccentRed,
        )
    }
}

@Composable
private fun TranscriberBadge() {
    val t = LocalStrings.current
    Row(
        modifier = Modifier
            .background(
                AccentGreen.copy(alpha = 0.1f),
                RoundedCornerShape(20.dp),
            )
            .border(
                1.dp,
                AccentGreen.copy(alpha = 0.2f),
                RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(AccentGreen),
        )
        Text(
            text = t.liveTranscript,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            ),
            color = AccentGreen,
        )
    }
}

@Composable
private fun TimerDisplay(durationMs: Long, isRecording: Boolean) {
    val totalSeconds = (durationMs / 1000).toInt()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    val formatted = if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)

    Text(
        text = formatted,
        style = MaterialTheme.typography.displayLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Thin,
            fontSize = 72.sp,
            letterSpacing = 2.sp,
        ),
        color = if (isRecording) TextPrimary else TextSecondary.copy(alpha = 0.5f),
    )
}

@Composable
private fun LiveTranscriptList(segments: List<TranscriptSegment>) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom. Single composite key avoids two separate effect
    // restarts when both size and last-text change in the same frame.
    val scrollKey = remember(segments) {
        segments.size to (segments.lastOrNull()?.text?.length ?: 0)
    }
    LaunchedEffect(scrollKey) {
        if (segments.isNotEmpty()) {
            listState.animateScrollToItem(segments.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        items(segments, key = { it.id }) { segment ->
            TranscriptBubble(segment = segment)
        }
    }
}

@Composable
private fun TranscriptBubble(segment: TranscriptSegment) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Timestamp + correction indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (segment.isFinal) segment.formattedTimeRange else segment.formattedTime,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                ),
                color = TextTertiary,
            )
            CorrectionIndicator(segment.correctionState)
        }
        // Bubble
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .background(
                    BgTertiary.copy(alpha = 0.6f),
                    RoundedCornerShape(10.dp),
                )
                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = segment.displayText.ifEmpty { " " },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun CorrectionIndicator(state: CorrectionState) {
    when (state) {
        CorrectionState.DONE -> Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(12.dp),
        )
        CorrectionState.PENDING, CorrectionState.STREAMING -> PulsingDot()
        CorrectionState.NONE, CorrectionState.FAILED -> {}
    }
}

@Composable
private fun PulsingDot() {
    val transition = rememberInfiniteTransition(label = "correctionPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(AccentGreen.copy(alpha = alpha)),
    )
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(BgTertiary.copy(alpha = 0.7f))
                .border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = TextSecondary,
        )
    }
}

@Composable
private fun UploadQueueBar(
    pendingCount: Int,
    failedCount: Int,
    isProcessing: Boolean,
) {
    val t = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(
                BgTertiary.copy(alpha = 0.8f),
                RoundedCornerShape(10.dp),
            )
            .border(
                1.dp,
                GlassBorder,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Upload,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(13.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        if (pendingCount > 0) {
            Text(
                text = "$pendingCount ${t.pending}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = TextSecondary,
            )
        }

        if (failedCount > 0) {
            if (pendingCount > 0) Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$failedCount ${t.failed}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = AccentRed,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = TextSecondary,
            )
        }
    }
}
