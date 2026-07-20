package com.game4399.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.game4399.app.data.GameType
import com.game4399.app.databinding.ActivityMainBinding
import com.game4399.app.ui.HomeFragment
import com.game4399.app.ui.MeFragment
import com.game4399.app.ui.WebFragment
import com.game4399.app.webview.NavHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText

/**
 * 主界面：底部导航 4 Tab（首页 / 游戏 / 分类 / 我的）+ 顶部工具栏。
 *
 * - 首页：4399 入口 + 经典游戏列表
 * - 游戏：WebView 浏览 4399 手机版
 * - 分类：WebView 浏览 4399 全部分类
 * - 我的：收藏 / 历史 / 设置
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment by lazy { HomeFragment() }
    private val gameFragment by lazy { WebFragment.newInstance(NavHelper.URL_4399_MOBILE) }
    private val categoryFragment by lazy { WebFragment.newInstance(NavHelper.URL_4399_CATEGORY) }
    private val meFragment by lazy { MeFragment() }

    private var currentTabId = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.bottomNav.setOnItemSelectedListener(BottomNavigationView.OnNavigationItemSelectedListener {
            switchTab(it.itemId)
            true
        })
        if (savedInstanceState == null) switchTab(R.id.nav_home)
        setupBackHandler()
    }

    /** 返回键：优先让当前 WebView 后退 */
    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val web = currentWebFragment()
                if (web != null && web.canGoBack()) {
                    web.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * 用 show/hide 切换 Fragment，保留各 Tab 的 WebView 状态与浏览历史。
     */
    private fun switchTab(tabId: Int) {
        currentTabId = tabId
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()

        fun obtain(fragment: Fragment, tag: String): Fragment {
            return fm.findFragmentByTag(tag) ?: fragment.also { tx.add(R.id.nav_host_container, it, tag) }
        }
        val target = when (tabId) {
            R.id.nav_home     -> obtain(homeFragment, TAG_HOME)
            R.id.nav_game     -> obtain(gameFragment, TAG_GAME)
            R.id.nav_category -> obtain(categoryFragment, TAG_CATEGORY)
            R.id.nav_me       -> obtain(meFragment, TAG_ME)
            else -> obtain(homeFragment, TAG_HOME)
        }
        // 隐藏其余，显示目标
        listOf(
            TAG_HOME to homeFragment, TAG_GAME to gameFragment,
            TAG_CATEGORY to categoryFragment, TAG_ME to meFragment
        ).forEach { (tag, frag) ->
            val f = fm.findFragmentByTag(tag)
            if (f != null && f !== target) tx.hide(f)
        }
        tx.show(target)
        tx.commitAllowingStateLoss()

        binding.toolbar.title = when (tabId) {
            R.id.nav_home     -> getString(R.string.app_name)
            R.id.nav_game     -> getString(R.string.quick_4399_mobile)
            R.id.nav_category -> getString(R.string.nav_category)
            R.id.nav_me       -> getString(R.string.nav_me)
            else -> getString(R.string.app_name)
        }
    }

    // ---------------- 工具栏菜单 ----------------
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_open_url -> { showOpenUrlDialog(); true }
        R.id.action_favorites -> {
            startActivity(Intent(this, FavoritesActivity::class.java)); true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java)); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** 输入网址 / 游戏 ID 打开 */
    private fun showOpenUrlDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input_url, null)
        val et = view.findViewById<TextInputEditText>(R.id.etInput)
        AlertDialog.Builder(this)
            .setTitle(R.string.enter_game_url)
            .setView(view)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val input = et.text?.toString().orEmpty().trim()
                if (input.isNotEmpty()) {
                    val (url, type) = NavHelper.normalizeInput(input)
                    openInGame(url, "自定义游戏", type)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 由首页快捷入口等调用：直接启动游戏播放器 */
    fun openInGame(url: String, title: String, type: GameType) {
        GameActivity.launch(this, url, title, type)
    }

    /** 当前显示的 WebFragment（用于返回键 WebView 后退） */
    private fun currentWebFragment(): WebFragment? {
        val tag = when (currentTabId) {
            R.id.nav_game -> TAG_GAME
            R.id.nav_category -> TAG_CATEGORY
            else -> return null
        }
        return supportFragmentManager.findFragmentByTag(tag) as? WebFragment
    }

    companion object {
        private const val TAG_HOME = "tag_home"
        private const val TAG_GAME = "tag_game"
        private const val TAG_CATEGORY = "tag_category"
        private const val TAG_ME = "tag_me"
    }
}
