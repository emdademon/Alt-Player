package com.example.ui.browser

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PlaybackPreferencesRepository
import com.example.data.SortOption
import com.example.data.SortOrder
import com.example.data.VideoFolder
import com.example.data.VideoItem
import com.example.data.VideoMediaInfo
import com.example.data.formatFileSize
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.MediaInfoDialog
import com.example.ui.components.RenameVideoDialog
import com.example.ui.components.SortOptionsSheet
import com.example.ui.components.VideoCard
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folder: VideoFolder?,
    uiState: LibraryUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectSort: (SortOption, SortOrder) -> Unit,
    onToggleGridView: () -> Unit,
    onRefreshScan: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    onInspectMediaInfo: (VideoItem) -> Unit,
    onDismissMediaInfo: () -> Unit,
    createDeleteIntentSender: (List<Uri>) -> IntentSender?,
    createWriteIntentSender: (List<Uri>) -> IntentSender?,
    onDeleteVideosLegacy: (List<VideoItem>, (IntentSender) -> Unit) -> Unit,
    onPerformRename: (VideoItem, String, (IntentSender) -> Unit) -> Unit,
    onMoveVideoToVault: (VideoItem, (VideoItem) -> Unit) -> Unit,
    getShareableUri: (VideoItem) -> Uri,
    onClearStatusMessage: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VideoItem?>(null) }
    var pendingRenameNewTitle by remember { mutableStateOf<String?>(null) }
    var legacyDeleteVideos by remember { mutableStateOf<List<VideoItem>?>(null) }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onClearStatusMessage()
    }

    val deleteSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onRefreshScan(false)
        }
    }

    val writeSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val target = renameTarget
        val newName = pendingRenameNewTitle
        if (result.resultCode == Activity.RESULT_OK && target != null && !newName.isNullOrBlank()) {
            onPerformRename(target, newName) { }
        }
        renameTarget = null
        pendingRenameNewTitle = null
    }

    fun requestDeleteVideos(videos: List<VideoItem>) {
        val sender = createDeleteIntentSender(videos.map { it.uri })
        if (sender != null) {
            deleteSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } else {
            legacyDeleteVideos = videos
        }
    }

    fun requestRenameVideo(video: VideoItem, newTitle: String) {
        val sender = createWriteIntentSender(listOf(video.uri))
        if (sender != null) {
            renameTarget = video
            pendingRenameNewTitle = newTitle
            writeSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } else {
            onPerformRename(video, newTitle) { recoverable ->
                renameTarget = video
                pendingRenameNewTitle = newTitle
                writeSenderLauncher.launch(IntentSenderRequest.Builder(recoverable).build())
            }
            renameTarget = null
        }
    }

    Scaffold(
        containerColor = AmoledBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DeepNavySurface, AmoledBlack, AmoledBlack)
                    )
                )
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("folder_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = TextPrimary
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder?.name ?: "Folder",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (folder != null) {
                            val countText = if (folder.videoCount == 1) "1 video" else "${folder.videoCount} videos"
                            Text(
                                text = "$countText • ${formatFileSize(folder.totalSizeBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleGridView,
                        modifier = Modifier.testTag("folder_layout_toggle")
                    ) {
                        Icon(
                            imageVector = if (uiState.isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = stringResource(R.string.toggle_layout_view),
                            tint = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = { showSortSheet = true },
                        modifier = Modifier.testTag("folder_sort_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = stringResource(R.string.sort_by),
                            tint = ElectricCyan
                        )
                    }
                }

                SearchCapsule(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )

                val videos = folder?.videos.orEmpty()
                if (videos.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.empty_search_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextMuted
                        )
                    }
                } else {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { onRefreshScan(true) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val columns = when {
                                uiState.isGridView && maxWidth >= 640.dp -> 3
                                uiState.isGridView -> 2
                                maxWidth >= 640.dp -> 2
                                else -> 1
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 28.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = videos,
                                    key = { "vid_${it.id}_${it.uri}" },
                                    contentType = { "video" }
                                ) { video ->
                                    val key = PlaybackPreferencesRepository.uriKey(video.uri.toString())
                                    val resumeMs = uiState.positionsByUri[key] ?: 0L
                                    val isWatched = key in uiState.watchedUris
                                    VideoCard(
                                        video = video,
                                        resumePositionMs = resumeMs,
                                        isWatched = isWatched,
                                        isGrid = uiState.isGridView,
                                        onClick = { onPlayVideo(video, videos) },
                                        onRename = { renameTarget = video },
                                        onDelete = { requestDeleteVideos(listOf(video)) },
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
                                                    if (video.isAudio) "Share audio" else "Share video"
                                                )
                                            )
                                        },
                                        onMoveToVault = {
                                            onMoveVideoToVault(video) { original ->
                                                requestDeleteVideos(listOf(original))
                                            }
                                        },
                                        onProperties = { onInspectMediaInfo(video) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        SortOptionsSheet(
            currentOption = uiState.sortOption,
            currentOrder = uiState.sortOrder,
            onSelectSort = onSelectSort,
            onDismiss = { showSortSheet = false }
        )
    }

    renameTarget?.takeIf { pendingRenameNewTitle == null }?.let { target ->
        RenameVideoDialog(
            initialTitle = target.title,
            onConfirm = { newTitle -> requestRenameVideo(target, newTitle) },
            onDismiss = { renameTarget = null }
        )
    }

    legacyDeleteVideos?.let { targets ->
        DeleteConfirmDialog(
            title = "Delete Video?",
            message = "This will permanently remove \"${targets.firstOrNull()?.title.orEmpty()}\" from your device.",
            onConfirm = {
                onDeleteVideosLegacy(targets) { recoverable ->
                    deleteSenderLauncher.launch(IntentSenderRequest.Builder(recoverable).build())
                }
                legacyDeleteVideos = null
            },
            onDismiss = { legacyDeleteVideos = null }
        )
    }

    MediaInfoDialog(
        isLoading = uiState.isLoadingMediaInfo,
        info = uiState.inspectedMediaInfo,
        onDismiss = onDismissMediaInfo
    )
}
