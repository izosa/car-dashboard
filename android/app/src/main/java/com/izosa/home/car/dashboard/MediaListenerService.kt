package com.izosa.home.car.dashboard

import android.service.notification.NotificationListenerService

/**
 * Does nothing itself: Android only lets apps with an enabled notification listener read other
 * apps' media sessions, which [NowPlaying] needs to show and control what Spotify is playing.
 */
class MediaListenerService : NotificationListenerService()
