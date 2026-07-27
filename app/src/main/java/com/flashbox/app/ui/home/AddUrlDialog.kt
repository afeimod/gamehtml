package com.flashbox.app.ui.home

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.flashbox.app.R
import com.flashbox.app.data.WebShortcutEntity
import com.flashbox.app.databinding.DialogAddUrlBinding
import com.flashbox.app.web.WebMode

class AddUrlDialog(
    private val edit: WebShortcutEntity? = null,
    private val onSave: (title: String, url: String, mode: WebMode) -> Unit
) : DialogFragment() {

    private var mode: WebMode = WebMode.fromId(edit?.mode)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogAddUrlBinding.inflate(layoutInflater)
        binding.etTitle.setText(edit?.title ?: "")
        binding.etUrl.setText(edit?.url ?: "https://")

        val modes = WebMode.values()
        val labels = modes.map { it.displayName }.toTypedArray()
        val checked = modes.indexOf(mode).coerceAtLeast(0)

        val view = binding.root
        return AlertDialog.Builder(requireContext(), R.style.Theme_FlashBox_Dialog)
            .setTitle(if (edit == null) R.string.home_add_url else R.string.edit)
            .setView(view)
            .setSingleChoiceItems(labels, checked) { _, which -> mode = modes[which] }
            .setPositiveButton(R.string.save) { _, _ ->
                val t = binding.etTitle.text.toString().trim().ifBlank { binding.etUrl.text.toString().trim() }
                val u = binding.etUrl.text.toString().trim()
                if (u.isNotBlank()) onSave(t, u, mode)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }
}
