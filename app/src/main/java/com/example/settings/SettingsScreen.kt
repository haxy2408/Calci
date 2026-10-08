package com.example.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.CalculatorEngine

/**
 * Settings Screen.
 * Contains:
 * - General calculator settings (decimal places, vibration feedback)
 * - Security settings (Change secret trigger expression, change PIN, biometrics toggle, auto-lock toggle)
 * - Vault storage stats
 * - Security Architecture overview & App version
 *
 * NOTE: Changing secret operation or PIN requires prior authentication.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    secretExpression: String,
    isBiometricsEnabled: Boolean,
    isAutoLockEnabled: Boolean,
    totalStorageBytes: Long,
    totalVaultItems: Int,
    onBack: () -> Unit,
    onUpdateSecretExpression: (String) -> Unit,
    onChangePin: (oldPin: String, newPin: String) -> Boolean,
    onToggleBiometrics: (Boolean) -> Unit,
    onToggleAutoLock: (Boolean) -> Unit,
    onRequestSensitiveAuth: (actionName: String, onAuthenticated: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var showChangeTriggerDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Security & Protection
            SettingsSectionHeader("Security & Protection")

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Secret Expression
                    SettingsRow(
                        icon = Icons.Default.Calculate,
                        title = "Secret Operation Trigger",
                        subtitle = "Current: $secretExpression",
                        onClick = {
                            onRequestSensitiveAuth("Change Secret Operation") {
                                showChangeTriggerDialog = true
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Change PIN
                    SettingsRow(
                        icon = Icons.Default.Pin,
                        title = "Change Vault PIN",
                        subtitle = "Update your secure backup PIN",
                        onClick = {
                            onRequestSensitiveAuth("Change Vault PIN") {
                                showChangePinDialog = true
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Biometrics toggle (disabling requires sensitive auth)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Biometric Authentication", fontWeight = FontWeight.Medium)
                                Text("Prompt fingerprint after secret trigger", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = isBiometricsEnabled,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    onRequestSensitiveAuth("Disable Biometrics") {
                                        onToggleBiometrics(false)
                                    }
                                } else {
                                    onToggleBiometrics(true)
                                }
                            },
                            modifier = Modifier.testTag("toggle_biometrics_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Auto-lock on background toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LockClock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Auto-Lock on Background", fontWeight = FontWeight.Medium)
                                Text("Instantly lock vault when app leaves foreground", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = isAutoLockEnabled,
                            onCheckedChange = onToggleAutoLock,
                            modifier = Modifier.testTag("toggle_autolock_switch")
                        )
                    }
                }
            }

            // Section 2: Vault Storage
            SettingsSectionHeader("Vault Storage")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Encrypted Media Items", style = MaterialTheme.typography.bodyMedium)
                        Text("$totalVaultItems items", fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Storage Utilized", style = MaterialTheme.typography.bodyMedium)
                        Text(formatStorageSize(totalStorageBytes), fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Stored in sandboxed app files, encrypted with AES-256 GCM, and excluded from Gallery indexing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section 3: About & Architecture
            SettingsSectionHeader("About & Encryption")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Calci Secure Architecture", fontWeight = FontWeight.SemiBold)
                    Text("• Master Key: Android KeyStore (AES-256)", style = MaterialTheme.typography.bodySmall)
                    Text("• Cipher Mode: AES/GCM/NoPadding (128-bit auth tag)", style = MaterialTheme.typography.bodySmall)
                    Text("• Storage: Private Sandboxed Internal Storage", style = MaterialTheme.typography.bodySmall)
                    Text("• Gallery Isolation: Zero MediaStore Registration + .nomedia", style = MaterialTheme.typography.bodySmall)
                    Text("• Version: 1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Change Trigger Dialog
    if (showChangeTriggerDialog) {
        ChangeTriggerDialog(
            currentExpression = secretExpression,
            onDismiss = { showChangeTriggerDialog = false },
            onConfirm = { newExpr ->
                onUpdateSecretExpression(newExpr)
                showChangeTriggerDialog = false
            }
        )
    }

    // Change PIN Dialog
    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onChangePin = onChangePin
        )
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ChangeTriggerDialog(
    currentExpression: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentExpression) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Secret Trigger") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter a calculator expression (e.g. '2 + 9', '7 × 8', or '100 - 37'). Pressing '=' after evaluating this will trigger authentication.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (error != null) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    label = { Text("Expression") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("change_secret_expr_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (CalculatorEngine.isValidExpression(text)) {
                        onConfirm(text)
                    } else {
                        error = "Please enter a valid arithmetic operation (e.g. 7 * 8 or 5 + 12)"
                    }
                },
                modifier = Modifier.testTag("confirm_change_secret_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit,
    onChangePin: (oldPin: String, newPin: String) -> Boolean
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Vault PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (error != null) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) oldPin = it },
                    label = { Text("Current PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("old_pin_input")
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("New PIN (4-8 digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("new_pin_input")
                )

                OutlinedTextField(
                    value = confirmNewPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmNewPin = it },
                    label = { Text("Confirm New PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("confirm_new_pin_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin.length < 4) {
                        error = "New PIN must be at least 4 digits"
                    } else if (newPin != confirmNewPin) {
                        error = "New PINs do not match"
                    } else {
                        val success = onChangePin(oldPin, newPin)
                        if (success) {
                            onDismiss()
                        } else {
                            error = "Current PIN is incorrect"
                        }
                    }
                },
                modifier = Modifier.testTag("save_pin_change_button")
            ) {
                Text("Update PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
