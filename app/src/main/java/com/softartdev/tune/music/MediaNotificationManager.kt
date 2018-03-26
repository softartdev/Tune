/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.softartdev.tune.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.session.PlaybackState
import android.os.Build
import android.os.RemoteException
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

import com.softartdev.tune.R

import timber.log.Timber

/**
 * Keeps track of a notification and updates it automatically for a given
 * MediaSession. Maintaining a visible notification (usually) guarantees that the music service
 * won't be killed during playback.
 */
class MediaNotificationManager(private val mService: MediaPlaybackService) : BroadcastReceiver() {
    private var mSessionToken: MediaSessionCompat.Token? = null
    private var mController: MediaControllerCompat? = null
    private var mTransportControls: MediaControllerCompat.TransportControls? = null

    private var mPlaybackState: PlaybackStateCompat? = null
    private var mMetadata: MediaMetadataCompat? = null

    private val mNotificationManager: NotificationManager = mService.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val pkg = mService.packageName
    private val mPauseIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE, Intent(ACTION_PAUSE).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)
    private val mPlayIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE, Intent(ACTION_PLAY).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)
    private val mPreviousIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE, Intent(ACTION_PREV).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)
    private val mNextIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE, Intent(ACTION_NEXT).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)

    private var mStarted = false

    companion object {
        private const val CHANNEL_ID = "com.softartdev.tune.MUSIC_CHANNEL_ID"
        private const val NOTIFICATION_ID = 412
        private const val REQUEST_CODE = 100

        const val ACTION_PAUSE = "com.softartdev.tune.pause"
        const val ACTION_PLAY = "com.softartdev.tune.play"
        const val ACTION_PREV = "com.softartdev.tune.prev"
        const val ACTION_NEXT = "com.softartdev.tune.next"
    }

    init {
        updateSessionToken()

        // Cancel all notifications to handle the case where the Service was killed and
        // restarted by the system.
        mNotificationManager.cancelAll()
    }

    private val mCb = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            mPlaybackState = state
            Timber.d("Received new playback state %s", state)
            if (state?.state == PlaybackStateCompat.STATE_STOPPED || state?.state == PlaybackStateCompat.STATE_NONE) {
                stopNotification()
            } else {
                mNotificationManager.notify(NOTIFICATION_ID, createNotification())
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            mMetadata = metadata
            Timber.d("Received new metadata %s", metadata)
            mNotificationManager.notify(NOTIFICATION_ID, createNotification())
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            Timber.d("Session was destroyed, resetting to the new session token")
            updateSessionToken()
        }
    }

    /**
     * Posts the notification and starts tracking the session to keep it
     * updated. The notification will automatically be removed if the session is
     * destroyed before [.stopNotification] is called.
     */
    fun startNotification() {
        if (!mStarted) {
            mMetadata = mController?.metadata
            mPlaybackState = mController?.playbackState

            // The notification must be updated after setting started to true
            mController?.registerCallback(mCb)
            val filter = IntentFilter().apply {
                addAction(ACTION_NEXT)
                addAction(ACTION_PAUSE)
                addAction(ACTION_PLAY)
                addAction(ACTION_PREV)
            }
            mService.registerReceiver(this, filter)

            mService.startForeground(NOTIFICATION_ID, createNotification())
            mStarted = true
        }
    }

    /**
     * Removes the notification and stops tracking the session. If the session
     * was destroyed this has no effect.
     */
    fun stopNotification() {
        if (mStarted) {
            mStarted = false
            mController?.unregisterCallback(mCb)
            try {
                mNotificationManager.cancel(NOTIFICATION_ID)
                mService.unregisterReceiver(this)
            } catch (ex: IllegalArgumentException) {
                // ignore if the receiver is not registered.
            }
            mService.stopForeground(true)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        intent.action?.let {
            Timber.d("Received intent with action %s", it)
            when (it) {
                ACTION_PAUSE -> mTransportControls?.pause()
                ACTION_PLAY -> mTransportControls?.play()
                ACTION_NEXT -> mTransportControls?.skipToNext()
                ACTION_PREV -> mTransportControls?.skipToPrevious()
                else -> Timber.w("Unknown intent ignored. Action=%s", it)
            }
        }
    }

    /**
     * Update the state based on a change on the session token. Called either when
     * we are running for the first time or when the media session owner has destroyed the session
     * (see [android.media.session.MediaController.Callback.onSessionDestroyed])
     */
    private fun updateSessionToken() {
        val freshToken = mService.sessionToken
        if (mSessionToken != freshToken) {
            mController?.unregisterCallback(mCb)
            mSessionToken = freshToken
            try {
                mController = mSessionToken?.let { MediaControllerCompat(mService, it) }
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            mTransportControls = mController?.transportControls
            if (mStarted) {
                mController?.registerCallback(mCb)
            }
        }
    }

    private fun createContentIntent(): PendingIntent {
        val openUI = Intent(mService, MediaBrowserServiceCompat::class.java)
        openUI.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(mService, REQUEST_CODE, openUI, PendingIntent.FLAG_CANCEL_CURRENT)
    }

    private fun createNotification(): Notification? {
        Timber.d("updateNotificationMetadata. mMetadata=%s", mMetadata)
        if (mMetadata == null || mPlaybackState == null) {
            return null
        }
        // Notification channels are only supported on Android O+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        val notificationBuilder = NotificationCompat.Builder(mService, CHANNEL_ID)
        var playPauseButtonPosition = 0

        // If skip to previous action is enabled
        if (mPlaybackState!!.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) {
            notificationBuilder.addAction(R.drawable.ic_skip_previous_white_24dp, mService.getString(R.string.skip_previous), mPreviousIntent)
            // If there is a "skip to previous" button, the play/pause button will
            // be the second one. We need to keep track of it, because the MediaStyle notification
            // requires to specify the index of the buttons (actions) that should be visible
            // when in compact view.
            playPauseButtonPosition = 1
        }
        addPlayPauseAction(notificationBuilder)
        // If skip to next action is enabled
        if (mPlaybackState!!.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) {
            notificationBuilder.addAction(R.drawable.ic_skip_next_white_24dp, mService.getString(R.string.skip_next), mNextIntent)
        }
        var fetchArtUrl: String? = null
        var art: Bitmap? = null
        mMetadata?.description?.iconUri?.let {
            // This sample assumes the iconUri will be a valid URL formatted String, but
            // it can actually be any valid Android Uri formatted String.
            // async fetch the album art icon
            val artUrl = it.toString()
            art = AlbumArtCache.getBigImage(artUrl)

            AlbumArtCache.getBigImage(artUrl)?.let { art = it } ?: let {
                fetchArtUrl = artUrl
                // use a placeholder art while the remote art is being downloaded
                art = BitmapFactory.decodeResource(mService.resources, R.drawable.ic_default_art)
            }
        }
        with(notificationBuilder) {
            setStyle(android.support.v4.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(playPauseButtonPosition) // show only play/pause in compact view
                    .setMediaSession(mSessionToken))
            color = ContextCompat.getColor(mService, R.color.colorPrimary)
            setSmallIcon(R.drawable.ic_notification)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setUsesChronometer(true)
            setContentIntent(createContentIntent())
            setContentTitle(mMetadata?.description?.title)
            setContentText(mMetadata?.description?.subtitle)
            setLargeIcon(art)
        }
        setNotificationPlaybackState(notificationBuilder)
        fetchArtUrl?.let { fetchBitmapFromURLAsync(it, notificationBuilder) }
        return notificationBuilder.build()
    }

    private fun addPlayPauseAction(builder: NotificationCompat.Builder) {
        Timber.d("updatePlayPauseAction")
        val playing = mPlaybackState?.state == PlaybackStateCompat.STATE_PLAYING
        val label = if (playing) mService.getString(R.string.play_pause) else mService.getString(R.string.play_item)
        val icon = if (playing) R.drawable.ic_pause_white_24dp else R.drawable.ic_play_arrow_white_24dp
        val intent = if (playing) mPauseIntent else mPlayIntent
        builder.addAction(NotificationCompat.Action(icon, label, intent))
    }

    private fun setNotificationPlaybackState(builder: NotificationCompat.Builder) {
        Timber.d("updateNotificationPlaybackState. mPlaybackState=%s", mPlaybackState)
        mPlaybackState?.let {
            if (it.state == PlaybackState.STATE_PLAYING && it.position >= 0) {
                Timber.d("updateNotificationPlaybackState. updating playback position to %s seconds",
                        (System.currentTimeMillis() - it.position) / 1000)
                builder.setWhen(System.currentTimeMillis() - it.position)
                        .setShowWhen(true)
                        .setUsesChronometer(true)
            } else {
                Timber.d("updateNotificationPlaybackState. hiding playback position")
                builder.setWhen(0).setShowWhen(false).setUsesChronometer(false)
            }
        } ?: if (!mStarted) {
            Timber.d("updateNotificationPlaybackState. cancelling notification!")
            mService.stopForeground(true)
            return
        }
        // Make sure that the notification can be dismissed by the user when we are not playing:
        builder.setOngoing(mPlaybackState?.state == PlaybackStateCompat.STATE_PLAYING)
    }

    private fun fetchBitmapFromURLAsync(bitmapUrl: String, builder: NotificationCompat.Builder) {
        AlbumArtCache.fetch(bitmapUrl, object : AlbumArtCache.FetchListener() {
            override fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap) {
                if (artUrl == mMetadata?.description?.iconUri?.toString()) {
                    // If the media is still the same, update the notification:
                    Timber.d("fetchBitmapFromURLAsync: set bitmap to %s", artUrl)
                    builder.setLargeIcon(bigImage)
                    mNotificationManager.notify(NOTIFICATION_ID, builder.build())
                }
            }
        })
    }

    /**
     * Creates Notification Channel. This is required in Android O+ to display notifications.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        if (mNotificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val notificationChannel = NotificationChannel(CHANNEL_ID, mService.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)

            notificationChannel.description = mService.getString(R.string.notification_channel_description)

            mNotificationManager.createNotificationChannel(notificationChannel)
        }
    }
}
