package com.flashbox.app.ui.fav

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.flashbox.app.FlashBoxApp
import com.flashbox.app.R
import com.flashbox.app.data.BaseEntry
import com.flashbox.app.data.toEntry
import com.flashbox.app.databinding.FragmentListBinding
import com.flashbox.app.player.PlayerActivity
import com.flashbox.app.ui.history.HistoryAdapter

class FavoriteFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val app get() = requireActivity().application as FlashBoxApp
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = HistoryAdapter(
            onClick = { open(it) },
            onDelete = { app.database.favoriteDao().delete(it.id); refresh() }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        refresh()
    }

    private fun open(entry: BaseEntry) {
        if (entry.isLocal) {
            PlayerActivity.launchLocalPath(requireContext(), entry.url.removePrefix("file://"), entry.title)
        } else {
            PlayerActivity.launchUrl(requireContext(), entry.url)
        }
    }

    private fun refresh() {
        val list = app.database.favoriteDao().getAll().map { it.toEntry() }
        adapter.submitList(list)
        binding.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.empty.text = getString(R.string.fav_empty)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
