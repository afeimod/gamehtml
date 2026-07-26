package com.game4399.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.game4399.app.MainActivity
import com.game4399.app.R
import com.game4399.app.data.GameRepository
import com.game4399.app.data.GameType

/**
 * 分类页：网页游戏大全网格。
 * 展示知名网页游戏门户，点击直接在游戏 WebView 中打开。
 */
class CategoryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_category, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebGameGrid(view)
    }

    private fun setupWebGameGrid(view: View) {
        val grid = view.findViewById<androidx.gridlayout.widget.GridLayout>(R.id.webGameGrid)
        grid.removeAllViews()

        val icons = intArrayOf(
            R.drawable.ic_game, R.drawable.ic_star, R.drawable.ic_home,
            R.drawable.ic_category, R.drawable.ic_game, R.drawable.ic_star,
            R.drawable.ic_home, R.drawable.ic_category, R.drawable.ic_game,
            R.drawable.ic_star, R.drawable.ic_home, R.drawable.ic_category
        )

        GameRepository.webGamePortals.forEachIndexed { index, entry ->
            val card = layoutInflater.inflate(R.layout.item_web_game, grid, false) as
                com.google.android.material.card.MaterialCardView
            val tvLabel = card.findViewById<android.widget.TextView>(R.id.tvLabel)
            val ivIcon = card.findViewById<android.widget.ImageView>(R.id.ivIcon)
            tvLabel.text = entry.title
            ivIcon.setImageResource(icons[index % icons.size])
            card.setOnClickListener {
                (requireActivity() as? MainActivity)?.openInGame(entry.url, entry.title, GameType.URL)
            }
            grid.addView(card)
        }
    }
}
