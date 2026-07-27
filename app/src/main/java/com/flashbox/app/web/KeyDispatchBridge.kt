package com.flashbox.app.web

/** Bridge for dispatching synthetic keyboard events into the WebView content. */
interface KeyDispatchBridge {
    fun sendKeyDown(key: String, code: Int)
    fun sendKeyUp(key: String, code: Int)
}
