package com.game4399.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.game4399.app.R
import com.game4399.app.data.Prefs
import com.game4399.app.databinding.ActivityMainBinding
import com.game4399.app.ui.browser.BrowserActivity
import com.game4399.app.ui.browser.BrowserEntryFragment
import com.game4399.app.ui.local.LocalFragment
import com.game4399.app.ui.settings.SettingsFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 主题
        when (Prefs.darkMode()) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        b.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_pc -> {
                    Prefs.setUiMode("pc")
                    b.pager.setCurrentItem(0, false)
                    BrowserActivity.openHome(this, true)
                }
                R.id.action_mobile -> {
                    Prefs.setUiMode("mobile")
                    b.pager.setCurrentItem(0, false)
                    BrowserActivity.openHome(this, false)
                }
                R.id.action_add_url -> showAddUrlDialog()
            }
            true
        }

        b.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> BrowserEntryFragment()
                1 -> LocalFragment()
                else -> SettingsFragment()
            }
        }
        b.pager.isUserInputEnabled = true
        b.pager.offscreenPageLimit = 2

        TabLayoutMediator(b.tabs, b.pager) { tab, pos ->
            when (pos) {
                0 -> { tab.text = getString(R.string.tab_browser); tab.setIcon(R.drawable.ic_browser) }
                1 -> { tab.text = getString(R.string.tab_local); tab.setIcon(R.drawable.ic_game) }
                2 -> { tab.text = getString(R.string.tab_settings); tab.setIcon(R.drawable.ic_settings) }
            }
        }.attach()
        b.tabs.tabMode = TabLayout.MODE_FIXED

        // 跳到指定 tab
        val tab = intent.getIntExtra("tab", -1)
        if (tab in 0..2) b.pager.setCurrentItem(tab, false)

        // 第一次启动？提示引擎选择
        if (!Prefs.engineChosenBefore()) {
            showEnginePicker()
        }
    }

    private fun showAddUrlDialog() {
        val name = EditText(this).apply { hint = getString(R.string.enter_name) }
        val url = EditText(this).apply {
            hint = getString(R.string.enter_url)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(name)
            addView(url)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_url)
            .setView(layout)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add) { _, _ ->
                val u = url.text.toString().trim()
                if (u.isEmpty()) {
                    Toast.makeText(this, R.string.enter_url, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val finalUrl = if (u.startsWith("http", true) || u.startsWith("file", true)) u
                else "https://$u"
                val n = name.text.toString().ifEmpty { finalUrl }
                com.game4399.app.data.Prefs.addFavorite(com.game4399.app.data.HistoryItem(n, finalUrl))
                BrowserActivity.openUrl(this, finalUrl, n)
            }
            .show()
    }

    private fun showEnginePicker() {
        val engines = arrayOf(
            getString(R.string.engine_ruffle),
            getString(R.string.engine_waflash)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.engine_select)
            .setMessage(R.string.engine_warning_message)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ ->
                Prefs.markEngineChosen()
            }
            .setSingleChoiceItems(engines, if (Prefs.engine() == "ruffle") 0 else 1) { _, which ->
                Prefs.setEngine(if (which == 0) "ruffle" else "waflash")
            }
            .show()
    }
}
