package com.flashbox.app.ui.local

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flashbox.app.data.LocalFileEntity
import com.flashbox.app.databinding.ItemLocalFileBinding

class LocalFileAdapter(
    private val onPlay: (LocalFileEntity) -> Unit,
    private val onDelete: (LocalFileEntity) -> Unit
) : ListAdapter<LocalFileEntity, LocalFileAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemLocalFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemLocalFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvTitle.text = item.name
            tvSubtitle.text = item.path
            btnPlay.visibility = if (item.isFolder) android.view.View.GONE else android.view.View.VISIBLE
            btnPlay.setOnClickListener { onPlay(item) }
            btnDelete.setOnClickListener { onDelete(item) }
            root.setOnClickListener { if (!item.isFolder) onPlay(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<LocalFileEntity>() {
            override fun areItemsTheSame(a: LocalFileEntity, b: LocalFileEntity) = a.id == b.id
            override fun areContentsTheSame(a: LocalFileEntity, b: LocalFileEntity) = a == b
        }
    }
}
