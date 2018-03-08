package com.softartdev.tune.ui.main.music

import android.content.ComponentName
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import com.softartdev.tune.R
import com.softartdev.tune.music.MediaIDHelper
import com.softartdev.tune.music.MediaPlaybackService
import com.softartdev.tune.music.MusicUtils
import com.softartdev.tune.ui.base.BaseActivity
import com.softartdev.tune.ui.common.ErrorView
import com.softartdev.tune.ui.main.media.MainMediaAdapter
import com.softartdev.tune.ui.main.media.MainMediaPresenter
import com.softartdev.tune.ui.main.media.MainMediaView
import kotlinx.android.synthetic.main.activity_music_browser.*
import kotlinx.android.synthetic.main.view_error.view.*
import timber.log.Timber
import javax.inject.Inject

class MusicBrowserActivity(override val layout: Int = R.layout.activity_music_browser) : BaseActivity(), MainMediaView, MainMediaAdapter.ClickListener, ErrorView.ErrorListener {
    @Inject lateinit var mainMediaPresenter: MainMediaPresenter
    @Inject lateinit var mainMediaAdapter: MainMediaAdapter

    private var mediaBrowserCompat: MediaBrowserCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityComponent().inject(this)
        mainMediaPresenter.attachView(this)
        mainMediaAdapter.clickListener = this

        music_swipe_refresh?.apply {
            setProgressBackgroundColorSchemeResource(R.color.colorPrimary)
            setColorSchemeResources(R.color.white)
            setOnRefreshListener { mainMediaPresenter.mediaItems() }
        }

        music_recycler_view?.apply {
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(music_recycler_view.context, DividerItemDecoration.VERTICAL))
            adapter = mainMediaAdapter
        }

        music_error_view?.setErrorListener(this)
/*
        if (mainMediaAdapter.itemCount == 0) {
            mainMediaPresenter.mediaItems()
        }
*/
        val serviceComponent = ComponentName(this, MediaPlaybackService::class.java)
        mediaBrowserCompat = MediaBrowserCompat(this, serviceComponent, connectionCallBack, null)
    }

    override fun onStart() {
        super.onStart()
        mediaBrowserCompat?.connect()
    }

    override fun onStop() {
        super.onStop()
        mediaBrowserCompat?.disconnect()
    }

    private val subscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
            showMedia(children)
        }
        override fun onError(parentId: String) {
            val errorMessage = getString(R.string.error_loading_media)
            showError(errorMessage)
            Timber.d("$errorMessage with parentId: $parentId")
        }
    }

    private val mediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            state?.let {
                mainMediaAdapter.playbackState = it.state
                mainMediaAdapter.notifyDataSetChanged()
            }
        }
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            metadata?.let {
                mainMediaAdapter.playbackMediaId = it.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                mainMediaAdapter.notifyDataSetChanged()
            }
            MusicUtils.updateNowPlaying(this@MusicBrowserActivity)
        }
    }

    private val connectionCallBack = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val sessionToken = mediaBrowserCompat?.sessionToken ?: throw IllegalArgumentException("No Session token")
            Timber.d("onConnected: session token %s", sessionToken)
            mediaBrowserCompat?.subscribe(MediaIDHelper.MEDIA_ID_MUSICS_BY_SONG, subscriptionCallback)
            val mediaControllerCompat = MediaControllerCompat(this@MusicBrowserActivity, sessionToken)
            mediaControllerCompat.registerCallback(mediaControllerCallback)
            MediaControllerCompat.setMediaController(this@MusicBrowserActivity, mediaControllerCompat)

            if (mediaControllerCompat.metadata != null) {
                MusicUtils.updateNowPlaying(this@MusicBrowserActivity)
            }
        }
        override fun onConnectionFailed() {
            Timber.d("onConnectionFailed")
        }
        override fun onConnectionSuspended() {
            Timber.d("onConnectionSuspended")
            MediaControllerCompat.setMediaController(this@MusicBrowserActivity, null)
        }
    }

    override fun showMedia(mediaItems: List<MediaBrowserCompat.MediaItem>) {
        mainMediaAdapter.apply {
            mediaList = mediaItems
            notifyDataSetChanged()
        }
    }

    override fun onMediaIdClick(mediaId: String) {
        mainMediaPresenter.play(mediaId)
        MediaControllerCompat.getMediaController(this).transportControls.playFromMediaId(mediaId, null)
    }
    
    override fun showProgress(show: Boolean) {
        if (music_swipe_refresh.isRefreshing) {
            music_swipe_refresh.isRefreshing = show
        } else {
            music_progress_view.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun showError(throwable: Throwable) {
        music_error_view?.visibility = View.VISIBLE
        Timber.e(throwable, "There was an error retrieving the media")
    }

    fun showError(errorMessage: String) {
        music_error_view.visibility = View.VISIBLE
        music_error_view.text_error_message.text = errorMessage
    }

    override fun onReloadData() {
        music_error_view?.visibility = View.GONE
        mainMediaPresenter.mediaItems()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainMediaPresenter.detachView()
    }
}
