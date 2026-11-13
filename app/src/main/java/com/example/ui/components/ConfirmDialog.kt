package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.ConfirmDialogState
import com.example.ui.theme.*

@Composable
fun ConfirmDialog(
    state: ConfirmDialogState?,
    onDismiss: () -> Unit
) {
    if (state == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = state.title,
                    style = Typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = AppTheme.colors.text
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = state.message,
                    style = Typography.bodyMedium.copy(
                        color = AppTheme.colors.textMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    modifier = Modifier.padding(bottom = 22.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModernOutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, AppTheme.colors.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.textMuted),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(state.cancelText, fontWeight = FontWeight.SemiBold)
                    }

                    ModernButton(
                        onClick = {
                            state.onConfirm()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClayOrange,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(state.confirmText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

