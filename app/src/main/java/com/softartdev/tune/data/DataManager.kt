package com.softartdev.tune.data

import android.os.Environment
import android.support.v4.media.MediaBrowserCompat
import io.reactivex.Single
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataManager @Inject
constructor() {

    fun getFiles(dirType: String?): Single<Array<File>> {
        return Single.fromCallable {
            val dirDownloads = Environment.getExternalStoragePublicDirectory(dirType)

            dirDownloads ?: throw IllegalStateException("Failed to get external storage public directory")
            if (dirDownloads.exists()) {
                if (!dirDownloads.isDirectory) {
                    throw IllegalStateException(dirDownloads.absolutePath + " already exists and is not a directory")
                }
            } else {
                if (!dirDownloads.mkdirs()) {
                    throw IllegalStateException("Unable to create directory: " + dirDownloads.absolutePath)
                }
            }

            dirDownloads.listFiles() ?: arrayOfNulls(0)
        }
    }

    fun getMediaItems(): Single<List<MediaBrowserCompat.MediaItem>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}