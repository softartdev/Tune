package com.softartdev.tune.ui.main.file

import android.support.annotation.DrawableRes
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.softartdev.tune.R
import com.softartdev.tune.di.ConfigPersistent
import kotlinx.android.synthetic.main.item_file.view.*
import java.io.File
import javax.inject.Inject

@ConfigPersistent
class MainFileAdapter @Inject
constructor() : RecyclerView.Adapter<MainFileAdapter.FilesViewHolder>() {
    private var fileList: List<File>
    private var clickListener: ClickListener? = null

    init {
        fileList = emptyList()
    }

    fun setFiles(files: List<File>) {
        this.fileList = files
    }

    fun setClickListener(clickListener: ClickListener) {
        this.clickListener = clickListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilesViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FilesViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilesViewHolder, position: Int) {
        val file = fileList[position]
        holder.bind(file)
    }

    override fun getItemCount(): Int = fileList.size

    interface ClickListener {
        fun onFileItemClick(file: File)
    }

    inner class FilesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private lateinit var selectedFile: File

        init {
            itemView.setOnClickListener { clickListener?.onFileItemClick(selectedFile) }
        }

        fun bind(file: File) {
            selectedFile = file
            @DrawableRes val drawableLeft = if (selectedFile.isDirectory) R.drawable.ic_folder_black_24dp else R.drawable.ic_insert_drive_file_black_24dp
            itemView.item_file_name_text_view?.apply {
                text = selectedFile.name
                setCompoundDrawablesWithIntrinsicBounds(drawableLeft, 0, 0, 0)
            }
        }
    }

}
