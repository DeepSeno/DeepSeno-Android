package com.enmooy.deepseno.ui.screen.briefing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.enmooy.deepseno.data.remote.model.ExtractedItem
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.TextTertiary

/**
 * Trailing "…" button on each Briefing item. Opens a small menu with
 * "View quote" (when source available) and "Ask AI about this".
 *
 * Replaces the previous long-press / combinedClickable approach: long-press
 * on every row of a scrolling list competes with the user's vertical drag,
 * making the list feel unscrollable. An explicit button has zero conflict.
 */
@Composable
fun BriefingItemMenu(
    item: ExtractedItem,
    onShowQuote: () -> Unit,
    onAskAI: (prompt: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    val moreActionsLabel = t.briefingMoreActions

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = modifier
                .size(32.dp)
                .semantics { contentDescription = moreActionsLabel },
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (item.hasSource) {
                DropdownMenuItem(
                    text = { Text(t.briefingQuoteSheetTitle) },
                    leadingIcon = {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    onClick = {
                        expanded = false
                        onShowQuote()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(t.briefingAskAI) },
                leadingIcon = {
                    Icon(Icons.Default.QuestionAnswer, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                onClick = {
                    expanded = false
                    onAskAI(t.briefingAskAIPrefixFormat.format(item.content))
                },
            )
        }
    }
}
