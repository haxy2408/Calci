package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Handles PIN hashing, verification, lockout counter, and security settings.
 * PIN is salted with a unique 32-byte cryptographically secure salt,
 * hashed with multiple rounds of SHA-256, and never stored in plain text.
 */
class PinAuthManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "calci_auth_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_TIMESTAMP = "lockout_timestamp"
        private const val KEY_SECRET_EXPRESSION = "secret_expression"
        private const val KEY_BIOMETRICS_ENABLED = "biometrics_enabled"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_AUTO_LOCK_ON_BACKGROUND = "auto_lock_background"

        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds
    }

    val isSetupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)

    val isBiometricsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRICS_ENABLED, true)

    val isAutoLockOnBackground: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOCK_ON_BACKGROUND, true)

    val secretExpression: String
        get() = prefs.getString(KEY_SECRET_EXPRESSION, "2 + 9") ?: "2 + 9"

    fun setBiometricsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRICS_ENABLED, enabled).apply()
    }

    fun setAutoLockOnBackground(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LOCK_ON_BACKGROUND, enabled).apply()
    }

    fun setSecretExpression(expression: String) {
        prefs.edit().putString(KEY_SECRET_EXPRESSION, expression.trim()).apply()
    }

    /**
     * Hashes the PIN using a random 32-byte salt and SHA-256 (10,000 iterations).
     */
    fun savePin(pin: String) {
        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        val hash = hashPinWithSalt(pin, salt)

        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_TIMESTAMP, 0L)
            .apply()
    }

    /**
     * Checks if PIN verification is temporarily locked out due to repeated wrong attempts.
     * Returns remaining lockout seconds (0 if not locked out).
     */
    fun getRemainingLockoutSeconds(): Long {
        val failed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        if (failed < MAX_FAILED_ATTEMPTS) return 0L

        val lockoutStart = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        val elapsed = now - lockoutStart
        return if (elapsed < LOCKOUT_DURATION_MS) {
            ((LOCKOUT_DURATION_MS - elapsed) / 1000) + 1
        } else {
            0L
        }
    }

    /**
     * Verifies the provided PIN against the stored salted hash.
     */
    fun verifyPin(pin: String): Boolean {
        if (getRemainingLockoutSeconds() > 0) {
            return false
        }

        val saltStr = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val storedHashStr = prefs.getString(KEY_PIN_HASH, null) ?: return false

        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        val computedHash = hashPinWithSalt(pin, salt)
        val computedHashStr = Base64.encodeToString(computedHash, Base64.NO_WRAP)

        val matches = MessageDigest.isEqual(
            computedHashStr.toByteArray(Charsets.UTF_8),
            storedHashStr.toByteArray(Charsets.UTF_8)
        )

        if (matches) {
            // Reset failed counter
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_TIMESTAMP, 0L)
                .apply()
            return true
        } else {
            // Increment failed counter
            val newCount = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, newCount)
            if (newCount >= MAX_FAILED_ATTEMPTS) {
                editor.putLong(KEY_LOCKOUT_TIMESTAMP, System.currentTimeMillis())
            }
            editor.apply()
            return false
        }
    }

    private fun hashPinWithSalt(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        var hash = digest.digest(pin.toByteArray(Charsets.UTF_8))
        // Multiple iterations to slow down brute force attacks
        for (i in 0 until 10_000) {
            digest.reset()
            digest.update(hash)
            hash = digest.digest(salt)
        }
        return hash
    }
}
