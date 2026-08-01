package com.game4399.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.game4399.app.GameActivity
import com.game4399.app.MainActivity
import com.game4399.app.R
import com.game4399.app.data.GameRepository
import com.game4399.app.data.GameType
import com.game4399.app.data.LocalSwfStore
import com.game4399.app.databinding.FragmentHomeBinding
import com.game4399.app.webview.NavHelper

/**
 * 首页：Banner + 快捷入口 + 本地 SWF 列表 + 自定义 URL 输入。
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
        setupLocalSwfList()
        setupCustomInput()
        binding.swipeRefresh.isEnabled = false
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

    private fun setupLocalSwfList() {
        adapter = GameAdapter(
            onPlay = { item ->
                GameActivity.launch(requireContext(), item.resolveUrl(), item.title, item.type)
            },
            onLongClick = { item ->
                // 长按删除本地 SWF
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("删除")
                    .setMessage("确定要删除「${item.title}」吗？")
                    .setPositiveButton("删除") { _, _ ->
                        LocalSwfStore.remove(item.id)
                        refreshLocalSwfList()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
        )
        binding.classicList.layoutManager = LinearLayoutManager(requireContext())
        binding.classicList.adapter = adapter

        // 添加本地 SWF 按钮
        binding.btnAddSwf.setOnClickListener {
            openFilePicker()
        }

        // 加载已保存的本地 SWF 列表
        refreshLocalSwfList()
    }

    private fun refreshLocalSwfList() {
        val items = LocalSwfStore.toGameItems()
        adapter.submit(items)
        // 空列表时显示提示
        binding.classicList.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            // 支持 .swf 文件
            val mimeTypes = arrayOf(
                "application/x-shockwave-flash",
                "application/octet-stream",
                "*/*"
            )
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        try {
            startActivityForResult(intent, REQUEST_CODE_OPEN_SWF)
        } catch (e: Exception) {
            // fallback
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(fallback, REQUEST_CODE_OPEN_SWF)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_SWF && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                // 持久化 URI 权限
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // 某些设备不支持，忽略
            }
            val title = LocalSwfStore.titleFromUri(uri)
            LocalSwfStore.add(uri.toString(), title)
            refreshLocalSwfList()
        }
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

    override fun onResume() {
        super.onResume()
        // 从 GameActivity 返回后刷新列表
        if (::adapter.isInitialized) {
            refreshLocalSwfList()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_CODE_OPEN_SWF = 1001
    }
}
