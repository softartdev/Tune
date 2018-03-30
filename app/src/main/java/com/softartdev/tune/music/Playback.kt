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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import android.text.TextUtils
import android.util.Log

import java.io.IOException

import android.media.MediaPlayer.OnCompletionListener
import android.media.MediaPlayer.OnErrorListener
import android.media.MediaPlayer.OnPreparedListener
import android.media.MediaPlayer.OnSeekCompleteListener

/**
 * A class that implements local media playback using [android.media.MediaPlayer]
 */
class Playback(private val mService: MediaPlaybackService, private val mMusicProvider: MusicProvider) : AudioManager.OnAudioFocusChangeListener, OnCompletionListener, OnErrorListener, OnPreparedListener, OnSeekCompleteListener {
    private val mAudioManager: AudioManager = mService.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    // Create the Wifi lock (this does not acquire the lock, this just creates it)
    private val mWifiLock: WifiManager.WifiLock = (mService.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL, "sample_lock")

    companion object {
        private const val TAG = "Playback"
        // The volume we set the media player to when we lose audio focus, but are allowed to reduce the volume instead of stopping playback.
        const val VOLUME_DUCK = 0.2f
        // The volume we set the media player when we have audio focus.
        const val VOLUME_NORMAL = 1.0f
        // we don't have audio focus, and can't duck (play at a low volume)
        private const val AUDIO_NO_FOCUS_NO_DUCK = 0
        // we don't have focus, but can duck (play at a low volume)
        private const val AUDIO_NO_FOCUS_CAN_DUCK = 1
        // we have full audio focus
        private const val AUDIO_FOCUSED = 2
    }

    var state: Int = 0
    private var mPlayOnFocusGain: Boolean = false
    var callback: Callback? = null
    @Volatile private var mAudioNoisyReceiverRegistered: Boolean = false
    @Volatile private var mCurrentMediaId: String? = null

    // Type of audio focus we have:
    private var mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK
    
    private val mMediaPlayer: MediaPlayer by lazy {
        Log.d(TAG, "create MediaPlayer")
        MediaPlayer().apply {
            // Make sure the media player will acquire a wake-lock while
            // playing. If we don't do that, the CPU might go to sleep while the
            // song is playing, causing playback to stop.
            setWakeMode(mService.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            // we want the media player to notify us when it's ready preparing,
            // and when it's done playing:
            setOnPreparedListener(this@Playback)
            setOnCompletionListener(this@Playback)
            setOnErrorListener(this@Playback)
            setOnSeekCompleteListener(this@Playback) }
    }

    private val mAudioNoisyIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

    private val mAudioNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                Log.d(TAG, "Headphones disconnected.")
                if (isPlaying) {
                    val mediaIntent = Intent(context, MediaPlaybackService::class.java)
                    mediaIntent.action = MediaPlaybackService.ACTION_CMD
                    mediaIntent.putExtra(MediaPlaybackService.CMD_NAME, MediaPlaybackService.CMD_PAUSE)
                    mService.startService(mediaIntent)
                }
            }
        }
    }

    val isConnected = true

    val isPlaying: Boolean
        get() = mPlayOnFocusGain || mMediaPlayer.isPlaying

    val currentStreamPosition: Int
        get() = mMediaPlayer.currentPosition

    fun start() {}

    fun stop(notifyListeners: Boolean) {
        state = PlaybackState.STATE_STOPPED
        if (notifyListeners) {
            callback?.onPlaybackStatusChanged(state)
        }
        // Give up Audio focus
        giveUpAudioFocus()
        unregisterAudioNoisyReceiver()
        // Relax all resources
        relaxResources(true)
        if (mWifiLock.isHeld) {
            mWifiLock.release()
        }
    }

    fun play(item: QueueItem) {
        mPlayOnFocusGain = true
        tryToGetAudioFocus()
        registerAudioNoisyReceiver()
        val mediaId = item.description.mediaId
        val mediaHasChanged = !TextUtils.equals(mediaId, mCurrentMediaId)
        if (mediaHasChanged) {
            mCurrentMediaId = mediaId
        }

        if (state == PlaybackState.STATE_PAUSED && !mediaHasChanged) {
            configMediaPlayerState()
        } else {
            state = PlaybackState.STATE_STOPPED
            relaxResources(false) // release everything except MediaPlayer
            val track = mMusicProvider
                    .getMusicByMediaId(MediaIDHelper.extractMusicIDFromMediaID(item.description.mediaId!!))!!
                    .metadata

            val source = track.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE)

            try {
                mMediaPlayer.reset()

                state = PlaybackState.STATE_BUFFERING

                val attributes = AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build()
                mMediaPlayer.apply {
                    setAudioAttributes(attributes)
                    setDataSource(source)
                    // Starts preparing the media player in the background. When
                    // it's done, it will call our OnPreparedListener (that is,
                    // the onPrepared() method on this class, since we set the
                    // listener to 'this'). Until the media player is prepared,
                    // we *cannot* call start() on it!
                    prepareAsync()
                }
                // If we are streaming from the internet, we want to hold a
                // Wifi lock, which prevents the Wifi radio from going to
                // sleep while the song is playing.
                //                mWifiLock.acquire();
                callback?.onPlaybackStatusChanged(state)
            } catch (ex: IOException) {
                Log.e(TAG, ex.toString() + "Exception playing song")
                callback?.onError(ex.message)
            }

        }
    }

    fun pause() {
        if (state == PlaybackState.STATE_PLAYING) {
            // Pause media player and cancel the 'foreground service' state.
            if (mMediaPlayer.isPlaying) {
                mMediaPlayer.pause()
            }
            // while paused, retain the MediaPlayer but give up audio focus
            relaxResources(false)
            giveUpAudioFocus()
        }
        state = PlaybackState.STATE_PAUSED
        callback?.onPlaybackStatusChanged(state)
        unregisterAudioNoisyReceiver()
    }

    fun seekTo(position: Int) {
        Log.d(TAG, "seekTo called with $position")
        if (mMediaPlayer.isPlaying) {
            state = PlaybackState.STATE_BUFFERING
        }
        mMediaPlayer.seekTo(position)
        callback?.onPlaybackStatusChanged(state)
    }

    /**
     * Try to get the system audio focus.
     */
    private fun tryToGetAudioFocus() {
        Log.d(TAG, "tryToGetAudioFocus")
        if (mAudioFocus != AUDIO_FOCUSED) {
            val result = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mAudioFocus = AUDIO_FOCUSED
            }
        }
    }

    /**
     * Give up the audio focus.
     */
    private fun giveUpAudioFocus() {
        Log.d(TAG, "giveUpAudioFocus")
        if (mAudioFocus == AUDIO_FOCUSED) {
            if (mAudioManager.abandonAudioFocus(this) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK
            }
        }
    }

    /**
     * Reconfigures MediaPlayer according to audio focus settings and
     * starts/restarts it. This method starts/restarts the MediaPlayer
     * respecting the current audio focus state. So if we have focus, it will
     * play normally; if we don't have focus, it will either leave the
     * MediaPlayer paused or set it to a low volume, depending on what is
     * allowed by the current focus settings. This method assumes mPlayer !=
     * null, so if you are calling it, you have to do so from a context where
     * you are sure this is the case.
     */
    private fun configMediaPlayerState() {
        Log.d(TAG, "configMediaPlayerState. mAudioFocus=$mAudioFocus")
        if (mAudioFocus == AUDIO_NO_FOCUS_NO_DUCK) {
            // If we don't have audio focus and can't duck, we have to pause,
            if (state == PlaybackState.STATE_PLAYING) {
                pause()
            }
        } else { // we have audio focus:
            if (mAudioFocus == AUDIO_NO_FOCUS_CAN_DUCK) {
                mMediaPlayer.setVolume(VOLUME_DUCK, VOLUME_DUCK) // we'll be relatively quiet
            } else {
                mMediaPlayer.setVolume(VOLUME_NORMAL, VOLUME_NORMAL) // we can be loud again else do something for remote client.
            }
            // If we were playing when we lost focus, we need to resume playing.
            if (mPlayOnFocusGain) {
                if (!mMediaPlayer.isPlaying) {
                    Log.d(TAG, "configMediaPlayerState startMediaPlayer. seeking to $mMediaPlayer.currentPosition")
                    mMediaPlayer.start()
                    state = PlaybackState.STATE_PLAYING
                }
                mPlayOnFocusGain = false
            }
        }
        callback?.onPlaybackStatusChanged(state)
    }

    /**
     * Called by AudioManager on audio focus changes.
     * Implementation of [android.media.AudioManager.OnAudioFocusChangeListener]
     */
    override fun onAudioFocusChange(focusChange: Int) {
        Log.d(TAG, "onAudioFocusChange. focusChange=$focusChange")
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            // We have gained focus:
            mAudioFocus = AUDIO_FOCUSED

        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            // We have lost focus. If we can duck (low playback volume), we can keep playing.
            // Otherwise, we need to pause the playback.
            val canDuck = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
            mAudioFocus = if (canDuck) AUDIO_NO_FOCUS_CAN_DUCK else AUDIO_NO_FOCUS_NO_DUCK

            // If we are playing, we need to reset media player by calling configMediaPlayerState
            // with mAudioFocus properly set.
            if (state == PlaybackState.STATE_PLAYING && !canDuck) {
                // If we don't have audio focus and can't duck, we save the information that
                // we were playing, so that we can resume playback once we get the focus back.
                mPlayOnFocusGain = true
            }
        } else {
            Log.e(TAG, "onAudioFocusChange: Ignoring unsupported focusChange: $focusChange")
        }
        configMediaPlayerState()
    }

    /**
     * Called when MediaPlayer has completed a seek
     *
     * @see android.media.MediaPlayer.OnSeekCompleteListener
     */
    override fun onSeekComplete(mp: MediaPlayer) {
        Log.d(TAG, "onSeekComplete from MediaPlayer:" + mp.currentPosition)
        if (state == PlaybackState.STATE_BUFFERING) {
            mMediaPlayer.start()
            state = PlaybackState.STATE_PLAYING
        }
        callback?.onPlaybackStatusChanged(state)
    }

    /**
     * Called when media player is done playing current song.
     *
     * @see android.media.MediaPlayer.OnCompletionListener
     */
    override fun onCompletion(player: MediaPlayer) {
        Log.d(TAG, "onCompletion from MediaPlayer")
        // The media player finished playing the current song, so we go ahead and start the next.
        callback?.onCompletion()
    }

    /**
     * Called when media player is done preparing.
     *
     * @see android.media.MediaPlayer.OnPreparedListener
     */
    override fun onPrepared(player: MediaPlayer) {
        Log.d(TAG, "onPrepared from MediaPlayer")
        // The media player is done preparing. That means we can start playing if we have audio focus.
        configMediaPlayerState()
    }

    /**
     * Called when there's an error playing media. When this happens, the media
     * player goes to the Error state. We warn the user about the error and
     * reset the media player.
     *
     * @see android.media.MediaPlayer.OnErrorListener
     */
    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        Log.e(TAG, "Media player error: what=$what, extra=$extra")
        callback?.onError("MediaPlayer error $what ($extra)")
        return true // true indicates we handled the error
    }

    /**
     * Releases resources used by the service for playback. This includes the
     * "foreground service" status, the wake locks and possibly the MediaPlayer.
     *
     * @param releaseMediaPlayer Indicates whether the Media Player should also
     * be released or not
     */
    private fun relaxResources(releaseMediaPlayer: Boolean) {
        Log.d(TAG, "relaxResources. releaseMediaPlayer=$releaseMediaPlayer")

        mService.stopForeground(true)

        // stop and release the Media Player, if it's available
        if (releaseMediaPlayer) {
            mMediaPlayer.reset()
            mMediaPlayer.release()
        }

        // we can also release the Wifi lock, if we're holding it
        if (mWifiLock.isHeld) {
            mWifiLock.release()
        }
    }

    private fun registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            mService.registerReceiver(mAudioNoisyReceiver, mAudioNoisyIntentFilter)
            mAudioNoisyReceiverRegistered = true
        }
    }

    private fun unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            mService.unregisterReceiver(mAudioNoisyReceiver)
            mAudioNoisyReceiverRegistered = false
        }
    }

    interface Callback {
        /**
         * On current music completed.
         */
        fun onCompletion()

        /**
         * on Playback status changed
         * Implementations can use this callback to update
         * playback state on the media sessions.
         */
        fun onPlaybackStatusChanged(state: Int)

        /**
         * @param error to be added to the PlaybackState
         */
        fun onError(error: String?)
    }

}
