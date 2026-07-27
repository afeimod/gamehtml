package com.game4399.app.ui.local

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.game4399.app.R
import com.game4399.app.data.LocalItem
import com.game4399.app.data.Prefs

class LocalAdapter(
    private var items: List<LocalItem>,
    private val onPlay: (LocalItem) -> Unit,
    private val onDelete: (LocalItem) -> Unit
) : RecyclerView.Adapter<LocalAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvPath: TextView = v.findViewById(R.id.tvPath)
        val ivIcon: ImageView = v.findViewById(R.id.ivIcon)
        val ivEngine: ImageView = v.findViewById(R.id.ivEngine)
        val ivDelete: ImageView = v.findViewById(R.id.ivDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_local, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.tvName.text = item.name
        h.tvPath.text = item.path
        h.ivIcon.setImageResource(if (item.isDir) R.drawable.ic_folder else R.drawable.ic_file)
        h.ivEngine.setImageResource(if (Prefs.engine() == "ruffle") R.drawable.ic_play else R.drawable.ic_joystick)
        h.ivDelete.setOnClickListener { onDelete(item) }
        h.itemView.setOnClickListener { onPlay(item) }
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<LocalItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
