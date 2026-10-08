package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.auth.BiometricAuthHelper
import com.example.auth.PinAuthDialog
import com.example.auth.PinAuthManager
import com.example.auth.SetupVaultDialog
import com.example.calculator.CalculatorEngine
import com.example.calculator.CalculatorScreen
import com.example.database.VaultDatabase
import com.example.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.vault.VaultCameraScreen
import com.example.vault.VaultScreen
import com.example.vault.VaultViewModel
import kotlinx.coroutines.launch

enum class AppScreen {
    CALCULATOR,
    VAULT,
    CAMERA,
    SETTINGS
}

class MainActivity : FragmentActivity() {

    private lateinit var pinAuthManager: PinAuthManager
    private lateinit var biometricAuthHelper: BiometricAuthHelper
    private val vaultViewModel: VaultViewModel by viewModels()

    // Authentication session state
    private val _isVaultUnlocked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pinAuthManager = PinAuthManager(this)
        biometricAuthHelper = BiometricAuthHelper(this)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Auto-lock vault on background if enabled
        if (pinAuthManager.isAutoLockOnBackground) {
            lockVaultSession()
        }
    }

    private fun lockVaultSession() {
        if (_isVaultUnlocked.value) {
            _isVaultUnlocked.value = false
            vaultViewModel.onVaultLocked()
            // Clear FLAG_SECURE if set
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun unlockVaultSession() {
        _isVaultUnlocked.value = true
        // Set FLAG_SECURE so private vault contents do not show in recent-apps switcher preview
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    @Composable
    private fun AppNavHost() {
        var currentScreen by remember { mutableStateOf(AppScreen.CALCULATOR) }
        val isVaultUnlocked by _isVaultUnlocked

        var showSetupDialog by remember { mutableStateOf(!pinAuthManager.isSetupCompleted) }
        var showPinFallbackDialog by remember { mutableStateOf(false) }
        var pinErrorMessage by remember { mutableStateOf<String?>(null) }
        var remainingLockoutSeconds by remember { mutableStateOf(pinAuthManager.getRemainingLockoutSeconds()) }

        // Sensitive auth state (for protected changes inside Vault Settings)
        var pendingSensitiveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        var sensitiveAuthTitle by remember { mutableStateOf("Security Verification") }

        // Settings state
        var secretExpression by remember { mutableStateOf(pinAuthManager.secretExpression) }
        var biometricsEnabled by remember { mutableStateOf(pinAuthManager.isBiometricsEnabled) }
        var autoLockEnabled by remember { mutableStateOf(pinAuthManager.isAutoLockOnBackground) }

        val coroutineScope = rememberCoroutineScope()
        val dao = remember { VaultDatabase.getDatabase(applicationContext).vaultDao() }
        var totalStorageBytes by remember { mutableStateOf(0L) }
        var totalVaultItems by remember { mutableStateOf(0) }

        fun refreshStats() {
            coroutineScope.launch {
                totalStorageBytes = dao.getTotalStorageUsed() ?: 0L
                totalVaultItems = dao.getItemCount()
            }
        }

        // Triggered after secret expression evaluation (e.g. 2 + 9 =)
        fun startBiometricOrPinFlow() {
            if (!pinAuthManager.isSetupCompleted) {
                // If user hasn't set up a PIN yet, prompt setup first
                showSetupDialog = true
                return
            }

            val bioStatus = biometricAuthHelper.checkBiometricAvailability()
            val useBiometrics = biometricsEnabled && bioStatus == BiometricAuthHelper.BiometricStatus.AVAILABLE

            if (useBiometrics) {
                biometricAuthHelper.promptBiometric(
                    title = "Security Verification",
                    subtitle = "Verify your fingerprint to continue",
                    negativeButtonText = "Use PIN",
                    onSuccess = {
                        unlockVaultSession()
                        currentScreen = AppScreen.VAULT
                    },
                    onError = { _, _ ->
                        // Biometrics failed or negative button ("Use PIN") clicked
                        remainingLockoutSeconds = pinAuthManager.getRemainingLockoutSeconds()
                        showPinFallbackDialog = true
                    },
                    onFailed = {
                        // Single attempt failure: BiometricPrompt handles inline feedback
                    }
                )
            } else {
                // Biometrics not available or disabled: fallback directly to PIN dialog
                remainingLockoutSeconds = pinAuthManager.getRemainingLockoutSeconds()
                pinErrorMessage = null
                showPinFallbackDialog = true
            }
        }

        // Sensitive re-auth helper for protected settings inside vault
        fun requestSensitiveAuth(actionName: String, onAuthenticated: () -> Unit) {
            val bioStatus = biometricAuthHelper.checkBiometricAvailability()
            val useBiometrics = biometricsEnabled && bioStatus == BiometricAuthHelper.BiometricStatus.AVAILABLE

            sensitiveAuthTitle = "Confirm: $actionName"
            pendingSensitiveAction = onAuthenticated

            if (useBiometrics) {
                biometricAuthHelper.promptBiometric(
                    title = sensitiveAuthTitle,
                    subtitle = "Confirm your identity to apply this change",
                    negativeButtonText = "Use PIN",
                    onSuccess = {
                        pendingSensitiveAction?.invoke()
                        pendingSensitiveAction = null
                    },
                    onError = { _, _ ->
                        remainingLockoutSeconds = pinAuthManager.getRemainingLockoutSeconds()
                        showPinFallbackDialog = true
                    },
                    onFailed = {}
                )
            } else {
                remainingLockoutSeconds = pinAuthManager.getRemainingLockoutSeconds()
                pinErrorMessage = null
                showPinFallbackDialog = true
            }
        }

        // BackHandler for secondary screens
        BackHandler(enabled = currentScreen != AppScreen.CALCULATOR) {
            when (currentScreen) {
                AppScreen.CAMERA, AppScreen.SETTINGS -> {
                    currentScreen = AppScreen.VAULT
                }
                AppScreen.VAULT -> {
                    lockVaultSession()
                    currentScreen = AppScreen.CALCULATOR
                }
                AppScreen.CALCULATOR -> {}
            }
        }

        when (currentScreen) {
            AppScreen.CALCULATOR -> {
                CalculatorScreen(
                    onSecretTriggered = {
                        startBiometricOrPinFlow()
                    },
                    secretExpression = secretExpression
                )
            }

            AppScreen.VAULT -> {
                if (isVaultUnlocked) {
                    VaultScreen(
                        vaultViewModel = vaultViewModel,
                        onLockVault = {
                            lockVaultSession()
                            currentScreen = AppScreen.CALCULATOR
                        },
                        onOpenCamera = {
                            currentScreen = AppScreen.CAMERA
                        },
                        onOpenSettings = {
                            refreshStats()
                            currentScreen = AppScreen.SETTINGS
                        }
                    )
                } else {
                    // Safety check: if vault locked, revert to calculator
                    currentScreen = AppScreen.CALCULATOR
                }
            }

            AppScreen.CAMERA -> {
                if (isVaultUnlocked) {
                    VaultCameraScreen(
                        vaultViewModel = vaultViewModel,
                        onBack = {
                            currentScreen = AppScreen.VAULT
                        }
                    )
                } else {
                    currentScreen = AppScreen.CALCULATOR
                }
            }

            AppScreen.SETTINGS -> {
                if (isVaultUnlocked) {
                    SettingsScreen(
                        secretExpression = secretExpression,
                        isBiometricsEnabled = biometricsEnabled,
                        isAutoLockEnabled = autoLockEnabled,
                        totalStorageBytes = totalStorageBytes,
                        totalVaultItems = totalVaultItems,
                        onBack = { currentScreen = AppScreen.VAULT },
                        onUpdateSecretExpression = { newExpr ->
                            pinAuthManager.setSecretExpression(newExpr)
                            secretExpression = newExpr
                        },
                        onChangePin = { oldPin, newPin ->
                            if (pinAuthManager.verifyPin(oldPin)) {
                                pinAuthManager.savePin(newPin)
                                true
                            } else {
                                false
                            }
                        },
                        onToggleBiometrics = { enabled ->
                            pinAuthManager.setBiometricsEnabled(enabled)
                            biometricsEnabled = enabled
                        },
                        onToggleAutoLock = { enabled ->
                            pinAuthManager.setAutoLockOnBackground(enabled)
                            autoLockEnabled = enabled
                        },
                        onRequestSensitiveAuth = { actionName, onAuthSuccess ->
                            requestSensitiveAuth(actionName, onAuthSuccess)
                        }
                    )
                } else {
                    currentScreen = AppScreen.CALCULATOR
                }
            }
        }

        // First-run setup modal dialog
        if (showSetupDialog) {
            SetupVaultDialog(
                onCompleteSetup = { pin, enableBio ->
                    pinAuthManager.savePin(pin)
                    pinAuthManager.setBiometricsEnabled(enableBio)
                    biometricsEnabled = enableBio
                    showSetupDialog = false
                },
                onDismiss = {
                    showSetupDialog = false
                }
            )
        }

        // PIN Fallback / Sensitive Auth Dialog
        if (showPinFallbackDialog) {
            PinAuthDialog(
                title = if (pendingSensitiveAction != null) sensitiveAuthTitle else "Enter Security PIN",
                remainingLockoutSeconds = remainingLockoutSeconds,
                errorMessage = pinErrorMessage,
                onVerifyPin = { enteredPin ->
                    val valid = pinAuthManager.verifyPin(enteredPin)
                    if (valid) {
                        showPinFallbackDialog = false
                        pinErrorMessage = null
                        if (pendingSensitiveAction != null) {
                            pendingSensitiveAction?.invoke()
                            pendingSensitiveAction = null
                        } else {
                            unlockVaultSession()
                            currentScreen = AppScreen.VAULT
                        }
                    } else {
                        val lockout = pinAuthManager.getRemainingLockoutSeconds()
                        remainingLockoutSeconds = lockout
                        pinErrorMessage = if (lockout > 0) {
                            "Locked out for $lockout seconds"
                        } else {
                            "Incorrect PIN. Please try again."
                        }
                    }
                },
                onCancel = {
                    showPinFallbackDialog = false
                    pinErrorMessage = null
                    pendingSensitiveAction = null
                },
                onRetryBiometric = {
                    showPinFallbackDialog = false
                    if (pendingSensitiveAction != null) {
                        val action = pendingSensitiveAction!!
                        requestSensitiveAuth(sensitiveAuthTitle, action)
                    } else {
                        startBiometricOrPinFlow()
                    }
                }
            )
        }
    }
}
