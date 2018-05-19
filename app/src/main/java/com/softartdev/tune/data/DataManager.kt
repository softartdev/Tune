package com.softartdev.tune.data

import android.os.Environment
import android.support.v4.media.MediaBrowserCompat
import com.softartdev.tune.music.MusicProvider
import io.reactivex.Single
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataManager @Inject
constructor(private val musicProvider: MusicProvider) {

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

    fun getMediaItems(): Single<List<MediaBrowserCompat.MediaItem>> = Single.fromCallable {
        //TODO
        for (media in musicProvider.musicList) {
            Timber.d("Review media item: %s", media)
        }
        emptyList<MediaBrowserCompat.MediaItem>()
    }

}