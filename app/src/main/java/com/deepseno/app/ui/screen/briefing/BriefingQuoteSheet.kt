package com.enmooy.deepseno.ui.screen.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmooy.deepseno.data.remote.model.ExtractedItem
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.service.ApiClient
import com.enmooy.deepseno.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Modal bottom sheet shown when a user picks "View source" on a briefing item.
 * Fetches the segment by id (via getSegments → filter) and displays the
 * original text, with a primary action to jump to the recording.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefingQuoteSheet(
    item: ExtractedItem,
    apiClient: ApiClient,
    onDismiss: () -> Unit,
    onJumpToSource: (recordingId: Int, segmentId: Int?) -> Unit,
) {
    val t = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var quote by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(item.id) {
        val rid = item.recordingId
        val sid = item.segmentId
        if (rid == null || sid == null) {
            quote = item.content
            loading = false
            return@LaunchedEffect
        }
        try {
            val api = apiClient.api
            val segments = api?.getSegments(rid).orEmpty()
            quote = segments.firstOrNull { it.id == sid }?.let { it.cleanText ?: it.rawText }
                ?: item.content
        } catch (_: Throwable) {
            quote = item.content
        }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = t.briefingQuoteSheetTitle,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = TextSecondary,
            )

            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = AccentGreen)
                }
            } else {
                Text(
                    text = quote.orEmpty(),
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                    color = TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgSecondary)
                        .padding(14.dp),
                )
            }

            item.recordingId?.let { rid ->
                Surface(
                    color = AccentGreen,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                                onJumpToSource(rid, item.segmentId)
                            }
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowOutward,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = t.briefingViewSource,
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
            }
        }
    }
}
