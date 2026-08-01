package com.game4399.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.game4399.app.GameActivity
import com.game4399.app.R
import com.game4399.app.data.GameType
import com.game4399.app.data.PrefsManager
import com.game4399.app.databinding.FragmentWebBinding
import com.game4399.app.webview.GameWebChromeClient
import com.game4399.app.webview.GameWebView
import com.game4399.app.webview.GameWebViewClient
import com.game4399.app.webview.NavHelper
import com.game4399.app.webview.WebAppInterface

/**
 * 浏览器式 WebView Fragment：用于"游戏"和"分类"Tab。
 *
 * 行为：
 *  - 默认加载 [defaultUrl]
 *  - 下拉刷新、进度条、错误重试
 *  - 自动注入 Ruffle（4399 PC Flash 页）
 *  - 点击 4399 游戏 URL（flash/{id}.htm / play/{id}.htm / .swf）→ 启动 GameActivity
 *    以获得虚拟手柄 + 物理键盘的完整游戏体验
 */
class WebFragment : Fragment() {

    private var _binding: FragmentWebBinding? = null
    private val binding get() = _binding!!

    private lateinit var webView: GameWebView
    private var defaultUrl: String = NavHelper.URL_4399_MOBILE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        defaultUrl = arguments?.getString(ARG_URL) ?: NavHelper.URL_4399_MOBILE
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webView = binding.webView
        webView.apply {
            addJavascriptInterface(WebAppInterface(requireContext()), "Android")
            webChromeClient = object : GameWebChromeClient(chromeCb) {}
            webViewClient = object : GameWebViewClient(viewCb) {}
        }
        // 注入 Document Start 脚本（在页面 JS 之前执行 View Transitions polyfill 等）
        webView.injectDocumentStartScripts()

        // 取消下拉刷新，避免游戏操作误触发
        binding.swipeRefresh.isEnabled = false
        binding.btnRetry.setOnClickListener { webView.reload() }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(defaultUrl)
        }
    }

    private val chromeCb = object : GameWebChromeClient.Callback {
        override fun onProgress(progress: Int) {
            binding.progressBar.apply {
                visibility = if (progress in 1..99) View.VISIBLE else View.GONE
                this.progress = progress
            }
            if (progress == 100) binding.swipeRefresh.isRefreshing = false
        }
        override fun onTitle(title: String?) {}
        override fun onConsole(level: String, msg: String, sourceId: String?, line: Int) {}
        override fun onShowFullscreen(view: View, callback: android.webkit.WebChromeClient.CustomViewCallback) {
            // 网页全屏：隐藏 WebView，将全屏 View 添加到容器
            binding.webView.visibility = View.GONE
            binding.progressBar.visibility = View.GONE
            binding.fullscreenContainer.removeAllViews()
            binding.fullscreenContainer.addView(view)
            binding.fullscreenContainer.visibility = View.VISIBLE
        }
        override fun onHideFullscreen() {
            // 退出全屏：移除全屏 View，恢复 WebView
            binding.fullscreenContainer.removeAllViews()
            binding.fullscreenContainer.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
        }
        override fun onFileChooser(
            cb: android.webkit.ValueCallback<Array<android.net.Uri>>, accept: String?
        ): Boolean { cb.onReceiveValue(null); return true }
    }

    private val viewCb = object : GameWebViewClient.Callback {
        override fun onPageStarted(url: String?) {
            binding.errorView.visibility = View.GONE
        }
        override fun onPageFinished(url: String?) {
            binding.swipeRefresh.isRefreshing = false
        }
        override fun onProgress(progress: Int) = chromeCb.onProgress(progress)
        override fun onError(url: String?, errorCode: Int, description: String?) {
            // 忽略内置播放器页面的错误和跳转过程中的临时错误
            if (url != null && url.startsWith("file:///android_asset/")) return
            if (errorCode == -1) return
            binding.swipeRefresh.isRefreshing = false
            binding.errorView.visibility = View.VISIBLE
        }
        override fun onSwfIntercepted(swfUrl: String, pageUrl: String) {
            // 直接启动游戏播放器
            GameActivity.launch(requireContext(), swfUrl, "Flash 游戏", GameType.URL)
        }
        override fun shouldInjectRuffle(url: String?): Boolean {
            if (url == null) return false
            if (url.startsWith("file:///android_asset/")) return false
            if (url.startsWith("https://flash.local/")) return false
            // 排除登录/账号/API 接口（避免拦截 POST 请求导致无法登录）
            val lowerUrl = url.lowercase()
            if (lowerUrl.contains("/login") || lowerUrl.contains("/signin") ||
                lowerUrl.contains("/register") || lowerUrl.contains("/signup") ||
                lowerUrl.contains("/api/") || lowerUrl.contains("/ajax/") ||
                lowerUrl.contains("/account") || lowerUrl.contains("/user/") ||
                lowerUrl.contains("/passport") || lowerUrl.contains("/auth") ||
                lowerUrl.contains("/logout") || lowerUrl.contains("/sso")) {
                return false
            }
            // Flash 开启时，对所有网页注入 Flash 支持（不限 4399）
            return PrefsManager.isFlashEnabled
        }
        override fun getCachedSwfPath(): String? = null
        override fun getLocalSwfUri(): String? = null
    }

    /**
     * 拦截 4399 游戏 URL，启动 GameActivity。
     * 在 WebViewClient.shouldOverrideUrlLoading 之外，这里用 HitTestResult 兜底。
     */
    /** 当前 WebView 是否可后退 */
    fun canGoBack(): Boolean = ::webView.isInitialized && webView.canGoBack()

    /** WebView 后退一步 */
    fun goBack() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroyView() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_URL = "arg_url"
        fun newInstance(url: String): WebFragment = WebFragment().apply {
            arguments = Bundle().apply { putString(ARG_URL, url) }
        }
    }
}
