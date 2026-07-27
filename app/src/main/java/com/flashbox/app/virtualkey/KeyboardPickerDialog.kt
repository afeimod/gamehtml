package com.flashbox.app.virtualkey

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.flashbox.app.R
import com.google.android.material.button.MaterialButton

/**
 * A keyboard-model picker dialog. Renders a visual keyboard and lets the user
 * tap any key to add it as an independent button.
 */
class KeyboardPickerDialog : DialogFragment() {

    private var onPicked: ((KeyDef) -> Unit)? = null
    private val existing = mutableSetOf<Int>() // already-added keyCodes

    fun setOnPicked(callback: (KeyDef) -> Unit, existingCodes: Set<Int>) {
        onPicked = callback
        existing.clear()
        existing.addAll(existingCodes)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_keyboard_picker, null)
        val container = view.findViewById<LinearLayout>(R.id.keyboard_container)
        val density = resources.displayMetrics.density
        val hMargin = (4 * density).toInt()
        val vMargin = (4 * density).toInt()

        KeyboardModel.rows().forEach { row ->
            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            row.keys.forEach { key ->
                val btn = MaterialButton(requireContext()).apply {
                    text = key.label
                    isCheckable = false
                    cornerRadius = (8 * density).toInt()
                    elevation = 0f
                    layoutParams = LinearLayout.LayoutParams(0, (44 * density).toInt(), 1f).apply {
                        setMargins(hMargin, vMargin, hMargin, vMargin)
                    }
                    if (existing.contains(key.keyCode)) {
                        alpha = 0.4f
                        isEnabled = false
                    }
                    setOnClickListener {
                        onPicked?.invoke(key)
                        dismiss()
                    }
                }
                rowLayout.addView(btn)
            }
            container.addView(rowLayout)
        }

        return AlertDialog.Builder(requireContext(), R.style.Theme_FlashBox_Dialog)
            .setTitle(R.string.vk_pick_key)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .create()
    }
}
