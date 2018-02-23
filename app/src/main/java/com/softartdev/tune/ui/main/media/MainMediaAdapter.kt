package com.softartdev.tune.ui.main.media

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.softartdev.tune.R
import com.softartdev.tune.di.ConfigPersistent
import kotlinx.android.synthetic.main.item_media.view.*
import javax.inject.Inject

@ConfigPersistent
class MainMediaAdapter @Inject
constructor() : RecyclerView.Adapter<MainMediaAdapter.MediaItemsViewHolder>() {
    var mediaList: List<MediaBrowserCompat.MediaItem> = emptyList()
    var clickListener: ClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaItemsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
        return MediaItemsViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaItemsViewHolder, position: Int) {
        val mediaDescriptionCompat = mediaList[position].description
        holder.bind(mediaDescriptionCompat)
    }

    override fun getItemCount(): Int = mediaList.size

    interface ClickListener {
        fun onMediaIdClick(mediaId: String)
    }

    inner class MediaItemsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private lateinit var selectedMediaId: String

        init {
            itemView.setOnClickListener { clickListener?.onMediaIdClick(selectedMediaId) }
        }

        fun bind(mediaDescriptionCompat: MediaDescriptionCompat) {
            selectedMediaId = mediaDescriptionCompat.mediaId!!
            itemView.item_media_icon_image_view.setImageBitmap(mediaDescriptionCompat.iconBitmap)
            itemView.item_media_title_text_view.text = mediaDescriptionCompat.title
            itemView.item_media_subtitle_text_view.text = mediaDescriptionCompat.subtitle
        }

    }
}