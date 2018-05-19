package com.softartdev.tune.data

import android.os.Environment
import io.reactivex.Single
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataManager @Inject
constructor(/*private val musicProvider: MusicProvider*/) {

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
/*
    fun getMediaItems(): Single<List<MediaBrowserCompat.MediaItem>> = Single.fromCallable {
        //TODO
        emptyList<MediaBrowserCompat.MediaItem>()
    }
*/
}