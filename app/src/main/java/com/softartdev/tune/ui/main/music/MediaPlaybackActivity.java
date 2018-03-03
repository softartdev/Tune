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

package com.softartdev.tune.ui.main.music;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Layout;
import android.text.TextUtils.TruncateAt;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.softartdev.tune.R;
import com.softartdev.tune.music.MediaPlaybackService;
import com.softartdev.tune.music.MusicProvider;
import com.softartdev.tune.music.MusicUtils;
import com.softartdev.tune.music.RepeatingImageButton;

import timber.log.Timber;

/*
 * This is the Now Playing Activity
 */
public class MediaPlaybackActivity extends AppCompatActivity implements View.OnTouchListener, View.OnLongClickListener {
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private ImageButton mPlayPauseButton;
    private ImageButton mRepeatButton;
    private ImageButton mShuffleButton;
    private ImageButton mQueueButton;
    private int mTouchSlop;

    private ImageView mAlbumArt;
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private TextView mArtistName;
    private TextView mAlbumName;
    private TextView mTrackName;
    private LinearLayout mTrackInfo;
    private ProgressBar mProgress;
    private BitmapDrawable mDefaultAlbumArt;
    private Toast mToast;

    private MediaBrowserCompat mMediaBrowser;
    private final Handler mHandler = new Handler();

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.audio_player);

        mCurrentTime = findViewById(R.id.currenttime);
        mTotalTime = findViewById(R.id.totaltime);
        mProgress = findViewById(android.R.id.progress);
        mAlbumArt = findViewById(R.id.album);
        mArtistName = findViewById(R.id.artistname);
        mAlbumName = findViewById(R.id.albumname);
        mTrackName = findViewById(R.id.trackname);
        mTrackInfo = findViewById(R.id.trackinfo);

        Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.albumart_mp_unknown);
        mDefaultAlbumArt = new BitmapDrawable(getResources(), b);
        // no filter or dither, it's a lot faster and we can't tell the difference
        mDefaultAlbumArt.setFilterBitmap(false);
        mDefaultAlbumArt.setDither(false);

        /* Set metadata listeners */
        View v = (View) mArtistName.getParent();
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);
        v = (View) mAlbumName.getParent();
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);
        v = (View) mTrackName.getParent();
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);
        
        /* Set button listeners */
        RepeatingImageButton mPrevButton = findViewById(R.id.prev);
        mPrevButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
                if (mediaController == null) return;
                if (mediaController.getPlaybackState().getPosition() < 2000) {
                    mediaController.getTransportControls().skipToPrevious();
                } else {
                    mediaController.getTransportControls().seekTo(0);
                    mediaController.getTransportControls().play();
                }
            }
        });
        mPrevButton.setRepeatListener(new RepeatingImageButton.RepeatListener() {
            public void onRepeat(View v, long howlong, int repcnt) {
                scanBackward(repcnt, howlong);
            }
        }, 260);
        mPlayPauseButton = findViewById(R.id.pause);
        mPlayPauseButton.requestFocus();
        mPlayPauseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
                if (mediaController != null) {
                    if (mediaController.getPlaybackState().getState()
                            != PlaybackState.STATE_PLAYING) {
                        mediaController.getTransportControls().play();
                    } else {
                        mediaController.getTransportControls().pause();
                    }
                }
            }
        });
        RepeatingImageButton mNextButton = findViewById(R.id.next);
        mNextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
                if (mediaController == null) return;
                mediaController.getTransportControls().skipToNext();
            }
        });
        mNextButton.setRepeatListener(new RepeatingImageButton.RepeatListener() {
            public void onRepeat(View v, long howlong, int repcnt) {
                scanForward(repcnt, howlong);
            }
        }, 260);
        mQueueButton = findViewById(R.id.curplaylist);
        mQueueButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleQueue();
            }
        });
        mShuffleButton = findViewById(R.id.shuffle);
        mShuffleButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleShuffle();
            }
        });
        mRepeatButton = findViewById(R.id.repeat);
        mRepeatButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
                if (mediaController == null) return;
                Bundle extras = mediaController.getExtras();
                if (extras == null) return;
                MediaPlaybackService.RepeatMode repeatMode = MediaPlaybackService.RepeatMode.values()[extras.getInt(MediaPlaybackService.REPEAT_MODE)];
                MediaPlaybackService.RepeatMode nextRepeatMode;
                switch (repeatMode) {
                    case REPEAT_NONE:
                        nextRepeatMode = MediaPlaybackService.RepeatMode.REPEAT_ALL;
                        showToast(R.string.repeat_all_notif);
                        break;
                    case REPEAT_ALL:
                        nextRepeatMode = MediaPlaybackService.RepeatMode.REPEAT_CURRENT;
                        showToast(R.string.repeat_current_notif);
                        break;
                    case REPEAT_CURRENT:
                    default:
                        nextRepeatMode = MediaPlaybackService.RepeatMode.REPEAT_NONE;
                        showToast(R.string.repeat_off_notif);
                        break;
                }
                setRepeatMode(nextRepeatMode);
                // TODO(siyuanh): Should use a callback to register changes on service side
                setRepeatButtonImage(nextRepeatMode);
            }
        });

        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                boolean mmFromTouch = false;

                public void onStartTrackingTouch(SeekBar bar) {
                    mLastSeekEventTime = 0;
                    mmFromTouch = true;
                }

                public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
                    MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
                    if (!fromuser || (mediaController == null)) return;
                    long now = SystemClock.elapsedRealtime();
                    if ((now - mLastSeekEventTime) > 250) {
                        mLastSeekEventTime = now;
                        long duration = mediaController.getMetadata().getLong(
                                MediaMetadataCompat.METADATA_KEY_DURATION);
                        long position = duration * progress / 1000;
                        mediaController.getTransportControls().seekTo(position);
                        // trackball event, allow progress updates
                        if (!mmFromTouch) {
                            updateProgressBar();
                        }
                    }
                }

                public void onStopTrackingTouch(SeekBar bar) {
                    mmFromTouch = false;
                }
            });
        } else {
            Timber.d("Seeking not supported");
        }
        mProgress.setMax(1000);

        mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        Timber.d("Creating MediaBrowser");
        mMediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, MediaPlaybackService.class),
                mConnectionCallback, null);
    }

    // Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private MediaControllerCompat.Callback mMediaControllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onSessionDestroyed() {
            Timber.d("Session destroyed. Need to fetch a new Media Session");
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            if (state == null) {
                return;
            }
            Timber.d("Received playback state change to state %s", state.toString());
            updateProgressBar();
            setPauseButtonImage();
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            if (metadata == null) {
                return;
            }
            Timber.d("Received updated metadata: %s", metadata);
            updateTrackInfo();
        }
    };

    private MediaBrowserCompat.ConnectionCallback mConnectionCallback = new MediaBrowserCompat.ConnectionCallback() {
        @Override
        public void onConnected() {
            try {
                MediaSessionCompat.Token sessionToken = mMediaBrowser.getSessionToken();
                Timber.d("onConnected: session token %s", sessionToken);
                MediaControllerCompat mediaController = new MediaControllerCompat(MediaPlaybackActivity.this, sessionToken);
                mediaController.registerCallback(mMediaControllerCallback);
                MediaControllerCompat.setMediaController(MediaPlaybackActivity.this, mediaController);
                mRepeatButton.setVisibility(View.VISIBLE);
                mShuffleButton.setVisibility(View.VISIBLE);
                mQueueButton.setVisibility(View.VISIBLE);
                setRepeatButtonImage(null);
                setShuffleButtonImage();
                setPauseButtonImage();
                updateTrackInfo();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        long delay = updateProgressBar();
                        mHandler.postDelayed(this, delay);
                    }
                });
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConnectionFailed() {
            Timber.d("onConnectionFailed");
        }

        @Override
        public void onConnectionSuspended() {
            Timber.d("onConnectionSuspended");
            mHandler.removeCallbacksAndMessages(null);
            MediaControllerCompat.setMediaController(MediaPlaybackActivity.this, null);
        }
    };

    int mInitialX = -1;
    int mLastX = -1;
    int mTextWidth = 0;
    int mViewWidth = 0;
    boolean mDraggingLabel = false;

    TextView textViewForContainer(View v) {
        View vv = v.findViewById(R.id.artistname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.albumname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.trackname);
        if (vv != null) return (TextView) vv;
        return null;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int action = event.getAction();
        TextView tv = textViewForContainer(v);
        if (tv == null) {
            return false;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            v.setBackgroundColor(0xff606060);
            mInitialX = mLastX = (int) event.getX();
            mDraggingLabel = false;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            v.setBackgroundColor(0);
            if (mDraggingLabel) {
                Message msg = mLabelScroller.obtainMessage(0, tv);
                mLabelScroller.sendMessageDelayed(msg, 1000);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (mDraggingLabel) {
                int scrollx = tv.getScrollX();
                int x = (int) event.getX();
                int delta = mLastX - x;
                if (delta != 0) {
                    mLastX = x;
                    scrollx += delta;
                    if (scrollx > mTextWidth) {
                        // scrolled the text completely off the view to the left
                        scrollx -= mTextWidth;
                        scrollx -= mViewWidth;
                    }
                    if (scrollx < -mViewWidth) {
                        // scrolled the text completely off the view to the right
                        scrollx += mViewWidth;
                        scrollx += mTextWidth;
                    }
                    tv.scrollTo(scrollx, 0);
                }
                return true;
            }
            int delta = mInitialX - (int) event.getX();
            if (Math.abs(delta) > mTouchSlop) {
                // start moving
                mLabelScroller.removeMessages(0, tv);

                // Only turn ellipsizing off when it's not already off, because it
                // causes the scroll position to be reset to 0.
                if (tv.getEllipsize() != null) {
                    tv.setEllipsize(null);
                }
                Layout ll = tv.getLayout();
                // layout might be null if the text just changed, or ellipsizing
                // was just turned off
                if (ll == null) {
                    return false;
                }
                // get the non-ellipsized line width, to determine whether scrolling
                // should even be allowed
                mTextWidth = (int) tv.getLayout().getLineWidth(0);
                mViewWidth = tv.getWidth();
                if (mViewWidth > mTextWidth) {
                    tv.setEllipsize(TruncateAt.END);
                    v.cancelLongPress();
                    return false;
                }
                mDraggingLabel = true;
                tv.setHorizontalFadingEdgeEnabled(true);
                v.cancelLongPress();
                return true;
            }
        }
        return false;
    }

    @SuppressLint("HandlerLeak")
    Handler mLabelScroller = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            TextView tv = (TextView) msg.obj;
            int x = tv.getScrollX();
            x = x * 3 / 4;
            tv.scrollTo(x, 0);
            if (x == 0) {
                tv.setEllipsize(TruncateAt.END);
            } else {
                Message newmsg = obtainMessage(0, tv);
                mLabelScroller.sendMessageDelayed(newmsg, 15);
            }
        }
    };

    @Override
    public boolean onLongClick(View view) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
        CharSequence title;
        String mime;
        String query;
        if (mediaController == null) {
            Timber.d("No media controller avalable yet");
            return true;
        }
        MediaMetadataCompat metadata = mediaController.getMetadata();
        if (metadata == null) {
            Timber.d("No metadata avalable yet");
            return true;
        }
        String artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
        String album = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
        String song = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
        long audioid = metadata.getLong(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);

        if (album == null && artist == null && song != null && song.startsWith("recording")) {
            Timber.d("Item is not music");
            return false;
        }

        if (audioid < 0) {
            return false;
        }

        boolean knownartist = (artist != null) && !MediaStore.UNKNOWN_STRING.equals(artist);
        boolean knownalbum = (album != null) && !MediaStore.UNKNOWN_STRING.equals(album);

        if (knownartist && view.equals(mArtistName.getParent())) {
            title = artist;
            query = artist;
            mime = MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE;
        } else if (knownalbum && view.equals(mAlbumName.getParent())) {
            title = album;
            if (knownartist) {
                query = artist + " " + album;
            } else {
                query = album;
            }
            mime = MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE;
        } else if (view.equals(mTrackName.getParent()) || !knownartist || !knownalbum) {
            if ((song == null) || MediaStore.UNKNOWN_STRING.equals(song)) {
                // A popup of the form "Search for null/'' using ..." is pretty
                // unhelpful, plus, we won't find any way to buy it anyway.
                return true;
            }

            title = song;
            if (knownartist) {
                query = artist + " " + song;
            } else {
                query = song;
            }
            mime = "audio/*"; // the specific type doesn't matter, so don't bother retrieving it
        } else {
            throw new RuntimeException("shouldn't be here");
        }
        title = getString(R.string.mediasearch, title);

        Intent i = new Intent();
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        i.putExtra(SearchManager.QUERY, query);
        if (knownartist) {
            i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist);
        }
        if (knownalbum) {
            i.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, album);
        }
        i.putExtra(MediaStore.EXTRA_MEDIA_TITLE, song);
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, mime);

        startActivity(Intent.createChooser(i, title));
        return true;
    }

    @Override
    public void onStart() {
        Timber.d("onStart()");
        super.onStart();
        mMediaBrowser.connect();
    }

    @Override
    public void onStop() {
        Timber.d("onStop()");
        mMediaBrowser.disconnect();
        super.onStop();
    }

    @Override
    public void onResume() {
        Timber.d("onResume()");
        super.onResume();
        updateTrackInfo();
        setPauseButtonImage();
    }

    @Override
    public void onDestroy() {
        Timber.d("onDestroy()");
        super.onDestroy();
    }

    private void scanBackward(int repcnt, long delta) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
        if (mediaController == null) return;
        if (repcnt == 0) {
            mStartSeekPos = mediaController.getPlaybackState().getPosition();
            mLastSeekEventTime = 0;
        } else {
            if (delta < 5000) {
                // seek at 10x speed for the first 5 seconds
                delta = delta * 10;
            } else {
                // seek at 40x after that
                delta = 50000 + (delta - 5000) * 40;
            }
            long newpos = mStartSeekPos - delta;
            if (newpos < 0) {
                // move to previous track
                mediaController.getTransportControls().skipToPrevious();
                long duration = mediaController.getMetadata().getLong(
                        MediaMetadataCompat.METADATA_KEY_DURATION);
                mStartSeekPos += duration;
                newpos += duration;
            }
            if (((delta - mLastSeekEventTime) > 250) || repcnt < 0) {
                mediaController.getTransportControls().seekTo(newpos);
                mLastSeekEventTime = delta;
            }
            updateProgressBar();
        }
    }

    private void scanForward(int repcnt, long delta) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
        if (mediaController == null) return;
        if (repcnt == 0) {
            mStartSeekPos = mediaController.getPlaybackState().getPosition();
            mLastSeekEventTime = 0;
        } else {
            if (delta < 5000) {
                // seek at 10x speed for the first 5 seconds
                delta = delta * 10;
            } else {
                // seek at 40x after that
                delta = 50000 + (delta - 5000) * 40;
            }
            long newpos = mStartSeekPos + delta;
            long duration =
                    mediaController.getMetadata().getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
            if (newpos >= duration) {
                // move to next track
                mediaController.getTransportControls().skipToNext();
                mStartSeekPos -= duration; // is OK to go negative
                newpos -= duration;
            }
            if (((delta - mLastSeekEventTime) > 250) || repcnt < 0) {
                mediaController.getTransportControls().seekTo(newpos);
                mLastSeekEventTime = delta;
            }
            updateProgressBar();
        }
    }

    private void toggleQueue() {
        // TODO: Implement queue
        Toast.makeText(this, "Queue not implemented yet", Toast.LENGTH_SHORT).show();
    }

    private void toggleShuffle() {
        // TODO(b/36371715): Implement shuffle for SHUFFLE_NORMAL, SHUFFLE_AUTO, SHUFFLE_NONE
        Timber.d("Shuffle not implemented yet");
        Toast.makeText(this, "Shuffle not implemented yet", Toast.LENGTH_SHORT).show();
    }

    private void setRepeatMode(MediaPlaybackService.RepeatMode repeatMode) {
        Bundle extras = new Bundle();
        extras.putInt(MediaPlaybackService.REPEAT_MODE, repeatMode.ordinal());
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
        mediaController.getTransportControls().sendCustomAction(MediaPlaybackService.CMD_REPEAT, extras);
    }

    private void setRepeatButtonImage(MediaPlaybackService.RepeatMode repeatMode) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
        if (mediaController == null) return;
        Bundle extras = mediaController.getExtras();
        if (extras == null) return;
        if (repeatMode == null) {
            repeatMode = MediaPlaybackService.RepeatMode.values()[extras.getInt(MediaPlaybackService.REPEAT_MODE)];
        }
        switch (repeatMode) {
            case REPEAT_CURRENT:
                mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_once_btn);
                break;
            case REPEAT_ALL:
                mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_all_btn);
                break;
            case REPEAT_NONE:
            default:
                mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_off_btn);
                break;
        }
    }

    @SuppressLint("ShowToast")
    private void showToast(int resid) {
        if (mToast == null) {
            mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        }
        mToast.setText(resid);
        mToast.show();
    }

    private void setShuffleButtonImage() {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
        if (mediaController == null) return;
        mShuffleButton.setImageResource(R.drawable.ic_mp_shuffle_off_btn);
    }

    private void setPauseButtonImage() {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
        if (mediaController == null) {
            return;
        }
        if (mediaController.getPlaybackState().getState() != PlaybackState.STATE_PLAYING) {
            mPlayPauseButton.setImageResource(android.R.drawable.ic_media_play);
        } else {
            mPlayPauseButton.setImageResource(android.R.drawable.ic_media_pause);
        }
    }

    private long updateProgressBar() {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
        if (mediaController == null) {
            return 500;
        }
        long duration = mediaController.getMetadata().getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        long pos = mediaController.getPlaybackState().getPosition();
        if ((pos >= 0) && (duration > 0)) {
            mCurrentTime.setText(MusicUtils.makeTimeString(this, pos / 1000));
            int progress = (int) (1000 * pos / duration);
            mProgress.setProgress(progress);

            if (mediaController.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                mCurrentTime.setVisibility(View.VISIBLE);
            } else {
                // blink the counter
                int vis = mCurrentTime.getVisibility();
                mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                return 500;
            }
        } else {
            mCurrentTime.setText("--:--");
            mProgress.setProgress(1000);
        }
        // calculate the number of milliseconds until the next full second, so
        // the counter can be updated at just the right time
        long remaining = 1000 - (pos % 1000);

        // approximate how often we would need to refresh the slider to
        // move it smoothly
        int width = mProgress.getWidth();
        if (width == 0) width = 320;
        long smoothrefreshtime = duration / width;

        if (smoothrefreshtime > remaining) return remaining;
        if (smoothrefreshtime < 20) return 20;
        return smoothrefreshtime;
    }

    private void updateTrackInfo() {
        Timber.d("updateTrackInfo()");
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MediaPlaybackActivity.this);
        if (mediaController == null) {
            return;
        }
        MediaMetadataCompat metadata = mediaController.getMetadata();
        if (metadata == null) {
            return;
        }
        mTrackInfo.setVisibility(View.VISIBLE);
        mTrackName.setText(metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
        Timber.d("Track Name: %s", mTrackName.getText());
        String artistName = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
        if (artistName.equals(MusicProvider.UNKOWN)) {
            artistName = getString(R.string.unknown_artist_name);
        }
        mArtistName.setText(artistName);
        String albumName = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
        if (albumName.equals(MusicProvider.UNKOWN)) {
            albumName = getString(R.string.unknown_album_name);
        }
        mAlbumName.setText(albumName);
        Bitmap albumArt = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
        if (albumArt != null) {
            mAlbumArt.setImageBitmap(albumArt);
        } else {
            mAlbumArt.setImageDrawable(mDefaultAlbumArt);
        }
        mAlbumArt.setVisibility(View.VISIBLE);

        long duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        mTotalTime.setText(MusicUtils.makeTimeString(this, duration / 1000));
    }
}
