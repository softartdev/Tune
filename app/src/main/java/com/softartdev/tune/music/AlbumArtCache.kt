/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.softartdev.tune.music

import android.graphics.Bitmap
import android.os.AsyncTask
import android.util.LruCache

import java.io.IOException

import timber.log.Timber

/**
 * Implements a basic cache of album arts, with async loading support.
 */
object AlbumArtCache {
    private const val MAX_ALBUM_ART_CACHE_SIZE = 12 * 1024 * 1024 // 12 MB
    private const val MAX_ART_WIDTH = 800 // pixels
    private const val MAX_ART_HEIGHT = 480 // pixels

    // Resolution reasonable for carrying around as an icon (generally in
    // MediaDescription.getIconBitmap). This should not be bigger than necessary, because
    // the MediaDescription object should be lightweight. If you set it too high and try to
    // serialize the MediaDescription, you may get FAILED BINDER TRANSACTION errors.
    private const val MAX_ART_WIDTH_ICON = 128 // pixels
    private const val MAX_ART_HEIGHT_ICON = 128 // pixels

    private const val BIG_BITMAP_INDEX = 0
    private const val ICON_BITMAP_INDEX = 1

    // Holds no more than MAX_ALBUM_ART_CACHE_SIZE bytes, bounded by maxmemory/4 and
    // Integer.MAX_VALUE:
    val maxSize = Math.min(MAX_ALBUM_ART_CACHE_SIZE, Math.min(Integer.MAX_VALUE.toLong(), Runtime.getRuntime().maxMemory() / 4).toInt())

    private val mCache: LruCache<String, Array<Bitmap>> = object : LruCache<String, Array<Bitmap>>(maxSize) {
        override fun sizeOf(key: String, value: Array<Bitmap>): Int {
            return value[BIG_BITMAP_INDEX].byteCount + value[ICON_BITMAP_INDEX].byteCount
        }
    }

    fun getBigImage(artUrl: String): Bitmap? = mCache.get(artUrl)[BIG_BITMAP_INDEX]
/*
    fun getIconImage(artUrl: String): Bitmap? = mCache.get(artUrl)[ICON_BITMAP_INDEX]
*/
    fun fetch(artUrl: String, listener: FetchListener) {
        // WARNING: for the sake of simplicity, simultaneous multi-thread fetch requests
        // are not handled properly: they may cause redundant costly operations, like HTTP
        // requests and bitmap rescales. For production-level apps, we recommend you use
        // a proper image loading library, like Glide.
        mCache.get(artUrl)?.let {
            Timber.d("getOrFetch: album art is in cache, using it %s", artUrl)
            listener.onFetched(artUrl, it[BIG_BITMAP_INDEX], it[ICON_BITMAP_INDEX])
            return
        }
        Timber.d("getOrFetch: starting asynctask to fetch %s", artUrl)

        val fetchAsyncTask = FetchAsyncTask(artUrl, listener)
        fetchAsyncTask.execute()
    }

    private class FetchAsyncTask(val artUrl: String, val listener: FetchListener): AsyncTask<Void, Void, Array<Bitmap>>() {
        override fun doInBackground(objects: Array<Void>): Array<Bitmap>? {
            var bitmaps: Array<Bitmap>? = null
            try {
                val bitmap = BitmapHelper.fetchAndRescaleBitmap(artUrl, MAX_ART_WIDTH, MAX_ART_HEIGHT)
                val icon = BitmapHelper.scaleBitmap(bitmap, MAX_ART_WIDTH_ICON, MAX_ART_HEIGHT_ICON)
                bitmaps = arrayOf(bitmap, icon)
                mCache.put(artUrl, bitmaps)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            Timber.d("doInBackground: putting bitmap in cache. cache size=%s", mCache.size())
            return bitmaps
        }
        override fun onPostExecute(bitmaps: Array<Bitmap>?) {
            bitmaps?.let {
                listener.onFetched(artUrl, it[BIG_BITMAP_INDEX], it[ICON_BITMAP_INDEX])
            } ?: listener.onError(artUrl, IllegalArgumentException("got null bitmaps"))
        }
    }

    abstract class FetchListener {
        abstract fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap)
        fun onError(artUrl: String, e: Exception) {
            Timber.e(e, "AlbumArtFetchListener: error while downloading %s", artUrl)
        }
    }

}
