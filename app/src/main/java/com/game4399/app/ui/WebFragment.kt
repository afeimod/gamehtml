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
        override fun onShowFullscreen(view: View, callback: android.webkit.WebChromeClient.CustomViewCallback) {}
        override fun onHideFullscreen() {}
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
            return url.contains("4399.com") && (url.contains("/flash/") || url.contains(".swf"))
        }
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
