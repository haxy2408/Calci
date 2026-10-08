package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaType {
    PHOTO,
    VIDEO
}

/**
 * Vault item metadata record.
 * Important: The actual media content is encrypted and stored in app's private files.
 * This entity stores metadata references only.
 */
@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey
    val id: String,
    val encryptedFileName: String,
    val originalFileName: String,
    val mediaType: MediaType,
    val mimeType: String,
    val fileSize: Long,
    val importedAt: Long,
    val encryptedThumbnailFileName: String? = null,
    val durationMs: Long? = null,
    val width: Int = 0,
    val height: Int = 0
)
