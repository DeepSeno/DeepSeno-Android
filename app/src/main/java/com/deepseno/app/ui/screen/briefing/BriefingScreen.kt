package com.enmooy.deepseno.ui.screen.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.*
import com.enmooy.deepseno.ui.viewmodel.AppState
import com.enmooy.deepseno.ui.viewmodel.BriefingMode
import com.enmooy.deepseno.ui.viewmodel.BriefingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefingScreen(
    /**
     * Invoked when a briefing item's "view source" affordance is tapped.
     * The host navigation graph routes this to SourceDetailScreen with focusSegmentId.
     */
    onSourceClick: (recordingId: Int, segmentId: Int?) -> Unit = { _, _ -> },
    /**
     * Invoked when "Ask AI about this" is selected on a briefing item. The
     * host should set the pending chat prompt on AppState and navigate to the
     * Chat tab.
     */
    onAskAI: (prompt: String) -> Unit = {},
    appState: AppState = hiltViewModel(),
    briefingViewModel: BriefingViewModel = hiltViewModel(),
) {
    val t = LocalStrings.current

    val mode by briefingViewModel.mode.collectAsStateWithLifecycle()
    val selectedDate by briefingViewModel.selectedDate.collectAsStateWithLifecycle()
    val dailySummary by briefingViewModel.dailySummary.collectAsStateWithLifecycle()
    val weeklySummary by briefingViewModel.weeklySummary.collectAsStateWithLifecycle()
    val monthlySummary by briefingViewModel.monthlySummary.collectAsStateWithLifecycle()
    val todos by briefingViewModel.todos.collectAsStateWithLifecycle()
    val items by briefingViewModel.items.collectAsStateWithLifecycle()
    val isLoading by briefingViewModel.isLoading.collectAsStateWithLifecycle()
    val isRegenerating by briefingViewModel.isRegenerating.collectAsStateWithLifecycle()
    val errorMessage by briefingViewModel.errorMessage.collectAsStateWithLifecycle()
    val isConnected by appState.connectionActive.collectAsStateWithLifecycle()

    // Load data on appear and on connection change
    LaunchedEffect(isConnected) {
        if (isConnected) briefingViewModel.loadData()
    }

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    var showDatePicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Mode picker (segmented)
            ModePicker(
                mode = mode,
                onModeChange = { briefingViewModel.setMode(it) },
            )

            // Date navigation bar
            DateNavigationBar(
                displayText = when (mode) {
                    BriefingMode.Daily -> briefingViewModel.displayDate
                    BriefingMode.Weekly -> briefingViewModel.weekDisplayRange
                    BriefingMode.Monthly -> briefingViewModel.monthDisplayRange
                },
                onPrevious = { briefingViewModel.previousDate() },
                onNext = { briefingViewModel.nextDate() },
                onDateClick = { showDatePicker = true },
            )

            // Content with pull-to-refresh
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { briefingViewModel.loadData() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (mode) {
                        BriefingMode.Daily -> DailyContent(
                            summary = dailySummary,
                            todos = todos,
                            items = items,
                            onToggleTodo = { id, status -> briefingViewModel.toggleTodo(id, status) },
                            onSourceClick = onSourceClick,
                            onAskAI = onAskAI,
                            onRegenerate = { briefingViewModel.regenerate() },
                            isRegenerating = isRegenerating,
                        )
                        BriefingMode.Weekly -> WeeklyContent(
                            summary = weeklySummary,
                            onSourceClick = onSourceClick,
                            onRegenerate = { briefingViewModel.regenerate() },
                            isRegenerating = isRegenerating,
                        )
                        BriefingMode.Monthly -> MonthlyContent(
                            summary = monthlySummary,
                            onSourceClick = onSourceClick,
                        )
                    }
                }
            }
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Calendar date picker — tapping the date in the nav bar opens this.
    if (showDatePicker) {
        // Material3 DatePicker works in UTC; keep both directions UTC to avoid
        // an off-by-one day from timezone drift.
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(java.time.ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.of("UTC"))
                            .toLocalDate()
                        briefingViewModel.selectDate(picked)
                    }
                    showDatePicker = false
                }) { Text(t.done) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(t.cancel) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun ModePicker(
    mode: BriefingMode,
    onModeChange: (BriefingMode) -> Unit,
) {
    val t = LocalStrings.current

    val entries = listOf(
        BriefingMode.Daily to t.daily,
        BriefingMode.Weekly to t.weekly,
        BriefingMode.Monthly to t.monthly,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSecondary)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        SingleChoiceSegmentedButtonRow {
            entries.forEachIndexed { index, (m, label) ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { onModeChange(m) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = AccentGreen.copy(alpha = 0.2f),
                        activeContentColor = AccentGreen,
                        inactiveContainerColor = BgTertiary,
                        inactiveContentColor = TextSecondary,
                    ),
                    // No leading check icon — the default checkmark pushed the
                    // label off-center and left a gap. Empty icon = centered text.
                    icon = {},
                ) {
                    Text(
                        text = label,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DateNavigationBar(
    displayText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDateClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSecondary)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = TextSecondary,
            )
        }

        // Tap the date to open a calendar and jump to any day/week/month.
        Text(
            text = displayText,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = TextPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onDateClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
            )
        }
    }
}

@Composable
private fun DailyContent(
    summary: com.enmooy.deepseno.data.remote.model.DailySummary?,
    todos: List<com.enmooy.deepseno.data.remote.model.ExtractedItem>,
    items: List<com.enmooy.deepseno.data.remote.model.ExtractedItem>,
    onToggleTodo: (id: Int, currentStatus: String) -> Unit,
    onSourceClick: (recordingId: Int, segmentId: Int?) -> Unit,
    onAskAI: (prompt: String) -> Unit,
    onRegenerate: () -> Unit,
    isRegenerating: Boolean,
) {
    val t = LocalStrings.current

    val hasSummary = !summary?.summaryText.isNullOrBlank()
    // Briefing intentionally excludes todos — they belong in a future dedicated
    // Tasks view, not in the AI digest.
    val totallyEmpty = !hasSummary && items.isEmpty()

    if (totallyEmpty) {
        EmptyState(
            title = t.noBriefing,
            subtitle = t.noBriefingSubtitle,
        )
        return
    }

    // Generated-at row (only when server provided the timestamp)
    summary?.generatedAt?.let { iso ->
        com.enmooy.deepseno.ui.util.RelativeTime.ago(iso)?.let { ago ->
            GeneratedAtRow(
                text = t.briefingGeneratedAtFormat.format(ago),
                isRegenerating = isRegenerating,
                onRegenerate = onRegenerate,
            )
        }
    }

    if (hasSummary) {
        SummaryHeroCard(text = summary!!.summaryText!!)
    } else {
        // Items exist but no narrative — explain why and offer to retry now.
        NoNarrativePlaceholder(isRegenerating = isRegenerating, onRegenerate = onRegenerate)
    }

    // Extracted items only — todos intentionally excluded from briefing.
    if (items.isNotEmpty()) {
        ExtractedItemsSection(
            items = items,
            onSourceClick = onSourceClick,
            onAskAI = onAskAI,
        )
    }
}

@Composable
private fun WeeklyContent(
    summary: com.enmooy.deepseno.data.remote.model.WeeklySummary?,
    onSourceClick: (recordingId: Int, segmentId: Int?) -> Unit,
    onRegenerate: () -> Unit,
    isRegenerating: Boolean,
) {
    val t = LocalStrings.current

    if (summary?.summaryJson == null) {
        EmptyState(
            title = t.noWeeklySummary,
            subtitle = t.noWeeklySummarySubtitle,
        )
        return
    }

    // Generated-at row
    summary.generatedAt?.let { iso ->
        com.enmooy.deepseno.ui.util.RelativeTime.ago(iso)?.let { ago ->
            GeneratedAtRow(
                text = t.briefingGeneratedAtFormat.format(ago),
                isRegenerating = isRegenerating,
                onRegenerate = onRegenerate,
            )
        }
    }

    // Prefer structured rendering when the server returns the new schema; fall
    // back to plain text for legacy responses.
    val structured = com.enmooy.deepseno.data.remote.model.WeeklySummaryStructured.tryDecode(summary.summaryJson)
    if (structured != null) {
        WeeklyStructuredContent(structured = structured, onSourceClick = onSourceClick)
        return
    }

    Column {
        Text(
            text = t.weeklySummary,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            color = BgSecondary,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = summary.summaryJson,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
                color = TextPrimary,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun MonthlyContent(
    summary: com.enmooy.deepseno.data.remote.model.WeeklySummary?,
    onSourceClick: (recordingId: Int, segmentId: Int?) -> Unit,
) {
    val t = LocalStrings.current

    if (summary?.summaryJson == null) {
        EmptyState(
            title = t.noMonthlySummary,
            subtitle = t.noMonthlySummarySubtitle,
        )
        return
    }

    // Monthly shares the weekly structured schema — reuse the same renderer.
    val structured = com.enmooy.deepseno.data.remote.model.WeeklySummaryStructured.tryDecode(summary.summaryJson)
    if (structured != null) {
        WeeklyStructuredContent(structured = structured, onSourceClick = onSourceClick)
        return
    }

    // Plain-text fallback for legacy responses.
    Surface(
        color = BgSecondary,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = summary.summaryJson,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
            color = TextPrimary,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun WeeklyStructuredContent(
    structured: com.enmooy.deepseno.data.remote.model.WeeklySummaryStructured,
    onSourceClick: (recordingId: Int, segmentId: Int?) -> Unit,
) {
    val t = LocalStrings.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Overview
        structured.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            WeeklySectionHeader(t.weeklySummary)
            Surface(color = BgSecondary, shape = RoundedCornerShape(12.dp)) {
                Text(
                    text = overview,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                    color = TextPrimary,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // Themes
        structured.themes?.takeIf { it.isNotEmpty() }?.let { themes ->
            WeeklySectionHeader(t.briefingWeeklyThemes)
            Surface(color = BgSecondary, shape = RoundedCornerShape(12.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    themes.forEach { theme ->
                        Column {
                            Text(
                                text = theme.title,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = TextPrimary,
                            )
                            theme.summary?.takeIf { it.isNotBlank() }?.let { s ->
                                Text(
                                    text = s,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                    ),
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // People mentioned
        structured.people?.takeIf { it.isNotEmpty() }?.let { people ->
            WeeklySectionHeader(t.briefingWeeklyPeople)
            Surface(color = BgSecondary, shape = RoundedCornerShape(12.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    people.forEach { person ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = person.name,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = TextPrimary,
                            )
                            person.mentionCount?.let { n ->
                                Text(
                                    text = "${n}x",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    ),
                                    color = TextTertiary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Key moments — vertical timeline; each moment is clickable to jump to source.
        structured.keyMoments?.takeIf { it.isNotEmpty() }?.let { moments ->
            WeeklySectionHeader(t.briefingWeeklyKeyMoments)
            Surface(color = BgSecondary, shape = RoundedCornerShape(12.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    moments.forEachIndexed { index, moment ->
                        KeyMomentTimelineRow(
                            moment = moment,
                            isFirst = index == 0,
                            isLast = index == moments.lastIndex,
                            onClick = { onSourceClick(moment.recordingId, moment.segmentId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyMomentTimelineRow(
    moment: com.enmooy.deepseno.data.remote.model.WeeklySummaryStructured.KeyMoment,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Left rail: connector line spanning full row + solid dot at top
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(IntrinsicSize.Min)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .padding(top = if (isFirst) 14.dp else 0.dp)
                    .background(AccentGreen.copy(alpha = 0.35f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .size(9.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(AccentGreen),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                moment.date?.let { d ->
                    Text(
                        text = d,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = TextTertiary,
                    )
                }
                moment.recordingTitle?.takeIf { it.isNotBlank() }?.let { title ->
                    Text(
                        text = title,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = AccentGreen,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = moment.summary,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
                color = TextPrimary,
            )
        }
    }
}

@Composable
private fun SummaryHeroCard(text: String) {
    val t = LocalStrings.current
    Surface(
        color = AccentGreen.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = t.summaryHeader,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = AccentGreen,
                )
            }
            Text(
                text = text,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                ),
                color = TextPrimary,
            )
        }
    }
}

@Composable
private fun NoNarrativePlaceholder(
    isRegenerating: Boolean = false,
    onRegenerate: () -> Unit = {},
) {
    val t = LocalStrings.current
    Surface(
        color = BgSecondary.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = t.briefingNoNarrativeTitle,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                RegenerateInlineButton(isRegenerating = isRegenerating, onClick = onRegenerate)
            }
            Text(
                text = t.briefingNoNarrativeSubtitle,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                ),
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun GeneratedAtRow(
    text: String,
    isRegenerating: Boolean = false,
    onRegenerate: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(10.dp),
        )
        Text(
            text = text,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            ),
            color = TextTertiary,
            modifier = Modifier.weight(1f),
        )
        RegenerateInlineButton(isRegenerating = isRegenerating, onClick = onRegenerate)
    }
}

@Composable
private fun RegenerateInlineButton(isRegenerating: Boolean, onClick: () -> Unit) {
    val t = LocalStrings.current
    Row(
        modifier = Modifier
            .clickable(enabled = !isRegenerating) { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isRegenerating) {
            CircularProgressIndicator(
                color = AccentGreen,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(10.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(11.dp),
            )
        }
        Text(
            text = t.briefingRegenerate,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            ),
            color = if (isRegenerating) TextTertiary else AccentGreen,
        )
    }
}

@Composable
private fun WeeklySectionHeader(text: String) {
    Text(
        text = text,
        style = androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = TextSecondary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Inbox,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            ),
            color = TextSecondary.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}
