package uk.ac.tees.mad.fixit.presentation.feature.reportissue

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import uk.ac.tees.mad.fixit.data.model.IssueType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueScreen(
    viewModel: ReportIssueViewModel = hiltViewModel() // Changed: Use Hilt ViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // REMOVED: No more manual initialization needed with Hilt

    // Add confirmation dialog state
    var showConfirmationDialog by remember { mutableStateOf(false) }

    // Handle successful submission
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSubmitted) {
        if (uiState.isSubmitted) {
            showSuccess = true
            delay(2000)
            viewModel.resetForm()
            showSuccess = false
        }
    }

    // Confirmation Dialog
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text("Submit Report") },
            text = { Text("Are you sure you want to submit this issue report?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmationDialog = false
                        viewModel.submitReport(context)
                    }
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmationDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create image picker handler
    val imagePickerHandler = remember {
        ImagePickerHandler(context) { uri ->
            viewModel.updateImageUri(uri)
        }
    }

    val imagePickerLaunchers = imagePickerHandler.createImagePickerLaunchers()

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted || coarseLocationGranted) {
                viewModel.fetchCurrentLocation()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Report Issue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (showSuccess) {
                    SuccessCard(message = "Report submitted successfully!")
                }

                if (uiState.isLoading && uploadProgress > 0f) {
                    UploadProgressSection(progress = uploadProgress)
                }

                ImagePickerSection(
                    imageUri = uiState.imageUri,
                    imageErrors = uiState.imageErrors,
                    onCameraClick = imagePickerLaunchers.onCameraClick,
                    onGalleryClick = imagePickerLaunchers.onGalleryClick,
                    onRemoveImage = { viewModel.removeImage() }
                )

                DescriptionSection(
                    description = uiState.description,
                    descriptionErrors = uiState.descriptionErrors,
                    descriptionCharCount = uiState.descriptionCharCount,
                    onDescriptionChange = viewModel::updateDescription
                )

                IssueTypeSection(
                    selectedIssueType = uiState.selectedIssueType,
                    onIssueTypeSelected = viewModel::updateIssueType
                )

                LocationSection(
                    location = uiState.location,
                    locationErrors = uiState.locationErrors,
                    isLoading = uiState.isLoading,
                    onLocationFetch = {
                        checkLocationPermissions(
                            context = context,
                            permissionLauncher = locationPermissionLauncher,
                            onPermissionGranted = { viewModel.fetchCurrentLocation() }
                        )
                    }
                )

                SubmitButton(
                    isLoading = uiState.isLoading,
                    onSubmit = { showConfirmationDialog = true }
                )

                uiState.errorMessage?.let { message ->
                    ErrorCard(message = message)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.isLoading) {
                LoadingOverlay()
            }
        }
    }
}

@Composable
private fun UploadProgressSection(progress: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Uploading",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Uploading Image",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun SuccessCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun checkLocationPermissions(
    context: Context,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    onPermissionGranted: () -> Unit
) {
    val fineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    val coarseLocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION

    val fineLocationGranted = ContextCompat.checkSelfPermission(
        context,
        fineLocationPermission
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val coarseLocationGranted = ContextCompat.checkSelfPermission(
        context,
        coarseLocationPermission
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    if (fineLocationGranted || coarseLocationGranted) {
        onPermissionGranted()
    } else {
        permissionLauncher.launch(
            arrayOf(fineLocationPermission, coarseLocationPermission)
        )
    }
}

@Composable
private fun LocationSection(
    location: uk.ac.tees.mad.fixit.data.model.IssueLocation?,
    locationErrors: List<String>,
    isLoading: Boolean,
    onLocationFetch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "*",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (location != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = location.address.ifEmpty { "Address not available" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Lat: ${String.format("%.6f", location.latitude)}, " +
                                "Lng: ${String.format("%.6f", location.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Location not set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ValidationErrorText(errors = locationErrors)

            Button(
                onClick = onLocationFetch,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Getting Location...")
                } else {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Get Current Location")
                }
            }

            Text(
                text = "Note: Location is required for issue reporting",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ImagePickerSection(
    imageUri: android.net.Uri?,
    imageErrors: List<String>,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onRemoveImage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Camera",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Issue Photo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "*",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = if (imageErrors.isNotEmpty()) 2.dp else 1.dp,
                        color = if (imageErrors.isNotEmpty())
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Selected issue image",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )

                        IconButton(
                            onClick = onRemoveImage,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove image",
                                tint = Color.White
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Add photo",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No image selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            ValidationErrorText(errors = imageErrors)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCameraClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Camera")
                }

                OutlinedButton(
                    onClick = onGalleryClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gallery")
                }
            }
        }
    }
}

@Composable
private fun getIconForIssueType(issueType: IssueType) = when (issueType) {
    IssueType.POTHOLE -> Icons.Default.Warning
    IssueType.STREETLIGHT -> Icons.Default.Email
    IssueType.GARBAGE -> Icons.Default.Delete
    IssueType.DRAINAGE -> Icons.Default.Email
    IssueType.ROAD_DAMAGE -> Icons.Default.Build
    IssueType.OTHER -> Icons.Default.Info
}

@Composable
private fun DescriptionSection(
    description: String,
    descriptionErrors: List<String>,
    descriptionCharCount: Int,
    onDescriptionChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Description",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Issue Description",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "*",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Describe the issue in detail (10-500 characters)")
                },
                minLines = 4,
                maxLines = 8,
                isError = descriptionErrors.isNotEmpty(),
                supportingText = {
                    Column {
                        if (descriptionErrors.isNotEmpty()) {
                            ValidationErrorText(
                                errors = descriptionErrors,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$descriptionCharCount characters",
                                color = if (descriptionErrors.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            CharacterCounter(
                                currentCount = descriptionCharCount,
                                maxCount = 500,
                                isError = descriptionCharCount > 500
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueTypeSection(
    selectedIssueType: IssueType,
    onIssueTypeSelected: (IssueType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Category",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Issue Type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedIssueType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    IssueType.entries.forEach { issueType ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = getIconForIssueType(issueType),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(issueType.displayName)
                                }
                            },
                            onClick = {
                                onIssueTypeSelected(issueType)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmitButton(
    isLoading: Boolean,
    onSubmit: () -> Unit
) {
    Button(
        onClick = onSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = !isLoading,
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Submitting...")
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Submit Report",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp)
                )
                Text("Processing...")
            }
        }
    }
}