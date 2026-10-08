package com.example.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_items ORDER BY importedAt DESC")
    fun getAllItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE mediaType = :type ORDER BY importedAt DESC")
    fun getItemsByType(type: MediaType): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: VaultItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<VaultItemEntity>)

    @Delete
    suspend fun deleteItem(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id IN (:ids)")
    suspend fun deleteItemsByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM vault_items")
    suspend fun getItemCount(): Int

    @Query("SELECT SUM(fileSize) FROM vault_items")
    suspend fun getTotalStorageUsed(): Long?
}
