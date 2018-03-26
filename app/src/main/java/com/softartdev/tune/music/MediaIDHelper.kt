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

import java.util.Arrays

/**
 * Utility class to help on queue related tasks.
 */
object MediaIDHelper {
    // Media IDs used on browseable items of MediaBrowser
    const val MEDIA_ID_ROOT = "__ROOT__"
    const val MEDIA_ID_MUSICS_BY_ARTIST = "__BY_ARTIST__"
    const val MEDIA_ID_MUSICS_BY_ALBUM = "__BY_ALBUM__"
    const val MEDIA_ID_MUSICS_BY_SONG = "__BY_SONG__"
    const val MEDIA_ID_MUSICS_BY_PLAYLIST = "__BY_PLAYLIST__"
    const val MEDIA_ID_MUSICS_BY_SEARCH = "__BY_SEARCH__"
    const val MEDIA_ID_NOW_PLAYING = "__NOW_PLAYING__"

    private const val CATEGORY_SEPARATOR: Char = 31.toChar()
    private const val LEAF_SEPARATOR: Char = 30.toChar()

    /**
     * MediaIDs are of the form <categoryType>/<categoryValue>|<musicUniqueId>, to make it easy
     * to find the category (like genre) that a music was selected from, so we
     * can correctly build the playing queue. This is specially useful when
     * one music can appear in more than one list, like "by genre -> genre_1"
     * and "by artist -> artist_1".
     */
    fun createMediaID(musicID: String?, vararg categories: String): String = with(StringBuilder()) {
        if (categories.isNotEmpty()) {
            append(categories[0])
            for (i in 1 until categories.size) {
                append(CATEGORY_SEPARATOR).append(categories[i])
            }
        }
        musicID?.let { append(LEAF_SEPARATOR).append(it) }
        toString()
    }

    fun createBrowseCategoryMediaID(categoryType: String, categoryValue: String): String = categoryType + CATEGORY_SEPARATOR + categoryValue

    /**
     * Extracts unique musicID from the mediaID. mediaID is, by this sample's convention, a
     * concatenation of category (eg "by_genre"), categoryValue (eg "Classical") and unique
     * musicID. This is necessary so we know where the user selected the music from, when the music
     * exists in more than one music list, and thus we are able to correctly build the playing
     * queue.
     *
     * @param mediaID that contains the musicID
     * @return musicID
     */
    fun extractMusicIDFromMediaID(mediaID: String): String? {
        val pos = mediaID.indexOf(LEAF_SEPARATOR)
        return if (pos >= 0) {
            mediaID.substring(pos + 1)
        } else null
    }

    /**
     * Extracts category and categoryValue from the mediaID. mediaID is, by this sample's
     * convention, a concatenation of category (eg "by_genre"), categoryValue (eg "Classical") and
     * mediaID. This is necessary so we know where the user selected the music from, when the music
     * exists in more than one music list, and thus we are able to correctly build the playing
     * queue.
     *
     * @param mediaID that contains a category and categoryValue.
     */
    fun getHierarchy(mediaID: String): Array<String> {
        val pos = mediaID.indexOf(LEAF_SEPARATOR)
        val categoryValue = if (pos >= 0) mediaID.substring(0, pos) else mediaID
        return categoryValue.split(CATEGORY_SEPARATOR.toString().toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    }

    fun extractBrowseCategoryValueFromMediaID(mediaID: String): String? {
        val hierarchy = getHierarchy(mediaID)
        return if (hierarchy.size == 2) {
            hierarchy[1]
        } else null
    }

    fun getParentMediaID(mediaID: String): String {
        val hierarchy = getHierarchy(mediaID)
        if (!isBrowsable(mediaID)) {
            return createMediaID(null, *hierarchy)
        }
        if (hierarchy.size <= 1) {
            return MEDIA_ID_ROOT
        }
        val parentHierarchy = Arrays.copyOf(hierarchy, hierarchy.size - 1)
        return createMediaID(null, *parentHierarchy)
    }

    private fun isBrowsable(mediaID: String): Boolean = mediaID.indexOf(LEAF_SEPARATOR) < 0

}
