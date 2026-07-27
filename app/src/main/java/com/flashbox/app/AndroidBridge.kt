package com.flashbox.app

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * JS <-> Native bridge. Exposed to the WebView as `window.Android`.
 * All collection getters return JSON strings; persistence accepts JSON strings.
 * UI-triggering calls (pickers, navigation, zoom) are forwarded to the host
 * activity which runs them on the main thread.
 */
class AndroidBridge(private val host: Host) {

    interface Host {
        fun runOnUi(r: Runnable)
        fun pickFile()
        fun pickFolder()
        fun openUrl(url: String, mode: String)
        fun goHome()
        fun goBack(): Boolean
        fun setZoom(percent: Int)
        fun setPageMode(mode: String)
        fun injectEngineNow()
        fun toast(msg: String)
        fun openExternal(url: String)
        fun shareUrl(url: String)
        fun vibrate(ms: Long)
        fun keepScreenOn(on: Boolean)
        fun appInfo(): String
    }

    @JavascriptInterface fun getSettings(): String = Store.getSettings()
    @JavascriptInterface fun saveSettings(json: String) = Store.saveSettings(json)
    @JavascriptInterface fun getEngineConfigs(): String = Store.getEngineConfigs()
    @JavascriptInterface fun saveEngineConfigs(json: String) = Store.saveEngineConfigs(json)
    @JavascriptInterface fun getControls(): String = Store.getControls()
    @JavascriptInterface fun saveControls(json: String) = Store.saveControls(json)

    @JavascriptInterface fun getLibrary(): String = Store.getLibrary()
    @JavascriptInterface fun removeLibraryItem(id: String): String = Store.removeLibraryItem(id).toString()
    @JavascriptInterface fun clearLibrary() = Store.clearLibrary()

    @JavascriptInterface fun getHistory(): String = Store.getHistory()
    @JavascriptInterface fun addHistory(json: String): String = Store.addHistory(JSONObject(json)).toString()
    @JavascriptInterface fun removeHistory(id: String): String = Store.removeHistory(id).toString()
    @JavascriptInterface fun clearHistory() = Store.clearHistory()

    @JavascriptInterface fun getFavorites(): String = Store.getFavorites()
    @JavascriptInterface fun toggleFavorite(json: String): Boolean = Store.toggleFavorite(JSONObject(json))
    @JavascriptInterface fun removeFavorite(id: String): String = Store.removeFavorite(id).toString()

    @JavascriptInterface fun getSites(): String = Store.getSites()
    @JavascriptInterface fun addSite(json: String): String = Store.addSite(JSONObject(json)).toString()
    @JavascriptInterface fun removeSite(id: String): String = Store.removeSite(id).toString()

    @JavascriptInterface fun pickFile() = host.runOnUi(Runnable { host.pickFile() })
    @JavascriptInterface fun pickFolder() = host.runOnUi(Runnable { host.pickFolder() })
    @JavascriptInterface fun openUrl(url: String, mode: String) = host.runOnUi(Runnable { host.openUrl(url, mode) })
    @JavascriptInterface fun goHome() = host.runOnUi(Runnable { host.goHome() })
    @JavascriptInterface fun goBack(): Boolean = host.goBack()
    @JavascriptInterface fun setZoom(percent: Int) = host.runOnUi(Runnable { host.setZoom(percent) })
    @JavascriptInterface fun setPageMode(mode: String) = host.runOnUi(Runnable { host.setPageMode(mode) })
    @JavascriptInterface fun injectEngineNow() = host.runOnUi(Runnable { host.injectEngineNow() })

    @JavascriptInterface fun toast(msg: String) = host.runOnUi(Runnable { host.toast(msg) })
    @JavascriptInterface fun openExternal(url: String) = host.runOnUi(Runnable { host.openExternal(url) })
    @JavascriptInterface fun shareUrl(url: String) = host.runOnUi(Runnable { host.shareUrl(url) })
    @JavascriptInterface fun vibrate(ms: Long) = host.vibrate(ms)
    @JavascriptInterface fun keepScreenOn(on: Boolean) = host.runOnUi(Runnable { host.keepScreenOn(on) })
    @JavascriptInterface fun appInfo(): String = host.appInfo()
}
