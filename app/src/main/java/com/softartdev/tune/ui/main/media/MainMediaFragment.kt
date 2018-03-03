package com.softartdev.tune.ui.main.media

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import com.softartdev.tune.R
import com.softartdev.tune.ui.base.BaseFragment
import com.softartdev.tune.ui.common.ErrorView
import kotlinx.android.synthetic.main.fragment_media_main.*
import timber.log.Timber
import javax.inject.Inject

class MainMediaFragment : BaseFragment(), MainMediaView, MainMediaAdapter.ClickListener, ErrorView.ErrorListener {
    @Inject lateinit var mainMediaPresenter: MainMediaPresenter
    @Inject lateinit var mainMediaAdapter: MainMediaAdapter

    override fun layoutId(): Int = R.layout.fragment_media_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentComponent().inject(this)
        mainMediaPresenter.attachView(this)
        mainMediaAdapter.clickListener = this
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
    
    override fun onReloadData() {
        media_error_view?.visibility = View.GONE
        mainMediaPresenter.mediaItems()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainMediaPresenter.detachView()
    }
}