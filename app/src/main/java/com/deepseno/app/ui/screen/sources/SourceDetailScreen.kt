package com.enmooy.deepseno.ui.screen.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.ImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.enmooy.deepseno.data.remote.model.*
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.service.ApiClient
import com.enmooy.deepseno.service.TlsPinning
import com.enmooy.deepseno.ui.screen.common.StatusBadge
import com.enmooy.deepseno.ui.theme.*
import com.enmooy.deepseno.ui.viewmodel.AppState
import okhttp3.OkHttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private enum class DetailTab { Summary, Timeline, Transcript, Content, OcrText }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SourceDetailScreen(
    recordingId: Int,
    onBack: () -> Unit,
    /**
     * When set, after segments load we switch to the Transcript tab, scroll to
     * this segment, and briefly highlight it. Used by Briefing items that jump
     * to source.
     */
    focusSegmentId: Int? = null,
    appState: AppState = hiltViewModel(),
) {
    val apiClient = appState.apiClient
    val t = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Public-relay (secure) target uses a self-signed cert reachable via a VPS IP.
    // Coil + ExoPlayer each build their own OkHttpClient, so they must be given a
    // cert-pinned one or the TLS handshake is rejected (image placeholders / video
    // won't play). On LAN (plain HTTP) these are null and the default clients apply.
    val secure by appState.connectionSecure.collectAsState()
    val activeHost by appState.connectionHost.collectAsState()
    val activeFingerprint by appState.connectionFingerprint.collectAsState()

    var recording by remember { mutableStateOf<Recording?>(null) }
    var segments by remember { mutableStateOf<List<Segment>>(emptyList()) }
    var extractedItems by remember { mutableStateOf<List<ExtractedItem>>(emptyList()) }
    var meetingNotes by remember { mutableStateOf<MeetingNotes?>(null) }
    var imageInfo by remember { mutableStateOf<ImageInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(DetailTab.Summary) }
    var highlightedSegmentId by remember { mutableStateOf<Int?>(null) }
    val transcriptListState = rememberLazyListState()

    // Rebuilt when the active target's pinning params change. On the public relay
    // (secure + fingerprint), inject a CertificatePinner-backed OkHttpClient via
    // Coil's OkHttp NetworkFetcher so the self-signed cert is trusted; LAN uses the
    // plain default loader.
    val authImageLoader = remember(apiClient.token, secure, activeHost, activeFingerprint) {
        val builder = ImageLoader.Builder(context)
        if (secure && activeHost != null && activeFingerprint != null) {
            val pinned = TlsPinning.pinnedClient(OkHttpClient(), activeHost!!, activeFingerprint!!)
            builder.components { add(OkHttpNetworkFetcherFactory(callFactory = { pinned })) }
        }
        builder.build()
    }

    LaunchedEffect(recordingId) {
        isLoading = true
        val api = apiClient.api ?: return@LaunchedEffect

        try {
            val recordings = api.getRecordings()
            recording = recordings.find { it.id == recordingId }
        } catch (_: Exception) {}

        scope.launch {
            val segmentsDeferred = async {
                try { api.getSegments(recordingId) } catch (_: Exception) { emptyList() }
            }
            val itemsDeferred = async {
                try { api.getExtractedItems(recordingId = recordingId) } catch (_: Exception) { emptyList() }
            }
            val notesDeferred = async {
                try { api.getMeetingNotes(recordingId) } catch (_: Exception) { null }
            }
            val imageDeferred = async {
                try { api.getImageInfo(recordingId) } catch (_: Exception) { null }
            }

            segments = segmentsDeferred.await()
            extractedItems = itemsDeferred.await()
            meetingNotes = notesDeferred.await()
            imageInfo = imageDeferred.await()

            // Default to transcript if no meeting notes
            if (meetingNotes == null && segments.isNotEmpty()) {
                selectedTab = when (recording?.mediaType) {
                    "pdf", "docx", "text" -> DetailTab.Content
                    "image" -> DetailTab.OcrText
                    else -> DetailTab.Transcript
                }
            }

            // Briefing-source jump: force transcript tab so the focus segment is visible.
            if (focusSegmentId != null && segments.any { it.id == focusSegmentId }) {
                selectedTab = DetailTab.Transcript
            }

            isLoading = false
        }
    }

    // After segments + tab are ready, scroll to the focus segment and flash highlight.
    LaunchedEffect(focusSegmentId, segments, selectedTab) {
        val sid = focusSegmentId ?: return@LaunchedEffect
        if (selectedTab != DetailTab.Transcript) return@LaunchedEffect
        val idx = segments.indexOfFirst { it.id == sid }
        if (idx < 0) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        transcriptListState.animateScrollToItem(idx)
        highlightedSegmentId = sid
        kotlinx.coroutines.delay(2000)
        highlightedSegmentId = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = recording?.fileName ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = TextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary,
                    titleContentColor = TextPrimary,
                ),
            )
        },
        containerColor = BgPrimary,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Header
            recording?.let { rec ->
                HeaderSection(recording = rec)
            }

            // Video player. Uses ExoPlayer (Media3) so the HTTP DataSource can be
            // cert-pinned on the public relay — the old VideoView could not pin a
            // self-signed cert and failed to play over HTTPS. The token rides in the
            // URL query (?token=), matching ApiClient.mediaUrl.
            if (recording?.mediaType == "video") {
                val videoUrl = apiClient.mediaUrl(recordingId)
                val exoPlayer = remember(videoUrl, secure, activeHost, activeFingerprint) {
                    // Build an OkHttp-backed HTTP data source so we can pin the
                    // self-signed cert on the public relay; LAN gets a plain client.
                    val httpClient = if (secure && activeHost != null && activeFingerprint != null) {
                        TlsPinning.pinnedClient(OkHttpClient(), activeHost!!, activeFingerprint!!)
                    } else {
                        OkHttpClient()
                    }
                    val okHttpFactory = OkHttpDataSource.Factory(httpClient)
                    // Wrap in DefaultDataSource so non-http schemes still resolve.
                    val dataSourceFactory = DefaultDataSource.Factory(context, okHttpFactory)
                    ExoPlayer.Builder(context)
                        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                        .build()
                        .apply {
                            setMediaItem(MediaItem.fromUri(videoUrl))
                            prepare()
                            playWhenReady = true
                        }
                }
                DisposableEffect(exoPlayer) {
                    onDispose { exoPlayer.release() }
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }

            // Tab selector — underline indicator style
            TabSelector(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                mediaType = recording?.mediaType,
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AccentGreen)
                }
            } else {
                val isImageType = recording?.mediaType == "image"

                LazyColumn(
                    state = transcriptListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    when (selectedTab) {
                        DetailTab.Summary -> {
                            // Image gallery
                            val imgInfo = imageInfo
                            if (imgInfo != null && imgInfo.count > 0) {
                                item(key = "images") {
                                    ImageGallerySection(
                                        recordingId = recordingId,
                                        imageCount = imgInfo.count,
                                        apiClient = apiClient,
                                        imageLoader = authImageLoader,
                                    )
                                }
                            }

                            val isConversationType = recording?.mediaType in listOf("audio", "video", null)

                            // Meeting notes — only for conversation types
                            if (isConversationType) {
                                meetingNotes?.let { notes ->
                                    if (notes.title != null || notes.discussionSummary != null || !notes.keyTopics.isNullOrEmpty()) {
                                        item(key = "notes") {
                                            MeetingNotesSection(notes = notes)
                                        }
                                    }

                                    // Participants
                                    if (!notes.participants.isNullOrEmpty()) {
                                        item(key = "participants") {
                                            ParticipantsSection(participants = notes.participants!!)
                                        }
                                    }

                                    // Decisions
                                    if (!notes.decisions.isNullOrEmpty()) {
                                        item(key = "decisions") {
                                            DecisionsSection(decisions = notes.decisions!!)
                                        }
                                    }

                                    // Action items
                                    if (!notes.actionItems.isNullOrEmpty()) {
                                        item(key = "actions") {
                                            ActionItemsSection(actionItems = notes.actionItems!!)
                                        }
                                    }
                                }
                            }

                            // Extracted items
                            if (extractedItems.isNotEmpty()) {
                                item(key = "extracted") {
                                    ExtractedItemsSection(items = extractedItems)
                                }
                            }

                            // Empty state — only when nothing to show
                            val hasSummaryContent = run {
                                if (isImageType && (imageInfo?.count ?: 0) > 0) return@run true
                                if (isConversationType) {
                                    meetingNotes?.let { notes ->
                                        if (notes.title != null || notes.discussionSummary != null) return@run true
                                        if (!notes.participants.isNullOrEmpty()) return@run true
                                        if (!notes.decisions.isNullOrEmpty()) return@run true
                                        if (!notes.actionItems.isNullOrEmpty()) return@run true
                                        if (!notes.keyTopics.isNullOrEmpty()) return@run true
                                    }
                                }
                                if (extractedItems.isNotEmpty()) return@run true
                                false
                            }
                            if (!hasSummaryContent) {
                                item { EmptyState(t.noTranscript) }
                            }
                        }

                        DetailTab.Timeline -> {
                            if (segments.isEmpty()) {
                                item { EmptyState(t.noTranscript) }
                            } else if (isImageType) {
                                // Image OCR: plain text blocks, no timeline spine
                                items(segments, key = { it.id }) { segment ->
                                    GlassCard {
                                        Text(
                                            text = segment.displayText,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                lineHeight = 20.sp,
                                            ),
                                            color = TextPrimary,
                                        )
                                    }
                                }
                            } else {
                                val blocks = buildTimelineBlocks(segments)
                                itemsIndexed(blocks, key = { i, _ -> "timeline_$i" }) { index, block ->
                                    TimelineBlockRow(
                                        block = block,
                                        isLast = index == blocks.lastIndex,
                                    )
                                }
                            }
                        }

                        DetailTab.Transcript -> {
                            if (segments.isEmpty()) {
                                item { EmptyState(t.noTranscript) }
                            } else if (isImageType) {
                                // Image OCR: clean text blocks without speaker/time metadata
                                items(segments, key = { it.id }) { segment ->
                                    val highlighted = segment.id == highlightedSegmentId
                                    Text(
                                        text = segment.displayText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            lineHeight = 20.sp,
                                        ),
                                        color = TextPrimary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (highlighted) AccentGreen.copy(alpha = 0.18f)
                                                else BgSecondary.copy(alpha = 0.45f)
                                            )
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                    )
                                }
                            } else {
                                // Audio/video: segments with speaker-colored accent bar
                                items(segments, key = { it.id }) { segment ->
                                    SegmentRow(
                                        segment = segment,
                                        highlighted = segment.id == highlightedSegmentId,
                                    )
                                }
                            }
                        }

                        DetailTab.Content -> {
                            if (segments.isEmpty()) {
                                item { EmptyState(t.noTranscript) }
                            } else {
                                // Document content: numbered paragraphs without speaker/time
                                itemsIndexed(segments, key = { _, seg -> "content_${seg.id}" }) { index, segment ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(BgSecondary.copy(alpha = 0.45f))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Text(
                                            text = "§${index + 1}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                            ),
                                            color = TextTertiary,
                                        )
                                        Text(
                                            text = segment.displayText,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                lineHeight = 20.sp,
                                            ),
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }

                        DetailTab.OcrText -> {
                            if (segments.isEmpty()) {
                                item { EmptyState(t.noTranscript) }
                            } else {
                                // Image OCR: plain text blocks
                                items(segments, key = { it.id }) { segment ->
                                    Text(
                                        text = segment.displayText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            lineHeight = 20.sp,
                                        ),
                                        color = TextPrimary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(BgSecondary.copy(alpha = 0.45f))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// MARK: - Glass Card helper

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgSecondary.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        content = content,
    )
}

// MARK: - Tab Selector (underline indicator style)

@Composable
private fun TabSelector(
    selectedTab: DetailTab,
    onTabSelected: (DetailTab) -> Unit,
    mediaType: String? = null,
) {
    val t = LocalStrings.current
    val tabs = when (mediaType) {
        "video" -> listOf(
            DetailTab.Summary to t.summaryTab,
            DetailTab.Transcript to t.transcriptTab,
        )
        "pdf", "docx", "text" -> listOf(
            DetailTab.Summary to t.summaryTab,
            DetailTab.Content to t.contentTab,
        )
        "image" -> listOf(
            DetailTab.Summary to t.summaryTab,
            DetailTab.OcrText to t.ocrTextTab,
        )
        else -> listOf(
            DetailTab.Summary to t.summaryTab,
            DetailTab.Timeline to t.timelineTab,
            DetailTab.Transcript to t.transcriptTab,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        tabs.forEach { (tab, title) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                    ),
                    color = if (selectedTab == tab) AccentGreen else TextTertiary,
                )
                Spacer(Modifier.height(6.dp))
                // Active underline
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (selectedTab == tab) AccentGreen else Color.Transparent),
                )
            }
        }
    }
}

// MARK: - Header

@Composable
private fun HeaderSection(recording: Recording) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BgSecondary.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (recording.status.isNotEmpty()) {
            StatusBadge(status = recording.status)
        }
        recording.formattedDuration?.let { duration ->
            MetaItem(icon = Icons.Default.Schedule, text = duration)
        }
        recording.recordedAt?.let { date ->
            MetaItem(icon = Icons.Default.CalendarToday, text = date.take(10))
        }
        val speakerCount = recording.speakerCount
        if (speakerCount != null && speakerCount > 0) {
            MetaItem(icon = Icons.Default.Groups, text = "$speakerCount", color = AccentBlue)
        }
    }
}

@Composable
private fun MetaItem(
    icon: ImageVector,
    text: String,
    color: Color = TextSecondary,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = color,
        )
    }
}

// MARK: - Participants

@Composable
private fun ParticipantsSection(participants: List<Participant>) {
    val t = LocalStrings.current
    GlassCard {
        SectionHeader(title = t.participants)
        Spacer(Modifier.height(8.dp))

        participants.forEachIndexed { index, participant ->
            val avatarColor = speakerColors[index % speakerColors.size]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(avatarColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = participant.name.take(1).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        ),
                        color = avatarColor,
                    )
                }
                Text(
                    text = participant.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                participant.speakingTime?.let { time ->
                    val mins = (time / 60).toInt()
                    val secs = (time % 60).toInt()
                    Text(
                        text = String.format("%d:%02d", mins, secs),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextTertiary,
                    )
                }
            }
        }
    }
}

// MARK: - Decisions

@Composable
private fun DecisionsSection(decisions: List<String>) {
    val t = LocalStrings.current
    GlassCard {
        SectionHeader(title = t.decisions)
        Spacer(Modifier.height(8.dp))
        decisions.forEach { decision ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.Verified,
                    contentDescription = null,
                    tint = AccentAmber,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = decision,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

// MARK: - Action Items

@Composable
private fun ActionItemsSection(actionItems: List<ActionItem>) {
    val t = LocalStrings.current
    GlassCard {
        SectionHeader(title = t.actionItems)
        Spacer(Modifier.height(8.dp))
        actionItems.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(16.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.task,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.assignee?.let { assignee ->
                            Text(
                                text = assignee,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = AccentBlue,
                            )
                        }
                        item.dueDate?.let { due ->
                            Text(
                                text = due,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = TextTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Timeline

private data class TimelineBlock(
    val startTime: String?,
    val speakerName: String?,
    val speakerId: Int?,
    val text: String,
)

// A new timeline node starts on a speaker change, after a silence gap of at
// least this many seconds, or once a node has spanned this long. The last two
// rules matter for single-speaker recordings: without them every segment shares
// one speakerId and collapses into a single block, leaving the Timeline tab with
// no visible time progression.
private const val TIMELINE_PAUSE_GAP = 4.0
private const val TIMELINE_MAX_SPAN = 30.0

private fun buildTimelineBlocks(segments: List<Segment>): List<TimelineBlock> {
    if (segments.isEmpty()) return emptyList()

    val speakerNames = HashMap<Int, String>()
    for (s in segments) {
        val id = s.speakerId
        val name = s.speakerName
        if (id != null && name != null) speakerNames[id] = name
    }

    val blocks = mutableListOf<TimelineBlock>()
    var currentSpeaker: Int? = null
    var currentTexts = mutableListOf<String>()
    var blockStart: String? = null
    var blockStartValue: Double? = null
    var prevEnd: Double? = null
    var started = false

    fun flush() {
        if (currentTexts.isEmpty()) return
        blocks.add(
            TimelineBlock(
                startTime = blockStart,
                speakerName = currentSpeaker?.let { speakerNames[it] },
                speakerId = currentSpeaker,
                text = currentTexts.joinToString(" "),
            )
        )
    }

    for (segment in segments) {
        val segStart = segment.startTime
        val pe = prevEnd
        val bsv = blockStartValue
        val gap = if (pe != null && segStart != null) segStart - pe else null
        val span = if (bsv != null && segStart != null) segStart - bsv else null

        val speakerChanged = started && segment.speakerId != currentSpeaker
        val longPause = (gap ?: 0.0) >= TIMELINE_PAUSE_GAP
        val longSpan = (span ?: 0.0) >= TIMELINE_MAX_SPAN

        if (!started || speakerChanged || longPause || longSpan) {
            flush()
            currentSpeaker = segment.speakerId
            currentTexts = mutableListOf(segment.displayText)
            blockStart = segment.formattedTime
            blockStartValue = segment.startTime
            started = true
        } else {
            currentTexts.add(segment.displayText)
        }
        prevEnd = segment.endTime ?: segment.startTime
    }
    flush()

    return blocks
}

private val speakerColors = listOf(
    AccentBlue, AccentGreen, Color(0xFFFF9800), Color(0xFF9C27B0),
    Color(0xFFE91E63), Color(0xFF00BCD4), Color(0xFF8BC34A), Color(0xFF3F51B5),
)

private fun speakerColor(speakerId: Int?): Color {
    if (speakerId == null) return AccentGreen
    return speakerColors[speakerId % speakerColors.size]
}

@Composable
private fun TimelineBlockRow(block: TimelineBlock, isLast: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Timeline spine
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(speakerColor(block.speakerId))
                    .border(2.dp, BgPrimary, CircleShape),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .weight(1f)
                        .background(BgTertiary.copy(alpha = 0.6f)),
                )
            }
        }

        // Content card
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BgSecondary.copy(alpha = 0.45f))
                .padding(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                block.startTime?.let { time ->
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextTertiary,
                    )
                }
                block.speakerName?.let { speaker ->
                    Text(
                        text = speaker,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = speakerColor(block.speakerId),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = TextSecondary,
        )
    }
}

// MARK: - Existing sections

@Composable
private fun ImageGallerySection(
    recordingId: Int,
    imageCount: Int,
    apiClient: ApiClient,
    imageLoader: ImageLoader,
) {
    val context = LocalContext.current

    Column {
        SectionHeader(title = "IMAGES")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (index in 0 until imageCount) {
                val imageUrl = apiClient.imageUrl(recordingId, index)
                // 160dp @3x ≈ 480px — cap decoded size so a 12MP server image
                // (~40MB bitmap) doesn't blow up memory for a tiny thumbnail.
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(480, 480)
                    .httpHeaders(
                        NetworkHeaders.Builder()
                            .set("Authorization", "Bearer ${apiClient.token}")
                            .build()
                    )
                    .build()

                AsyncImage(
                    model = request,
                    imageLoader = imageLoader,
                    contentDescription = "Image $index",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgTertiary),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeetingNotesSection(notes: MeetingNotes) {
    GlassCard {
        // Title
        notes.title?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
        }

        // Discussion summary with green quote border
        notes.discussionSummary?.let { summary ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .heightIn(min = 20.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(AccentGreen.copy(alpha = 0.6f)),
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Key topics
        val topics = notes.keyTopics
        if (!topics.isNullOrEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                topics.forEach { topic ->
                    Text(
                        text = topic,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                        ),
                        color = AccentGreen,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AccentGreen.copy(alpha = 0.1f))
                            .border(0.5.dp, AccentGreen.copy(alpha = 0.15f), RoundedCornerShape(50))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtractedItemsSection(items: List<ExtractedItem>) {
    val t = LocalStrings.current
    val grouped = items.groupBy { it.type }

    GlassCard {
        SectionHeader(title = t.extractedItems)
        Spacer(Modifier.height(8.dp))

        grouped.forEach { (type, typeItems) ->
            Text(
                text = type.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                ),
                color = colorForType(type),
            )
            Spacer(Modifier.height(4.dp))

            typeItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(colorForType(item.type)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.content,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = TextPrimary,
                        )
                        item.relatedPerson?.let { person ->
                            Text(
                                text = person,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// Transcript segment with colored left accent bar
@Composable
private fun SegmentRow(segment: Segment, highlighted: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (highlighted) AccentGreen.copy(alpha = 0.18f)
                else BgSecondary.copy(alpha = 0.45f)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Colored left accent bar
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.5.dp))
                .background(speakerColor(segment.speakerId)),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, top = 2.dp, bottom = 2.dp),
        ) {
            // Speaker name above text. Timestamps intentionally live only in the
            // Timeline tab — here the focus is readable continuous text.
            segment.speakerName?.let { speaker ->
                Text(
                    text = speaker,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    ),
                    color = speakerColor(segment.speakerId),
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = segment.displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        ),
        color = TextTertiary,
    )
}

private fun colorForType(type: String): Color {
    return when (type) {
        "todo" -> AccentGreen
        "meeting" -> AccentBlue
        "decision" -> AccentAmber
        else -> TextSecondary
    }
}

private fun extractedTypeIcon(type: String): ImageVector {
    return when (type) {
        "todo" -> Icons.Default.CheckCircle
        "meeting" -> Icons.Default.Groups
        "decision" -> Icons.Default.Gavel
        "contact" -> Icons.Default.Person
        "number" -> Icons.Default.Tag
        else -> Icons.Default.Info
    }
}
