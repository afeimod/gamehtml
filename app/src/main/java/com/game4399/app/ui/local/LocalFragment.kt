package com.game4399.app.ui.local

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.game4399.app.R
import com.game4399.app.data.LocalItem
import com.game4399.app.data.Prefs
import com.game4399.app.databinding.FragmentLocalBinding
import com.game4399.app.ui.player.LocalPlayerActivity

class LocalFragment : Fragment() {

    private var _b: FragmentLocalBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: LocalAdapter

    private val openFileTree = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {}
        val name = queryName(uri) ?: "File"
        Prefs.addLocal(LocalItem(name, uri.toString(), isDir = false))
        Toast.makeText(requireContext(), R.string.imported, Toast.LENGTH_SHORT).show()
        refresh()
    }

    private val openDirTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {}
        val name = queryName(uri) ?: "Folder"
        Prefs.addLocal(LocalItem(name, uri.toString(), isDir = true))
        Toast.makeText(requireContext(), R.string.imported, Toast.LENGTH_SHORT).show()
        refresh()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentLocalBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = LocalAdapter(
            items = Prefs.locals(),
            onPlay = { startPlay(it) },
            onDelete = { Prefs.removeLocal(it.path); refresh() }
        )
        b.rv.layoutManager = LinearLayoutManager(requireContext())
        b.rv.adapter = adapter

        b.btnAddFile.setOnClickListener {
            openFileTree.launch(arrayOf("*/*", "application/x-shockwave-flash", "application/octet-stream"))
        }
        b.btnAddFolder.setOnClickListener {
            openDirTree.launch(null)
        }
        b.swipe.setOnRefreshListener { refresh(); b.swipe.isRefreshing = false }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun startPlay(item: LocalItem) {
        // 检查文件是否还可达
        try {
            val p = Uri.parse(item.path)
            requireContext().contentResolver.takePersistableUriPermission(p, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {}
        LocalPlayerActivity.open(requireContext(), item)
    }

    private fun queryName(uri: Uri): String? {
        return try {
            requireContext().contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Throwable) { uri.lastPathSegment }
    }

    private fun refresh() {
        val list = Prefs.locals()
        adapter.update(list)
        b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
