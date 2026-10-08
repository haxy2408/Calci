package com.example.vault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.database.MediaType
import com.example.database.VaultItemEntity
import com.example.settings.formatStorageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen photo viewer and video player for vault media.
 * Safely decrypts media into memory/transient private cache, and displays cleanly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    item: VaultItemEntity,
    vaultViewModel: VaultViewModel,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id) {
        isLoading = true
        errorMessage = null
        try {
            if (item.mediaType == MediaType.PHOTO) {
                val bytes = vaultViewModel.loadPhotoBytes(item)
                if (bytes != null) {
                    val bmp = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    photoBitmap = bmp
                } else {
                    errorMessage = "Failed to decrypt photo"
                }
            } else {
                val file = vaultViewModel.decryptVideoForPlayback(item)
                if (file != null) {
                    videoFile = file
                } else {
                    errorMessage = "Failed to decrypt video for playback"
                }
            }
        } catch (e: Exception) {
            errorMessage = "Decryption error: ${e.localizedMessage}"
        } finally {
            isLoading = false
        }
    }

    DisposableEffect(item.id) {
        onDispose {
            photoBitmap?.recycle()
            photoBitmap = null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = item.originalFileName,
                        color = Color.White,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.testTag("viewer_info_button")) {
                        Icon(Icons.Default.Info, contentDescription = "Media Info", tint = Color.White)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("viewer_delete_button")) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Decrypting secure media...", color = Color.White.copy(alpha = 0.8f))
                    }
                }
                errorMessage != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = errorMessage ?: "Error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                item.mediaType == MediaType.PHOTO && photoBitmap != null -> {
                    Image(
                        bitmap = photoBitmap!!.asImageBitmap(),
                        contentDescription = item.originalFileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                item.mediaType == MediaType.VIDEO && videoFile != null -> {
                    val context = LocalContext.current
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoPath(videoFile!!.absolutePath)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showInfoDialog) {
        val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.importedAt))
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Encrypted Media Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Original Name: ${item.originalFileName}")
                    Text("Media Type: ${item.mediaType.name}")
                    Text("Format: ${item.mimeType}")
                    Text("Encrypted Size: ${formatStorageSize(item.fileSize)}")
                    Text("Import Date: $dateStr")
                    if (item.width > 0 && item.height > 0) {
                        Text("Resolution: ${item.width} × ${item.height}")
                    }
                    if (item.durationMs != null && item.durationMs > 0) {
                        Text("Duration: ${item.durationMs / 1000}s")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
