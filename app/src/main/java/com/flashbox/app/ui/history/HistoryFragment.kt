package com.flashbox.app.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.flashbox.app.FlashBoxApp
import com.flashbox.app.R
import com.flashbox.app.data.BaseEntry
import com.flashbox.app.data.toEntry
import com.flashbox.app.databinding.FragmentListBinding
import com.flashbox.app.player.PlayerActivity

class HistoryFragment : Fragment() {

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
            onDelete = { app.database.historyDao().delete(it.id); refresh() }
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
        val list = app.database.historyDao().getAll().map { it.toEntry() }
        adapter.submitList(list)
        binding.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.empty.text = getString(R.string.history_empty)
    }

    fun clearAll() {
        AlertDialog.Builder(requireContext(), R.style.Theme_FlashBox_Dialog)
            .setTitle(R.string.action_clear_all)
            .setMessage(R.string.settings_clear_history)
            .setPositiveButton(R.string.ok) { _, _ -> app.database.historyDao().clearAll(); refresh() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
