package com.example.player

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.example.MainActivity
import com.example.R
import com.example.data.PlaybackPreferencesRepository
import com.example.data.VideoItem
import com.example.data.formatDuration
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

data class PlaybackSessionState(
    val hasActiveSession: Boolean = false,
    val playlist: List<VideoItem> = emptyList(),
    val currentIndex: Int = 0,
    val currentItem: VideoItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val decoderMode: DecoderMode = DecoderMode.HW,
    val backgroundPlayEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val loopMode: LoopMode = LoopMode.OFF,
    val audioBoostPercent: Int = 100,
    val audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET,
    val videoDimensions: Pair<Int, Int> = 16 to 9,
    val audioTracks: List<AudioTrackOption> = emptyList(),
    val embeddedSubtitles: List<SubtitleSelection.Embedded> = emptyList(),
    val embeddedCueText: String = "",
    val sleepTimerOption: SleepTimerOption = SleepTimerOption.OFF,
    val sleepRemainingSeconds: Long = 0L,
    val abStartMs: Long? = null,
    val abEndMs: Long? = null,
    val equalizer: EqualizerUiState = EqualizerUiState(),
    val statusToastLabel: String? = null
)

data class PendingPlaybackRequest(
    val sessionKey: Int,
    val playlist: List<VideoItem>,
    val startIndex: Int,
    val startPositionMs: Long,
    val autoPlay: Boolean
)

object PlaybackEngine {
    private val _sessionState = MutableStateFlow(PlaybackSessionState())
    val sessionState: StateFlow<PlaybackSessionState> = _sessionState.asStateFlow()

    private val _exoPlayerFlow = MutableStateFlow<ExoPlayer?>(null)
    val exoPlayerFlow: StateFlow<ExoPlayer?> = _exoPlayerFlow.asStateFlow()

    private var serviceRef: PlaybackService? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingRequest: PendingPlaybackRequest? = null

    var activeSessionKey: Int = -1
        private set

    fun attachService(service: PlaybackService) {
        serviceRef = service
        _exoPlayerFlow.value = service.currentExoPlayer()
        pendingRequest?.let { req ->
            pendingRequest = null
            service.loadPlaylistInternal(
                sessionKey = req.sessionKey,
                playlist = req.playlist,
                startIndex = req.startIndex,
                startPositionMs = req.startPositionMs,
                autoPlay = req.autoPlay
            )
        }
    }

    fun detachService(service: PlaybackService) {
        if (serviceRef === service) {
            serviceRef = null
            _exoPlayerFlow.value = null
            controllerFuture?.let { future ->
                MediaController.releaseFuture(future)
            }
            controllerFuture = null
        }
    }

    fun updatePlayerInstance(player: ExoPlayer?) {
        _exoPlayerFlow.value = player
    }

    fun updateState(transform: (PlaybackSessionState) -> PlaybackSessionState) {
        _sessionState.update(transform)
    }

    fun markSessionKey(key: Int) {
        activeSessionKey = key
    }

    fun ensureServiceConnected(context: Context) {
        val appContext = context.applicationContext
        if (serviceRef == null && controllerFuture == null) {
            val token = SessionToken(
                appContext,
                ComponentName(appContext, PlaybackService::class.java)
            )
            val future = MediaController.Builder(appContext, token).buildAsync()
            controllerFuture = future
        }
    }

    fun requestPlaylistPlayback(
        context: Context,
        sessionKey: Int,
        playlist: List<VideoItem>,
        startIndex: Int,
        startPositionMs: Long,
        autoPlay: Boolean
    ) {
        ensureServiceConnected(context)
        val service = serviceRef
        if (service != null) {
            pendingRequest = null
            service.loadPlaylistInternal(
                sessionKey = sessionKey,
                playlist = playlist,
                startIndex = startIndex,
                startPositionMs = startPositionMs,
                autoPlay = autoPlay
            )
        } else {
            pendingRequest = PendingPlaybackRequest(
                sessionKey = sessionKey,
                playlist = playlist,
                startIndex = startIndex,
                startPositionMs = startPositionMs,
                autoPlay = autoPlay
            )
        }
    }

    fun togglePlayPause() {
        serviceRef?.currentExoPlayer()?.let { p ->
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun seekTo(positionMs: Long) {
        serviceRef?.seekToInternal(positionMs)
    }

    fun skipNext() {
        serviceRef?.skipNextInternal()
    }

    fun skipPrevious() {
        serviceRef?.skipPreviousInternal()
    }

    fun setBackgroundPlayEnabled(enabled: Boolean) {
        _sessionState.update { it.copy(backgroundPlayEnabled = enabled) }
    }

    fun setPlaybackSpeed(speed: Float) {
        serviceRef?.setPlaybackSpeedInternal(speed)
    }

    fun switchDecoderMode(mode: DecoderMode) {
        serviceRef?.switchDecoderModeInternal(mode)
    }

    fun cycleLoopMode() {
        serviceRef?.cycleLoopModeInternal()
    }

    fun setAudioBoostPercent(percent: Int) {
        serviceRef?.setAudioBoostInternal(percent)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        serviceRef?.setEqualizerEnabledInternal(enabled)
            ?: _sessionState.update { it.copy(equalizer = it.equalizer.copy(enabled = enabled)) }
    }

    fun selectEqualizerPreset(preset: EqPreset) {
        serviceRef?.selectEqualizerPresetInternal(preset)
            ?: _sessionState.update {
                val eq = it.equalizer
                val normalized = PlayerMath.normalizeEqBands(
                    preset.bandOffsetsMb,
                    eq.minLevelMb,
                    eq.maxLevelMb,
                    eq.bandLevelsMb.size.coerceAtLeast(5)
                )
                it.copy(
                    equalizer = eq.copy(enabled = true, preset = preset, bandLevelsMb = normalized),
                    statusToastLabel = "EQ: ${preset.label}"
                )
            }
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelMb: Int) {
        serviceRef?.setEqualizerBandLevelInternal(bandIndex, levelMb)
            ?: _sessionState.update {
                val eq = it.equalizer
                if (bandIndex !in eq.bandLevelsMb.indices) return@update it
                val clamped = levelMb.coerceIn(eq.minLevelMb, eq.maxLevelMb)
                val updated = eq.bandLevelsMb.toMutableList().apply { set(bandIndex, clamped) }
                it.copy(
                    equalizer = eq.copy(enabled = true, preset = EqPreset.CUSTOM, bandLevelsMb = updated)
                )
            }
    }

    fun setSleepTimerOption(option: SleepTimerOption) {
        serviceRef?.setSleepTimerInternal(option)
    }

    fun toggleAbRepeat() {
        serviceRef?.toggleAbRepeatInternal()
    }

    fun clearAbRepeat() {
        _sessionState.update {
            it.copy(abStartMs = null, abEndMs = null, statusToastLabel = "A-B Repeat Cleared")
        }
    }

    fun clearToastLabel() {
        _sessionState.update { it.copy(statusToastLabel = null) }
    }

    fun showToastLabel(label: String) {
        _sessionState.update { it.copy(statusToastLabel = label) }
    }

    fun selectAudioTrack(option: AudioTrackOption) {
        serviceRef?.selectAudioTrackInternal(option)
    }

    fun disableEmbeddedSubtitles() {
        serviceRef?.disableEmbeddedSubtitlesInternal()
    }

    fun selectEmbeddedSubtitle(sub: SubtitleSelection.Embedded) {
        serviceRef?.selectEmbeddedSubtitleInternal(sub)
    }

    fun saveCurrentProgress() {
        serviceRef?.saveCurrentProgressInternal()
    }

    fun pauseForForegroundExit() {
        serviceRef?.pauseAndSaveInternal()
    }

    fun closePlayback(context: Context) {
        pendingRequest = null
        activeSessionKey = -1
        serviceRef?.stopAndClosePlaybackInternal()
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        controllerFuture = null
        context.applicationContext.stopService(
            Intent(context.applicationContext, PlaybackService::class.java)
        )
    }
}

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefsRepo: PlaybackPreferencesRepository

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var audioEqualizer: Equalizer? = null
    private var attachedEqSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var fallbackArtworkBytes: ByteArray? = null

    private var pollingJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var artworkLoadJob: Job? = null

    private val closeCommand = SessionCommand(ACTION_CLOSE_PLAYBACK, Bundle.EMPTY)
    private val closeCommandButton by lazy {
        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("Close")
            .setCustomIconResId(R.drawable.ic_close_notification)
            .setSessionCommand(closeCommand)
            .build()
    }

    private val dismissNotificationPendingIntent by lazy {
        val intent = Intent(ACTION_NOTIFICATION_DISMISSED).apply {
            setPackage(packageName)
        }
        PendingIntent.getBroadcast(
            this,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val notificationDismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_NOTIFICATION_DISMISSED) {
                stopAndClosePlaybackInternal()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsRepo = PlaybackPreferencesRepository(applicationContext)

        ContextCompat.registerReceiver(
            this,
            notificationDismissReceiver,
            IntentFilter(ACTION_NOTIFICATION_DISMISSED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Allow notification to become swipe-dismissible immediately when paused
        setForegroundServiceTimeoutMs(0L)

        val defaultProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_notification_channel)
            .build().apply {
                setSmallIcon(R.drawable.ic_notification_alt)
            }

        val notificationProvider = object : MediaNotification.Provider {
            override fun createNotification(
                mediaSession: MediaSession,
                customLayout: ImmutableList<CommandButton>,
                actionFactory: MediaNotification.ActionFactory,
                onNotificationChangedCallback: MediaNotification.Provider.Callback
            ): MediaNotification {
                val created = defaultProvider.createNotification(
                    mediaSession,
                    customLayout,
                    actionFactory,
                    onNotificationChangedCallback
                )
                created.notification.deleteIntent = dismissNotificationPendingIntent
                if (!mediaSession.player.playWhenReady) {
                    created.notification.flags =
                        created.notification.flags and Notification.FLAG_ONGOING_EVENT.inv()
                }
                return created
            }

            override fun handleCustomCommand(
                session: MediaSession,
                action: String,
                extras: Bundle
            ): Boolean = defaultProvider.handleCustomCommand(session, action, extras)

            override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
                defaultProvider.notificationChannelInfo
        }
        setMediaNotificationProvider(notificationProvider)

        val initialPlayer = buildExoPlayer(DecoderMode.HW)
        exoPlayer = initialPlayer

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_PLAYER, true)
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, initialPlayer)
            .setSessionActivity(sessionActivity)
            .setCallback(SessionCallback())
            .build()

        startPositionPolling()
        PlaybackEngine.attachService(this)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired)
        if (!startInForegroundRequired && !session.player.playWhenReady) {
            stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = exoPlayer
        val state = PlaybackEngine.sessionState.value
        val keepRunning = player != null && player.playWhenReady && state.backgroundPlayEnabled
        if (!keepRunning) {
            saveCurrentProgressInternal()
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveCurrentProgressInternal()
        pollingJob?.cancel()
        sleepTimerJob?.cancel()
        artworkLoadJob?.cancel()
        runCatching { unregisterReceiver(notificationDismissReceiver) }
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
        runCatching { audioEqualizer?.release() }
        audioEqualizer = null
        attachedEqSessionId = C.AUDIO_SESSION_ID_UNSET
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        PlaybackEngine.detachService(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    fun currentExoPlayer(): ExoPlayer? = exoPlayer

    private fun buildExoPlayer(decoderMode: DecoderMode): ExoPlayer {
        val trackSelector = DefaultTrackSelector(this)
        val renderersFactory = DefaultRenderersFactory(this).apply {
            if (decoderMode == DecoderMode.SW) {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    val all = MediaCodecSelector.DEFAULT.getDecoderInfos(
                        mimeType,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder
                    )
                    val swOnly = all.filter { info ->
                        !info.hardwareAccelerated ||
                            info.softwareOnly ||
                            info.name.startsWith("OMX.google.", ignoreCase = true) ||
                            info.name.startsWith("c2.android.", ignoreCase = true)
                    }
                    swOnly.ifEmpty { all }
                }
            }
        }
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setRenderersFactory(renderersFactory)
            .setAudioAttributes(audioAttrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                addListener(playerListener)
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            PlaybackEngine.updateState { it.copy(isPlaying = playing) }
            if (!playing) {
                saveCurrentProgressInternal()
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                PlaybackEngine.updateState {
                    it.copy(videoDimensions = videoSize.width to videoSize.height)
                }
            }
        }

        override fun onAudioSessionIdChanged(sessionId: Int) {
            PlaybackEngine.updateState { it.copy(audioSessionId = sessionId) }
            applyLoudnessEnhancer(sessionId, PlaybackEngine.sessionState.value.audioBoostPercent)
            applyEqualizerState(sessionId, PlaybackEngine.sessionState.value.equalizer)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val player = exoPlayer ?: return
            val nextIdx = player.currentMediaItemIndex
            val state = PlaybackEngine.sessionState.value
            val nextItem = state.playlist.getOrNull(nextIdx)
            PlaybackEngine.updateState { current ->
                current.copy(
                    currentIndex = nextIdx,
                    currentItem = nextItem ?: current.currentItem,
                    durationMs = nextItem?.durationMs ?: 0L,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    abStartMs = null,
                    abEndMs = null,
                    embeddedCueText = "",
                    backgroundPlayEnabled = current.backgroundPlayEnabled || (nextItem?.isAudio == true)
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = exoPlayer ?: return
            val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            if (dur != null) {
                PlaybackEngine.updateState { it.copy(durationMs = dur) }
            }
            if (playbackState == Player.STATE_ENDED) {
                saveCurrentProgressInternal()
                val state = PlaybackEngine.sessionState.value
                if (state.sleepTimerOption == SleepTimerOption.END_OF_VIDEO) {
                    player.pause()
                    PlaybackEngine.updateState {
                        it.copy(
                            sleepTimerOption = SleepTimerOption.OFF,
                            sleepRemainingSeconds = 0L,
                            statusToastLabel = "Sleep Timer Finished"
                        )
                    }
                    return
                }
                if (state.loopMode == LoopMode.REPEAT_FOLDER && state.playlist.size > 1) {
                    player.seekTo(0, 0L)
                    player.play()
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            PlaybackEngine.updateState {
                it.copy(
                    audioTracks = extractAudioTracks(tracks),
                    embeddedSubtitles = extractEmbeddedSubtitles(tracks)
                )
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            val text = cueGroup.cues
                .mapNotNull { it.text?.toString()?.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
            PlaybackEngine.updateState { it.copy(embeddedCueText = text) }
        }
    }

    fun loadPlaylistInternal(
        sessionKey: Int,
        playlist: List<VideoItem>,
        startIndex: Int,
        startPositionMs: Long,
        autoPlay: Boolean
    ) {
        val player = exoPlayer ?: return
        val safeList = playlist.ifEmpty { return }
        val safeIdx = startIndex.coerceIn(0, safeList.lastIndex)
        val targetItem = safeList[safeIdx]

        PlaybackEngine.markSessionKey(sessionKey)
        val fallbackArt = getFallbackArtworkBytes()
        val mediaItems = safeList.map { buildMediaItem(it, fallbackArt) }

        val currentState = PlaybackEngine.sessionState.value
        val bgEnabled = targetItem.isAudio || currentState.backgroundPlayEnabled

        PlaybackEngine.updateState {
            it.copy(
                hasActiveSession = true,
                playlist = safeList,
                currentIndex = safeIdx,
                currentItem = targetItem,
                durationMs = targetItem.durationMs,
                positionMs = startPositionMs,
                backgroundPlayEnabled = bgEnabled,
                abStartMs = null,
                abEndMs = null,
                embeddedCueText = ""
            )
        }

        player.setMediaItems(mediaItems, safeIdx, startPositionMs)
        player.repeatMode = currentState.loopMode.exoRepeatMode
        player.setPlaybackSpeed(currentState.playbackSpeed)
        player.prepare()
        player.playWhenReady = autoPlay
        applyLoudnessEnhancer(player.audioSessionId, currentState.audioBoostPercent)
        applyEqualizerState(player.audioSessionId, currentState.equalizer)

        loadArtworkAsync(safeList, fallbackArt)
    }

    private fun buildMediaItem(item: VideoItem, artworkBytes: ByteArray?): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(item.title)
            .setDisplayTitle(item.title)
            .setArtist(item.artist.ifBlank { "Alt Player" })
            .setAlbumTitle(item.album.ifBlank { item.folderName })
            .setIsPlayable(true)
            .setMediaType(
                if (item.isAudio) MediaMetadata.MEDIA_TYPE_MUSIC else MediaMetadata.MEDIA_TYPE_VIDEO
            )
        if (artworkBytes != null) {
            metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return MediaItem.Builder()
            .setMediaId(item.uri.toString())
            .setUri(item.uri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun loadArtworkAsync(playlist: List<VideoItem>, fallbackArt: ByteArray?) {
        artworkLoadJob?.cancel()
        artworkLoadJob = serviceScope.launch {
            playlist.forEachIndexed { index, item ->
                val artBytes = withContext(Dispatchers.IO) {
                    extractArtworkBytes(item) ?: fallbackArt
                } ?: return@forEachIndexed
                val player = exoPlayer ?: return@launch
                if (index < player.mediaItemCount) {
                    val existing = player.getMediaItemAt(index)
                    if (existing.mediaId == item.uri.toString()) {
                        val updatedMeta = existing.mediaMetadata.buildUpon()
                            .setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build()
                        val updatedItem = existing.buildUpon()
                            .setMediaMetadata(updatedMeta)
                            .build()
                        player.replaceMediaItem(index, updatedItem)
                    }
                }
            }
        }
    }

    private fun extractArtworkBytes(item: VideoItem): ByteArray? {
        val rawBitmap = runCatching {
            if (item.isAudio) {
                if (item.albumId > 0L) {
                    val albumUri = Uri.parse("content://media/external/audio/albumart/${item.albumId}")
                    contentResolver.openInputStream(albumUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } else null
                    ?: MediaMetadataRetriever().useCompat { retriever ->
                        retriever.setDataSource(this, item.uri)
                        val pic = retriever.embeddedPicture
                        if (pic != null && pic.isNotEmpty()) {
                            BitmapFactory.decodeByteArray(pic, 0, pic.size)
                        } else null
                    }
            } else {
                MediaMetadataRetriever().useCompat { retriever ->
                    retriever.setDataSource(this, item.uri)
                    retriever.getFrameAtTime(1_500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(-1)
                }
            }
        }.getOrNull() ?: return null

        val scaled = scaleBitmapDown(rawBitmap, 320)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        return baos.toByteArray()
    }

    private fun getFallbackArtworkBytes(): ByteArray? {
        fallbackArtworkBytes?.let { return it }
        return runCatching {
            val bmp = BitmapFactory.decodeResource(resources, R.drawable.alt_player_logo) ?: return null
            val scaled = scaleBitmapDown(bmp, 256)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 95, baos)
            baos.toByteArray().also { fallbackArtworkBytes = it }
        }.getOrNull()
    }

    private fun scaleBitmapDown(source: Bitmap, maxDim: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim) return source
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(
            source,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    fun seekToInternal(positionMs: Long) {
        val player = exoPlayer ?: return
        val clamped = positionMs.coerceAtLeast(0L)
        player.seekTo(clamped)
        PlaybackEngine.updateState { it.copy(positionMs = clamped) }
    }

    fun skipNextInternal() {
        val player = exoPlayer ?: return
        saveCurrentProgressInternal()
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
        } else if (player.mediaItemCount > 1) {
            player.seekTo(0, 0L)
            player.play()
        }
    }

    fun skipPreviousInternal() {
        val player = exoPlayer ?: return
        saveCurrentProgressInternal()
        if (player.currentPosition > 3_500L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.play()
        } else {
            player.seekTo(0L)
        }
    }

    fun setPlaybackSpeedInternal(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        PlaybackEngine.updateState { it.copy(playbackSpeed = speed) }
    }

    fun switchDecoderModeInternal(mode: DecoderMode) {
        val oldPlayer = exoPlayer ?: return
        val state = PlaybackEngine.sessionState.value
        if (state.decoderMode == mode) return

        val pos = oldPlayer.currentPosition.coerceAtLeast(0L)
        val idx = oldPlayer.currentMediaItemIndex.coerceAtLeast(0)
        val shouldPlay = oldPlayer.isPlaying || oldPlayer.playWhenReady

        val newPlayer = buildExoPlayer(mode)
        val fallbackArt = getFallbackArtworkBytes()
        val mediaItems = state.playlist.map { buildMediaItem(it, fallbackArt) }
        if (mediaItems.isNotEmpty()) {
            newPlayer.setMediaItems(mediaItems, idx.coerceIn(0, mediaItems.lastIndex), pos)
            newPlayer.repeatMode = state.loopMode.exoRepeatMode
            newPlayer.setPlaybackSpeed(state.playbackSpeed)
            newPlayer.prepare()
            newPlayer.playWhenReady = shouldPlay
        }

        mediaSession?.player = newPlayer
        exoPlayer = newPlayer
        PlaybackEngine.updatePlayerInstance(newPlayer)
        PlaybackEngine.updateState {
            it.copy(
                decoderMode = mode,
                statusToastLabel = "${mode.label} Decoder • ${mode.fullName}"
            )
        }
        applyLoudnessEnhancer(newPlayer.audioSessionId, state.audioBoostPercent)
        applyEqualizerState(newPlayer.audioSessionId, state.equalizer)
        oldPlayer.removeListener(playerListener)
        oldPlayer.release()
    }

    fun cycleLoopModeInternal() {
        val next = PlaybackEngine.sessionState.value.loopMode.next()
        exoPlayer?.repeatMode = next.exoRepeatMode
        PlaybackEngine.updateState {
            it.copy(loopMode = next, statusToastLabel = "Loop: ${next.label}")
        }
    }

    fun setAudioBoostInternal(percent: Int) {
        val clamped = percent.coerceIn(
            PlayerMath.MIN_AUDIO_BOOST_PERCENT,
            PlayerMath.MAX_AUDIO_BOOST_PERCENT
        )
        PlaybackEngine.updateState {
            it.copy(audioBoostPercent = clamped, statusToastLabel = "Audio Boost: $clamped%")
        }
        val sessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        applyLoudnessEnhancer(sessionId, clamped)
    }

    private fun applyLoudnessEnhancer(audioSessionId: Int, boostPercent: Int) {
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
        if (boostPercent <= 100) return
        loudnessEnhancer = runCatching {
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(PlayerMath.computeLoudnessGainMb(boostPercent))
                enabled = true
            }
        }.getOrNull()
    }

    fun setEqualizerEnabledInternal(enabled: Boolean) {
        val updated = PlaybackEngine.sessionState.value.equalizer.copy(enabled = enabled)
        PlaybackEngine.updateState {
            it.copy(
                equalizer = updated,
                statusToastLabel = if (enabled) "Equalizer On (${updated.preset.label})" else "Equalizer Off"
            )
        }
        val sessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        applyEqualizerState(sessionId, updated)
    }

    fun selectEqualizerPresetInternal(preset: EqPreset) {
        val eq = PlaybackEngine.sessionState.value.equalizer
        val count = eq.bandLevelsMb.size.coerceAtLeast(1)
        val normalized = PlayerMath.normalizeEqBands(
            targetMb = preset.bandOffsetsMb,
            minMb = eq.minLevelMb,
            maxMb = eq.maxLevelMb,
            bandCount = count
        )
        val updated = eq.copy(enabled = true, preset = preset, bandLevelsMb = normalized)
        PlaybackEngine.updateState {
            it.copy(
                equalizer = updated,
                statusToastLabel = "EQ: ${preset.label}"
            )
        }
        val sessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        applyEqualizerState(sessionId, updated)
    }

    fun setEqualizerBandLevelInternal(bandIndex: Int, levelMb: Int) {
        val eq = PlaybackEngine.sessionState.value.equalizer
        if (bandIndex !in eq.bandLevelsMb.indices) return
        val clamped = levelMb.coerceIn(eq.minLevelMb, eq.maxLevelMb)
        val updatedBands = eq.bandLevelsMb.toMutableList().apply { set(bandIndex, clamped) }
        val updated = eq.copy(enabled = true, preset = EqPreset.CUSTOM, bandLevelsMb = updatedBands)
        PlaybackEngine.updateState { it.copy(equalizer = updated) }
        val sessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        applyEqualizerState(sessionId, updated)
    }

    private fun applyEqualizerState(audioSessionId: Int, eqState: EqualizerUiState) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
        runCatching {
            if (audioEqualizer == null || attachedEqSessionId != audioSessionId) {
                runCatching { audioEqualizer?.release() }
                val eq = Equalizer(0, audioSessionId)
                audioEqualizer = eq
                attachedEqSessionId = audioSessionId

                val numBands = eq.numberOfBands.toInt().coerceIn(1, 10)
                val range = eq.bandLevelRange
                val minMb = range.getOrElse(0) { -1500 }.toInt()
                val maxMb = range.getOrElse(1) { 1500 }.toInt()
                val freqs = List(numBands) { band ->
                    (eq.getCenterFreq(band.toShort()) / 1000).coerceAtLeast(20)
                }
                val normalized = PlayerMath.normalizeEqBands(
                    targetMb = if (eqState.preset == EqPreset.CUSTOM) eqState.bandLevelsMb else eqState.preset.bandOffsetsMb,
                    minMb = minMb,
                    maxMb = maxMb,
                    bandCount = numBands
                )
                val syncedState = eqState.copy(
                    bandLevelsMb = normalized,
                    bandCenterFreqsHz = freqs,
                    minLevelMb = minMb,
                    maxLevelMb = maxMb
                )
                PlaybackEngine.updateState { it.copy(equalizer = syncedState) }
                for (band in 0 until numBands) {
                    eq.setBandLevel(band.toShort(), normalized[band].toShort())
                }
                eq.enabled = syncedState.enabled
                return
            }

            val eq = audioEqualizer ?: return
            val numBands = eq.numberOfBands.toInt().coerceAtLeast(1)
            val normalized = PlayerMath.normalizeEqBands(
                targetMb = eqState.bandLevelsMb,
                minMb = eqState.minLevelMb,
                maxMb = eqState.maxLevelMb,
                bandCount = numBands
            )
            for (band in 0 until numBands) {
                eq.setBandLevel(band.toShort(), normalized[band].toShort())
            }
            eq.enabled = eqState.enabled
        }
    }

    fun setSleepTimerInternal(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        if (option == SleepTimerOption.OFF) {
            PlaybackEngine.updateState {
                it.copy(
                    sleepTimerOption = SleepTimerOption.OFF,
                    sleepRemainingSeconds = 0L,
                    statusToastLabel = "Sleep Timer Off"
                )
            }
            return
        }

        PlaybackEngine.updateState {
            it.copy(
                sleepTimerOption = option,
                sleepRemainingSeconds = (option.minutes ?: 0) * 60L,
                statusToastLabel = "Sleep Timer: ${option.label}"
            )
        }

        val mins = option.minutes ?: return
        sleepTimerJob = serviceScope.launch {
            var remaining = mins * 60L
            while (remaining > 0L) {
                delay(1000L)
                remaining -= 1L
                PlaybackEngine.updateState { it.copy(sleepRemainingSeconds = remaining) }
            }
            exoPlayer?.pause()
            PlaybackEngine.updateState {
                it.copy(
                    sleepTimerOption = SleepTimerOption.OFF,
                    sleepRemainingSeconds = 0L,
                    statusToastLabel = "Sleep Timer Paused Playback"
                )
            }
        }
    }

    fun toggleAbRepeatInternal() {
        val pos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val state = PlaybackEngine.sessionState.value
        val start = state.abStartMs
        if (start == null) {
            PlaybackEngine.updateState {
                it.copy(
                    abStartMs = pos,
                    abEndMs = null,
                    statusToastLabel = "A-B Marker A: ${formatDuration(pos)}"
                )
            }
        } else if (state.abEndMs == null) {
            if (pos > start + 500L) {
                exoPlayer?.seekTo(start)
                PlaybackEngine.updateState {
                    it.copy(
                        abEndMs = pos,
                        positionMs = start,
                        statusToastLabel = "A-B Loop: ${formatDuration(start)}–${formatDuration(pos)}"
                    )
                }
            } else {
                PlaybackEngine.updateState {
                    it.copy(
                        abStartMs = pos,
                        statusToastLabel = "Set Marker B after ${formatDuration(start)}"
                    )
                }
            }
        }
    }

    fun selectAudioTrackInternal(option: AudioTrackOption) {
        val player = exoPlayer ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(
                    option.trackGroup.mediaTrackGroup,
                    option.trackIndex
                )
            )
            .build()
    }

    fun disableEmbeddedSubtitlesInternal() {
        val player = exoPlayer ?: return
        PlaybackEngine.updateState { it.copy(embeddedCueText = "") }
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    fun selectEmbeddedSubtitleInternal(sub: SubtitleSelection.Embedded) {
        val player = exoPlayer ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(
                    sub.trackGroup.mediaTrackGroup,
                    sub.trackIndex
                )
            )
            .build()
    }

    fun saveCurrentProgressInternal() {
        val player = exoPlayer ?: return
        val item = PlaybackEngine.sessionState.value.currentItem ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: item.durationMs
        serviceScope.launch(Dispatchers.IO) {
            prefsRepo.savePlaybackProgress(item.uri.toString(), pos, dur)
        }
    }

    fun pauseAndSaveInternal() {
        saveCurrentProgressInternal()
        exoPlayer?.pause()
    }

    fun stopAndClosePlaybackInternal() {
        saveCurrentProgressInternal()
        sleepTimerJob?.cancel()
        artworkLoadJob?.cancel()
        exoPlayer?.run {
            pause()
            stop()
            clearMediaItems()
        }
        PlaybackEngine.updateState {
            PlaybackSessionState(
                decoderMode = it.decoderMode,
                equalizer = it.equalizer
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startPositionPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            var ticks = 0
            while (true) {
                val player = exoPlayer
                val state = PlaybackEngine.sessionState.value
                if (player != null && state.hasActiveSession) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: state.durationMs
                    val startMarker = state.abStartMs
                    val endMarker = state.abEndMs
                    if (startMarker != null && endMarker != null && endMarker > startMarker && pos >= endMarker) {
                        player.seekTo(startMarker)
                        PlaybackEngine.updateState { it.copy(positionMs = startMarker, durationMs = dur) }
                    } else if (pos != state.positionMs || dur != state.durationMs) {
                        PlaybackEngine.updateState { it.copy(positionMs = pos, durationMs = dur) }
                    }
                    ticks++
                    if (ticks % 25 == 0 && player.isPlaying) {
                        saveCurrentProgressInternal()
                    }
                }
                delay(if (state.abEndMs != null) 100L else 250L)
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(closeCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setMediaButtonPreferences(listOf(closeCommandButton))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_CLOSE_PLAYBACK) {
                PlaybackEngine.closePlayback(this@PlaybackService)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    companion object {
        private const val CHANNEL_ID = "altplayer_media_session"
        const val ACTION_CLOSE_PLAYBACK = "com.example.player.CLOSE_PLAYBACK"
        const val ACTION_NOTIFICATION_DISMISSED = "com.example.player.NOTIFICATION_DISMISSED"
        const val EXTRA_OPEN_PLAYER = "extra_open_player_from_notification"
    }
}

private inline fun <R> MediaMetadataRetriever.useCompat(block: (MediaMetadataRetriever) -> R): R {
    return try {
        block(this)
    } finally {
        runCatching { release() }
    }
}

private fun extractAudioTracks(tracks: Tracks): List<AudioTrackOption> {
    val result = mutableListOf<AudioTrackOption>()
    var counter = 1
    tracks.groups.forEachIndexed { groupIdx, group ->
        if (group.type == C.TRACK_TYPE_AUDIO) {
            for (trackIdx in 0 until group.length) {
                if (!group.isTrackSupported(trackIdx)) continue
                val format = group.getTrackFormat(trackIdx)
                val langCode = format.language.orEmpty()
                val langName = if (langCode.isNotBlank() && langCode != "und") {
                    Locale.forLanguageTag(langCode).displayLanguage.replaceFirstChar { it.uppercase() }
                } else {
                    "Audio Track $counter"
                }
                val title = format.label?.takeIf { it.isNotBlank() } ?: langName
                val channels = when (format.channelCount) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    6 -> "5.1"
                    8 -> "7.1"
                    else -> if (format.channelCount > 0) "${format.channelCount}ch" else ""
                }
                val codec = format.sampleMimeType?.substringAfter('/')?.uppercase().orEmpty()
                val detail = listOf(channels, codec).filter { it.isNotBlank() }.joinToString(" • ")

                result.add(
                    AudioTrackOption(
                        id = "${groupIdx}_$trackIdx",
                        label = title,
                        detail = detail,
                        isSelected = group.isTrackSelected(trackIdx),
                        trackGroup = group,
                        trackIndex = trackIdx
                    )
                )
                counter++
            }
        }
    }
    return result
}

private fun extractEmbeddedSubtitles(tracks: Tracks): List<SubtitleSelection.Embedded> {
    val result = mutableListOf<SubtitleSelection.Embedded>()
    var counter = 1
    tracks.groups.forEachIndexed { groupIdx, group ->
        if (group.type == C.TRACK_TYPE_TEXT) {
            for (trackIdx in 0 until group.length) {
                val format = group.getTrackFormat(trackIdx)
                val langCode = format.language.orEmpty()
                val langName = if (langCode.isNotBlank() && langCode != "und") {
                    Locale.forLanguageTag(langCode).displayLanguage.replaceFirstChar { it.uppercase() }
                } else {
                    ""
                }
                val label = format.label?.takeIf { it.isNotBlank() }
                    ?: langName.ifBlank { "Subtitle Track $counter" }
                result.add(
                    SubtitleSelection.Embedded(
                        id = "${groupIdx}_$trackIdx",
                        label = label,
                        language = langName,
                        isSelected = group.isTrackSelected(trackIdx),
                        trackGroup = group,
                        trackIndex = trackIdx
                    )
                )
                counter++
            }
        }
    }
    return result
}
