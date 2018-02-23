package com.softartdev.tune.ui.main.file

import com.softartdev.tune.data.DataManager
import com.softartdev.tune.di.ConfigPersistent
import com.softartdev.tune.ui.base.BasePresenter
import javax.inject.Inject

@ConfigPersistent
class MainFilePresenter @Inject
constructor(private val dataManager: DataManager) : BasePresenter<MainFileView>() {

    fun files(dirType: String?) {
        checkViewAttached()
        mvpView?.showProgress(true)
        dataManager.getFiles(dirType)
                .subscribe({ files ->
                    mvpView?.showProgress(false)
                    mvpView?.showFiles(files.toList())
                }) { throwable ->
                    throwable.printStackTrace()
                    mvpView?.showProgress(false)
                    mvpView?.showError(throwable)
                }
    }

}