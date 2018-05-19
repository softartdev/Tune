/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.AsyncTask
import android.provider.MediaStore
import android.support.annotation.RawRes
import android.support.v4.media.MediaMetadataCompat
import com.softartdev.tune.R
import timber.log.Timber
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/*
A provider of music contents to the music application, it reads external storage for any music
files, parse them and
store them in this class for future use.
 */
class MusicProvider(private val context: Context) {
    // Album Name --> list of Metadata
    private val mMusicListByAlbum: ConcurrentMap<String, MutableList<MediaMetadataCompat>> = ConcurrentHashMap()
    // Playlist Name --> list of Metadata
    private val mMusicListByPlaylist: ConcurrentMap<String, List<MediaMetadataCompat>> = ConcurrentHashMap()
    // Artist Name --> Map of (album name --> album metadata)
    private val mArtistAlbumDb: ConcurrentMap<String, MutableMap<String, MediaMetadataCompat>> = ConcurrentHashMap()
    val musicList: MutableList<MediaMetadataCompat> = ArrayList()
    private val mMusicListById: ConcurrentMap<Long, Song> = ConcurrentHashMap()
    private val mMusicListByMediaId: ConcurrentMap<String, Song> = ConcurrentHashMap()

    init {
        mMusicListByPlaylist[MediaIDHelper.MEDIA_ID_NOW_PLAYING] = ArrayList()
    }

    companion object {
        // Public constants
        const val UNKNOWN = "UNKNOWN"
        // Uri source of this track
        const val CUSTOM_METADATA_TRACK_SOURCE = "__SOURCE__"
        private const val MUSIC_SORT_ORDER = MediaStore.Audio.Media.TITLE + " ASC"
    }

    @Volatile private var mCurrentState = State.NON_INITIALIZED

    val isInitialized: Boolean
        get() = mCurrentState == State.INITIALIZED

    /**
     * Get an iterator over the list of artists
     *
     * @return list of artists
     */
    val artists: Iterable<String>
        get() = if (mCurrentState != State.INITIALIZED) emptyList() else mArtistAlbumDb.keys

    /**
     * Get an iterator over the list of albums
     *
     * @return list of albums
     */
    val albums: Iterable<MediaMetadataCompat>
        get() {
            return if (mCurrentState != State.INITIALIZED) {
                emptyList()
            } else {
                val albumList = ArrayList<MediaMetadataCompat>()
                for (artist_albums in mArtistAlbumDb.values) {
                    albumList.addAll(artist_albums.values)
                }
                albumList
            }
        }

    /**
     * Get an iterator over the list of playlists
     *
     * @return list of playlists
     */
    val playlists: Iterable<String>
        get() = if (mCurrentState != State.INITIALIZED) emptyList() else mMusicListByPlaylist.keys

    private val defaultAlbumArt: Bitmap by lazy {
        val opts = BitmapFactory.Options()
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888
        @RawRes val artRes = R.drawable.albumart_mp_unknown
        BitmapFactory.decodeStream(context.resources.openRawResource(artRes), null, opts)
    }

    internal enum class State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    /**
     * Get albums of a certain artist
     *
     */
    fun getAlbumByArtist(artist: String): Iterable<MediaMetadataCompat> {
        return if (mCurrentState == State.INITIALIZED || mArtistAlbumDb.containsKey(artist)) {
            mArtistAlbumDb[artist]?.values ?: emptyList()
        } else emptyList()
    }

    /**
     * Get music tracks of the given album
     *
     */
    fun getMusicsByAlbum(album: String): Iterable<MediaMetadataCompat> {
        return if (mCurrentState == State.INITIALIZED && mMusicListByAlbum.containsKey(album)) {
            mMusicListByAlbum[album] ?: emptyList()
        } else emptyList()
    }

    /**
     * Get music tracks of the given playlist
     *
     */
    fun getMusicsByPlaylist(playlist: String): Iterable<MediaMetadataCompat> {
        return if (mCurrentState == State.INITIALIZED && mMusicListByPlaylist.containsKey(playlist)) {
            mMusicListByPlaylist[playlist] ?: emptyList()
        } else emptyList()
    }

    /**
     * Return the MediaMetadataCompat for the given musicID.
     *
     * @param musicId The unique, non-hierarchical music ID.
     */
    fun getMusicByMediaId(musicId: String?): Song? {
        return if (mMusicListByMediaId.containsKey(musicId)) mMusicListByMediaId[musicId] else null
    }

    /**
     * Very basic implementation of a search that filter music tracks which title containing
     * the given query.
     *
     */
    fun searchMusic(titleQuery: String): Iterable<MediaMetadataCompat> {
        if (mCurrentState != State.INITIALIZED) {
            return emptyList()
        }
        val result = ArrayList<MediaMetadataCompat>()
        for (song in mMusicListByMediaId.values) {
            if (song.metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                            .toLowerCase()
                            .contains(titleQuery.toLowerCase())) {
                result.add(song.metadata)
            }
        }
        return result
    }

    interface MusicProviderCallback {
        fun onMusicCatalogReady(success: Boolean)
    }

    /**
     * Get the list of music tracks from disk and caches the track information
     * for future reference, keying tracks by musicId and grouping by genre.
     */
    fun retrieveMediaAsync(callback: MusicProviderCallback?) {
        Timber.d("retrieveMediaAsync called")
        if (mCurrentState == State.INITIALIZED) {
            // Nothing to do, execute callback immediately
            callback?.onMusicCatalogReady(true)
            return
        }
        // Asynchronously load the music catalog in a separate thread
        RetrieveMediaTask(callback).execute()
    }

    @SuppressLint("StaticFieldLeak")
    private inner class RetrieveMediaTask(val callback: MusicProviderCallback?) : AsyncTask<Void, Void, State>() {
        override fun doInBackground(vararg params: Void): State {
            mCurrentState = State.INITIALIZING
            mCurrentState = if (retrieveMedia()) {
                State.INITIALIZED
            } else {
                State.NON_INITIALIZED
            }
            return mCurrentState
        }

        override fun onPostExecute(current: State) {
            callback?.onMusicCatalogReady(current == State.INITIALIZED)
        }
    }

    @Synchronized
    private fun retrieveMedia(): Boolean {
        val cursor = context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null, MUSIC_SORT_ORDER)
        if (cursor == null) {
            Timber.e("Failed to retreive music: cursor is null")
            mCurrentState = State.NON_INITIALIZED
            return false
        }
        if (!cursor.moveToFirst()) {
            Timber.d("Failed to move cursor to first row (no query result)")
            cursor.close()
            return true
        }
        val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
        val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
        do {
            Timber.i("Music ID: %s Title: %s", cursor.getString(idColumn), cursor.getString(titleColumn))
            val thisId = cursor.getLong(idColumn)
            val thisPath = cursor.getString(pathColumn)
            val metadata = retrieveMediaMetadataCompat(thisId, thisPath) ?: continue
            Timber.i("MediaMetadataCompat: %s", metadata)
            val thisSong = Song(thisId, metadata, null)
            // Construct per feature database
            musicList.add(metadata)
            mMusicListById[thisId] = thisSong
            mMusicListByMediaId[thisId.toString()] = thisSong
            addMusicToAlbumList(metadata)
            addMusicToArtistList(metadata)
        } while (cursor.moveToNext())
        cursor.close()
        return true
    }

    @Synchronized
    private fun retrieveMediaMetadataCompat(musicId: Long, musicPath: String): MediaMetadataCompat? {
        val contentUri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, musicId)
        return if (File(musicPath).exists()) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, contentUri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val duration: Long = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0

            val embedded: Bitmap? = retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            val bitmap: Bitmap? = embedded?.let { Bitmap.createScaledBitmap(it, defaultAlbumArt.width, defaultAlbumArt.height, false) }

            retriever.release()
            with(MediaMetadataCompat.Builder()) {
                putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, musicId.toString())
                putString(CUSTOM_METADATA_TRACK_SOURCE, musicPath)
                putString(MediaMetadataCompat.METADATA_KEY_TITLE, title ?: UNKNOWN)
                putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album ?: UNKNOWN)
                putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist ?: UNKNOWN)
                putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                build()
            }
        } else {
            Timber.d("Does not exist, deleting item")
            context.contentResolver.delete(contentUri, null, null)
            null
        }
    }

    private fun addMusicToAlbumList(metadata: MediaMetadataCompat) {
        val thisAlbum = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: UNKNOWN
        if (!mMusicListByAlbum.containsKey(thisAlbum)) {
            mMusicListByAlbum[thisAlbum] = ArrayList()
        }
        mMusicListByAlbum[thisAlbum]?.add(metadata)
    }

    private fun addMusicToArtistList(metadata: MediaMetadataCompat) {
        val thisArtist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: UNKNOWN
        val thisAlbum = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: UNKNOWN
        if (!mArtistAlbumDb.containsKey(thisArtist)) {
            mArtistAlbumDb[thisArtist] = ConcurrentHashMap()
        }
        val albumsMap: MutableMap<String, MediaMetadataCompat>? = mArtistAlbumDb[thisArtist]
        val builder: MediaMetadataCompat.Builder
        var count: Long = 0
        var thisAlbumArt: Bitmap? = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
        if (albumsMap?.containsKey(thisAlbum) == true) {
            val albumMetadata = albumsMap[thisAlbum]
            count = albumMetadata?.getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS) ?: 0L
            val nAlbumArt = albumMetadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            builder = MediaMetadataCompat.Builder(albumMetadata)
            if (nAlbumArt != null) {
                thisAlbumArt = null
            }
        } else {
            builder = MediaMetadataCompat.Builder()
            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, thisAlbum)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, thisArtist)
        }
        if (thisAlbumArt != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, thisAlbumArt)
        }
        builder.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, count + 1)
        albumsMap?.put(thisAlbum, builder.build())
    }

    @Synchronized
    fun updateMusic(musicId: String?, metadata: MediaMetadataCompat) {
        val song = mMusicListByMediaId[musicId] ?: return

        val oldGenre = song.metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE)
        val newGenre = metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE)

        song.metadata = metadata

        // if genre has changed, we need to rebuild the list by genre
        if (oldGenre != newGenre) {
            //            buildListsByGenre();
        }
    }
}