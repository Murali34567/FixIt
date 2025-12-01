package uk.ac.tees.mad.fixit.presentation.feature.profile

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Auto-clear messages after 3 seconds
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            delay(3000)
            viewModel.clearMessages()
        }
    }

    // Image picker for profile photo
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoDialog by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            photoUri = it
            viewModel.uploadProfilePhoto(it, context)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let {
                viewModel.uploadProfilePhoto(it, context)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createImageUri(context)
            photoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                ),
                actions = {
                    if (uiState.isEditMode) {
                        TextButton(
                            onClick = { viewModel.cancelEdit() }
                        ) {
                            Text("Cancel", color = Color.White)
                        }
                        TextButton(
                            onClick = { viewModel.saveEdit() }
                        ) {
                            Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(onClick = { viewModel.toggleEditMode() }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = Color.White
                            )
                        }
                    }
                }
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Offline Banner
                if (uiState.isOffline) {
                    OfflineBanner()
                }

                // Success Message
                uiState.successMessage?.let {
                    SuccessMessage(it)
                }

                // Error Message
                uiState.errorMessage?.let {
                    ErrorMessage(it)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Profile Photo Section
                ProfilePhotoSection(
                    photoUrl = uiState.profile?.photoUrl,
                    isLoading = uiState.isLoading,
                    onPhotoClick = { showPhotoDialog = true }
                )

                // Profile Info Card
                ProfileInfoCard(
                    profile = uiState.profile,
                    isEditMode = uiState.isEditMode,
                    editedName = uiState.editedName,
                    onNameChange = viewModel::updateEditedName
                )

                // Notification Preferences Card
                NotificationPreferencesCard(
                    profile = uiState.profile,
                    onPreferencesChange = viewModel::updateNotificationPreferences
                )

                // Account Statistics Card
                AccountStatisticsCard(profile = uiState.profile)

                Spacer(modifier = Modifier.height(8.dp))

                // Logout Button
                LogoutButton(
                    onClick = { viewModel.showLogoutDialog(true) }
                )

                // Delete Account Button
                DeleteAccountButton(
                    onClick = { viewModel.showDeleteAccountDialog(true) }
                )

                Spacer(modifier = Modifier.height(32.dp))
            }

            // Loading Overlay
            if (uiState.isLoading) {
                LoadingOverlay()
            }
        }
    }

    // Photo Picker Dialog
    if (showPhotoDialog) {
        PhotoPickerDialog(
            onDismiss = { showPhotoDialog = false },
            onCameraClick = {
                showPhotoDialog = false
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onGalleryClick = {
                showPhotoDialog = false
                galleryLauncher.launch("image/*")
            }
        )
    }

    // Logout Confirmation Dialog
    if (uiState.showLogoutDialog) {
        ConfirmationDialog(
            title = "Logout",
            message = "Are you sure you want to logout?",
            confirmText = "Logout",
            onConfirm = {
                FirebaseAuth.getInstance().signOut()
                viewModel.showLogoutDialog(false)
                onLogout()
            },
            onDismiss = { viewModel.showLogoutDialog(false) }
        )
    }

    // Delete Account Confirmation Dialog
    if (uiState.showDeleteAccountDialog) {
        DeleteAccountDialog(
            onConfirm = {
                viewModel.deleteAccount(onSuccess = onLogout)
            },
            onDismiss = { viewModel.showDeleteAccountDialog(false) }
        )
    }
}

@Composable
private fun OfflineBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "Offline",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "You're offline. Some features may be limited.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SuccessMessage(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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

@Composable
private fun ErrorMessage(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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
private fun ProfilePhotoSection(
    photoUrl: String?,
    isLoading: Boolean,
    onPhotoClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .clickable(onClick = onPhotoClick),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Profile",
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        // Camera overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "Change photo",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }
    }

    Text(
        text = "Tap to change photo",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ProfileInfoCard(
    profile: uk.ac.tees.mad.fixit.data.model.UserProfile?,
    isEditMode: Boolean,
    editedName: String,
    onNameChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Profile Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            // Name Field
            if (isEditMode) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = "Name")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                ProfileInfoRow(
                    icon = Icons.Default.Person,
                    label = "Name",
                    value = profile?.name ?: "Not set"
                )
            }

            // Email (read-only)
            ProfileInfoRow(
                icon = Icons.Default.Email,
                label = "Email",
                value = profile?.email ?: "Not available"
            )

            // User ID
            ProfileInfoRow(
                icon = Icons.Default.Info,
                label = "User ID",
                value = profile?.uid?.take(16) + "..." ?: "Unknown"
            )
        }
    }
}

@Composable
private fun NotificationPreferencesCard(
    profile: uk.ac.tees.mad.fixit.data.model.UserProfile?,
    onPreferencesChange: (Boolean, Boolean, Boolean) -> Unit
) {
    var notificationsEnabled by remember(profile) {
        mutableStateOf(profile?.notificationsEnabled ?: true)
    }
    var emailNotifications by remember(profile) {
        mutableStateOf(profile?.emailNotifications ?: true)
    }
    var pushNotifications by remember(profile) {
        mutableStateOf(profile?.pushNotifications ?: true)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Notification Preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            // Master notifications toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications"
                    )
                    Column {
                        Text(
                            text = "Enable Notifications",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Receive updates about your reports",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = {
                        notificationsEnabled = it
                        onPreferencesChange(it, emailNotifications, pushNotifications)
                    }
                )
            }

            if (notificationsEnabled) {
                HorizontalDivider()

                // Email notifications
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email"
                        )
                        Text(
                            text = "Email Notifications",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = emailNotifications,
                        onCheckedChange = {
                            emailNotifications = it
                            onPreferencesChange(notificationsEnabled, it, pushNotifications)
                        }
                    )
                }

                HorizontalDivider()

                // Push notifications
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Push"
                        )
                        Text(
                            text = "Push Notifications",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = pushNotifications,
                        onCheckedChange = {
                            pushNotifications = it
                            onPreferencesChange(notificationsEnabled, emailNotifications, it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountStatisticsCard(
    profile: uk.ac.tees.mad.fixit.data.model.UserProfile?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Account Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            ProfileInfoRow(
                icon = Icons.Default.DateRange,
                label = "Member Since",
                value = profile?.createdAt?.let {
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it))
                } ?: "Unknown"
            )

            ProfileInfoRow(
                icon = Icons.Default.Edit,
                label = "Last Updated",
                value = profile?.lastUpdated?.let {
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it))
                } ?: "Unknown"
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LogoutButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Logout",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun DeleteAccountButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Delete Account",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun PhotoPickerDialog(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Photo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCameraClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Take Photo")
                }
                OutlinedButton(
                    onClick = onGalleryClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose from Gallery")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete Account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This action cannot be undone. All your data will be permanently deleted:")
                Text("• Profile information", style = MaterialTheme.typography.bodySmall)
                Text("• All reported issues", style = MaterialTheme.typography.bodySmall)
                Text("• Account history", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Text("Processing...")
            }
        }
    }
}

// Helper function to create image URI for camera
private fun createImageUri(context: android.content.Context): Uri {
    val imageFile = File(
        context.cacheDir,
        "profile_${System.currentTimeMillis()}.jpg"
    )
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        imageFile
    )
}