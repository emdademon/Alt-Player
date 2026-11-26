package com.example.ui.browser

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.PlaybackPreferencesRepository
import com.example.data.VideoItem
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.MediaInfoDialog
import com.example.ui.components.RenameVideoDialog
import com.example.ui.components.VideoCard
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.CardOutline
import com.example.ui.theme.CoralError
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedNavy
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent

@Composable
fun PrivateVaultScreen(
    uiState: LibraryUiState,
    onToggleGridView: () -> Unit,
    onImportUri: (Uri) -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    onRenameVaultVideo: (VideoItem, String) -> Unit,
    onDeleteVaultVideo: (VideoItem) -> Unit,
    onInspectMediaInfo: (VideoItem) -> Unit,
    onDismissMediaInfo: () -> Unit,
    getShareableUri: (VideoItem) -> Uri,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<VideoItem?>(null) }
    var deleteTarget by remember { mutableStateOf<VideoItem?>(null) }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportUri(uri)
    }

    Scaffold(
        containerColor = AmoledBlack,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0C1A16), AmoledBlack, AmoledBlack)
                    )
                )
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("vault_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = TextPrimary
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = VioletAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.private_vault_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "${uiState.vaultVideos.size} encrypted/isolated local file(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                IconButton(
                    onClick = onToggleGridView,
                    modifier = Modifier.testTag("vault_layout_toggle")
                ) {
                    Icon(
                        imageVector = if (uiState.isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                        contentDescription = stringResource(R.string.toggle_layout_view),
                        tint = TextSecondary
                    )
                }

                Button(
                    onClick = { pickVideoLauncher.launch(arrayOf("video/*", "audio/*")) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = AmoledBlack
                    ),
                    modifier = Modifier.testTag("vault_import_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (uiState.vaultVideos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = VioletAccent.copy(alpha = 0.16f),
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = VioletAccent,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Text(
                            text = "Private Vault is Empty",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Import videos here or use \"Move to Private Vault\" from any video's 3-dot menu to keep them out of public folders.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columns = when {
                        uiState.isGridView && maxWidth >= 640.dp -> 3
                        uiState.isGridView -> 2
                        maxWidth >= 640.dp -> 2
                        else -> 1
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.vaultVideos,
                            key = { it.fullFilePath.ifBlank { it.uri.toString() } },
                            contentType = { "vault_item" }
                        ) { video ->
                            val key = PlaybackPreferencesRepository.uriKey(video.uri.toString())
                            val resumeMs = uiState.positionsByUri[key] ?: 0L
                            val isWatched = key in uiState.watchedUris
                            VideoCard(
                                video = video,
                                resumePositionMs = resumeMs,
                                isWatched = isWatched,
                                isGrid = uiState.isGridView,
                                onClick = { onPlayVideo(video, uiState.vaultVideos) },
                                onRename = { renameTarget = video },
                                onDelete = { deleteTarget = video },
                                onShare = {
                                    val shareUri = getShareableUri(video)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = video.mimeType.ifBlank {
                                            if (video.isAudio) "audio/*" else "video/*"
                                        }
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            if (video.isAudio) "Share audio" else "Share media"
                                        )
                                    )
                                },
                                onMoveToVault = null,
                                onProperties = { onInspectMediaInfo(video) }
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameVideoDialog(
            initialTitle = target.title,
            onConfirm = { newTitle ->
                onRenameVaultVideo(target, newTitle)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            title = "Remove from Private Vault?",
            message = "\"${target.title}\" will be permanently deleted from Alt Player's Private Vault.",
            onConfirm = {
                onDeleteVaultVideo(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    MediaInfoDialog(
        isLoading = uiState.isLoadingMediaInfo,
        info = uiState.inspectedMediaInfo,
        onDismiss = onDismissMediaInfo
    )
}

@Composable
fun PinPadDialog(
    isCreatingNewPin: Boolean,
    onVerifyPin: (String) -> Boolean,
    onCreatePin: (String) -> Unit,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var firstCreatedPin by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val titleText = when {
        !isCreatingNewPin -> "Unlock Private Vault"
        firstCreatedPin == null -> "Create 4-Digit PIN"
        else -> "Confirm 4-Digit PIN"
    }

    val subtitleText = when {
        !isCreatingNewPin -> "Enter your 4-digit PIN to access private videos"
        firstCreatedPin == null -> "Choose a 4-digit PIN stored locally on your device"
        else -> "Re-enter your 4-digit PIN to confirm"
    }

    fun onDigitPressed(digit: String) {
        if (enteredPin.length >= 4) return
        errorText = null
        val updated = enteredPin + digit
        enteredPin = updated

        if (updated.length == 4) {
            if (!isCreatingNewPin) {
                if (onVerifyPin(updated)) {
                    onSuccess()
                } else {
                    errorText = "Incorrect PIN. Try again."
                    enteredPin = ""
                }
            } else if (firstCreatedPin == null) {
                firstCreatedPin = updated
                enteredPin = ""
            } else if (firstCreatedPin == updated) {
                onCreatePin(updated)
                onSuccess()
            } else {
                errorText = "PINs did not match. Start over."
                firstCreatedPin = null
                enteredPin = ""
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = DeepNavySurface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, CardOutline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = VioletAccent.copy(alpha = 0.16f),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = VioletAccent)
                    }
                }

                Text(text = titleText, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                // 4 PIN dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    repeat(4) { idx ->
                        val filled = idx < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (filled) ElectricCyan else ElevatedNavy)
                                .border(1.dp, if (filled) ElectricCyan else CardOutline, CircleShape)
                        )
                    }
                }

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = CoralError
                    )
                }

                // Keypad
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "DEL")
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { key ->
                                when (key) {
                                    "" -> Spacer(modifier = Modifier.size(60.dp))
                                    "DEL" -> {
                                        Surface(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    if (enteredPin.isNotEmpty()) {
                                                        enteredPin = enteredPin.dropLast(1)
                                                    }
                                                },
                                            color = ElevatedNavy,
                                            shape = CircleShape
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                                    contentDescription = "Delete",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        Surface(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(CircleShape)
                                                .clickable { onDigitPressed(key) }
                                                .testTag("pin_digit_$key"),
                                            color = ElevatedNavy,
                                            shape = CircleShape,
                                            border = BorderStroke(1.dp, CardOutline)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = key,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    }
}
