package com.flashbox.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.flashbox.app.data.Defaults
import com.flashbox.app.databinding.ActivityMainBinding
import com.flashbox.app.ui.fav.FavoriteFragment
import com.flashbox.app.ui.history.HistoryFragment
import com.flashbox.app.ui.home.HomeFragment
import com.flashbox.app.ui.local.LocalFragment
import com.flashbox.app.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        seedDefaultsIfNeeded()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.bottomNav.setOnItemSelectedListener { item ->
            val frag: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_local -> LocalFragment()
                R.id.nav_history -> HistoryFragment()
                R.id.nav_fav -> FavoriteFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> HomeFragment()
            }
            supportFragmentManager.beginTransaction().replace(R.id.nav_host, frag).commit()
            true
        }

        // handle tab selection from PlayerActivity (e.g. open history)
        val tab = intent.getStringExtra(EXTRA_TAB)
        if (savedInstanceState == null) {
            val target = when (tab) {
                "local" -> R.id.nav_local
                "history" -> R.id.nav_history
                "fav" -> R.id.nav_fav
                "settings" -> R.id.nav_settings
                else -> R.id.nav_home
            }
            binding.bottomNav.selectedItemId = target
        }
    }

    private fun seedDefaultsIfNeeded() {
        val app = application as FlashBoxApp
        if (app.database.webShortcutDao().defaultsCount() == 0) {
            Defaults.toEntities().forEach { app.database.webShortcutDao().insert(it) }
        }
    }

    companion object {
        const val EXTRA_TAB = "extra_tab"
    }
}
