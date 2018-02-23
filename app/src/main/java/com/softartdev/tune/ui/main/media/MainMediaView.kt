package com.softartdev.tune.ui.main.media

import android.support.v4.media.MediaBrowserCompat
import com.softartdev.tune.ui.base.MvpView

interface MainMediaView : MvpView {
    fun showProgress(show: Boolean)
    fun showMedia(mediaItems: List<MediaBrowserCompat.MediaItem>)
    fun showError(throwable: Throwable)
}