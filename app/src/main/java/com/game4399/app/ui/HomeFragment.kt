package com.game4399.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.game4399.app.GameActivity
import com.game4399.app.MainActivity
import com.game4399.app.R
import com.game4399.app.data.GameRepository
import com.game4399.app.data.GameType
import com.game4399.app.databinding.FragmentHomeBinding
import com.game4399.app.webview.NavHelper

/**
 * 首页：Banner + 快捷入口 + 经典怀旧游戏列表 + 自定义 URL 输入。
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: GameAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupQuickEntries()
        setupClassicList()
        setupCustomInput()
        binding.swipeRefresh.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        // 恢复状态栏和 AppBarLayout（从 WebFragment 返回时）
        val activity = requireActivity()
        WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .show(WindowInsetsCompat.Type.statusBars())
        val appBar = activity.findViewById<View>(com.game4399.app.R.id.appBar)
        appBar?.visibility = View.VISIBLE
    }

    private fun setupQuickEntries() {
        val grid = binding.quickGrid
        grid.removeAllViews()
        GameRepository.quickUrls.forEachIndexed { index, (label, url) ->
            val card = layoutInflater.inflate(R.layout.item_quick, grid, false) as
                com.google.android.material.card.MaterialCardView
            val tvLabel = card.findViewById<android.widget.TextView>(R.id.tvLabel)
            val ivIcon = card.findViewById<android.widget.ImageView>(R.id.ivIcon)
            tvLabel.text = label
            ivIcon.setImageResource(when (index) {
                0 -> R.drawable.ic_home
                1 -> R.drawable.ic_game
                2 -> R.drawable.ic_star
                else -> R.drawable.ic_category
            })
            card.setOnClickListener {
                (requireActivity() as? MainActivity)?.openInGame(url, label, GameType.URL)
            }
            grid.addView(card)
        }
    }

    private fun setupClassicList() {
        adapter = GameAdapter { item ->
            GameActivity.launch(requireContext(), item.resolveUrl(), item.title, item.type)
        }
        binding.classicList.layoutManager = LinearLayoutManager(requireContext())
        binding.classicList.adapter = adapter
        adapter.submit(GameRepository.classicItems())
    }

    private fun setupCustomInput() {
        binding.btnPlayCustom.setOnClickListener {
            val input = binding.inputUrl.text?.toString().orEmpty().trim()
            if (input.isEmpty()) {
                binding.inputUrl.error = getString(R.string.enter_game_url)
                return@setOnClickListener
            }
            val (url, type) = NavHelper.normalizeInput(input)
            GameActivity.launch(requireContext(), url, "自定义游戏", type)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
