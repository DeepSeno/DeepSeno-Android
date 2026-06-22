package com.enmooy.deepseno.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.AccentGreen
import com.enmooy.deepseno.ui.theme.AccentRed
import com.enmooy.deepseno.ui.theme.BgTertiary
import com.enmooy.deepseno.ui.theme.TextSecondary

/**
 * Shows connection status + transport mode (P2P Direct / Server Relay).
 *
 * transportMode: "none" | "p2p" | "relay"
 */
@Composable
fun ConnectionBadge(
    isConnected: Boolean,
    hostName: String?,
    transportMode: String = "none",
    modifier: Modifier = Modifier,
) {
    val t = LocalStrings.current
    val dotColor = if (isConnected) AccentGreen else AccentRed
    val textColor = if (isConnected) AccentGreen else TextSecondary
    val label = if (isConnected) (hostName ?: t.connected) else t.disconnected

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(BgTertiary.copy(alpha = 0.8f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = textColor,
            modifier = Modifier.padding(start = 6.dp),
        )

        // Show transport mode when connected
        if (isConnected && transportMode != "none") {
            val modeLabel = when (transportMode) {
                "p2p" -> t.transportP2P
                "relay" -> t.transportRelay
                else -> ""
            }
            val modeColor = when (transportMode) {
                "p2p" -> AccentGreen
                "relay" -> Color(0xFFF59E0B) // amber
                else -> TextSecondary
            }
            if (modeLabel.isNotEmpty()) {
                Text(
                    text = "·",
                    style = TextStyle(fontSize = 12.sp),
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Text(
                    text = modeLabel,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = modeColor,
                )
            }
        }
    }
}
