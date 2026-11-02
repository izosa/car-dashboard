package com.izosa.home.car.dashboard

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat

/**
 * "Now playing" row: artist and track of the active media session (Spotify first, otherwise
 * whatever is playing) with previous / play-pause / next. Hidden when nothing is playing or
 * paused, and when notification access (see [MediaListenerService]) hasn't been granted.
 */
class NowPlaying(
    private val context: Context,
    private val row: View,
    private val title: TextView,
    private val previous: View,
    private val next: View,
) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onSessionDestroyed() = pick()
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pick() }

    init {
        previous.setOnClickListener {
            controller?.transportControls?.skipToPrevious()
            nudge(previous, -1)
        }
        next.setOnClickListener {
            controller?.transportControls?.skipToNext()
            nudge(next, 1)
        }
    }

    /**
     * For the logo: toggles the shown session, or without one (no notification access, or
     * Spotify not running yet) sends a Play key, which Android routes to the last media app.
     */
    fun togglePlayback() {
        val controls = controller?.transportControls
        when {
            controls == null -> {
                val audio = context.getSystemService(AudioManager::class.java)
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            }
            isPlaying() -> controls.pause()
            else -> controls.play()
        }
    }

    /** Tap feedback for the skip arrows: a quick swell and hop in the arrow's direction. */
    private fun nudge(arrow: View, direction: Int) {
        val hop = 10 * context.resources.displayMetrics.density * direction
        arrow.animate().cancel()
        arrow.animate().translationX(hop).scaleX(1.3f).scaleY(1.3f).setDuration(110).withEndAction {
            arrow.animate().translationX(0f).scaleX(1f).scaleY(1f).setDuration(260).start()
        }.start()
    }

    fun hasAccess() = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun start() {
        if (!hasAccess()) {
            row.visibility = View.GONE
            previous.visibility = View.INVISIBLE
            next.visibility = View.INVISIBLE
            return
        }
        sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
        pick()
    }

    fun stop() {
        if (hasAccess()) sessions.removeOnActiveSessionsChangedListener(sessionsChanged)
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun pick() {
        val active = sessions.getActiveSessions(listener)
        val chosen = active.firstOrNull { it.packageName == SPOTIFY }
            ?: active.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = chosen
            chosen?.registerCallback(callback, handler)
        }
        update()
    }

    private fun isPlaying() = controller?.playbackState?.state == PlaybackState.STATE_PLAYING

    private fun update() {
        val metadata = controller?.metadata
        val state = controller?.playbackState?.state
        val visible = metadata != null &&
            (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED)
        val skipVisibility = if (isPlaying()) View.VISIBLE else View.INVISIBLE
        previous.visibility = skipVisibility
        next.visibility = skipVisibility
        if (!visible) {
            row.visibility = View.GONE
            return
        }
        val artist = metadata!!.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val track = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        title.text = listOfNotNull(artist, track).filter { it.isNotBlank() }.joinToString(" — ")
        row.visibility = View.VISIBLE
    }

    private companion object {
        const val SPOTIFY = "com.spotify.music"
    }
}
