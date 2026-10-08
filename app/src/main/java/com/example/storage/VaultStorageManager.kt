package com.example.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.crypto.CryptoManager
import com.example.database.MediaType
import com.example.database.VaultItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Handles secure import, storage, decryption, and deletion of vault media.
 *
 * Privacy & Security measures:
 * 1. Storage Location: All encrypted files are placed in `context.filesDir/vault_secure/` (internal private storage).
 *    This location is sandboxed per-user and completely inaccessible to other apps.
 * 2. Gallery Exclusion: Internal private app storage is explicitly NOT scanned by Android MediaScanner or MediaStore.
 *    Additionally, a `.nomedia` file is placed in the vault directory as defense-in-depth.
 * 3. File Encryption: Stored files are ciphertext encrypted via AES-256 GCM using Android Keystore.
 *    Even with root or direct device file access, file contents cannot be parsed or viewed.
 * 4. Thumbnail Protection: Thumbnails are also encrypted with AES-256 GCM.
 * 5. Safe Temporary Decryption: When playing videos or displaying high-res photos, decrypted files
 *    are placed in private cache with zero external exposure and securely purged when finished.
 */
class VaultStorageManager(private val context: Context) {

    private val vaultDir: File by lazy {
        File(context.filesDir, "vault_secure").apply {
            if (!exists()) {
                mkdirs()
            }
            // Add .nomedia file for extra defense-in-depth against indexing
            val noMedia = File(this, ".nomedia")
            if (!noMedia.exists()) {
                try {
                    noMedia.createNewFile()
                } catch (ignored: Exception) {}
            }
        }
    }

    private val tempCacheDir: File by lazy {
        File(context.cacheDir, "vault_temp").apply {
            if (!exists()) {
                mkdirs()
            }
            val noMedia = File(this, ".nomedia")
            if (!noMedia.exists()) {
                try {
                    noMedia.createNewFile()
                } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * Imports a user-selected URI into the encrypted vault.
     */
    suspend fun importMedia(uri: Uri): VaultItemEntity = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val isVideo = mimeType.startsWith("video/")
        val mediaType = if (isVideo) MediaType.VIDEO else MediaType.PHOTO

        // Extract original file name
        var originalFileName = "imported_media_${System.currentTimeMillis()}"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                originalFileName = cursor.getString(nameIndex) ?: originalFileName
            }
        }

        val id = UUID.randomUUID().toString()
        val encryptedMediaFileName = "enc_${id}.bin"
        val encryptedMediaFile = File(vaultDir, encryptedMediaFileName)

        // Encrypt and stream media to private file
        contentResolver.openInputStream(uri)?.use { inStream ->
            CryptoManager.encryptStream(inStream, encryptedMediaFile)
        } ?: throw IllegalStateException("Could not read input stream from URI")

        val fileSize = encryptedMediaFile.length()

        // Generate encrypted thumbnail
        var encryptedThumbFileName: String? = null
        var durationMs: Long? = null
        var width = 0
        var height = 0

        try {
            if (mediaType == MediaType.PHOTO) {
                // Generate bitmap thumbnail from original URI
                contentResolver.openInputStream(uri)?.use { imgStream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(imgStream, null, options)
                    width = options.outWidth
                    height = options.outHeight
                }
                // Decode scaled sample for thumbnail
                contentResolver.openInputStream(uri)?.use { imgStream ->
                    val sampleOptions = BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(width, height, 300, 300)
                    }
                    val bitmap = BitmapFactory.decodeStream(imgStream, null, sampleOptions)
                    if (bitmap != null) {
                        val thumbFile = File(vaultDir, "thumb_${id}.bin")
                        val bos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                        CryptoManager.encryptBytesToFile(bos.toByteArray(), thumbFile)
                        encryptedThumbFileName = thumbFile.name
                        bitmap.recycle()
                    }
                }
            } else {
                // Video thumbnail and metadata
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durationMs = durationStr?.toLongOrNull()
                    val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    width = wStr?.toIntOrNull() ?: 0
                    height = hStr?.toIntOrNull() ?: 0

                    val frame = retriever.getFrameAtTime(1_000_000) // 1 second in
                    if (frame != null) {
                        val thumbFile = File(vaultDir, "thumb_${id}.bin")
                        val bos = ByteArrayOutputStream()
                        frame.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                        CryptoManager.encryptBytesToFile(bos.toByteArray(), thumbFile)
                        encryptedThumbFileName = thumbFile.name
                        frame.recycle()
                    }
                } finally {
                    retriever.release()
                }
            }
        } catch (e: Exception) {
            // Non-fatal if thumbnail fails
        }

        VaultItemEntity(
            id = id,
            encryptedFileName = encryptedMediaFileName,
            originalFileName = originalFileName,
            mediaType = mediaType,
            mimeType = mimeType,
            fileSize = fileSize,
            importedAt = System.currentTimeMillis(),
            encryptedThumbnailFileName = encryptedThumbFileName,
            durationMs = durationMs,
            width = width,
            height = height
        )
    }

    /**
     * Decrypts and loads a thumbnail Bitmap into memory.
     */
    suspend fun loadThumbnailBitmap(item: VaultItemEntity): Bitmap? = withContext(Dispatchers.IO) {
        val thumbName = item.encryptedThumbnailFileName
        if (thumbName != null) {
            val thumbFile = File(vaultDir, thumbName)
            if (thumbFile.exists()) {
                try {
                    val decryptedBytes = CryptoManager.decryptFileToBytes(thumbFile)
                    return@withContext BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
                } catch (e: Exception) {
                    // Fallback to full file if thumbnail decryption fails
                }
            }
        }

        // If no thumbnail exists or failed, attempt to decrypt part of photo
        if (item.mediaType == MediaType.PHOTO) {
            val fullFile = File(vaultDir, item.encryptedFileName)
            if (fullFile.exists()) {
                try {
                    val bytes = CryptoManager.decryptFileToBytes(fullFile)
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                } catch (e: Exception) {
                    return@withContext null
                }
            }
        }
        null
    }

    /**
     * Decrypts full photo bytes into memory for viewing.
     */
    suspend fun loadFullPhotoBytes(item: VaultItemEntity): ByteArray? = withContext(Dispatchers.IO) {
        val fullFile = File(vaultDir, item.encryptedFileName)
        if (!fullFile.exists()) return@withContext null
        try {
            CryptoManager.decryptFileToBytes(fullFile)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decrypts a video to a secure temporary cache file for ExoPlayer playback.
     */
    suspend fun decryptToTempCacheFile(item: VaultItemEntity): File? = withContext(Dispatchers.IO) {
        val encryptedFile = File(vaultDir, item.encryptedFileName)
        if (!encryptedFile.exists()) return@withContext null

        val tempFile = File(tempCacheDir, "temp_play_${item.id}.mp4")
        try {
            tempFile.outputStream().use { outStream ->
                CryptoManager.decryptFileToStream(encryptedFile, outStream)
            }
            tempFile
        } catch (e: Exception) {
            tempFile.delete()
            null
        }
    }

    /**
     * Deletes the item's encrypted media and thumbnail files from storage.
     */
    suspend fun deleteVaultItemFiles(item: VaultItemEntity) = withContext(Dispatchers.IO) {
        val file = File(vaultDir, item.encryptedFileName)
        if (file.exists()) file.delete()

        item.encryptedThumbnailFileName?.let {
            val thumb = File(vaultDir, it)
            if (thumb.exists()) thumb.delete()
        }

        // Also clean up temp file if present
        val tempFile = File(tempCacheDir, "temp_play_${item.id}.mp4")
        if (tempFile.exists()) tempFile.delete()
    }

    /**
     * Purges all temporary decrypted files in cache.
     * Called on vault lock or app backgrounding.
     */
    suspend fun clearDecryptedCache() = withContext(Dispatchers.IO) {
        tempCacheDir.listFiles()?.forEach { file ->
            if (file.name != ".nomedia") {
                file.delete()
            }
        }
    }

    /**
     * Creates a secure temporary file in private cache for CameraX capture.
     * Guaranteed to be outside public media stores and covered by .nomedia.
     */
    fun createTempCaptureFile(extension: String): File {
        return File(tempCacheDir, "capture_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")
    }

    /**
     * Directly imports a captured photo/video File from private cache into the encrypted vault,
     * encrypts it using Android Keystore AES-256 GCM, generates an encrypted thumbnail,
     * and deletes the temporary unencrypted capture file.
     */
    suspend fun importDirectFile(tempFile: File, mediaType: MediaType, originalName: String): VaultItemEntity = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val encryptedMediaFileName = "enc_${id}.bin"
            val encryptedMediaFile = File(vaultDir, encryptedMediaFileName)

            val mimeType = if (mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg"

            // Encrypt and stream media to private vault storage
            tempFile.inputStream().use { inStream ->
                CryptoManager.encryptStream(inStream, encryptedMediaFile)
            }

            val fileSize = encryptedMediaFile.length()
            var encryptedThumbFileName: String? = null
            var durationMs: Long? = null
            var width = 0
            var height = 0

            try {
                if (mediaType == MediaType.PHOTO) {
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(tempFile.absolutePath, boundsOptions)
                    width = boundsOptions.outWidth
                    height = boundsOptions.outHeight

                    val sampleOptions = BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(width, height, 300, 300)
                    }
                    val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, sampleOptions)
                    if (bitmap != null) {
                        val thumbFile = File(vaultDir, "thumb_${id}.bin")
                        val bos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                        CryptoManager.encryptBytesToFile(bos.toByteArray(), thumbFile)
                        encryptedThumbFileName = thumbFile.name
                        bitmap.recycle()
                    }
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(tempFile.absolutePath)
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        durationMs = durationStr?.toLongOrNull()
                        val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        width = wStr?.toIntOrNull() ?: 0
                        height = hStr?.toIntOrNull() ?: 0

                        val frame = retriever.getFrameAtTime(1_000_000)
                        if (frame != null) {
                            val thumbFile = File(vaultDir, "thumb_${id}.bin")
                            val bos = ByteArrayOutputStream()
                            frame.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                            CryptoManager.encryptBytesToFile(bos.toByteArray(), thumbFile)
                            encryptedThumbFileName = thumbFile.name
                            frame.recycle()
                        }
                    } finally {
                        retriever.release()
                    }
                }
            } catch (ignored: Exception) {
                // Non-fatal if thumbnail fails
            }

            VaultItemEntity(
                id = id,
                encryptedFileName = encryptedMediaFileName,
                originalFileName = originalName,
                mediaType = mediaType,
                mimeType = mimeType,
                fileSize = fileSize,
                importedAt = System.currentTimeMillis(),
                encryptedThumbnailFileName = encryptedThumbFileName,
                durationMs = durationMs,
                width = width,
                height = height
            )
        } finally {
            // Delete the unencrypted temporary capture file immediately
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
