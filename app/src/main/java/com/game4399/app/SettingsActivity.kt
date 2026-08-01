package com.game4399.app

import android.os.Bundle
import android.webkit.WebStorage
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.game4399.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.title_settings)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            // 清除缓存
            findPreference<androidx.preference.Preference>("clear_cache")?.setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dialog_clear_cache)
                    .setMessage(R.string.dialog_clear_cache_msg)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        WebStorage.getInstance().deleteAllData()
                        android.webkit.CookieManager.getInstance().removeAllCookies(null)
                        android.widget.Toast.makeText(
                            requireContext(), R.string.cache_cleared, android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
            // 关于版本
            findPreference<androidx.preference.Preference>("about_version")?.summary =
                BuildConfig.VERSION_NAME
        }
    }
}
