package com.game4399.app.ui.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.game4399.app.ui.browser.BrowserActivity

/**
 * 外部应用通过 ACTION_VIEW 唤起我们的入口。
 * 简单转发到 BrowserActivity 加载传入的 url。
 */
class WebPlayerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent.data
        if (uri == null) {
            finish()
            return
        }
        val i = Intent(this, BrowserActivity::class.java).apply {
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(i)
        finish()
    }
}
