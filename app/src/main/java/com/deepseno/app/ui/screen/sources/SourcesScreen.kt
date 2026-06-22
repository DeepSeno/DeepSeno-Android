package com.enmooy.deepseno.ui.screen.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.screen.common.EmptyStateView
import com.enmooy.deepseno.ui.theme.*
import com.enmooy.deepseno.ui.viewmodel.SourcesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    onRecordingClick: (Int) -> Unit = {},
    viewModel: SourcesViewModel = hiltViewModel(),
) {
    val t = LocalStrings.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadRecordings()
    }

    val filters = listOf(
        "all" to t.filterAll,
        "audio" to t.filterVoice,
        "video" to t.filterVideo,
        "document" to t.filterDocument,
        "image" to t.filterImage,
        "text" to t.filterText,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
    ) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BgSecondary)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = {
                    Text(
                        text = t.searchPlaceholder,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    cursorColor = AccentGreen,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { viewModel.search() },
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                ),
            )
            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = t.a11yClearSearch,
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable {
                            viewModel.setSearchQuery("")
                            viewModel.search()
                        },
                )
            }
        }

        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { (key, label) ->
                FilterChip(
                    key = key,
                    label = label,
                    isSelected = selectedFilter == key,
                    onClick = { viewModel.setSelectedFilter(key) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Content
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.loadRecordings() },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            when {
                // Loading state (initial)
                isLoading && recordings.isEmpty() && searchResults == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = AccentGreen)
                    }
                }

                // Search results
                searchResults != null -> {
                    val results = searchResults!!
                    if (results.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.SearchOff,
                            title = t.noResults,
                            subtitle = t.noResultsSubtitle,
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(results, key = { it.id }) { result ->
                                SearchResultCard(
                                    result = result,
                                    onClick = { onRecordingClick(result.recordingId) },
                                )
                            }
                        }
                    }
                }

                // Recordings list
                else -> {
                    val filtered = viewModel.filteredRecordings
                    if (filtered.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Inbox,
                            title = t.noSources,
                            subtitle = t.noSourcesSubtitle,
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(filtered, key = { it.id }) { recording ->
                                SourceCard(
                                    recording = recording,
                                    onClick = { onRecordingClick(recording.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    key: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) AccentGreen else BgSecondary
    val textColor = if (isSelected) BgPrimary else TextSecondary

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun SearchResultCard(
    result: com.enmooy.deepseno.data.remote.model.SearchResult,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = BgSecondary,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            result.recordingName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = result.displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                result.speakerName?.let { speaker ->
                    Text(
                        text = speaker,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                result.startTime?.let { time ->
                    val m = (time / 60).toInt()
                    val s = (time % 60).toInt()
                    Text(
                        text = String.format("%d:%02d", m, s),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}
