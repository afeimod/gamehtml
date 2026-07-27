package com.flashbox.app.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flashbox.app.R
import com.flashbox.app.data.BaseEntry
import com.flashbox.app.databinding.ItemHistoryBinding

/** Generic adapter for history and favorites (both backed by [BaseEntry]). */
class HistoryAdapter(
    private val onClick: (BaseEntry) -> Unit,
    private val onDelete: (BaseEntry) -> Unit
) : ListAdapter<BaseEntry, HistoryAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvTitle.text = item.title
            tvSubtitle.text = item.url
            iconType.setImageResource(if (item.isLocal) R.drawable.ic_file else R.drawable.ic_web)
            root.setOnClickListener { onClick(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<BaseEntry>() {
            override fun areItemsTheSame(a: BaseEntry, b: BaseEntry) = a.id == b.id && a.kind == b.kind
            override fun areContentsTheSame(a: BaseEntry, b: BaseEntry) = a == b
        }
    }
}
