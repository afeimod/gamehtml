package com.flashbox.app.ui.local

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.flashbox.app.FlashBoxApp
import com.flashbox.app.R
import com.flashbox.app.data.LocalFileEntity
import com.flashbox.app.databinding.FragmentLocalBinding
import com.flashbox.app.player.PlayerActivity
import java.io.File

class LocalFragment : Fragment() {

    private var _binding: FragmentLocalBinding? = null
    private val binding get() = _binding!!
    private val app get() = requireActivity().application as FlashBoxApp
    private lateinit var adapter: LocalFileAdapter
    private var pendingAction: (() -> Unit)? = null

    private val pickSwf = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        uris.forEach { uri ->
            val name = queryName(uri) ?: "game.swf"
            // persist permission so we can read later
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val path = uri.toString()
            if (app.database.localFileDao().exists(path) == 0) {
                app.database.localFileDao().insert(LocalFileEntity(
                    name = name, path = path, isFolder = false,
                    addedAt = System.currentTimeMillis()
                ))
            }
        }
        refresh()
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        // Add the folder entry, then scan it for swf files (immediate refresh)
        val folderName = queryName(uri) ?: "文件夹"
        if (app.database.localFileDao().exists(uri.toString()) == 0) {
            app.database.localFileDao().insert(LocalFileEntity(
                name = folderName, path = uri.toString(), isFolder = true,
                addedAt = System.currentTimeMillis()
            ))
        }
        scanFolder(uri)
        refresh()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLocalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = LocalFileAdapter(
            onPlay = { item -> launchLocal(item) },
            onDelete = { item -> app.database.localFileDao().delete(item.id); refresh() }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnAddFile.setOnClickListener { ensureStoragePermission { pickSwf.launch(arrayOf("*/*")) } }
        binding.btnAddFolder.setOnClickListener { ensureStoragePermission { pickFolder.launch(null) } }

        refresh()
    }

    private fun launchLocal(item: LocalFileEntity) {
        if (item.isFolder) return
        if (item.path.startsWith("content://") || item.path.startsWith("file:///")) {
            PlayerActivity.launchLocalUri(requireContext(), Uri.parse(item.path), item.name)
        } else {
            PlayerActivity.launchLocalPath(requireContext(), item.path, item.name)
        }
    }

    private fun scanFolder(uri: Uri) {
        val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(requireContext(), uri)
        docFile?.listFiles()?.forEach { f ->
            if (!f.isDirectory && f.name?.lowercase()?.endsWith(".swf") == true) {
                val path = f.uri.toString()
                if (app.database.localFileDao().exists(path) == 0) {
                    app.database.localFileDao().insert(LocalFileEntity(
                        name = f.name ?: "game.swf",
                        path = path, isFolder = false,
                        addedAt = System.currentTimeMillis()
                    ))
                }
            }
        }
    }

    private fun refresh() {
        val list = app.database.localFileDao().getAll()
        adapter.submitList(list)
        binding.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun ensureStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                action()
            } else {
                pendingAction = action
                AlertDialog.Builder(requireContext(), R.style.Theme_FlashBox_Dialog)
                    .setTitle(R.string.nav_local)
                    .setMessage("需要所有文件访问权限以读取本地 SWF 文件，请在设置中开启后返回")
                    .setPositiveButton(R.string.ok) { _, _ ->
                        openAllFilesAccessSettings()
                    }
                    .setNegativeButton(R.string.cancel) { _, _ -> pendingAction = null }
                    .setCancelable(false)
                    .show()
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
                action()
            } else {
                pendingAction = action
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1001)
            }
        }
    }

    private fun openAllFilesAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: try the general all-files-access settings page
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e2: Exception) {
                // Last resort: open app details settings
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                    startActivity(intent)
                } catch (e3: Exception) {
                    android.widget.Toast.makeText(
                        requireContext(), "无法打开权限设置，请手动前往设置开启", android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // After returning from the all-files-access settings page, execute pending action
        if (pendingAction != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                val action = pendingAction
                pendingAction = null
                action?.invoke()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            val action = pendingAction
            pendingAction = null
            action?.invoke()
        }
    }

    private fun queryName(uri: Uri): String? {
        var name: String? = null
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = c.getString(idx)
            }
        }
        return name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
