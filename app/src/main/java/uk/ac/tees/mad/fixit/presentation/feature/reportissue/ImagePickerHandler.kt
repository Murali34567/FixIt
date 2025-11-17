package uk.ac.tees.mad.fixit.presentation.feature.reportissue

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class ImagePickerHandler(
    private val context: Context,
    private val onImageSelected: (Uri?) -> Unit
) {
    private lateinit var currentPhotoUri: Uri

    @Composable
    fun createImagePickerLaunchers(): ImagePickerLaunchers {
        val context = LocalContext.current

        // Camera launcher
        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
            onResult = { success ->
                if (success) {
                    onImageSelected(currentPhotoUri)
                } else {
                    onImageSelected(null)
                }
            }
        )

        // Gallery launcher
        val galleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri ->
                onImageSelected(uri)
            }
        )

        // Permission launcher for camera
        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                if (granted) {
                    launchCamera(cameraLauncher)
                }
            }
        )

        return ImagePickerLaunchers(
            cameraLauncher = cameraLauncher,
            galleryLauncher = galleryLauncher,
            cameraPermissionLauncher = cameraPermissionLauncher,
            onCameraClick = {
                checkCameraPermission(cameraPermissionLauncher)
            },
            onGalleryClick = {
                galleryLauncher.launch("image/*")
            }
        )
    }

    private fun checkCameraPermission(permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>) {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera(cameraLauncher: androidx.activity.result.ActivityResultLauncher<Uri>) {
        val photoFile = createImageFile()
        currentPhotoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        cameraLauncher.launch(currentPhotoUri)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir = context.getExternalFilesDir("Pictures")
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }
}

data class ImagePickerLaunchers(
    val cameraLauncher: androidx.activity.result.ActivityResultLauncher<Uri>,
    val galleryLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    val cameraPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    val onCameraClick: () -> Unit,
    val onGalleryClick: () -> Unit
)