package com.shivam.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.Immutable
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Never shows or touches notifications. Android only lets an app read other apps' media
 * sessions while it has a notification listener the user has allowed, so this exists to be
 * allowed (over adb on Fire OS, which has no switch for it; see RECOVERY.md).
 */
class MediaListenerService : NotificationListenerService()

/** What another app is playing, or has paused, for the home screen's Now playing card. */
@Immutable
class NowPlaying(
    val packageName: String,
    val appName: String,
    val title: String,
    val subtitle: String?,
    /** The artwork the app gives its session, or its icon when it gives none. */
    val art: Bitmap?,
    val playing: Boolean,
    /** Which controls the app says it supports; only those are offered. */
    val canPlayPause: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    /** Track length; 0 when the app doesn't say (live streams, some video apps). */
    val durationMs: Long,
    private val positionMs: Long,
    /** SystemClock.elapsedRealtime() when [positionMs] was reported. */
    private val positionAt: Long,
    private val speed: Float,
    private val controller: MediaController,
) {
    /** Where playback is now, moved on from the last report while playing. */
    fun position(): Long {
        if (positionMs < 0) return 0
        val moved = if (playing) ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong() else 0
        val now = positionMs + moved
        return if (durationMs > 0) now.coerceIn(0, durationMs) else now
    }

    fun playPause() {
        if (playing) controller.transportControls.pause() else controller.transportControls.play()
    }

    fun skipNext() = controller.transportControls.skipToNext()

    fun skipPrevious() = controller.transportControls.skipToPrevious()

    /** Back into the app, at what's playing when the app says where that is. */
    fun open(context: Context) {
        val session = controller.sessionActivity
        if (session != null && runCatching { session.send() }.isSuccess) return
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName) ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * The session to show, kept current: one that's playing if any, else the most recent paused one
 * with a title. Null when nothing qualifies, and always null while the listener isn't allowed.
 */
fun nowPlayingFlow(context: Context): Flow<NowPlaying?> = callbackFlow {
    val manager = context.getSystemService(MediaSessionManager::class.java)
    val listener = ComponentName(context, MediaListenerService::class.java)
    val handler = Handler(Looper.getMainLooper())
    var controllers = emptyList<MediaController>()

    fun publish() {
        trySend(pick(context, controllers))
    }

    val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = publish()
    }

    fun watch(sessions: List<MediaController>?) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = sessions.orEmpty().filter { it.packageName != context.packageName }
        controllers.forEach { it.registerCallback(callback, handler) }
        publish()
    }

    val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { watch(it) }
    // Throws SecurityException until the listener is allowed.
    val allowed = runCatching {
        manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
        watch(manager.getActiveSessions(listener))
    }.isSuccess
    if (!allowed) trySend(null)
    awaitClose {
        if (allowed) manager.removeOnActiveSessionsChangedListener(sessionsChanged)
        controllers.forEach { it.unregisterCallback(callback) }
    }
}.conflate()

/**
 * Stops playback in these apps' media sessions; true if any was stopped. An app playing media
 * runs a foreground service, which Android won't close from outside until playback stops.
 */
fun stopMediaSessions(context: Context, packages: Set<String>): Boolean {
    val manager = context.getSystemService(MediaSessionManager::class.java)
    val listener = ComponentName(context, MediaListenerService::class.java)
    val sessions = runCatching { manager.getActiveSessions(listener) }.getOrNull().orEmpty()
        .filter { it.packageName in packages }
    sessions.forEach { runCatching { it.transportControls.stop() } }
    return sessions.isNotEmpty()
}

private fun pick(context: Context, controllers: List<MediaController>): NowPlaying? {
    val shown = controllers.filter { it.state() != null && it.title() != null }
    val controller = shown.firstOrNull { it.state() == true } ?: shown.firstOrNull() ?: return null
    val metadata = controller.metadata
    val pm = context.packageManager
    val app = runCatching { pm.getApplicationInfo(controller.packageName, 0) }.getOrNull()
    val art = metadata?.let {
        it.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    } ?: app?.let { runCatching { pm.getApplicationIcon(it).toBitmap(160, 160) }.getOrNull() }
    val subtitle = metadata?.let {
        it.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: it.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: it.getString(MediaMetadata.METADATA_KEY_ALBUM)
    }?.takeIf { it.isNotBlank() }
    val state = controller.playbackState
    val actions = state?.actions ?: 0L
    fun supports(action: Long) = actions and action != 0L
    return NowPlaying(
        packageName = controller.packageName,
        appName = app?.let { pm.getApplicationLabel(it).toString() } ?: controller.packageName,
        title = controller.title()!!,
        subtitle = subtitle,
        art = art,
        playing = controller.state() == true,
        canPlayPause = supports(PlaybackState.ACTION_PLAY_PAUSE) ||
            supports(if (controller.state() == true) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY),
        canSkipPrevious = supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
        canSkipNext = supports(PlaybackState.ACTION_SKIP_TO_NEXT),
        durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
        positionMs = state?.position ?: -1,
        positionAt = state?.lastPositionUpdateTime ?: 0,
        speed = state?.playbackSpeed ?: 1f,
        controller = controller,
    )
}

/** True playing (or about to), false paused, null stopped or idle: not worth a card. */
private fun MediaController.state(): Boolean? = when (playbackState?.state) {
    PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> true
    PlaybackState.STATE_PAUSED -> false
    else -> null
}

private fun MediaController.title(): String? = metadata?.let {
    it.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: it.getString(MediaMetadata.METADATA_KEY_TITLE)
}?.takeIf { it.isNotBlank() }
