package com.game4399.app

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.game4399.app.GameActivity
import com.game4399.app.data.FavoriteStore
import com.game4399.app.data.GameType
import com.game4399.app.databinding.ActivityFavoritesBinding
import com.game4399.app.ui.GameAdapter
import com.game4399.app.data.GameItem

/**
 * 收藏 / 历史 列表。
 * 通过 [EXTRA_MODE] 区分两种模式。
 */
class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritesBinding
    private lateinit var adapter: GameAdapter
    private var mode: Int = MODE_FAVORITES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        mode = intent.getIntExtra(EXTRA_MODE, MODE_FAVORITES)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(
                if (mode == MODE_HISTORY) R.string.title_history else R.string.title_favorites
            )
        }

        adapter = GameAdapter { item ->
            GameActivity.launch(this, item.resolveUrl(), item.title, item.type)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        loadData()
    }

    private fun loadData() {
        val entries = if (mode == MODE_HISTORY) FavoriteStore.listHistory()
                      else FavoriteStore.listFavorites()
        val items = entries.map { e ->
            GameItem(
                id = e.url,
                title = e.title.ifBlank { e.url },
                desc = e.url,
                type = e.type,
                url = e.url,
                icon = e.title.firstOrNull()?.toString() ?: "游"
            )
        }
        adapter.submit(items)
        binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        loadData() // 收藏可能已变化
    }

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_FAVORITES = 0
        const val MODE_HISTORY = 1
    }
}
