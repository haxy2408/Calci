package com.example.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * First-run vault setup dialog for configuring PIN and biometric authentication.
 * Presented unobtrusively on first launch or when accessing settings for the first time.
 */
@Composable
fun SetupVaultDialog(
    onCompleteSetup: (pin: String, enableBiometrics: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var enableBiometrics by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Setup Security",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Setup Security PIN",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Create a 4 to 8-digit PIN as a backup for biometric verification.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 8 && it.all { ch -> ch.isDigit() }) {
                            pin = it
                            errorText = null
                        }
                    },
                    label = { Text("New PIN (4-8 digits)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("setup_new_pin_input")
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        if (it.length <= 8 && it.all { ch -> ch.isDigit() }) {
                            confirmPin = it
                            errorText = null
                        }
                    },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (pin.length < 4) {
                                errorText = "PIN must be at least 4 digits"
                            } else if (pin != confirmPin) {
                                errorText = "PINs do not match"
                            } else {
                                onCompleteSetup(pin, enableBiometrics)
                            }
                        }
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("setup_confirm_pin_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = enableBiometrics,
                        onCheckedChange = { enableBiometrics = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enable Fingerprint Biometrics",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length < 4) {
                        errorText = "PIN must be at least 4 digits"
                    } else if (pin != confirmPin) {
                        errorText = "PINs do not match"
                    } else {
                        onCompleteSetup(pin, enableBiometrics)
                    }
                },
                enabled = pin.length >= 4 && confirmPin.isNotEmpty(),
                modifier = Modifier.testTag("setup_save_button")
            ) {
                Text("Save & Continue")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("setup_skip_button")
            ) {
                Text("Later")
            }
        }
    )
}
