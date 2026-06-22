package com.enmooy.deepseno.ui.screen.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.*
import com.enmooy.deepseno.ui.viewmodel.CaptureViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextMemoDialog(
    viewModel: CaptureViewModel,
    onDismiss: () -> Unit,
) {
    val t = LocalStrings.current
    val memoText by viewModel.memoText.collectAsStateWithLifecycle()
    val sendEnabled = memoText.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgSecondary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = t.textMemo,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = memoText,
                onValueChange = { viewModel.memoText.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = {
                    Text(t.textMemo, color = TextSecondary)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentGreen,
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = BgTertiary,
                    focusedContainerColor = BgPrimary,
                    unfocusedContainerColor = BgPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.memoText.value = ""
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(t.cancel)
                }

                Button(
                    onClick = { viewModel.submitMemo() },
                    modifier = Modifier.weight(1f),
                    enabled = sendEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = BgPrimary,
                        disabledContainerColor = BgTertiary,
                        disabledContentColor = TextSecondary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(t.send)
                }
            }
        }
    }
}
