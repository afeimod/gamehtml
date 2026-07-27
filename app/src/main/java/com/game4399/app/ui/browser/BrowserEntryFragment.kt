package com.game4399.app.ui.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.game4399.app.R
import com.game4399.app.data.HistoryItem
import com.game4399.app.data.Prefs
import com.game4399.app.databinding.FragmentBrowserEntryBinding

class BrowserEntryFragment : Fragment() {

    private var _b: FragmentBrowserEntryBinding? = null
    private val b get() = _b!!

    private lateinit var favAdapter: LinkAdapter
    private lateinit var historyAdapter: LinkAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentBrowserEntryBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 3 个默认入口 (手动 inflate)
        b.portalContainer.removeAllViews()
        val portals = listOf(
            Triple("4399电脑版", "__BROWSER__https://www.4399.com/", R.drawable.ic_computer),
            Triple("4399手机版", "__BROWSER__https://m.4399.com/", R.drawable.ic_phone),
            Triple("灵动游戏", "file:///android_asset/web/index.html", R.drawable.ic_play),
        )
        for (p in portals) {
            val v = layoutInflater.inflate(R.layout.item_portal, b.portalContainer, false)
            v.layoutParams = (v.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                width = 0; height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT; weight = 1f
            }
            v.findViewById<android.widget.TextView>(R.id.tvName).text = p.first
            v.findViewById<android.widget.ImageView>(R.id.ivIcon).setImageResource(p.third)
            v.setOnClickListener {
                if (p.second.startsWith("__BROWSER__")) {
                    val real = p.second.removePrefix("__BROWSER__")
                    val bUrl = "file:///android_asset/web/browser.html?url=" + Uri.encode(real)
                    BrowserActivity.openUrl(requireContext(), bUrl, p.first)
                } else {
                    BrowserActivity.openUrl(requireContext(), p.second, p.first)
                }
            }
            b.portalContainer.addView(v)
        }

        b.swipe.setOnRefreshListener { refresh(); b.swipe.isRefreshing = false }

        b.btnGo.setOnClickListener { doSearch() }
        b.etSearch.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) { doSearch(); true } else false
        }
        b.btnAddFav.setOnClickListener { addDialog() }
        b.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.clear_history)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ -> Prefs.clearHistory(); refresh() }
                .show()
        }

        favAdapter = LinkAdapter(
            items = Prefs.favorites(),
            isFav = true,
            onClick = { BrowserActivity.openUrl(requireContext(), it.url, it.name) },
            onDelete = { Prefs.removeFavorite(it.url); refresh() }
        )
        b.rvFav.layoutManager = LinearLayoutManager(requireContext())
        b.rvFav.adapter = favAdapter

        historyAdapter = LinkAdapter(
            items = Prefs.history(),
            isFav = false,
            onClick = { BrowserActivity.openUrl(requireContext(), it.url, it.name) },
            onDelete = {
                Prefs.setHistory(Prefs.history().filterNot { x -> x.url == it.url })
                refresh()
            }
        )
        b.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        b.rvHistory.adapter = historyAdapter
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun doSearch() {
        val raw = b.etSearch.text.toString().trim()
        if (raw.isEmpty()) return
        val finalUrl = when {
            raw.startsWith("http://", true) || raw.startsWith("https://", true) || raw.startsWith("file://", true) -> raw
            raw.contains(".") && !raw.contains(" ") -> "https://$raw"
            else -> "https://www.baidu.com/s?wd=${Uri.encode(raw)}"
        }
        BrowserActivity.openUrl(requireContext(), finalUrl, raw)
    }

    private fun addDialog() {
        val name = EditText(requireContext()).apply { hint = getString(R.string.enter_name) }
        val url = EditText(requireContext()).apply { hint = getString(R.string.enter_url) }
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(name); addView(url)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_bookmark)
            .setView(layout)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add) { _, _ ->
                val u = url.text.toString().trim()
                if (u.isEmpty()) { Toast.makeText(requireContext(), R.string.enter_url, Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val finalUrl = if (u.startsWith("http", true) || u.startsWith("file", true)) u else "https://$u"
                val n = name.text.toString().ifEmpty { finalUrl }
                Prefs.addFavorite(HistoryItem(n, finalUrl))
                Toast.makeText(requireContext(), R.string.bookmark_added, Toast.LENGTH_SHORT).show()
                refresh()
            }
            .show()
    }

    private fun refresh() {
        val favs = Prefs.favorites()
        favAdapter.update(favs)
        b.tvFavEmpty.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE

        val hist = Prefs.history()
        historyAdapter.update(hist)
        b.tvHistoryEmpty.visibility = if (hist.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
