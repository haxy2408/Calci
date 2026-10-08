package com.example.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed AES-256 GCM encryption manager.
 * Media files are encrypted with authenticated encryption (AES/GCM/NoPadding)
 * before being written to private internal storage.
 *
 * Security guarantees:
 * - Master key resides in hardware-backed Android KeyStore (when available on device).
 * - Key never leaves the keystore in plaintext.
 * - Authenticated AES-GCM prevents tampering with ciphertext.
 * - Unique 12-byte random IV per file stored in the first 12 bytes of the encrypted file.
 */
object CryptoManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "CalciVaultMasterKey"
    private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
    private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
    private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    private const val IV_SIZE = 12 // 96 bits for GCM
    private const val TAG_SIZE_BITS = 128

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        // Generate a new 256-bit AES key inside Android KeyStore
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(false) // We manage IV securely via SecureRandom
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts an input stream to a target destination file.
     * The first 12 bytes of the file contain the random IV, followed by AES-GCM ciphertext + auth tag.
     */
    fun encryptStream(inputStream: InputStream, destinationFile: File): Long {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)

        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(TAG_SIZE_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        destinationFile.parentFile?.mkdirs()
        var totalBytesWritten: Long = 0

        FileOutputStream(destinationFile).use { fileOut ->
            // Write IV header first
            fileOut.write(iv)
            totalBytesWritten += IV_SIZE

            CipherOutputStream(fileOut, cipher).use { cipherOut ->
                val buffer = ByteArray(8192)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    cipherOut.write(buffer, 0, read)
                    totalBytesWritten += read
                }
                cipherOut.flush()
            }
        }
        return totalBytesWritten
    }

    /**
     * Encrypts in-memory bytes and writes to file.
     */
    fun encryptBytesToFile(bytes: ByteArray, destinationFile: File) {
        bytes.inputStream().use { input ->
            encryptStream(input, destinationFile)
        }
    }

    /**
     * Decrypts an encrypted file to a temporary stream or memory buffer.
     * Throws an exception if authentication tag mismatch or corrupted file.
     */
    fun decryptFileToBytes(encryptedFile: File): ByteArray {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)

        FileInputStream(encryptedFile).use { fileIn ->
            val iv = ByteArray(IV_SIZE)
            val ivRead = fileIn.read(iv)
            if (ivRead != IV_SIZE) {
                throw IllegalStateException("Corrupted encrypted file header (IV missing)")
            }

            val spec = GCMParameterSpec(TAG_SIZE_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            CipherInputStream(fileIn, cipher).use { cipherIn ->
                return cipherIn.readBytes()
            }
        }
    }

    /**
     * Decrypts an encrypted file directly into an output stream.
     */
    fun decryptFileToStream(encryptedFile: File, outputStream: OutputStream) {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)

        FileInputStream(encryptedFile).use { fileIn ->
            val iv = ByteArray(IV_SIZE)
            val ivRead = fileIn.read(iv)
            if (ivRead != IV_SIZE) {
                throw IllegalStateException("Corrupted encrypted file header (IV missing)")
            }

            val spec = GCMParameterSpec(TAG_SIZE_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            CipherInputStream(fileIn, cipher).use { cipherIn ->
                val buffer = ByteArray(8192)
                var read: Int
                while (cipherIn.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }
    }
}
