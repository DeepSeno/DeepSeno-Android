package com.enmooy.deepseno.ui.screen.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.AccentAmber
import com.enmooy.deepseno.ui.theme.AccentBlue
import com.enmooy.deepseno.ui.theme.AccentGreen
import com.enmooy.deepseno.ui.theme.AccentRed
import com.enmooy.deepseno.ui.theme.BgPrimary
import com.enmooy.deepseno.ui.theme.BgSecondary
import com.enmooy.deepseno.ui.theme.BgTertiary
import com.enmooy.deepseno.ui.theme.TextPrimary
import com.enmooy.deepseno.ui.theme.TextSecondary
import com.enmooy.deepseno.ui.theme.TextTertiary
import com.enmooy.deepseno.ui.viewmodel.AppState
import com.enmooy.deepseno.ui.viewmodel.SettingsSheet
import com.enmooy.deepseno.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appState: AppState = hiltViewModel(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val t = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isConnected by appState.connectionActive.collectAsStateWithLifecycle()
    val connectionHost by appState.connectionHost.collectAsStateWithLifecycle()
    val connectionPort by appState.connectionPort.collectAsStateWithLifecycle()
    val hasSavedPairing by appState.hasSavedPairing.collectAsStateWithLifecycle()
    val relayTransportMode by appState.relayTransportMode.collectAsStateWithLifecycle()
    val pendingCount by appState.captureQueue.pendingCount.collectAsStateWithLifecycle(initialValue = 0)
    val failedCount by appState.captureQueue.failedCount.collectAsStateWithLifecycle(initialValue = 0)

    val activeSheet by viewModel.activeSheet.collectAsStateWithLifecycle()
    val showQueueManagement by viewModel.showQueueManagement.collectAsStateWithLifecycle()
    val correctionEnabled by viewModel.correctionEnabled.collectAsStateWithLifecycle()
    val transcriptionLocale by viewModel.transcriptionLocale.collectAsStateWithLifecycle()

    val devices by viewModel.nsdBrowser.devices.collectAsStateWithLifecycle()
    val isSearching by viewModel.nsdBrowser.isSearching.collectAsStateWithLifecycle()

    val manualHost by viewModel.manualHost.collectAsStateWithLifecycle()
    val manualPort by viewModel.manualPort.collectAsStateWithLifecycle()
    val manualToken by viewModel.manualToken.collectAsStateWithLifecycle()
    val manualPublicHost by viewModel.manualPublicHost.collectAsStateWithLifecycle()
    val manualPublicPort by viewModel.manualPublicPort.collectAsStateWithLifecycle()
    val manualFingerprint by viewModel.manualFingerprint.collectAsStateWithLifecycle()
    val allowPublicAccess by viewModel.allowPublicAccess.collectAsStateWithLifecycle()
    val connectError by viewModel.connectError.collectAsStateWithLifecycle()
    val selectedBonjourDevice by viewModel.selectedBonjourDevice.collectAsStateWithLifecycle()

    // Start/stop NSD browsing based on connection state
    DisposableEffect(isConnected) {
        if (!isConnected) {
            viewModel.nsdBrowser.startBrowsing()
        }
        onDispose {
            viewModel.nsdBrowser.stopBrowsing()
        }
    }

    // Ambient glow tint reflects connection state — green when linked, neutral when not.
    val glowColor by animateColorAsState(
        targetValue = if (isConnected) AccentGreen else TextTertiary,
        animationSpec = tween(500),
        label = "glow",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .drawBehind {
                // Soft radial wash near the top — atmosphere instead of flat black.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 0.95f,
                    ),
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = t.settings,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Spacer(Modifier.height(20.dp))

            // -- HERO: Connection beacon --
            Reveal(0) {
                // Determine connection type for display
                val connectionType = when {
                    !isConnected -> "none"
                    relayTransportMode == "p2p" -> "p2p"
                    relayTransportMode == "relay" -> "relay"
                    else -> "lan" // connected but no relay = LAN direct
                }
                ConnectionHero(
                    connected = isConnected,
                    host = connectionHost,
                    port = connectionPort,
                    hasSavedPairing = hasSavedPairing,
                    connectionType = connectionType,
                    onDisconnect = { appState.disconnect() },
                    onPairQR = {
                        viewModel.resetForm()
                        viewModel.setActiveSheet(SettingsSheet.Pairing)
                    },
                    onManual = {
                        viewModel.resetForm()
                        viewModel.setActiveSheet(SettingsSheet.ManualConnect)
                    },
                    onForget = { appState.forget() },
                )
            }

            // -- Discovered Devices (when disconnected) --
            if (!isConnected) {
                Spacer(Modifier.height(14.dp))
                Reveal(60) {
                    Column {
                        SectionHeader(t.discoveredDevices)
                        Spacer(Modifier.height(8.dp))
                        CardContainer {
                            if (isSearching && devices.isEmpty()) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = AccentGreen,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = t.searchingDevices,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                            } else if (devices.isEmpty()) {
                                Text(
                                    text = t.searchingDevices,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            } else {
                                devices.forEach { device ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (hasSavedPairing) {
                                                    // Remembered pairing → one-tap reconnect, no sheet.
                                                    appState.connectWithSavedToken(device.host, device.port)
                                                } else {
                                                    viewModel.setSelectedBonjourDevice(device.name)
                                                    viewModel.setManualHost(device.host)
                                                    viewModel.setManualPort(device.port.toString())
                                                    viewModel.setActiveSheet(SettingsSheet.BonjourConnect)
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(AccentGreen)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = device.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary,
                                            )
                                            Text(
                                                text = "${device.host}:${device.port}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -- Upload Queue (state-adaptive) --
            Spacer(Modifier.height(14.dp))
            Reveal(120) {
                Column {
                    SectionHeader(t.uploadQueue)
                    Spacer(Modifier.height(8.dp))
                    CardContainer {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setShowQueueManagement(true) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            QueueStatus(pending = pendingCount, failed = failedCount)
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        // Recovery actions appear only when there's something to recover.
                        if (failedCount > 0) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QueueActionChip(Icons.Default.Refresh, t.retryAll, AccentGreen) {
                                    scope.launch { appState.captureQueue.retryAndProcess() }
                                }
                                QueueActionChip(Icons.Default.DeleteOutline, t.clear, AccentRed) {
                                    scope.launch { appState.captureQueue.clearAll() }
                                }
                            }
                        }
                    }
                }
            }

            // -- Live Transcription (language + AI polish, grouped) --
            Spacer(Modifier.height(14.dp))
            Reveal(180) {
                Column {
                    SectionHeader(t.liveTranscript)
                    Spacer(Modifier.height(8.dp))
                    CardContainer {
                        IconLabel(Icons.Default.GraphicEq, t.liveTranscriptionLanguageTitle)
                        Spacer(Modifier.height(10.dp))
                        LanguageSegmented(
                            selected = transcriptionLocale,
                            onSelect = { viewModel.setTranscriptionLocale(it) },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = t.liveTranscriptionLanguageHelp,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )

                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.06f))
                        )
                        Spacer(Modifier.height(12.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconLabel(
                                Icons.Default.AutoAwesome,
                                t.transcriptionCorrectionTitle,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = correctionEnabled,
                                onCheckedChange = { viewModel.setCorrectionEnabled(it) },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = t.transcriptionCorrectionHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }
                }
            }

            // -- About footer --
            Spacer(Modifier.height(14.dp))
            Reveal(240) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "DeepSeno",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "v${getAppVersion(context)} (${getAppBuildNumber(context)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // -- Sheets --

    // Pairing sheet (full screen)
    if (activeSheet == SettingsSheet.Pairing) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setActiveSheet(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = BgPrimary,
        ) {
            PairingScreen(
                appState = appState,
                onDismiss = { viewModel.setActiveSheet(null) },
            )
        }
    }

    // Manual connect sheet
    if (activeSheet == SettingsSheet.ManualConnect) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setActiveSheet(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = BgPrimary,
        ) {
            ManualConnectForm(
                host = manualHost,
                port = manualPort,
                token = manualToken,
                error = connectError,
                onHostChange = { viewModel.setManualHost(it) },
                onPortChange = { viewModel.setManualPort(it) },
                onTokenChange = { viewModel.setManualToken(it) },
                onPasteJSON = { viewModel.pasteConnectionJSON(context) },
                onConnect = { viewModel.manualConnect(appState) },
                allowPublicAccess = allowPublicAccess,
                onAllowPublicAccessChange = { viewModel.setAllowPublicAccess(it) },
                publicHost = manualPublicHost,
                publicPort = manualPublicPort,
                fingerprint = manualFingerprint,
                onPublicHostChange = { viewModel.setManualPublicHost(it) },
                onPublicPortChange = { viewModel.setManualPublicPort(it) },
                onFingerprintChange = { viewModel.setManualFingerprint(it) },
            )
        }
    }

    // Bonjour connect sheet
    if (activeSheet == SettingsSheet.BonjourConnect) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setActiveSheet(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = BgPrimary,
        ) {
            ManualConnectForm(
                host = manualHost,
                port = manualPort,
                token = manualToken,
                error = connectError,
                onHostChange = { viewModel.setManualHost(it) },
                onPortChange = { viewModel.setManualPort(it) },
                onTokenChange = { viewModel.setManualToken(it) },
                onPasteJSON = { viewModel.pasteConnectionJSON(context) },
                onConnect = { viewModel.connectFromBonjour(appState) },
                allowPublicAccess = allowPublicAccess,
                onAllowPublicAccessChange = { viewModel.setAllowPublicAccess(it) },
                publicHost = manualPublicHost,
                publicPort = manualPublicPort,
                fingerprint = manualFingerprint,
                onPublicHostChange = { viewModel.setManualPublicHost(it) },
                onPublicPortChange = { viewModel.setManualPublicPort(it) },
                onFingerprintChange = { viewModel.setManualFingerprint(it) },
                title = selectedBonjourDevice ?: t.connect,
                hostReadOnly = true,
                portReadOnly = true,
            )
        }
    }

    // Queue management sheet
    if (showQueueManagement) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowQueueManagement(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = BgPrimary,
        ) {
            QueueManagementScreen(
                captureQueue = appState.captureQueue,
                onDismiss = { viewModel.setShowQueueManagement(false) },
            )
        }
    }
}

// -- Subcomponents --

/** Staggered entrance: fade + slide-up driven by a per-card delay. */
@Composable
private fun Reveal(delayMillis: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(450),
        label = "revealAlpha",
    )
    val offsetY by animateDpAsState(
        targetValue = if (shown) 0.dp else 14.dp,
        animationSpec = tween(450),
        label = "revealOffset",
    )
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        shown = true
    }
    Box(
        Modifier
            .offset(y = offsetY)
            .graphicsLayer { this.alpha = alpha }
    ) {
        content()
    }
}

/** Section header: mono uppercase label with a leading emerald accent bar. */
@Composable
private fun SectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 3.dp, height = 13.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(AccentGreen)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun CardContainer(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSecondary, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        content()
    }
}

/** Inline control-row label: small emerald icon + title. */
@Composable
private fun IconLabel(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

// MARK: Connection Hero

@Composable
private fun ConnectionHero(
    connected: Boolean,
    host: String?,
    port: Int?,
    hasSavedPairing: Boolean,
    connectionType: String = "none",  // "none" | "lan" | "p2p" | "relay"
    onDisconnect: () -> Unit,
    onPairQR: () -> Unit,
    onManual: () -> Unit,
    onForget: () -> Unit,
) {
    val t = LocalStrings.current
    val borderColor = if (connected) AccentGreen else TextTertiary
    val typeLabel = when (connectionType) {
        "lan"   -> t.transportLan
        "p2p"   -> t.transportP2P
        "relay" -> t.transportRelay
        else    -> ""
    }
    val typeColor = when (connectionType) {
        "lan", "p2p" -> AccentGreen
        "relay"      -> Color(0xFFF59E0B) // amber
        else         -> TextTertiary
    }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (connected) 16.dp else 0.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = AccentGreen,
                ambientColor = AccentGreen,
            )
            .clip(RoundedCornerShape(18.dp))
            .background(BgSecondary.copy(alpha = 0.9f))
            .border(
                1.dp,
                Brush.linearGradient(listOf(borderColor.copy(alpha = 0.5f), Color.White.copy(alpha = 0.04f))),
                RoundedCornerShape(18.dp),
            )
            .padding(18.dp),
    ) {
        if (connected) {
            // Show connection type + address + disconnect
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Connection type badge
                    if (typeLabel.isNotEmpty()) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(typeColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (host != null) "$host:${port ?: ""}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AccentRed.copy(alpha = 0.12f))
                            .clickable { onDisconnect() }
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = AccentRed, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(t.disconnect, style = MaterialTheme.typography.bodySmall, color = AccentRed)
                    }
                }
                // Explanation text
                if (connectionType == "relay") {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = t.relayExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroActionButton(
                    icon = Icons.Default.QrCodeScanner,
                    label = t.pairQR,
                    filled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onPairQR,
                )
                HeroActionButton(
                    icon = Icons.Default.Keyboard,
                    label = t.manualConnect,
                    filled = false,
                    modifier = Modifier.weight(1f),
                    onClick = onManual,
                )
            }
            // A saved pairing persists after disconnect → offer a permanent clear.
            if (hasSavedPairing) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onForget() }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t.forgetDevice, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
            }
        }
    }
}

/** Disconnected-state primary action: filled (gradient) or ghost (outlined) tile. */
@Composable
private fun HeroActionButton(
    icon: ImageVector,
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (filled) {
                    Modifier.background(Brush.linearGradient(listOf(AccentGreen, AccentBlue)))
                } else {
                    Modifier
                        .background(AccentGreen.copy(alpha = 0.12f))
                        .border(1.dp, AccentGreen.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                }
            )
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (filled) BgPrimary else AccentGreen,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (filled) BgPrimary else AccentGreen,
            maxLines = 1,
        )
    }
}

// MARK: Upload Queue

/** State-dependent status line: green when empty, amber while uploading, red on failure. */
@Composable
private fun QueueStatus(pending: Int, failed: Int) {
    val t = LocalStrings.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            failed > 0 -> {
                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("$failed", style = MaterialTheme.typography.bodyMedium, color = AccentRed, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Text(t.failed, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                if (pending > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("· $pending ${t.pending}", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
            }
            pending > 0 -> {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("$pending", style = MaterialTheme.typography.bodyMedium, color = AccentAmber, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Text(t.pending, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            else -> {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(t.queueEmpty, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

/** Compact icon+label action chip for queue recovery actions. */
@Composable
private fun QueueActionChip(icon: ImageVector, title: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(title, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

// MARK: Transcription language

/** Custom 4-way segmented control for the transcription language preference. */
@Composable
private fun LanguageSegmented(selected: String, onSelect: (String) -> Unit) {
    val t = LocalStrings.current
    val options = listOf(
        "" to t.liveTranscriptionLanguageAuto,
        "zh-Hans" to t.liveTranscriptionLanguageChinese,
        "en-US" to t.liveTranscriptionLanguageEnglish,
        "multilingual" to t.liveTranscriptionLanguageMultilingual,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BgTertiary)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSel) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSel) TextPrimary else TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ManualConnectForm(
    host: String,
    port: String,
    token: String,
    error: String?,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onPasteJSON: () -> Unit,
    onConnect: () -> Unit,
    allowPublicAccess: Boolean,
    onAllowPublicAccessChange: (Boolean) -> Unit,
    publicHost: String,
    publicPort: String,
    fingerprint: String,
    onPublicHostChange: (String) -> Unit,
    onPublicPortChange: (String) -> Unit,
    onFingerprintChange: (String) -> Unit,
    title: String? = null,
    hostReadOnly: Boolean = false,
    portReadOnly: Boolean = false,
) {
    val t = LocalStrings.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title ?: t.manualConnect,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(16.dp))

        ConnectTextField(
            value = host,
            onValueChange = onHostChange,
            label = "Host",
            readOnly = hostReadOnly,
        )
        Spacer(Modifier.height(8.dp))
        ConnectTextField(
            value = port,
            onValueChange = onPortChange,
            label = "Port",
            readOnly = portReadOnly,
        )
        Spacer(Modifier.height(8.dp))
        ConnectTextField(
            value = token,
            onValueChange = onTokenChange,
            label = "Token",
        )

        // -- Public Access (optional public-relay fallback) --
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = t.publicAccess,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = t.publicAccessHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = allowPublicAccess,
                onCheckedChange = onAllowPublicAccessChange,
            )
        }

        if (allowPublicAccess) {
            Spacer(Modifier.height(8.dp))
            ConnectTextField(
                value = publicHost,
                onValueChange = onPublicHostChange,
                label = t.publicHostLabel,
            )
            Spacer(Modifier.height(8.dp))
            ConnectTextField(
                value = publicPort,
                onValueChange = onPublicPortChange,
                label = t.publicPortLabel,
            )
            Spacer(Modifier.height(8.dp))
            ConnectTextField(
                value = fingerprint,
                onValueChange = onFingerprintChange,
                label = t.fingerprintLabel,
            )
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = AccentRed,
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onPasteJSON,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(t.pasteJSON, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onConnect,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(t.connect, style = MaterialTheme.typography.bodySmall, color = BgPrimary)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConnectTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    readOnly: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        readOnly = readOnly,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentGreen,
            unfocusedBorderColor = BgTertiary,
            focusedLabelColor = AccentGreen,
            unfocusedLabelColor = TextSecondary,
            cursorColor = AccentGreen,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
    )
}

private fun getAppVersion(context: android.content.Context): String {
    return try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.versionName ?: "1.0.0"
    } catch (_: Exception) {
        "1.0.0"
    }
}

private fun getAppBuildNumber(context: android.content.Context): String {
    return try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toString()
        }
    } catch (_: Exception) {
        "1"
    }
}
