/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.softartdev.tune.music;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import com.softartdev.tune.R;
import com.softartdev.tune.ui.main.MainActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import timber.log.Timber;

import static com.softartdev.tune.music.MediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM;
import static com.softartdev.tune.music.MediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST;
import static com.softartdev.tune.music.MediaIDHelper.MEDIA_ID_MUSICS_BY_PLAYLIST;
import static com.softartdev.tune.music.MediaIDHelper.MEDIA_ID_MUSICS_BY_SONG;
import static com.softartdev.tune.music.MediaIDHelper.MEDIA_ID_NOW_PLAYING;
import static com.softartdev.tune.music.MediaIDHelper.MEDIA_ID_ROOT;

/**
 * Provides "background" audio playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
public class MediaPlaybackService extends MediaBrowserServiceCompat implements Playback.Callback {
    // Delay stopSelf by using a handler.
    private static final int STOP_DELAY = 30000;

    public static final String ACTION_CMD = "com.android.music.ACTION_CMD";
    public static final String CMD_NAME = "CMD_NAME";
    public static final String CMD_PAUSE = "CMD_PAUSE";
    public static final String CMD_REPEAT = "CMD_PAUSE";
    public static final String REPEAT_MODE = "REPEAT_MODE";

    public enum RepeatMode { REPEAT_NONE, REPEAT_ALL, REPEAT_CURRENT }

    // Music catalog manager
    private MusicProvider mMusicProvider;
    private MediaSessionCompat mSession;
    // "Now playing" queue:
    private List<MediaSessionCompat.QueueItem> mPlayingQueue = null;
    private int mCurrentIndexOnQueue = -1;
    private MediaNotificationManager mMediaNotificationManager;
    // Indicates whether the service was started.
    private boolean mServiceStarted;
    private DelayedStopHandler mDelayedStopHandler = new DelayedStopHandler(this);
    private Playback mPlayback;
    // Default mode is repeat none
    private RepeatMode mRepeatMode = RepeatMode.REPEAT_NONE;
    // Extra information for this session
    private Bundle mExtras;

    public MediaPlaybackService() {}

    @Override
    public void onCreate() {
        Timber.d("onCreate()");
        super.onCreate();
        Timber.d("Create MusicProvider");
        mPlayingQueue = new ArrayList<>();
        mMusicProvider = new MusicProvider(this);

        Timber.d("Create MediaSessionCompat");
        // Start a new MediaSessionCompat
        mSession = new MediaSessionCompat(this, "MediaPlaybackService");
        // Set extra information
        mExtras = new Bundle();
        mExtras.putInt(REPEAT_MODE, mRepeatMode.ordinal());
        mSession.setExtras(mExtras);
        // Enable callbacks from MediaButtons and TransportControls
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        // Set an initial PlaybackStateCompat with ACTION_PLAY, so media buttons can start the player
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder().setActions(
                PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE);
        mSession.setPlaybackState(stateBuilder.build());
        // MediaSessionCompatCallback() has methods that handle callbacks from a media controller
        mSession.setCallback(new MediaSessionCompatCallback());
        // Set the session's token so that client activities can communicate with it.
        setSessionToken(mSession.getSessionToken());

        mPlayback = new Playback(this, mMusicProvider);
        mPlayback.setState(PlaybackStateCompat.STATE_NONE);
        mPlayback.setCallback(this);
        mPlayback.start();

        Context context = getApplicationContext();
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 99 /*request code*/, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mSession.setSessionActivity(pi);

        updatePlaybackStateCompat(null);

        mMediaNotificationManager = new MediaNotificationManager(this);
    }

    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        if (startIntent != null) {
            String action = startIntent.getAction();
            String command = startIntent.getStringExtra(CMD_NAME);
            if (ACTION_CMD.equals(action)) {
                if (CMD_PAUSE.equals(command)) {
                    if (mPlayback != null && mPlayback.isPlaying()) {
                        handlePauseRequest();
                    }
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Timber.d("onDestroy");
        // Service is being killed, so make sure we release our resources
        handleStopRequest(null);

        mDelayedStopHandler.removeCallbacksAndMessages(null);
        // Always release the MediaSessionCompat to clean up resources
        // and notify associated MediaController(s).
        mSession.release();
    }

    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, Bundle rootHints) {
        Timber.d("OnGetRoot: clientPackageName=%s; clientUid=%s ; rootHints=%s", clientPackageName, clientUid, rootHints);
        // Allow everyone to browse
        return new BrowserRoot(MEDIA_ID_ROOT, null);
    }

    @Override
    public void onLoadChildren(@NonNull final String parentMediaId, @NonNull final Result<List<MediaItem>> result) {
        Timber.d("OnLoadChildren: parentMediaId=%s", parentMediaId);
        if (!mMusicProvider.isInitialized()) {
            // Use result.detach to allow calling result.sendResult from another thread:
            result.detach();

            mMusicProvider.retrieveMediaAsync(success -> {
                Timber.d("Received catalog result, success:  %s", String.valueOf(success));
                if (success) {
                    onLoadChildren(parentMediaId, result);
                } else {
                    result.sendResult(Collections.emptyList());
                }
            });

        } else {
            // If our music catalog is already loaded/cached, load them into result immediately
            List<MediaItem> mediaItems = new ArrayList<>();

            switch (parentMediaId) {
                case MEDIA_ID_ROOT:
                    Timber.d("OnLoadChildren.ROOT");
                    mediaItems.add(new MediaItem(new MediaDescriptionCompat.Builder()
                            .setMediaId(MEDIA_ID_MUSICS_BY_ARTIST)
                            .setTitle("Artists")
                            .build(),
                            MediaItem.FLAG_BROWSABLE));
                    mediaItems.add(new MediaItem(new MediaDescriptionCompat.Builder()
                            .setMediaId(MEDIA_ID_MUSICS_BY_ALBUM)
                            .setTitle("Albums")
                            .build(),
                            MediaItem.FLAG_BROWSABLE));
                    mediaItems.add(new MediaItem(new MediaDescriptionCompat.Builder()
                            .setMediaId(MEDIA_ID_MUSICS_BY_SONG)
                            .setTitle("Songs")
                            .build(),
                            MediaItem.FLAG_BROWSABLE));
                    mediaItems.add(new MediaItem(new MediaDescriptionCompat.Builder()
                            .setMediaId(MEDIA_ID_MUSICS_BY_PLAYLIST)
                            .setTitle("Playlists")
                            .build(),
                            MediaItem.FLAG_BROWSABLE));
                    break;
                case MEDIA_ID_MUSICS_BY_ARTIST:
                    Timber.d("OnLoadChildren.ARTIST");
                    for (String artist : mMusicProvider.getArtists()) {
                        MediaItem item = new MediaItem(
                                new MediaDescriptionCompat.Builder()
                                        .setMediaId(MediaIDHelper.INSTANCE.createBrowseCategoryMediaID(
                                                MEDIA_ID_MUSICS_BY_ARTIST, artist))
                                        .setTitle(artist)
                                        .build(),
                                MediaItem.FLAG_BROWSABLE);
                        mediaItems.add(item);
                    }
                    break;
                case MEDIA_ID_MUSICS_BY_PLAYLIST:
                    Timber.d("OnLoadChildren.PLAYLIST");
                    for (String playlist : mMusicProvider.getPlaylists()) {
                        MediaItem item = new MediaItem(
                                new MediaDescriptionCompat.Builder()
                                        .setMediaId(MediaIDHelper.INSTANCE.createBrowseCategoryMediaID(
                                                MEDIA_ID_MUSICS_BY_PLAYLIST, playlist))
                                        .setTitle(playlist)
                                        .build(),
                                MediaItem.FLAG_BROWSABLE);
                        mediaItems.add(item);
                    }
                    break;
                case MEDIA_ID_MUSICS_BY_ALBUM:
                    Timber.d("OnLoadChildren.ALBUM");
                    loadAlbum(mMusicProvider.getAlbums(), mediaItems);
                    break;
                case MEDIA_ID_MUSICS_BY_SONG:
                    Timber.d("OnLoadChildren.SONG");
                    String hierarchyAwareMediaID = MediaIDHelper.INSTANCE.createBrowseCategoryMediaID(
                            parentMediaId, MEDIA_ID_MUSICS_BY_SONG);
                    loadSong(mMusicProvider.getMusicList(), mediaItems, hierarchyAwareMediaID);
                    break;
                default:
                    if (parentMediaId.startsWith(MEDIA_ID_MUSICS_BY_ARTIST)) {
                        String artist = MediaIDHelper.INSTANCE.getHierarchy(parentMediaId)[1];
                        Timber.d("OnLoadChildren.SONGS_BY_ARTIST  artist=%s", artist);
                        loadAlbum(mMusicProvider.getAlbumByArtist(artist), mediaItems);
                    } else if (parentMediaId.startsWith(MEDIA_ID_MUSICS_BY_ALBUM)) {
                        String album = MediaIDHelper.INSTANCE.getHierarchy(parentMediaId)[1];
                        Timber.d("OnLoadChildren.SONGS_BY_ALBUM  album=%s", album);
                        loadSong(mMusicProvider.getMusicsByAlbum(album), mediaItems, parentMediaId);
                    } else if (parentMediaId.startsWith(MEDIA_ID_MUSICS_BY_PLAYLIST)) {
                        String playlist = MediaIDHelper.INSTANCE.getHierarchy(parentMediaId)[1];
                        Timber.d("OnLoadChildren.SONGS_BY_PLAYLIST playlist=%s", playlist);
                        if (playlist.equals(MEDIA_ID_NOW_PLAYING) && mPlayingQueue != null
                                && mPlayingQueue.size() > 0) {
                            loadPlayingQueue(mediaItems, parentMediaId);
                        } else {
                            loadSong(mMusicProvider.getMusicsByPlaylist(playlist), mediaItems,
                                    parentMediaId);
                        }
                    } else {
                        Timber.w("Skipping unmatched parentMediaId: %s", parentMediaId);
                    }
                    break;
            }
            Timber.d("OnLoadChildren sending %s results for %s", mediaItems.size(), parentMediaId);
            result.sendResult(mediaItems);
        }
    }

    private void loadPlayingQueue(List<MediaItem> mediaItems, String parentId) {
        for (MediaSessionCompat.QueueItem queueItem : mPlayingQueue) {
            MediaItem mediaItem = new MediaItem(queueItem.getDescription(), MediaItem.FLAG_PLAYABLE);
            mediaItems.add(mediaItem);
        }
    }

    private void loadSong(Iterable<MediaMetadataCompat> songList, List<MediaItem> mediaItems, String parentId) {
        for (MediaMetadataCompat metadata : songList) {
            String hierarchyAwareMediaID = MediaIDHelper.INSTANCE.createMediaID(metadata.getDescription().getMediaId(), parentId);
            Bundle songExtra = new Bundle();
            songExtra.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));
            String title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
            String artistName = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
            MediaItem item = new MediaItem(
                    new MediaDescriptionCompat.Builder()
                            .setMediaId(hierarchyAwareMediaID)
                            .setTitle(title)
                            .setSubtitle(artistName)
                            .setIconBitmap(metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
                            .setExtras(songExtra)
                            .build(),
                    MediaItem.FLAG_PLAYABLE);
            mediaItems.add(item);
        }
    }

    private void loadAlbum(Iterable<MediaMetadataCompat> albumList, List<MediaItem> mediaItems) {
        for (MediaMetadataCompat albumMetadata : albumList) {
            String albumName = albumMetadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
            String artistName = albumMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
            Bundle albumExtra = new Bundle();
            albumExtra.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, albumMetadata.getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS));
            MediaItem item = new MediaItem(
                    new MediaDescriptionCompat.Builder()
                            .setMediaId(MediaIDHelper.INSTANCE.createBrowseCategoryMediaID(MEDIA_ID_MUSICS_BY_ALBUM, albumName))
                            .setTitle(albumName)
                            .setSubtitle(artistName)
                            .setIconBitmap(albumMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
                            .setExtras(albumExtra)
                            .build(),
                    MediaItem.FLAG_BROWSABLE);
            mediaItems.add(item);
        }
    }

    private final class MediaSessionCompatCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            Timber.d("play");
            if (mPlayingQueue == null || mPlayingQueue.isEmpty()) {
                mPlayingQueue = QueueHelper.INSTANCE.getRandomQueue(mMusicProvider);
                mSession.setQueue(mPlayingQueue);
                mSession.setQueueTitle(getString(R.string.random_queue_title));
                // start playing from the beginning of the queue
                mCurrentIndexOnQueue = 0;
            }
            if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
                handlePlayRequest();
            }
        }

        @Override
        public void onSkipToQueueItem(long queueId) {
            Timber.d("OnSkipToQueueItem:%s", queueId);
            if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
                // set the current index on queue from the music Id:
                mCurrentIndexOnQueue = QueueHelper.INSTANCE.getMusicIndexOnQueue(mPlayingQueue, queueId);
                // play the music
                handlePlayRequest();
            }
        }

        @Override
        public void onSeekTo(long position) {
            Timber.d("onSeekTo:%s", position);
            mPlayback.seekTo((int) position);
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            Timber.d("playFromMediaId mediaId:%s extras=%s", mediaId, extras);
            // The mediaId used here is not the unique musicId. This one comes from the
            // MediaBrowser, and is actually a "hierarchy-aware mediaID": a concatenation of
            // the hierarchy in MediaBrowser and the actual unique musicID. This is necessary
            // so we can build the correct playing queue, based on where the track was
            // selected from.
            mPlayingQueue = QueueHelper.INSTANCE.getPlayingQueue(mediaId, mMusicProvider);
            mSession.setQueue(mPlayingQueue);
            String queueTitle = getString(R.string.browse_musics_by_genre_subtitle, MediaIDHelper.INSTANCE.extractBrowseCategoryValueFromMediaID(mediaId));
            mSession.setQueueTitle(queueTitle);
            if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
                // set the current index on queue from the media Id:
                mCurrentIndexOnQueue = QueueHelper.INSTANCE.getMusicIndexOnQueue(mPlayingQueue, mediaId);
                if (mCurrentIndexOnQueue < 0) {
                    Timber.e("playFromMediaId: media ID %s could not be found on queue. Ignoring.", mediaId);
                } else {
                    // play the music
                    handlePlayRequest();
                }
            }
        }

        @Override
        public void onPause() {
            Timber.d("pause. current state=%s", mPlayback.getState());
            handlePauseRequest();
        }

        @Override
        public void onStop() {
            Timber.d("stop. current state=%s", mPlayback.getState());
            handleStopRequest(null);
        }

        @Override
        public void onSkipToNext() {
            Timber.d("skipToNext");
            mCurrentIndexOnQueue++;
            if (mPlayingQueue != null && mCurrentIndexOnQueue >= mPlayingQueue.size()) {
                // This sample's behavior: skipping to next when in last song returns to the
                // first song.
                mCurrentIndexOnQueue = 0;
            }
            if (QueueHelper.INSTANCE.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
                handlePlayRequest();
            } else {
                Timber.e("skipToNext: cannot skip to next. next Index=" + mCurrentIndexOnQueue
                                + " queue length="
                                + (mPlayingQueue == null ? "null" : mPlayingQueue.size()));
                handleStopRequest("Cannot skip");
            }
        }

        @Override
        public void onSkipToPrevious() {
            Timber.d("skipToPrevious");
            mCurrentIndexOnQueue--;
            if (mPlayingQueue != null && mCurrentIndexOnQueue < 0) {
                // This sample's behavior: skipping to previous when in first song restarts the
                // first song.
                mCurrentIndexOnQueue = 0;
            }
            if (QueueHelper.INSTANCE.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
                handlePlayRequest();
            } else {
                Timber.e("skipToPrevious: cannot skip to previous. previous Index="
                                + mCurrentIndexOnQueue + " queue length="
                                + (mPlayingQueue == null ? "null" : mPlayingQueue.size()));
                handleStopRequest("Cannot skip");
            }
        }

        @Override
        public void onPlayFromSearch(String query, Bundle extras) {
            Timber.d("playFromSearch  query=%s", query);
            if (TextUtils.isEmpty(query)) {
                // A generic search like "Play music" sends an empty query
                // and it's expected that we start playing something. What will be played depends
                // on the app: favorite playlist, "I'm feeling lucky", most recent, etc.
                mPlayingQueue = QueueHelper.INSTANCE.getRandomQueue(mMusicProvider);
            } else {
                mPlayingQueue = QueueHelper.INSTANCE.getPlayingQueueFromSearch(query, mMusicProvider);
            }
            Timber.d("playFromSearch  playqueue.length=%s", mPlayingQueue.size());
            mSession.setQueue(mPlayingQueue);
            if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
                // immediately start playing from the beginning of the search results
                mCurrentIndexOnQueue = 0;
                handlePlayRequest();
            } else {
                // if nothing was found, we need to warn the user and stop playing
                handleStopRequest(getString(R.string.no_search_results));
            }
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            Timber.d("onCustomAction action=%s, extras=%s", action, extras);
            switch (action) {
                case CMD_REPEAT:
                    mRepeatMode = RepeatMode.values()[extras.getInt(REPEAT_MODE)];
                    mExtras.putInt(REPEAT_MODE, mRepeatMode.ordinal());
                    mSession.setExtras(mExtras);
                    Timber.d("modified repeatMode=%s", mRepeatMode);
                    break;
                default:
                    Timber.d("Unkown action=%s", action);
                    break;
            }
        }
    }

    /**
     * Handle a request to play music
     */
    private void handlePlayRequest() {
        Timber.d("handlePlayRequest: mState=%s", mPlayback.getState());
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        if (!mServiceStarted) {
            Timber.v("Starting service");
            // The MusicService needs to keep running even after the calling MediaBrowser
            // is disconnected. Call startService(Intent) and then stopSelf(..) when we no longer
            // need to play media.
            startService(new Intent(getApplicationContext(), MediaPlaybackService.class));
            mServiceStarted = true;
        }

        if (!mSession.isActive()) {
            mSession.setActive(true);
        }

        if (QueueHelper.INSTANCE.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
            updateMetadata();
            mPlayback.play(mPlayingQueue.get(mCurrentIndexOnQueue));
        }
    }

    /**
     * Handle a request to pause music
     */
    private void handlePauseRequest() {
        Timber.d("handlePauseRequest: mState=%s", mPlayback.getState());
        mPlayback.pause();
        // reset the delayed stop handler.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
    }

    /**
     * Handle a request to stop music
     */
    private void handleStopRequest(String withError) {
        Timber.d("handleStopRequest: mState=%s error=%s", mPlayback.getState(), withError);
        mPlayback.stop(true);
        // reset the delayed stop handler.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);

        updatePlaybackStateCompat(withError);

        // service is no longer necessary. Will be started again if needed.
        stopSelf();
        mServiceStarted = false;
    }

    private void updateMetadata() {
        if (!QueueHelper.INSTANCE.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
            Timber.e("Can't retrieve current metadata.");
            updatePlaybackStateCompat(getResources().getString(R.string.error_no_metadata));
            return;
        }
        MediaSessionCompat.QueueItem queueItem = mPlayingQueue.get(mCurrentIndexOnQueue);
        String musicId = MediaIDHelper.INSTANCE.extractMusicIDFromMediaID(queueItem.getDescription().getMediaId());
        MediaMetadataCompat track = mMusicProvider.getMusicByMediaId(musicId).getMetadata();
        final String trackId = track.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
        if (!musicId.equals(trackId)) {
            IllegalStateException e = new IllegalStateException("track ID should match musicId.");
            Timber.e(e, "track ID should match musicId. musicId=%s trackId=%s mediaId from queueItem=%s title from queueItem=%s mediaId from track=%s title from track=%s source.hashcode from track=%s", musicId, trackId, queueItem.getDescription().getMediaId(), queueItem.getDescription().getTitle(), track.getDescription().getMediaId(), track.getDescription().getTitle(), track.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE).hashCode());
            throw e;
        }
        Timber.d("Updating metadata for MusicID=%s", musicId);
        mSession.setMetadata(track);

        // Set the proper album artwork on the media session, so it can be shown in the
        // locked screen and in other places.
        if (track.getDescription().getIconBitmap() == null && track.getDescription().getIconUri() != null) {
            String albumUri = track.getDescription().getIconUri().toString();
            AlbumArtCache.INSTANCE.fetch(albumUri, new AlbumArtCache.FetchListener() {
                @Override
                public void onFetched(@NonNull String artUrl, @NonNull Bitmap bitmap, @NonNull Bitmap icon) {
                    MediaSessionCompat.QueueItem queueItem = mPlayingQueue.get(mCurrentIndexOnQueue);
                    MediaMetadataCompat track = mMusicProvider.getMusicByMediaId(trackId).getMetadata();
                    track = new MediaMetadataCompat
                                    .Builder(track)
                                    // set high resolution bitmap in METADATA_KEY_ALBUM_ART. This is
                                    // used, for
                                    // example, on the lockscreen background when the media session
                                    // is active.
                                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                                    // set small version of the album art in the DISPLAY_ICON. This
                                    // is used on
                                    // the MediaDescriptionCompat and thus it should be small to be
                                    // serialized if
                                    // necessary..
                                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, icon)
                                    .build();
                    mMusicProvider.updateMusic(trackId, track);
                    // If we are still playing the same music
                    String currentPlayingId = MediaIDHelper.INSTANCE.extractMusicIDFromMediaID(queueItem.getDescription().getMediaId());
                    if (trackId.equals(currentPlayingId)) {
                        mSession.setMetadata(track);
                    }
                }
            });
        }
    }

    /**
     * Update the current media player state, optionally showing an error message.
     *
     * @param error if not null, error message to present to the user.
     */
    private void updatePlaybackStateCompat(String error) {
        Timber.d("updatePlaybackStateCompat, playback state=%s", mPlayback.getState());
        long position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        if (mPlayback != null && mPlayback.isConnected()) {
            position = mPlayback.getCurrentStreamPosition();
        }

        PlaybackStateCompat.Builder stateBuilder =
                new PlaybackStateCompat.Builder().setActions(getAvailableActions());

        int state = mPlayback.getState();

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(error);
            state = PlaybackStateCompat.STATE_ERROR;
        }
        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime());

        // Set the activeQueueItemId if the current index is valid.
        if (QueueHelper.INSTANCE.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
            MediaSessionCompat.QueueItem item = mPlayingQueue.get(mCurrentIndexOnQueue);
            stateBuilder.setActiveQueueItemId(item.getQueueId());
        }

        mSession.setPlaybackState(stateBuilder.build());

        if (state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_PAUSED) {
            mMediaNotificationManager.startNotification();
        }
    }

    private long getAvailableActions() {
        long actions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH;
        if (mPlayingQueue == null || mPlayingQueue.isEmpty()) {
            return actions;
        }
        if (mPlayback.isPlaying()) {
            actions |= PlaybackStateCompat.ACTION_PAUSE;
        }
        if (mCurrentIndexOnQueue > 0) {
            actions |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        }
        if (mCurrentIndexOnQueue < mPlayingQueue.size() - 1) {
            actions |= PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
        }
        return actions;
    }

    private MediaMetadataCompat getCurrentPlayingMusic() {
        if (QueueHelper.INSTANCE.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
            MediaSessionCompat.QueueItem item = mPlayingQueue.get(mCurrentIndexOnQueue);
            if (item != null) {
                Timber.d("getCurrentPlayingMusic for musicId=%s", item.getDescription().getMediaId());
                return mMusicProvider
                        .getMusicByMediaId(MediaIDHelper.INSTANCE.extractMusicIDFromMediaID(item.getDescription().getMediaId()))
                        .getMetadata();
            }
        }
        return null;
    }

    /**
     * Implementation of the Playback.Callback interface
     */
    @Override
    public void onCompletion() {
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
            switch (mRepeatMode) {
                case REPEAT_ALL:
                    // Increase the index
                    mCurrentIndexOnQueue++;
                    // Restart queue when reaching the end
                    if (mCurrentIndexOnQueue >= mPlayingQueue.size()) {
                        mCurrentIndexOnQueue = 0;
                    }
                    break;
                case REPEAT_CURRENT:
                    // Do not change the index
                    break;
                case REPEAT_NONE:
                default:
                    // Increase the index
                    mCurrentIndexOnQueue++;
                    // Stop the queue when reaching the end
                    if (mCurrentIndexOnQueue >= mPlayingQueue.size()) {
                        handleStopRequest(null);
                        return;
                    }
                    break;
            }
            handlePlayRequest();
        } else {
            // If there is nothing to play, we stop and release the resources:
            handleStopRequest(null);
        }
    }

    @Override
    public void onPlaybackStatusChanged(int state) {
        updatePlaybackStateCompat(null);
    }

    @Override
    public void onError(String error) {
        updatePlaybackStateCompat(error);
    }

    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private static class DelayedStopHandler extends Handler {
        private final WeakReference<MediaPlaybackService> mWeakReference;

        private DelayedStopHandler(MediaPlaybackService service) {
            mWeakReference = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MediaPlaybackService service = mWeakReference.get();
            if (service != null && service.mPlayback != null) {
                if (service.mPlayback.isPlaying()) {
                    Timber.d("Ignoring delayed stop since the media player is in use.");
                    return;
                }
                Timber.d("Stopping service with delay handler.");
                service.stopSelf();
                service.mServiceStarted = false;
            }
        }
    }
}
