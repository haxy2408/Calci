package com.example.vault

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.database.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CameraMode {
    PHOTO,
    VIDEO
}

/**
 * In-Vault Camera Screen using CameraX.
 *
 * Privacy Guarantees:
 * - Camera captures are written to a temporary internal cache file inside sandbox.
 * - Immediately encrypted with Android Keystore AES-256 GCM.
 * - The unencrypted temporary file is wiped upon encryption completion.
 * - Zero storage into public DCIM, Pictures, Movies, or MediaStore.
 * - Permissions are requested only when entering this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultCameraScreen(
    vaultViewModel: VaultViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Vault Camera") },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("camera_back_no_perm")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "To capture encrypted photos and videos directly into your secure vault, allow camera access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.testTag("grant_camera_perm_button")
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }
        return
    }

    // Camera state
    var cameraMode by remember { mutableStateOf(CameraMode.PHOTO) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSeconds by remember { mutableLongStateOf(0L) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isProcessingCapture by remember { mutableStateOf(false) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setFlashMode(flashMode)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
            .build()
        VideoCapture.withOutput(recorder)
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Bind Camera Lifecycle
    LaunchedEffect(lensFacing, cameraMode) {
        val cameraProvider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            if (cameraMode == CameraMode.PHOTO) {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } else {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
            }
        } catch (e: Exception) {
            Log.e("VaultCamera", "Camera binding failed: ${e.message}")
        }
    }

    // Update Flash mode on ImageCapture
    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    // Timer for video recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startTime = SystemClock.elapsedRealtime()
            while (isRecording) {
                recordingDurationSeconds = (SystemClock.elapsedRealtime() - startTime) / 1000
                kotlinx.coroutines.delay(1000)
            }
        } else {
            recordingDurationSeconds = 0L
        }
    }

    // Safely stop video recording on dispose / backgrounding
    DisposableEffect(Unit) {
        onDispose {
            try {
                activeRecording?.stop()
                activeRecording = null
            } catch (ignored: Exception) {}
        }
    }

    fun takePhoto() {
        if (isProcessingCapture) return
        isProcessingCapture = true

        val tempFile = vaultViewModel.createTempCaptureFile("jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val originalName = "VAULT_IMG_$timeStamp.jpg"
                    vaultViewModel.importDirectCaptureFile(tempFile, MediaType.PHOTO, originalName) {
                        isProcessingCapture = false
                        onBack()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    tempFile.delete()
                    isProcessingCapture = false
                    Log.e("VaultCamera", "Photo capture failed: ${exception.message}")
                }
            }
        )
    }

    fun startVideoRecording() {
        if (!hasAudioPermission) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        val tempFile = vaultViewModel.createTempCaptureFile("mp4")
        val outputOptions = FileOutputOptions.Builder(tempFile).build()

        val pendingRecording = videoCapture.output.prepareRecording(context, outputOptions)
        if (hasAudioPermission && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                pendingRecording.withAudioEnabled()
            } catch (ignored: SecurityException) {}
        }

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    isRecording = true
                }
                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    if (!event.hasError()) {
                        isProcessingCapture = true
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val originalName = "VAULT_VID_$timeStamp.mp4"
                        vaultViewModel.importDirectCaptureFile(tempFile, MediaType.VIDEO, originalName) {
                            isProcessingCapture = false
                            onBack()
                        }
                    } else {
                        tempFile.delete()
                        isProcessingCapture = false
                        Log.e("VaultCamera", "Video recording error: ${event.error}")
                    }
                    activeRecording = null
                }
            }
        }
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Camera Preview
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Top Bar Controls: Back, Flash, Vault indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isRecording) {
                            stopVideoRecording()
                        }
                        onBack()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("camera_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Vault",
                        tint = Color.White
                    )
                }

                // In-Vault security badge
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Direct Encrypted Capture",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Flash toggle (Photos)
                IconButton(
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                            else -> ImageCapture.FLASH_MODE_AUTO
                        }
                    },
                    enabled = cameraMode == CameraMode.PHOTO,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("camera_flash_button")
                ) {
                    val flashIcon = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                        else -> Icons.Default.FlashAuto
                    }
                    Icon(
                        imageVector = flashIcon,
                        contentDescription = "Toggle Flash",
                        tint = if (cameraMode == CameraMode.PHOTO) Color.White else Color.Gray
                    )
                }
            }

            // Recording Duration Banner
            AnimatedVisibility(
                visible = isRecording,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                Surface(
                    color = Color.Red.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val mins = recordingDurationSeconds / 60
                        val secs = recordingDurationSeconds % 60
                        Text(
                            text = String.format("%02d:%02d", mins, secs),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Bottom Controls Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(vertical = 16.dp, horizontal = 24.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode Selector: Photo / Video
                if (!isRecording) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "PHOTO",
                            color = if (cameraMode == CameraMode.PHOTO) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (cameraMode == CameraMode.PHOTO) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { cameraMode = CameraMode.PHOTO }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("camera_mode_photo")
                        )
                        Text(
                            text = "VIDEO",
                            color = if (cameraMode == CameraMode.VIDEO) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (cameraMode == CameraMode.VIDEO) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { cameraMode = CameraMode.VIDEO }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("camera_mode_video")
                        )
                    }
                }

                // Shutter Row: Switch Camera, Shutter Button, Processing Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Empty spacer or placeholder on left
                    Box(modifier = Modifier.size(54.dp))

                    // Main Shutter / Record button
                    if (isProcessingCapture) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                    } else if (cameraMode == CameraMode.PHOTO) {
                        // Photo shutter button
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .border(4.dp, Color.White, CircleShape)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { takePhoto() }
                                .testTag("camera_shutter_button")
                        )
                    } else {
                        // Video record/stop button
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .border(4.dp, Color.White, CircleShape)
                                .padding(6.dp)
                                .clip(if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                                .background(Color.Red)
                                .clickable {
                                    if (isRecording) {
                                        stopVideoRecording()
                                    } else {
                                        startVideoRecording()
                                    }
                                }
                                .testTag("camera_record_button")
                        )
                    }

                    // Switch Camera (front/rear)
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        enabled = !isRecording && !isProcessingCapture,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .testTag("camera_switch_lens_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Switch Camera",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
