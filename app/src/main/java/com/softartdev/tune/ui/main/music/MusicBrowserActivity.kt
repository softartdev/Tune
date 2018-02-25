package com.softartdev.tune.ui.main.music

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import com.softartdev.tune.R
import com.softartdev.tune.ui.base.BaseActivity
import com.softartdev.tune.ui.common.ErrorView
import com.softartdev.tune.ui.main.media.MainMediaAdapter
import com.softartdev.tune.ui.main.media.MainMediaPresenter
import com.softartdev.tune.ui.main.media.MainMediaView
import kotlinx.android.synthetic.main.activity_music_browser.*
import timber.log.Timber
import javax.inject.Inject

class MusicBrowserActivity(override val layout: Int = R.layout.activity_music_browser) : BaseActivity(), MainMediaView, MainMediaAdapter.ClickListener, ErrorView.ErrorListener {
    @Inject lateinit var mainMediaPresenter: MainMediaPresenter
    @Inject lateinit var mainMediaAdapter: MainMediaAdapter

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

        if (mainMediaAdapter.itemCount == 0) {
            mainMediaPresenter.mediaItems()
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

    override fun onReloadData() {
        music_error_view?.visibility = View.GONE
        mainMediaPresenter.mediaItems()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainMediaPresenter.detachView()
    }
}
