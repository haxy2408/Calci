package com.example.vault

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.MediaType
import com.example.database.VaultDatabase
import com.example.database.VaultItemEntity
import com.example.storage.VaultStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class VaultFilter {
    ALL,
    PHOTOS,
    VIDEOS
}

enum class VaultSort {
    NEWEST_FIRST,
    OLDEST_FIRST,
    LARGEST_FIRST,
    SMALLEST_FIRST
}

data class VaultUiState(
    val isImporting: Boolean = false,
    val importProgressMessage: String? = null,
    val selectedItemIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val activeFilter: VaultFilter = VaultFilter.ALL,
    val activeSort: VaultSort = VaultSort.NEWEST_FIRST,
    val totalStorageBytes: Long = 0L,
    val totalCount: Int = 0,
    val errorMessage: String? = null,
    val activeViewingItem: VaultItemEntity? = null
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val db = VaultDatabase.getDatabase(application)
    private val dao = db.vaultDao()
    private val storageManager = VaultStorageManager(application)

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    // Thumbnail in-memory cache to prevent frequent decryptions
    private val thumbnailCache = mutableMapOf<String, Bitmap>()
    private val _thumbnailVersions = MutableStateFlow(0)
    val thumbnailVersions: StateFlow<Int> = _thumbnailVersions.asStateFlow()

    private val _itemsFlow = dao.getAllItems()

    val filteredItems: StateFlow<List<VaultItemEntity>> = combine(
        _itemsFlow,
        _uiState
    ) { items, state ->
        val filtered = when (state.activeFilter) {
            VaultFilter.ALL -> items
            VaultFilter.PHOTOS -> items.filter { it.mediaType == MediaType.PHOTO }
            VaultFilter.VIDEOS -> items.filter { it.mediaType == MediaType.VIDEO }
        }

        when (state.activeSort) {
            VaultSort.NEWEST_FIRST -> filtered.sortedByDescending { it.importedAt }
            VaultSort.OLDEST_FIRST -> filtered.sortedBy { it.importedAt }
            VaultSort.LARGEST_FIRST -> filtered.sortedByDescending { it.fileSize }
            VaultSort.SMALLEST_FIRST -> filtered.sortedBy { it.fileSize }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshStorageInfo()
    }

    fun setFilter(filter: VaultFilter) {
        _uiState.value = _uiState.value.copy(activeFilter = filter)
    }

    fun setSort(sort: VaultSort) {
        _uiState.value = _uiState.value.copy(activeSort = sort)
    }

    fun toggleSelection(itemId: String) {
        val current = _uiState.value.selectedItemIds.toMutableSet()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        _uiState.value = _uiState.value.copy(
            selectedItemIds = current,
            isSelectionMode = current.isNotEmpty()
        )
    }

    fun selectAll(items: List<VaultItemEntity>) {
        val allIds = items.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedItemIds = allIds,
            isSelectionMode = allIds.isNotEmpty()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedItemIds = emptySet(),
            isSelectionMode = false
        )
    }

    fun openItemViewer(item: VaultItemEntity) {
        _uiState.value = _uiState.value.copy(activeViewingItem = item)
    }

    fun closeItemViewer() {
        _uiState.value = _uiState.value.copy(activeViewingItem = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun importMediaUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, importProgressMessage = "Encrypting and storing media...")
            try {
                var count = 0
                for (uri in uris) {
                    count++
                    _uiState.value = _uiState.value.copy(importProgressMessage = "Encrypting file $count of ${uris.size}...")
                    val entity = storageManager.importMedia(uri)
                    dao.insertItem(entity)
                }
                refreshStorageInfo()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to import some media: ${e.localizedMessage ?: "Unknown error"}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isImporting = false, importProgressMessage = null)
            }
        }
    }

    fun createTempCaptureFile(extension: String): File {
        return storageManager.createTempCaptureFile(extension)
    }

    fun importDirectCaptureFile(tempFile: File, mediaType: MediaType, originalName: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, importProgressMessage = "Encrypting camera capture...")
            try {
                val entity = storageManager.importDirectFile(tempFile, mediaType, originalName)
                dao.insertItem(entity)
                refreshStorageInfo()
                onComplete()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to encrypt captured media: ${e.localizedMessage ?: "Unknown error"}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isImporting = false, importProgressMessage = null)
            }
        }
    }

    fun deleteSelectedItems() {
        val selected = _uiState.value.selectedItemIds.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            try {
                for (id in selected) {
                    val item = dao.getItemById(id)
                    if (item != null) {
                        storageManager.deleteVaultItemFiles(item)
                    }
                    thumbnailCache.remove(id)
                }
                dao.deleteItemsByIds(selected)
                clearSelection()
                refreshStorageInfo()
                _thumbnailVersions.value += 1
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error deleting files: ${e.localizedMessage}"
                )
            }
        }
    }

    fun deleteSingleItem(item: VaultItemEntity) {
        viewModelScope.launch {
            try {
                storageManager.deleteVaultItemFiles(item)
                dao.deleteItem(item)
                thumbnailCache.remove(item.id)
                if (_uiState.value.activeViewingItem?.id == item.id) {
                    closeItemViewer()
                }
                refreshStorageInfo()
                _thumbnailVersions.value += 1
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error deleting file: ${e.localizedMessage}"
                )
            }
        }
    }

    fun getCachedThumbnail(item: VaultItemEntity): Bitmap? {
        return thumbnailCache[item.id]
    }

    suspend fun loadThumbnail(item: VaultItemEntity): Bitmap? {
        thumbnailCache[item.id]?.let { return it }
        val bmp = storageManager.loadThumbnailBitmap(item)
        if (bmp != null) {
            thumbnailCache[item.id] = bmp
            _thumbnailVersions.value += 1
        }
        return bmp
    }

    suspend fun loadPhotoBytes(item: VaultItemEntity): ByteArray? {
        return storageManager.loadFullPhotoBytes(item)
    }

    suspend fun decryptVideoForPlayback(item: VaultItemEntity): File? {
        return storageManager.decryptToTempCacheFile(item)
    }

    fun onVaultLocked() {
        viewModelScope.launch {
            thumbnailCache.clear()
            storageManager.clearDecryptedCache()
            clearSelection()
            closeItemViewer()
        }
    }

    private fun refreshStorageInfo() {
        viewModelScope.launch {
            val totalBytes = dao.getTotalStorageUsed() ?: 0L
            val count = dao.getItemCount()
            _uiState.value = _uiState.value.copy(
                totalStorageBytes = totalBytes,
                totalCount = count
            )
        }
    }
}
