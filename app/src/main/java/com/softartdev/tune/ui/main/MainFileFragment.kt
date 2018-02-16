package com.softartdev.tune.ui.main

import android.Manifest
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.webkit.MimeTypeMap
import com.softartdev.tune.R
import com.softartdev.tune.ui.base.BaseFragment
import com.softartdev.tune.ui.common.ErrorView
import com.softartdev.tune.ui.main.MainActivity.Companion.DOWNLOADS_TAG
import com.softartdev.tune.ui.main.MainActivity.Companion.PODCASTS_TAG
import com.softartdev.tune.ui.main.MainActivity.Companion.SOUNDS_TAG
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.fragment_file_main.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class MainFileFragment : BaseFragment(), MainFileView, MainFileAdapter.ClickListener, DialogInterface.OnClickListener, ErrorView.ErrorListener {
    @Inject lateinit var mainFilePresenter: MainFilePresenter
    @Inject lateinit var mainFileAdapter: MainFileAdapter

    private var rxPermissions: RxPermissions? = null

    override fun layoutId(): Int = R.layout.fragment_file_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentComponent().inject(this)
        mainFilePresenter.attachView(this)
        mainFileAdapter.setClickListener(this)
        rxPermissions = RxPermissions(activity as Activity)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        files_swipe_refresh?.apply {
            setProgressBackgroundColorSchemeResource(R.color.colorPrimary)
            setColorSchemeResources(R.color.white)
            setOnRefreshListener { showDownloadsIfPermissionGranted() }
        }

        files_recycler_view?.apply {
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(files_recycler_view.context, DividerItemDecoration.VERTICAL))
            adapter = mainFileAdapter
        }

        files_error_view?.setErrorListener(this)

        if (mainFileAdapter.itemCount == 0) {
            showDownloadsIfPermissionGranted()
        }
    }

    private fun showDownloadsIfPermissionGranted() {
        val permRead = rxPermissions!!.isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
        val permWrite = rxPermissions!!.isGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permRead && permWrite) {
            when (tag) {
                SOUNDS_TAG -> mainFilePresenter.files(Environment.DIRECTORY_MUSIC)
                PODCASTS_TAG -> mainFilePresenter.files(Environment.DIRECTORY_PODCASTS)
                DOWNLOADS_TAG -> mainFilePresenter.files(Environment.DIRECTORY_DOWNLOADS)
            }
        } else {
            rxPermissions!!.request(Manifest.permission.READ_EXTERNAL_STORAGE)
                    .subscribe { granted ->
                        if (granted) {
                            showDownloadsIfPermissionGranted()
                        } else {
                            showRepeatableErrorWithSettings()
                        }
                    }
        }
    }

    override fun showFiles(files: List<File>) {
        mainFileAdapter.apply {
            setFiles(files)
            notifyDataSetChanged()
        }
    }

    override fun onFileItemClick(file: File) {
        val downloadsPath = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS)
        val downloadFile = File(downloadsPath, file.name)
        val contentUri = context?.let { FileProvider.getUriForFile(it, "com.softartdev.tune.fileprovider", downloadFile) }

        val ext = MimeTypeMap.getFileExtensionFromUrl(downloadFile.name)
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(contentUri, type)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }

    override fun showProgress(show: Boolean) {
        if (files_swipe_refresh.isRefreshing) {
            files_swipe_refresh.isRefreshing = show
        } else {
            files_progress_view.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun showError(throwable: Throwable) {
        files_error_view?.visibility = View.VISIBLE
        Timber.e(throwable, "There was an error retrieving the download")
    }

    override fun onReloadData() {
        files_error_view?.visibility = View.GONE
        showDownloadsIfPermissionGranted()
    }

    private fun showRepeatableErrorWithSettings() {
        context?.let {
            AlertDialog.Builder(it)
                    .setMessage(R.string.rationale_storage_permission)
                    .setNegativeButton(R.string.dialog_action_cancel, this)
                    .setPositiveButton(R.string.retry, this)
                    .setNeutralButton(R.string.settings, this)
                    .setCancelable(false)
                    .show()
        }
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        when (which) {
            DialogInterface.BUTTON_NEGATIVE -> {
                dialog.cancel()
                showError(IllegalStateException("No permission"))
            }
            DialogInterface.BUTTON_POSITIVE -> {
                dialog.cancel()
                showDownloadsIfPermissionGranted()
            }
            DialogInterface.BUTTON_NEUTRAL -> {
                dialog.cancel()
                val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context?.packageName))
                startActivityForResult(appSettingsIntent, REQUEST_PERMISSION_EXTERNAL_STORAGE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_PERMISSION_EXTERNAL_STORAGE -> showDownloadsIfPermissionGranted()
        }
    }

    companion object {
        internal const val REQUEST_PERMISSION_EXTERNAL_STORAGE = 1004
    }

}
