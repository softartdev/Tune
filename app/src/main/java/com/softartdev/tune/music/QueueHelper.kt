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

import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import com.softartdev.tune.music.MediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM
import com.softartdev.tune.music.MediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST
import com.softartdev.tune.music.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH
import com.softartdev.tune.music.MediaIDHelper.MEDIA_ID_MUSICS_BY_SONG
import timber.log.Timber
import java.util.*

/**
 * Utility class to help on queue related tasks.
 */
object QueueHelper {

    fun getPlayingQueue(mediaId: String, musicProvider: MusicProvider?): List<MediaSessionCompat.QueueItem>? {
        // extract the browsing hierarchy from the media ID:
        val hierarchy = MediaIDHelper.getHierarchy(mediaId)

        if (hierarchy.size != 2) {
            Timber.e("Could not build a playing queue for this mediaId: %s", mediaId)
            return null
        }
        val categoryType = hierarchy[0]
        val categoryValue = hierarchy[1]
        Timber.d("Creating playing queue for %s, %s", categoryType, categoryValue)

        // This sample only supports genre and by_search category types.
        val tracks: Iterable<MediaMetadataCompat>? = when (categoryType) {
            MEDIA_ID_MUSICS_BY_SONG -> musicProvider?.musicList
            MEDIA_ID_MUSICS_BY_ALBUM -> musicProvider?.getMusicsByAlbum(categoryValue)
            MEDIA_ID_MUSICS_BY_ARTIST -> {
                Timber.d("Not supported")
                null
            }
            else -> {
                Timber.e("Unrecognized category type: %s for mediaId %s", categoryType, mediaId)
                null
            }
        }
        return tracks?.let { convertToQueue(it, hierarchy[0], hierarchy[1]) }
    }

    fun getPlayingQueueFromSearch(query: String, musicProvider: MusicProvider): List<MediaSessionCompat.QueueItem> {
        Timber.d("Creating playing queue for musics from search %s", query)
        return convertToQueue(musicProvider.searchMusic(query), MEDIA_ID_MUSICS_BY_SEARCH, query)
    }

    fun getMusicIndexOnQueue(queue: Iterable<MediaSessionCompat.QueueItem>, mediaId: String): Int {
        for ((index, item) in queue.withIndex()) {
            if (mediaId == item.description.mediaId) {
                return index
            }
        }
        return -1
    }

    fun getMusicIndexOnQueue(queue: Iterable<MediaSessionCompat.QueueItem>, queueId: Long): Int {
        for ((index, item) in queue.withIndex()) {
            if (queueId == item.queueId) {
                return index
            }
        }
        return -1
    }

    private fun convertToQueue(tracks: Iterable<MediaMetadataCompat>, vararg categories: String): List<MediaSessionCompat.QueueItem> {
        val queue = ArrayList<MediaSessionCompat.QueueItem>()
        for ((count, track) in tracks.withIndex()) {
            // We create a hierarchy-aware mediaID, so we know what the queue is about by looking
            // at the QueueItem media IDs.
            val hierarchyAwareMediaID = MediaIDHelper.createMediaID(track.description.mediaId, *categories)
            val duration = track.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
            val descriptionBuilder = MediaDescriptionCompat.Builder()
            val description = track.description
            val extras = description.extras ?: Bundle()
            extras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            descriptionBuilder.setExtras(extras)
                    .setMediaId(hierarchyAwareMediaID)
                    .setTitle(description.title)
                    .setSubtitle(track.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
                    .setIconBitmap(description.iconBitmap)
                    .setIconUri(description.iconUri)
                    .setMediaUri(description.mediaUri)
                    .setDescription(description.description)
            // We don't expect queues to change after created, so we use the item index as the
            // queueId. Any other number unique in the queue would work.
            val item = MediaSessionCompat.QueueItem(descriptionBuilder.build(), count.toLong())
            queue.add(item)
        }
        return queue
    }

    /**
     * Create a random queue. For simplicity sake, instead of a random queue, we create a
     * queue using the first genre.
     *
     * @param musicProvider the provider used for fetching music.
     * @return list containing [android.support.v4.media.session.MediaSessionCompat]'s
     */
    fun getRandomQueue(musicProvider: MusicProvider): List<MediaSessionCompat.QueueItem> {
        val genres = musicProvider.artists.iterator()
        if (!genres.hasNext()) {
            return emptyList()
        }
        val genre = genres.next()
        val tracks = musicProvider.getMusicsByAlbum(genre)

        return convertToQueue(tracks, MEDIA_ID_MUSICS_BY_ARTIST, genre)
    }

    fun isIndexPlayable(index: Int, queue: List<MediaSessionCompat.QueueItem>?): Boolean
            = queue?.let { index >= 0 && index < it.size } ?: false
}
