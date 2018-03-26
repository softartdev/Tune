/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.softartdev.tune.R
import com.softartdev.tune.ui.main.music.MediaPlaybackActivity
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.content_main.view.*
import java.util.*

/**
 * Static methods useful for activities
 */
object MusicUtils {
    /**
     *  Try to use String.format() as little as possible, because it creates a
     *  new Formatter every time you call it, which is very inefficient.
     *  Reusing an existing Formatter more than tripled the speed of
     *  makeTimeString().
     *  This Formatter/StringBuilder are also used by makeAlbumSongsLabel()
     */
    private val sFormatBuilder = StringBuilder()
    private val sFormatter = Formatter(sFormatBuilder, Locale.getDefault())
    private val sTimeArgs = arrayOfNulls<Any>(5)

    fun makeTimeString(context: Context, secs: Long): String {
        val durationFormat = context.getString(
                if (secs < 3600) R.string.durationformatshort else R.string.durationformatlong)

        /* Provide multiple arguments so the format can be changed easily
         * by modifying the xml.
         */
        sFormatBuilder.setLength(0)

        val timeArgs = sTimeArgs
        timeArgs[0] = secs / 3600
        timeArgs[1] = secs / 60
        timeArgs[2] = secs / 60 % 60
        timeArgs[3] = secs
        timeArgs[4] = secs % 60

        return sFormatter.format(durationFormat, *timeArgs).toString()
    }

    fun resizeBitmap(bitmap: Bitmap, ref: Bitmap): Bitmap = Bitmap.createScaledBitmap(bitmap, ref.width, ref.height, false)

    fun updateNowPlaying(activity: AppCompatActivity) {
        MediaControllerCompat.getMediaController(activity)?.metadata?.let {
            activity.nowplaying.title.text = it.getString(MediaMetadataCompat.METADATA_KEY_TITLE)

            with(it.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)) {
                activity.nowplaying.artist.text = if (MusicProvider.UNKOWN == this) activity.getString(R.string.unknown_artist_name) else this
            }

            activity.nowplaying.visibility = View.VISIBLE
            activity.nowplaying.setOnClickListener { activity.startActivity(Intent(activity, MediaPlaybackActivity::class.java)) }
            return
        } ?: let { activity.nowplaying.visibility = View.GONE }
    }

}
