package com.game4399.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    private var isFullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        // 悬浮退出全屏按钮
        binding.btnFullscreenExit.setOnClickListener {
            val menuItem = binding.toolbar.menu.findItem(R.id.action_fullscreen)
            if (menuItem != null) {
                toggleFullscreen(menuItem)
            } else {
                // 菜单未创建时的兜底
                isFullscreen = false
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                binding.appBar.visibility = View.VISIBLE
                binding.bottomNav.visibility = View.VISIBLE
                binding.fullscreenExitOverlay.visibility = View.GONE
            }
        }

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
        updateFullscreenIcon(menu.findItem(R.id.action_fullscreen))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_fullscreen -> { toggleFullscreen(item); true }
        R.id.action_open_url -> { showOpenUrlDialog(); true }
        R.id.action_favorites -> {
            startActivity(Intent(this, FavoritesActivity::class.java)); true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java)); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** 全屏切换：隐藏/显示系统栏 + 工具栏 + 底部导航 + 悬浮退出按钮 */
    private fun toggleFullscreen(item: MenuItem) {
        isFullscreen = !isFullscreen
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (isFullscreen) {
            // 进入全屏：隐藏系统栏，让内容延伸到刘海屏区域
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // 隐藏工具栏和底部导航，让 WebView 占满全屏
            binding.appBar.visibility = View.GONE
            binding.bottomNav.visibility = View.GONE
            // 显示悬浮退出按钮
            binding.fullscreenExitOverlay.visibility = View.VISIBLE
        } else {
            // 退出全屏：恢复显示
            controller.show(WindowInsetsCompat.Type.systemBars())
            binding.appBar.visibility = View.VISIBLE
            binding.bottomNav.visibility = View.VISIBLE
            binding.fullscreenExitOverlay.visibility = View.GONE
        }
        updateFullscreenIcon(item)
    }

    private fun updateFullscreenIcon(item: MenuItem?) {
        item?.apply {
            if (isFullscreen) {
                setIcon(R.drawable.ic_fullscreen_exit)
                setTitle(R.string.fullscreen_exit)
            } else {
                setIcon(R.drawable.ic_fullscreen)
                setTitle(R.string.fullscreen)
            }
        }
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
