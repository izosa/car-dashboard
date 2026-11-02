package com.izosa.home.car.dashboard

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

/**
 * Volume in the status corner, like the Teyes status bar shows it. Tapping it brings up the
 * volume slider; the two background arcs act as volume down / up buttons.
 *
 * On the head unit this is the Teyes volume (sound module of `com.syu.ms`, 0..36 by default,
 * the one the hardware buttons change): Teyes keeps Android's music stream at its maximum and
 * sets the level in the amplifier, so changing the stream would only add a second, hidden
 * attenuation. Without the Teyes server (emulator, phone) it falls back to the music stream.
 */
class VolumeStatus(
    private val context: Context,
    private val teyes: TeyesServer,
    container: View,
    private val icon: ImageView,
    private val level: TextView,
    volumeDown: View,
    volumeUp: View,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = update()
    }

    /** Teyes volume and mute flag; null while the Teyes server hasn't reported. */
    private var teyesVolume: Int? = null
    private var teyesMute: Int? = null

    init {
        teyes.watch(TeyesServer.MODULE_SOUND, CODE_VOLUME) { teyesVolume = it; update() }
        teyes.watch(TeyesServer.MODULE_SOUND, CODE_MUTE) { teyesMute = it; update() }
        container.setOnClickListener {
            if (teyesVolume == null || !teyes.cmd(TeyesServer.MODULE_SOUND, CMD_VOLUME, TEYES_SHOW_SLIDER)) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
        }
        bindStepper(volumeDown, AudioManager.ADJUST_LOWER, TEYES_DOWN)
        bindStepper(volumeUp, AudioManager.ADJUST_RAISE, TEYES_UP)
    }

    /**
     * Tap = one step; holding keeps stepping. A touch that moves (a swipe up to the app drawer)
     * changes nothing, so the arcs never get in the way of the drawer gesture.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun bindStepper(zone: View, direction: Int, teyesStep: Int) {
        val step = {
            if (teyesVolume == null || !teyes.cmd(TeyesServer.MODULE_SOUND, CMD_VOLUME, teyesStep)) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            }
        }
        var repeated = false
        var downX = 0f
        var downY = 0f
        val repeat = object : Runnable {
            override fun run() {
                repeated = true
                step()
                handler.postDelayed(this, REPEAT_MS)
            }
        }
        fun release() {
            handler.removeCallbacks(repeat)
            zone.isPressed = false
        }
        zone.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    repeated = false
                    zone.drawableHotspotChanged(event.x, event.y)
                    zone.isPressed = true
                    handler.postDelayed(repeat, HOLD_MS)
                }
                MotionEvent.ACTION_MOVE ->
                    if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) release()
                MotionEvent.ACTION_UP -> {
                    if (zone.isPressed && !repeated) step()
                    release()
                }
                MotionEvent.ACTION_CANCEL -> release()
            }
            true
        }
    }

    fun start() {
        context.registerReceiver(receiver, IntentFilter(VOLUME_CHANGED_ACTION))
        update()
    }

    fun stop() {
        context.unregisterReceiver(receiver)
        handler.removeCallbacksAndMessages(null)
    }

    private fun update() {
        val volume = teyesVolume?.let { if (teyesMute == 1) 0 else it }
            ?: audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        level.text = volume.toString()
        icon.setImageResource(if (volume == 0) R.drawable.ic_volume_off else R.drawable.ic_volume)
    }

    private companion object {
        /** Hidden AudioManager broadcast, sent on every volume change; widely relied upon. */
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        // Sound module update codes and its volume command, as the Teyes SystemUI `VolLayout`
        // and the ms sound module use them: a level >= 0 sets it, negatives are actions.
        const val CODE_VOLUME = 2
        const val CODE_MUTE = 3
        const val CMD_VOLUME = 0
        const val TEYES_UP = -1
        const val TEYES_DOWN = -2
        const val TEYES_SHOW_SLIDER = -6
        const val HOLD_MS = 400L
        const val REPEAT_MS = 120L
    }
}
