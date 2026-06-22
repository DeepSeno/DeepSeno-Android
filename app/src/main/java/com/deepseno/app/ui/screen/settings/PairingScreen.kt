package com.enmooy.deepseno.ui.screen.settings

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.AccentBlue
import com.enmooy.deepseno.ui.theme.AccentGreen
import com.enmooy.deepseno.ui.theme.AccentRed
import com.enmooy.deepseno.ui.theme.BgPrimary
import com.enmooy.deepseno.ui.theme.BgSecondary
import com.enmooy.deepseno.ui.theme.BgTertiary
import com.enmooy.deepseno.ui.theme.TextPrimary
import com.enmooy.deepseno.ui.theme.TextSecondary
import com.enmooy.deepseno.ui.viewmodel.AppState
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    appState: AppState,
    onDismiss: () -> Unit,
) {
    val t = LocalStrings.current
    var isCameraMode by remember { mutableStateOf(true) }
    var manualInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pairingStatus by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        TopAppBar(
            title = {
                Text(
                    t.pairViaQR,
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
                IconButton(onClick = { isCameraMode = !isCameraMode }) {
                    Icon(
                        if (isCameraMode) Icons.Default.Edit else Icons.Default.CameraAlt,
                        contentDescription = if (isCameraMode) t.useManualInput else t.pairViaQR,
                        tint = AccentBlue,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary),
        )

        if (isCameraMode) {
            // Camera scan mode
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                QrScannerView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    onQrScanned = { raw ->
                        android.util.Log.d("PairingScreen", "QR scanned: ${raw.take(300)}")
                        val success = appState.connectFromQR(raw)
                        android.util.Log.d("PairingScreen", "connectFromQR result: $success")
                        if (success) onDismiss()
                    },
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = t.scanQRHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // Manual paste mode
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = t.pasteQRHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                OutlinedTextField(
                    value = manualInput,
                    onValueChange = {
                        manualInput = it
                        error = null
                    },
                    label = { Text(t.pasteJSON) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
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

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentRed,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (manualInput.isBlank()) {
                            error = t.invalidQR
                            return@Button
                        }
                        val success = appState.connectFromQR(manualInput.trim())
                        if (success) {
                            onDismiss()
                        } else {
                            error = t.invalidQR
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = t.connect,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BgPrimary,
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun QrScannerView(
    modifier: Modifier = Modifier,
    onQrScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var scanned by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val scanner = BarcodeScanning.getClient()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && !scanned) {
                                val inputImage = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees,
                                )
                                scanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        android.util.Log.d("QrScanner", "Found ${barcodes.size} barcodes, scanned=$scanned")
                                        for (barcode in barcodes) {
                                            if (barcode.format == Barcode.FORMAT_QR_CODE) {
                                                barcode.rawValue?.let { raw ->
                                                    android.util.Log.d("QrScanner", "QR content: ${raw.take(200)}")
                                                    if (!scanned) {
                                                        scanned = true
                                                        onQrScanned(raw)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (e: Exception) {
                    Log.e("QrScanner", "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
    )
}
