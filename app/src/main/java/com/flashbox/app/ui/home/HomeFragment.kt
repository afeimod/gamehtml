package com.flashbox.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.flashbox.app.FlashBoxApp
import com.flashbox.app.R
import com.flashbox.app.data.WebShortcutEntity
import com.flashbox.app.databinding.FragmentHomeBinding
import com.flashbox.app.player.PlayerActivity
import com.flashbox.app.web.WebMode

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val app get() = requireActivity().application as FlashBoxApp
    private lateinit var adapter: WebShortcutAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = WebShortcutAdapter(
            onClick = { item ->
                PlayerActivity.launchUrl(requireContext(), item.url, item.mode)
            },
            onLongClick = { item -> showItemMenu(item) }
        )
        binding.recycler.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recycler.adapter = adapter

        binding.btnGo.setOnClickListener {
            val text = binding.urlInput.text.toString().trim()
            if (text.isNotEmpty()) PlayerActivity.launchUrl(requireContext(), text)
        }
        binding.urlInput.setOnEditorActionListener { v, _, _ ->
            val text = v.text.toString().trim()
            if (text.isNotEmpty()) PlayerActivity.launchUrl(requireContext(), text)
            true
        }
        binding.btnAddUrl.setOnClickListener { showAddUrlDialog() }
        binding.refresh.isEnabled = false

        loadData()
    }

    private fun loadData() {
        val list = app.database.webShortcutDao().getAll()
        adapter.submitList(list)
    }

    private fun showItemMenu(item: WebShortcutEntity) {
        val opts = arrayOf(getString(R.string.action_open), getString(R.string.edit), getString(R.string.action_delete))
        AlertDialog.Builder(requireContext(), R.style.Theme_FlashBox_Dialog)
            .setTitle(item.title)
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> PlayerActivity.launchUrl(requireContext(), item.url, item.mode)
                    1 -> showAddUrlDialog(item)
                    2 -> { app.database.webShortcutDao().delete(item.id); loadData() }
                }
            }.show()
    }

    private fun showAddUrlDialog(edit: WebShortcutEntity? = null) {
        AddUrlDialog(edit) { title, url, mode ->
            val entity = WebShortcutEntity(
                id = edit?.id ?: 0,
                title = title,
                url = url,
                mode = mode.id,
                isDefault = edit?.isDefault ?: false,
                sortOrder = edit?.sortOrder ?: (app.database.webShortcutDao().getAll().size)
            )
            app.database.webShortcutDao().insert(entity)
            loadData()
        }.show(parentFragmentManager, "add_url")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
