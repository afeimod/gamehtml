package com.flashbox.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.flashbox.app.FlashBoxApp
import com.flashbox.app.R
import com.flashbox.app.databinding.FragmentSettingsBinding
import com.flashbox.app.databinding.ItemSettingsCategoryBinding
import com.flashbox.app.engine.EngineConfig
import com.flashbox.app.engine.EngineType
import com.flashbox.app.player.EngineSettingsDialog
import com.flashbox.app.web.WebMode

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val app get() = requireActivity().application as FlashBoxApp

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildGeneral()
        buildEngine()
        buildVirtualKeys()
        buildPrivacy()
        buildAbout()
    }

    private fun newCategory(title: String): ItemSettingsCategoryBinding {
        val cat = ItemSettingsCategoryBinding.inflate(layoutInflater, binding.settingsContainer, false)
        cat.tvCategory.text = title
        binding.settingsContainer.addView(cat.root)
        return cat
    }

    private fun buildGeneral() {
        val cat = newCategory(getString(R.string.settings_general))

        // Default engine
        addSpinnerRow(cat.categoryContainer, getString(R.string.settings_default_engine),
            EngineType.values().map { it.displayName },
            EngineType.values().indexOf(app.settings.defaultEngine)) { idx ->
            app.settings.defaultEngine = EngineType.values()[idx]
        }
        // Default web mode
        addSpinnerRow(cat.categoryContainer, getString(R.string.nav_mode),
            WebMode.values().map { it.displayName },
            WebMode.values().indexOf(app.settings.defaultWebMode)) { idx ->
            app.settings.defaultWebMode = WebMode.values()[idx]
        }
        // Page zoom
        addSliderRow(cat.categoryContainer, getString(R.string.nav_zoom), 25, 400, app.settings.pageZoom) { p ->
            app.settings.pageZoom = p
        }
        addSwitchRow(cat.categoryContainer, getString(R.string.settings_cache), app.settings.cacheEnabled) { b ->
            app.settings.cacheEnabled = b
        }
        addSwitchRow(cat.categoryContainer, getString(R.string.settings_keep_screen_on), app.settings.keepScreenOn) { b ->
            app.settings.keepScreenOn = b
        }
        addSwitchRow(cat.categoryContainer, getString(R.string.settings_landscape), app.settings.landscapePlay) { b ->
            app.settings.landscapePlay = b
        }
    }

    private fun buildEngine() {
        val cat = newCategory(getString(R.string.settings_engine))
        addButtonRow(cat.categoryContainer, "${getString(R.string.player_engine_ruffle)} ${getString(R.string.settings_engine)}") {
            EngineSettingsDialog.newInstance(EngineType.RUFFLE).show(parentFragmentManager, "r")
        }
        addButtonRow(cat.categoryContainer, "${getString(R.string.player_engine_waflash)} ${getString(R.string.settings_engine)}") {
            EngineSettingsDialog.newInstance(EngineType.WAFLASH).show(parentFragmentManager, "w")
        }
        // Show current quality/aspect summary
        val r = app.settings.engineConfig(EngineType.RUFFLE)
        val w = app.settings.engineConfig(EngineType.WAFLASH)
        addInfoRow(cat.categoryContainer, "Ruffle: 画质=${r.quality} 比例=${r.scale} 渲染=${r.renderer}")
        addInfoRow(cat.categoryContainer, "Waflash: 画质=${w.quality} 比例=${w.scale}")
    }

    private fun buildVirtualKeys() {
        val cat = newCategory(getString(R.string.nav_virtual_keys))
        addSwitchRow(cat.categoryContainer, getString(R.string.nav_virtual_keys), app.settings.vkEnabled) { b ->
            app.settings.vkEnabled = b
        }
        addSwitchRow(cat.categoryContainer, getString(R.string.settings_show_keys_online), app.settings.showKeysOnline) { b ->
            app.settings.showKeysOnline = b
        }
        addInfoRow(cat.categoryContainer, "默认：摇杆 + WASD，独立按键 J/K/L/U/I/O/Enter/Space")
        addInfoRow(cat.categoryContainer, "在播放页悬浮菜单内可切换摇杆/方向键、增删按键、缩放移动")
    }

    private fun buildPrivacy() {
        val cat = newCategory(getString(R.string.settings_adblock))
        addSwitchRow(cat.categoryContainer, getString(R.string.settings_adblock), app.settings.adblockEnabled) { b ->
            app.settings.adblockEnabled = b
        }
        addButtonRow(cat.categoryContainer, getString(R.string.settings_clear_cache)) {
            clearCache()
        }
        addButtonRow(cat.categoryContainer, getString(R.string.settings_clear_history)) {
            app.database.historyDao().clearAll()
            snack(getString(R.string.settings_clear_history))
        }
    }

    private fun buildAbout() {
        val cat = newCategory(getString(R.string.settings_about))
        addInfoRow(cat.categoryContainer, "${getString(R.string.app_name)} (FlashBox)")
        addInfoRow(cat.categoryContainer, "${getString(R.string.settings_version)} 1.0.0")
        addInfoRow(cat.categoryContainer, "引擎：Ruffle + Waflash 双引擎")
        addButtonRow(cat.categoryContainer, "GitHub 项目") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/")))
        }
    }

    private fun clearCache() {
        try {
            requireContext().cacheDir.deleteRecursively()
            val webCache = android.webkit.WebViewProvider::class.java
            // Android WebView manages its own cache; trigger clear
            android.webkit.WebStorage.getInstance().deleteAllData()
            snack("缓存已清除")
        } catch (e: Exception) {
            snack("清除失败")
        }
    }

    private fun snack(msg: String) {
        view?.let { com.google.android.material.snackbar.Snackbar.make(it, msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show() }
    }

    // ---- Row builders ----
    private fun addSwitchRow(container: LinearLayout, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = com.google.android.material.switchmaterial.SwitchMaterial(requireContext()).apply {
            text = title
            isChecked = checked
            setTextColor(requireContext().resources.getColor(R.color.text_primary, null))
            setOnCheckedChangeListener { _, b -> onChange(b) }
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_s)
            layoutParams = p
        }
        container.addView(row)
    }

    private fun addButtonRow(container: LinearLayout, title: String, onClick: () -> Unit) {
        val btn = com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = title
            style = com.google.android.material.button.MaterialButton.STYLE_TEXT
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            setOnClickListener { onClick() }
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_xs)
            layoutParams = p
        }
        container.addView(btn)
    }

    private fun addInfoRow(container: LinearLayout, text: String) {
        val tv = android.widget.TextView(requireContext()).apply {
            this.text = text
            setTextColor(requireContext().resources.getColor(R.color.text_secondary, null))
            textSize = 13f
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_xs)
            layoutParams = p
        }
        container.addView(tv)
    }

    private fun addSpinnerRow(container: LinearLayout, title: String, options: List<String>, selected: Int, onChange: (Int) -> Unit) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_s)
            layoutParams = p
        }
        val tv = android.widget.TextView(requireContext()).apply {
            text = title
            setTextColor(requireContext().resources.getColor(R.color.text_primary, null))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val spinner = android.widget.Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
            setSelection(selected.coerceAtLeast(0))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { onChange(pos) }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        row.addView(tv); row.addView(spinner)
        container.addView(row)
    }

    private fun addSliderRow(container: LinearLayout, title: String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit) {
        val tv = android.widget.TextView(requireContext()).apply {
            text = "$title: $value%"
            setTextColor(requireContext().resources.getColor(R.color.text_primary, null))
        }
        val seek = android.widget.SeekBar(requireContext()).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p + min
                    tv.text = "$title: $v%"
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        }
        container.addView(tv)
        container.addView(seek)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
