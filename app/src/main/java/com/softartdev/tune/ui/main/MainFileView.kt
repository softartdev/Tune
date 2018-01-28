package com.softartdev.tune.ui.main

import com.softartdev.tune.ui.base.MvpView
import java.io.File

interface MainFileView : MvpView {
    fun showProgress(show: Boolean)
    fun showFiles(files: List<File>)
    fun showError(throwable: Throwable)
}