package com.softartdev.tune.ui.main.media

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
import com.softartdev.tune.ui.base.BaseFragment
import com.softartdev.tune.ui.common.ErrorView
import kotlinx.android.synthetic.main.fragment_media_main.*
import kotlinx.android.synthetic.main.view_error.view.*
import timber.log.Timber
import javax.inject.Inject

class MainMediaFragment : BaseFragment(), MainMediaView, MainMediaAdapter.ClickListener, ErrorView.ErrorListener {
    @Inject lateinit var mainMediaPresenter: MainMediaPresenter
    @Inject lateinit var mainMediaAdapter: MainMediaAdapter

    private var mediaBrowserCompat: MediaBrowserCompat? = null

    override fun layoutId(): Int = R.layout.fragment_media_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentComponent().inject(this)
        mainMediaPresenter.attachView(this)
        mainMediaAdapter.clickListener = this

        val serviceComponent = ComponentName(context, MediaPlaybackService::class.java)
        mediaBrowserCompat = MediaBrowserCompat(context, serviceComponent, connectionCallBack, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        media_swipe_refresh?.apply {
            setProgressBackgroundColorSchemeResource(R.color.colorPrimary)
            setColorSchemeResources(R.color.white)
            setOnRefreshListener { mainMediaPresenter.mediaItems() }
        }

        media_recycler_view?.apply {
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(media_recycler_view.context, DividerItemDecoration.VERTICAL))
            adapter = mainMediaAdapter
        }

        media_error_view?.setErrorListener(this)
/*
        if (mainMediaAdapter.itemCount == 0) {
            mainMediaPresenter.mediaItems()
        }
*/
    }

    override fun onStart() {
        super.onStart()
        mediaBrowserCompat?.connect()
    }

    override fun onStop() {
        super.onStop()
        mediaBrowserCompat?.disconnect()
    }

    private val connectionCallBack = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            Timber.d("onConnected")
            mediaBrowserCompat?.subscribe(MediaIDHelper.MEDIA_ID_MUSICS_BY_SONG, subscriptionCallback)
            activity?.let { MediaControllerCompat.getMediaController(it).registerCallback(mediaControllerCallback) }
        }
        override fun onConnectionSuspended() {
            Timber.d("onConnectionFailed")
        }
        override fun onConnectionFailed() {
            Timber.d("onConnectionSuspended")
        }
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
        activity?.let { MediaControllerCompat.getMediaController(it).transportControls.playFromMediaId(mediaId, null) }
    }

    override fun showProgress(show: Boolean) {
        if (media_swipe_refresh.isRefreshing) {
            media_swipe_refresh.isRefreshing = show
        } else {
            media_progress_view.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun showError(throwable: Throwable) {
        media_error_view?.visibility = View.VISIBLE
        Timber.e(throwable, "There was an error retrieving the media")
    }

    fun showError(errorMessage: String) {
        media_error_view.visibility = View.VISIBLE
        media_error_view.text_error_message.text = errorMessage
    }
    
    override fun onReloadData() {
        media_error_view?.visibility = View.GONE
        mainMediaPresenter.mediaItems()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainMediaPresenter.detachView()
    }
}