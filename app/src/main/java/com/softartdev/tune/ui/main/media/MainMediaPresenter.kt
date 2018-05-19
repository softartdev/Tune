package com.softartdev.tune.ui.main.media

import com.softartdev.tune.di.ConfigPersistent
import com.softartdev.tune.ui.base.BasePresenter
import timber.log.Timber
import javax.inject.Inject

@ConfigPersistent
class MainMediaPresenter @Inject
constructor(/*private val dataManager: DataManager*/) : BasePresenter<MainMediaView>() {

    fun mediaItems() {
        checkViewAttached()
        mvpView?.showProgress(true)
/*
        dataManager.getMediaItems()
                .subscribe({ mediaItems ->
                    mvpView?.showProgress(false)
                    mvpView?.showMedia(mediaItems)
                }) { throwable ->
                    throwable.printStackTrace()
                    mvpView?.showProgress(false)
                    mvpView?.showError(throwable)
                }
*/
        mvpView?.showProgress(false)
    }

    fun play(mediaId: String) {
        Timber.d("Play media with id = $mediaId")
    }

}