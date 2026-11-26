package com.example.ui.browser

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.LibraryTab
import com.example.data.MediaAccessState
import com.example.data.PlaybackPreferencesRepository
import com.example.data.SortOption
import com.example.data.SortOrder
import com.example.data.VideoFolder
import com.example.data.VideoItem
import com.example.data.formatDuration
import com.example.player.PlaybackEngine
import com.example.player.PlaybackSessionState
import com.example.ui.components.AltPlayerLogo
import com.example.ui.components.ContinueWatchingBanner
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.FolderCard
import com.example.ui.components.MediaInfoDialog
import com.example.ui.components.PrivacyShieldSheet
import com.example.ui.components.RenameVideoDialog
import com.example.ui.components.SortOptionsSheet
import com.example.ui.components.VideoCard
import com.example.ui.components.VideoThumbnailImage
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.CardOutline
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedNavy
import com.example.ui.theme.EmeraldOffline
import com.example.ui.theme.LuminousMint
import com.example.ui.theme.OnCyanContainer
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.SurfaceHighlight
import com.example.ui.theme.TealEmeraldDeep
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectTab: (LibraryTab) -> Unit,
    onSelectSort: (SortOption, SortOrder) -> Unit,
    onToggleGridView: () -> Unit,
    onRefreshScan: (Boolean) -> Unit,
    onOpenFolder: (VideoFolder) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrivateVault: () -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    onOpenActivePlayer: () -> Unit,
    onOpenExternalUri: (Uri) -> Unit,
    onSetFolderHidden: (String, Boolean) -> Unit,
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
    val playbackState by PlaybackEngine.sessionState.collectAsStateWithLifecycle()

    var showSortSheet by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VideoItem?>(null) }
    var pendingRenameNewTitle by remember { mutableStateOf<String?>(null) }
    var legacyDeleteVideos by remember { mutableStateOf<List<VideoItem>?>(null) }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onClearStatusMessage()
    }

    val permissionsToRequest = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshScan(false)
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onOpenExternalUri(uri)
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
            onPerformRename(target, newName) { /* already granted */ }
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

    fun shareVideo(video: VideoItem) {
        val shareUri = getShareableUri(video)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = video.mimeType.ifBlank { if (video.isAudio) "audio/*" else "video/*" }
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, if (video.isAudio) "Share audio" else "Share video")
        )
    }

    val resumeFabTarget = uiState.continueWatchingVideo
        ?: if (uiState.activeTab == LibraryTab.AUDIO) {
            uiState.filteredAudio.firstOrNull()
        } else {
            uiState.filteredVideos.firstOrNull()
        }

    Scaffold(
        containerColor = AmoledBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            val activeItem = playbackState.currentItem
            if (playbackState.hasActiveSession && activeItem != null) {
                MiniPlayerBar(
                    playbackState = playbackState,
                    activeItem = activeItem,
                    onOpenPlayer = onOpenActivePlayer,
                    onTogglePlayPause = { PlaybackEngine.togglePlayPause() },
                    onNext = { PlaybackEngine.skipNext() },
                    onClose = { PlaybackEngine.closePlayback(context) }
                )
            }
        },
        floatingActionButton = {
            if (resumeFabTarget != null) {
                FloatingActionButton(
                    onClick = {
                        val playlist = if (resumeFabTarget.isAudio) {
                            uiState.filteredAudio.ifEmpty { listOf(resumeFabTarget) }
                        } else {
                            uiState.folders
                                .find { it.bucketId == resumeFabTarget.bucketId }
                                ?.videos ?: uiState.filteredVideos
                        }
                        onPlayVideo(resumeFabTarget, playlist)
                    },
                    shape = CircleShape,
                    containerColor = TealEmeraldDeep,
                    contentColor = AmoledBlack,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                    modifier = Modifier.testTag("home_resume_fab")
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(TealEmeraldDeep, LuminousMint)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.menu_play_resume),
                            tint = AmoledBlack,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0C1A16), AmoledBlack, AmoledBlack)
                    )
                )
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LibraryHeader(
                    isGridView = uiState.isGridView,
                    onPrivacyClick = { showPrivacySheet = true },
                    onPrivateVaultClick = onOpenPrivateVault,
                    onToggleGridView = onToggleGridView,
                    onOpenFileClick = { openDocumentLauncher.launch(arrayOf("video/*", "audio/*")) },
                    onSortClick = { showSortSheet = true },
                    onSettingsClick = onOpenSettings
                )

                SearchCapsule(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )

                if (uiState.accessState == MediaAccessState.GRANTED_ALL ||
                    uiState.accessState == MediaAccessState.GRANTED_PARTIAL
                ) {
                    LibraryTabRow(
                        activeTab = uiState.activeTab,
                        folderCount = uiState.filteredFolders.size,
                        videoCount = uiState.filteredVideos.size,
                        audioCount = uiState.filteredAudio.size,
                        onSelectTab = onSelectTab,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                AnimatedVisibility(visible = uiState.accessState == MediaAccessState.GRANTED_PARTIAL) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        color = CyanContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.partial_access_banner),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnCyanContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.grant_full_access),
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { permissionLauncher.launch(permissionsToRequest) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("grant_full_access_button")
                            )
                        }
                    }
                }

                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ElectricCyan)
                        }
                    }

                    uiState.accessState == MediaAccessState.DENIED ||
                        uiState.accessState == MediaAccessState.UNKNOWN -> {
                        PermissionRequestView(
                            onGrantPermission = { permissionLauncher.launch(permissionsToRequest) },
                            onPickFile = { openDocumentLauncher.launch(arrayOf("video/*", "audio/*")) }
                        )
                    }

                    uiState.activeTab == LibraryTab.AUDIO && uiState.allAudio.isEmpty() -> {
                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = { onRefreshScan(true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            EmptyLibraryView(
                                isSearchEmpty = false,
                                isAudioTab = true,
                                onAction = { openDocumentLauncher.launch(arrayOf("video/*", "audio/*")) },
                                onRescan = { onRefreshScan(true) }
                            )
                        }
                    }

                    uiState.activeTab == LibraryTab.AUDIO && uiState.filteredAudio.isEmpty() -> {
                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = { onRefreshScan(true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            EmptyLibraryView(
                                isSearchEmpty = true,
                                isAudioTab = true,
                                onAction = { onSearchQueryChange("") },
                                onRescan = { onRefreshScan(true) }
                            )
                        }
                    }

                    uiState.activeTab != LibraryTab.AUDIO && uiState.allVideos.isEmpty() -> {
                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = { onRefreshScan(true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            EmptyLibraryView(
                                isSearchEmpty = false,
                                isAudioTab = false,
                                onAction = { openDocumentLauncher.launch(arrayOf("video/*", "audio/*")) },
                                onRescan = { onRefreshScan(true) }
                            )
                        }
                    }

                    (uiState.activeTab == LibraryTab.FOLDERS && uiState.filteredFolders.isEmpty()) ||
                        (uiState.activeTab == LibraryTab.ALL_VIDEOS && uiState.filteredVideos.isEmpty()) -> {
                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = { onRefreshScan(true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            EmptyLibraryView(
                                isSearchEmpty = true,
                                isAudioTab = false,
                                onAction = { onSearchQueryChange("") },
                                onRescan = { onRefreshScan(true) }
                            )
                        }
                    }

                    else -> {
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
                                        top = 6.dp,
                                        bottom = 96.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val continueVideo = uiState.continueWatchingVideo
                                    if (continueVideo != null && uiState.searchQuery.isBlank()) {
                                        item(
                                            key = "continue_watching_banner",
                                            contentType = "banner",
                                            span = { GridItemSpan(columns) }
                                        ) {
                                            ContinueWatchingBanner(
                                                video = continueVideo,
                                                positionMs = uiState.continueWatchingPositionMs,
                                                onResumeClick = {
                                                    val playlist = if (continueVideo.isAudio) {
                                                        uiState.filteredAudio.ifEmpty { listOf(continueVideo) }
                                                    } else {
                                                        uiState.folders
                                                            .find { it.bucketId == continueVideo.bucketId }
                                                            ?.videos ?: uiState.filteredVideos
                                                    }
                                                    onPlayVideo(continueVideo, playlist)
                                                },
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }

                                    when (uiState.activeTab) {
                                        LibraryTab.FOLDERS -> {
                                            items(
                                                items = uiState.filteredFolders,
                                                key = { it.bucketId },
                                                contentType = { "folder" }
                                            ) { folder ->
                                                val hasUnwatched = folder.videos.any { vid ->
                                                    val key = PlaybackPreferencesRepository.uriKey(vid.uri.toString())
                                                    key !in uiState.watchedUris
                                                }
                                                FolderCard(
                                                    folder = folder,
                                                    hasUnwatchedVideos = hasUnwatched,
                                                    isGrid = uiState.isGridView,
                                                    onClick = { onOpenFolder(folder) },
                                                    onPlayFirst = {
                                                        folder.videos.firstOrNull()?.let { first ->
                                                            onPlayVideo(first, folder.videos)
                                                        }
                                                    },
                                                    onToggleHide = {
                                                        onSetFolderHidden(folder.bucketId, !folder.isHidden)
                                                    },
                                                    onDeleteFolder = {
                                                        requestDeleteVideos(folder.videos)
                                                    }
                                                )
                                            }
                                        }

                                        LibraryTab.ALL_VIDEOS -> {
                                            items(
                                                items = uiState.filteredVideos,
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
                                                    onClick = { onPlayVideo(video, uiState.filteredVideos) },
                                                    onRename = { renameTarget = video },
                                                    onDelete = { requestDeleteVideos(listOf(video)) },
                                                    onShare = { shareVideo(video) },
                                                    onMoveToVault = {
                                                        onMoveVideoToVault(video) { original ->
                                                            requestDeleteVideos(listOf(original))
                                                        }
                                                    },
                                                    onProperties = { onInspectMediaInfo(video) }
                                                )
                                            }
                                        }

                                        LibraryTab.AUDIO -> {
                                            items(
                                                items = uiState.filteredAudio,
                                                key = { "aud_${it.id}_${it.uri}" },
                                                contentType = { "audio" }
                                            ) { audio ->
                                                val key = PlaybackPreferencesRepository.uriKey(audio.uri.toString())
                                                val resumeMs = uiState.positionsByUri[key] ?: 0L
                                                val isWatched = key in uiState.watchedUris
                                                VideoCard(
                                                    video = audio,
                                                    resumePositionMs = resumeMs,
                                                    isWatched = isWatched,
                                                    isGrid = uiState.isGridView,
                                                    onClick = { onPlayVideo(audio, uiState.filteredAudio) },
                                                    onRename = { renameTarget = audio },
                                                    onDelete = { requestDeleteVideos(listOf(audio)) },
                                                    onShare = { shareVideo(audio) },
                                                    onMoveToVault = {
                                                        onMoveVideoToVault(audio) { original ->
                                                            requestDeleteVideos(listOf(original))
                                                        }
                                                    },
                                                    onProperties = { onInspectMediaInfo(audio) }
                                                )
                                            }
                                        }
                                    }
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

    if (showPrivacySheet) {
        PrivacyShieldSheet(onDismiss = { showPrivacySheet = false })
    }

    renameTarget?.takeIf { pendingRenameNewTitle == null }?.let { target ->
        RenameVideoDialog(
            initialTitle = target.title,
            onConfirm = { newTitle ->
                requestRenameVideo(target, newTitle)
            },
            onDismiss = { renameTarget = null }
        )
    }

    legacyDeleteVideos?.let { targets ->
        DeleteConfirmDialog(
            title = if (targets.size == 1) "Delete Video?" else "Delete ${targets.size} Videos?",
            message = "This will permanently remove the selected video file(s) from your device.",
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

@Composable
private fun LibraryHeader(
    isGridView: Boolean,
    onPrivacyClick: () -> Unit,
    onPrivateVaultClick: () -> Unit,
    onToggleGridView: () -> Unit,
    onOpenFileClick: () -> Unit,
    onSortClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AltPlayerLogo(modifier = Modifier.size(38.dp))

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                ),
                color = TextPrimary,
                maxLines = 1,
                softWrap = false
            )
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onPrivacyClick)
                    .testTag("offline_privacy_badge"),
                color = EmeraldOffline.copy(alpha = 0.14f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = EmeraldOffline,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = stringResource(R.string.offline_privacy_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldOffline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        IconButton(
            onClick = onSortClick,
            modifier = Modifier.testTag("sort_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = stringResource(R.string.sort_by),
                tint = ElectricCyan
            )
        }

        Box {
            IconButton(
                onClick = { overflowExpanded = true },
                modifier = Modifier.testTag("home_overflow_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = TextPrimary
                )
            }

            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false },
                containerColor = ElevatedNavy
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_video_file), color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.FileOpen,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                    },
                    onClick = {
                        overflowExpanded = false
                        onOpenFileClick()
                    },
                    modifier = Modifier.testTag("menu_open_file")
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.private_vault_title), color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                    },
                    onClick = {
                        overflowExpanded = false
                        onPrivateVaultClick()
                    },
                    modifier = Modifier.testTag("menu_private_vault")
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (isGridView) "List View" else "Grid View",
                            color = TextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    },
                    onClick = {
                        overflowExpanded = false
                        onToggleGridView()
                    },
                    modifier = Modifier.testTag("menu_toggle_grid_view")
                )
                HorizontalDivider(color = CardOutline)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_title), color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                    },
                    onClick = {
                        overflowExpanded = false
                        onSettingsClick()
                    },
                    modifier = Modifier.testTag("menu_settings")
                )
            }
        }
    }
}

@Composable
fun SearchCapsule(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = DeepNavySurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_videos_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    cursorBrush = SolidColor(ElectricCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_videos_input")
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.clear_search),
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                        .testTag("clear_search_button")
                )
            }
        }
    }
}

@Composable
private fun LibraryTabRow(
    activeTab: LibraryTab,
    folderCount: Int,
    videoCount: Int,
    audioCount: Int,
    onSelectTab: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = DeepNavySurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LibrarySegmentTab(
                selected = activeTab == LibraryTab.FOLDERS,
                icon = Icons.Rounded.Folder,
                label = "${stringResource(R.string.tab_folders)} ($folderCount)",
                onClick = { onSelectTab(LibraryTab.FOLDERS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_folders")
            )
            LibrarySegmentTab(
                selected = activeTab == LibraryTab.ALL_VIDEOS,
                icon = Icons.Rounded.VideoLibrary,
                label = "${stringResource(R.string.tab_all_videos)} ($videoCount)",
                onClick = { onSelectTab(LibraryTab.ALL_VIDEOS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_all_videos")
            )
            LibrarySegmentTab(
                selected = activeTab == LibraryTab.AUDIO,
                icon = Icons.Rounded.MusicNote,
                label = "${stringResource(R.string.tab_audio)} ($audioCount)",
                onClick = { onSelectTab(LibraryTab.AUDIO) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_audio")
            )
        }
    }
}

@Composable
private fun LibrarySegmentTab(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = if (selected) ElevatedNavy else Color.Transparent,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) ElectricCyan else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) TextPrimary else TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionRequestView(
    onGrantPermission: () -> Unit,
    onPickFile: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_cinema),
                contentDescription = stringResource(R.string.permission_title),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )

            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.permission_description),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onGrantPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("grant_permission_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = AmoledBlack
                )
            ) {
                Text(
                    text = stringResource(R.string.grant_permission),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onPickFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("pick_video_directly_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CardOutline)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileOpen,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pick_video_directly),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryView(
    isSearchEmpty: Boolean,
    isAudioTab: Boolean,
    onAction: () -> Unit,
    onRescan: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 380.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_cinema),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .clip(RoundedCornerShape(22.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = stringResource(
                    when {
                        isSearchEmpty -> R.string.empty_search_title
                        isAudioTab -> R.string.empty_audio_title
                        else -> R.string.empty_library_title
                    }
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(
                    when {
                        isSearchEmpty -> R.string.empty_search_subtitle
                        isAudioTab -> R.string.empty_audio_subtitle
                        else -> R.string.empty_library_subtitle
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = AmoledBlack
                    ),
                    modifier = Modifier.testTag("empty_primary_action")
                ) {
                    Text(
                        text = stringResource(
                            if (isSearchEmpty) R.string.clear_search else R.string.pick_video_directly
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                if (!isSearchEmpty) {
                    OutlinedButton(
                        onClick = onRescan,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CardOutline)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.refresh_library),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    playbackState: PlaybackSessionState,
    activeItem: VideoItem,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    val progress = if (playbackState.durationMs > 0L) {
        (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AmoledBlack.copy(alpha = 0.92f))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenPlayer)
                .testTag("mini_player_bar"),
            color = ElevatedNavy,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f)),
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        VideoThumbnailImage(
                            video = activeItem,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = activeItem.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = buildString {
                            if (activeItem.isAudio && activeItem.artist.isNotBlank()) {
                                append(activeItem.artist)
                                append(" • ")
                            }
                            append(formatDuration(playbackState.positionMs))
                            if (playbackState.durationMs > 0L) {
                                append(" / ")
                                append(formatDuration(playbackState.durationMs))
                            }
                        }
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.testTag("mini_player_play_pause")
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (playbackState.isPlaying) R.string.pause else R.string.play
                            ),
                            tint = ElectricCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    if (playbackState.playlist.size > 1) {
                        IconButton(
                            onClick = onNext,
                            modifier = Modifier.testTag("mini_player_next")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = stringResource(R.string.next_video),
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("mini_player_close")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close mini player",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = ElectricCyan,
                    trackColor = SurfaceHighlight
                )
            }
        }
    }
}

