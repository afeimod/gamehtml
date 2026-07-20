package com.game4399.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.game4399.app.data.GameItem
import com.game4399.app.databinding.ItemGameBinding

/**
 * 游戏列表适配器。点击整项触发 [onPlay]。
 */
class GameAdapter(
    private val items: MutableList<GameItem> = mutableListOf(),
    private val onPlay: (GameItem) -> Unit
) : RecyclerView.Adapter<GameAdapter.VH>() {

    fun submit(list: List<GameItem>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    class VH(val binding: ItemGameBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvIcon.text = item.icon
            tvTitle.text = item.title
            tvDesc.text = item.desc
            root.setOnClickListener { onPlay(item) }
            btnPlay.setOnClickListener { onPlay(item) }
        }
    }

    override fun getItemCount(): Int = items.size
}
