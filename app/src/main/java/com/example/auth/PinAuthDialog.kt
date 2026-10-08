package com.example.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Clean PIN authentication dialog/screen fallback when fingerprint is cancelled,
 * hardware unavailable, or during initial first-run setup.
 */
@Composable
fun PinAuthDialog(
    title: String = "Enter Security PIN",
    subtitle: String = "Biometric authentication fallback",
    remainingLockoutSeconds: Long = 0L,
    errorMessage: String? = null,
    onVerifyPin: (String) -> Unit,
    onCancel: () -> Unit,
    onRetryBiometric: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var pinText by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "PIN Lock",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (remainingLockoutSeconds > 0) {
                    Text(
                        text = "Too many failed attempts. Try again in $remainingLockoutSeconds seconds.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                } else if (!errorMessage.isNullOrEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedTextField(
                    value = pinText,
                    onValueChange = { if (it.length <= 8 && it.all { ch -> ch.isDigit() }) pinText = it },
                    label = { Text("PIN") },
                    singleLine = true,
                    enabled = remainingLockoutSeconds == 0L,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (pinText.isNotBlank()) onVerifyPin(pinText)
                        }
                    ),
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(
                                imageVector = if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPin) "Hide PIN" else "Show PIN"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_input_field")
                )

                if (onRetryBiometric != null) {
                    TextButton(
                        onClick = onRetryBiometric,
                        modifier = Modifier.testTag("retry_biometric_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Retry Fingerprint",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Fingerprint")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onVerifyPin(pinText) },
                enabled = pinText.length >= 4 && remainingLockoutSeconds == 0L,
                modifier = Modifier.testTag("confirm_pin_button")
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_pin_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
