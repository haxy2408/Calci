package com.example.vault

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.MediaType
import com.example.database.VaultItemEntity

/**
 * Vault Grid Screen.
 * Displays stored photos and videos in a responsive grid.
 * Allows importing media via Android's system picker, multi-select, deletion, sorting, filtering,
 * and lock button to return immediately to calculator.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    vaultViewModel: VaultViewModel,
    onLockVault: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by vaultViewModel.uiState.collectAsState()
    val items by vaultViewModel.filteredItems.collectAsState()
    val thumbVersions by vaultViewModel.thumbnailVersions.collectAsState()

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showImportNoticeDialog by remember { mutableStateOf(false) }

    // System Photo/Video Picker launcher
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            vaultViewModel.importMediaUris(uris)
        }
    }

    // Active full-screen viewing
    if (uiState.activeViewingItem != null) {
        MediaViewerScreen(
            item = uiState.activeViewingItem!!,
            vaultViewModel = vaultViewModel,
            onBack = { vaultViewModel.closeItemViewer() },
            onDelete = {
                vaultViewModel.deleteSingleItem(uiState.activeViewingItem!!)
            }
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSelectionMode) {
                        Text("${uiState.selectedItemIds.size} selected")
                    } else {
                        Text("Private Vault")
                    }
                },
                navigationIcon = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { vaultViewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    } else {
                        IconButton(onClick = onLockVault, modifier = Modifier.testTag("vault_back_lock_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Lock & Return to Calculator")
                        }
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { vaultViewModel.selectAll(items) }, modifier = Modifier.testTag("select_all_button")) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { showDeleteConfirmation = true }, modifier = Modifier.testTag("delete_selected_button")) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        // Sort dropdown
                        Box {
                            IconButton(onClick = { showSortMenu = true }, modifier = Modifier.testTag("sort_menu_button")) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Newest First") },
                                    onClick = { vaultViewModel.setSort(VaultSort.NEWEST_FIRST); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Oldest First") },
                                    onClick = { vaultViewModel.setSort(VaultSort.OLDEST_FIRST); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Largest First") },
                                    onClick = { vaultViewModel.setSort(VaultSort.LARGEST_FIRST); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Smallest First") },
                                    onClick = { vaultViewModel.setSort(VaultSort.SMALLEST_FIRST); showSortMenu = false }
                                )
                            }
                        }

                        // Vault Settings button (only accessible inside authenticated vault)
                        IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("vault_settings_button")) {
                            Icon(Icons.Default.Settings, contentDescription = "Vault Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Immediate lock button
                        IconButton(onClick = onLockVault, modifier = Modifier.testTag("vault_instant_lock_button")) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // In-Vault Camera Button
                SmallFloatingActionButton(
                    onClick = onOpenCamera,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("vault_camera_fab")
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Vault Camera")
                }

                // Import / Add Media Button
                FloatingActionButton(
                    onClick = {
                        showImportNoticeDialog = true
                    },
                    modifier = Modifier.testTag("import_media_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Import Photos/Videos")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter chips: All, Photos, Videos
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.activeFilter == VaultFilter.ALL,
                    onClick = { vaultViewModel.setFilter(VaultFilter.ALL) },
                    label = { Text("All (${uiState.totalCount})") },
                    modifier = Modifier.testTag("filter_all_chip")
                )
                FilterChip(
                    selected = uiState.activeFilter == VaultFilter.PHOTOS,
                    onClick = { vaultViewModel.setFilter(VaultFilter.PHOTOS) },
                    label = { Text("Photos") },
                    leadingIcon = { Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("filter_photos_chip")
                )
                FilterChip(
                    selected = uiState.activeFilter == VaultFilter.VIDEOS,
                    onClick = { vaultViewModel.setFilter(VaultFilter.VIDEOS) },
                    label = { Text("Videos") },
                    leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("filter_videos_chip")
                )
            }

            // Import progress banner
            AnimatedVisibility(visible = uiState.isImporting) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = uiState.importProgressMessage ?: "Encrypting media...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Items Grid or Empty State
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Text(
                            text = "Vault is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap the '+' button to securely import and encrypt photos or videos from your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { showImportNoticeDialog = true },
                            modifier = Modifier.padding(top = 8.dp).testTag("empty_state_import_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Media")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items, key = { it.id }) { item ->
                        val isSelected = uiState.selectedItemIds.contains(item.id)
                        VaultGridItem(
                            item = item,
                            isSelected = isSelected,
                            isSelectionMode = uiState.isSelectionMode,
                            vaultViewModel = vaultViewModel,
                            thumbVersion = thumbVersions,
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    vaultViewModel.toggleSelection(item.id)
                                } else {
                                    vaultViewModel.openItemViewer(item)
                                }
                            },
                            onLongClick = {
                                vaultViewModel.toggleSelection(item.id)
                            }
                        )
                    }
                }
            }
        }
    }

    // Privacy Notice Dialog before importing
    if (showImportNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showImportNoticeDialog = false },
            icon = {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = { Text("Import & Encryption") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Selected files will be encrypted using Android Keystore AES-256 and stored inside Calci's private, non-gallery storage.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Notice: The original file remains in your public photos/downloads until you delete it from there. Calci never deletes your originals silently.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportNoticeDialog = false
                        mediaPickerLauncher.launch(arrayOf("image/*", "video/*"))
                    },
                    modifier = Modifier.testTag("confirm_open_picker_button")
                ) {
                    Text("Select Files")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportNoticeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Encrypted Media?") },
            text = {
                Text("Permanently delete ${uiState.selectedItemIds.size} item(s) from your private vault? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        vaultViewModel.deleteSelectedItems()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_dialog_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error alert
    uiState.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { vaultViewModel.dismissError() },
            title = { Text("Vault Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { vaultViewModel.dismissError() }) { Text("OK") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultGridItem(
    item: VaultItemEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    vaultViewModel: VaultViewModel,
    thumbVersion: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var thumbnail by remember(item.id, thumbVersion) { mutableStateOf<Bitmap?>(vaultViewModel.getCachedThumbnail(item)) }

    LaunchedEffect(item.id, thumbVersion) {
        if (thumbnail == null) {
            thumbnail = vaultViewModel.loadThumbnail(item)
        }
    }

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("vault_item_${item.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = item.originalFileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Default.Videocam else Icons.Default.Photo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Dark gradient overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.4f))
            )

            // Video indicator or duration
            if (item.mediaType == MediaType.VIDEO) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    if (item.durationMs != null && item.durationMs > 0) {
                        val seconds = (item.durationMs / 1000) % 60
                        val minutes = (item.durationMs / (1000 * 60))
                        Text(
                            text = String.format("%d:%02d", minutes, seconds),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Selection checkmark or radio
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
