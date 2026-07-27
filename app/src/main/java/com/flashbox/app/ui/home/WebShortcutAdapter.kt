package com.flashbox.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flashbox.app.data.WebShortcutEntity
import com.flashbox.app.databinding.ItemWebShortcutBinding
import com.flashbox.app.web.WebMode

class WebShortcutAdapter(
    private val onClick: (WebShortcutEntity) -> Unit,
    private val onLongClick: (WebShortcutEntity) -> Unit
) : ListAdapter<WebShortcutEntity, WebShortcutAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemWebShortcutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemWebShortcutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvTitle.text = item.title
            tvSubtitle.text = item.url
            root.setOnClickListener { onClick(item) }
            root.setOnLongClickListener { onLongClick(item); true }
            btnMore.setOnClickListener { onLongClick(item) }
            // tint icon by mode
            val color = when (WebMode.fromId(item.mode)) {
                WebMode.DESKTOP -> 0xFF6C5CE7.toInt()
                WebMode.COMPAT -> 0xFF00CEC9.toInt()
                WebMode.MOBILE -> 0xFFFD79A8.toInt()
            }
            iconFav.background.setTint(color)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WebShortcutEntity>() {
            override fun areItemsTheSame(a: WebShortcutEntity, b: WebShortcutEntity) = a.id == b.id
            override fun areContentsTheSame(a: WebShortcutEntity, b: WebShortcutEntity) = a == b
        }
    }
}
