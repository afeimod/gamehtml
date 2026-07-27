package com.game4399.app.ui.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.game4399.app.R
import com.game4399.app.data.HistoryItem

class LinkAdapter(
    private var items: List<HistoryItem>,
    private val isFav: Boolean,
    private val onClick: (HistoryItem) -> Unit,
    private val onDelete: (HistoryItem) -> Unit
) : RecyclerView.Adapter<LinkAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvUrl: TextView = v.findViewById(R.id.tvUrl)
        val ivFav: ImageView = v.findViewById(R.id.ivFav)
        val ivDelete: ImageView = v.findViewById(R.id.ivDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_link, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val it = items[pos]
        h.tvName.text = it.name
        h.tvUrl.text = it.url
        h.ivFav.setImageResource(if (isFav) R.drawable.ic_bookmark else R.drawable.ic_history)
        h.ivDelete.setOnClickListener { onDelete(it) }
        h.itemView.setOnClickListener { onClick(it) }
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
