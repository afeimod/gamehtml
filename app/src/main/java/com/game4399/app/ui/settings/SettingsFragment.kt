package com.game4399.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.game4399.app.BuildConfig
import com.game4399.app.R
import com.game4399.app.data.Prefs
import com.game4399.app.databinding.FragmentSettingsBinding
import com.game4399.app.ui.browser.BrowserActivity

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    private lateinit var root: LinearLayout
    private val rows = mutableMapOf<String, TextView>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        root = b.root

        section("外观")
        row("theme", "主题", "跟随系统", ::pickTheme)
        row("home", "默认主页", "电脑版/手机版", ::setHomepage)
        row("zoom", "页面缩放", "100%", ::setZoom)

        section("引擎")
        row("engine", "Flash 引擎", "Ruffle", ::enginePicker)
        row("quality", "画质", "高", ::pickQuality)
        row("aspect", "画面比例", "保持比例", ::pickAspect)

        section("虚拟按键")
        row("padType", "移动方式", "摇杆", ::pickPadType)
        row("keyMode", "按键映射", "WASD", ::pickKeyMode)

        section("浏览器")
        addAdBlockRow()
        row("cache", "缓存", "默认", ::pickCache)
        row("ua", "User Agent", "系统默认", ::pickUA)

        section("其他")
        row("padTest", "虚拟按键测试", "→", ::openPadTest)
        row("clearCache", "清除缓存", "", { clearCache(); true })
        row("clearHistory", "清除历史", "", { Prefs.clearHistory(); refresh(); Toast.makeText(requireContext(), "已清除", Toast.LENGTH_SHORT).show(); true })
        row("clearAll", "清除所有数据", "", ::clearAll)
        row("about", "关于", "v${BuildConfig.VERSION_NAME}", ::showAbout)

        addVersionFooter()
        refresh()
    }

    private fun section(title: String) {
        val tv = TextView(requireContext()).apply {
            text = title
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 13f
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.topMargin = dp(12)
            p.bottomMargin = dp(6)
            layoutParams = p
        }
        root.addView(tv)
    }

    private fun row(key: String, name: String, defValue: String, onClick: () -> Boolean) {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.rounded_card)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.bottomMargin = dp(6)
            layoutParams = p
            isClickable = true
            isFocusable = true
        }
        val nameTv = TextView(requireContext()).apply {
            text = name
            setTextColor(resources.getColor(R.color.text_primary, null))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valueTv = TextView(requireContext()).apply {
            text = defValue
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 13f
        }
        card.addView(nameTv)
        card.addView(valueTv)
        card.setOnClickListener { onClick() }
        root.addView(card)
        rows[key] = valueTv
    }

    private fun addAdBlockRow() {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.rounded_card)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.bottomMargin = dp(6)
            layoutParams = p
        }
        val nameTv = TextView(requireContext()).apply {
            text = getString(R.string.ad_block)
            setTextColor(resources.getColor(R.color.text_primary, null))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = com.google.android.material.switchmaterial.SwitchMaterial(requireContext()).apply {
            isChecked = Prefs.adBlock()
            setOnCheckedChangeListener { _, v -> Prefs.setAdBlock(v); refresh() }
        }
        card.addView(nameTv)
        card.addView(sw)
        root.addView(card)
    }

    private fun addVersionFooter() {
        val tv = TextView(requireContext()).apply {
            text = "v${BuildConfig.VERSION_NAME}"
            setTextColor(resources.getColor(R.color.text_secondary, null))
            gravity = Gravity.CENTER
            textSize = 12f
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.topMargin = dp(16)
            p.bottomMargin = dp(16)
            layoutParams = p
        }
        root.addView(tv)
    }

    private fun refresh() {
        rows["theme"]?.text = when (Prefs.darkMode()) { "light" -> "浅色"; "dark" -> "深色"; else -> "跟随系统" }
        rows["home"]?.text = if (Prefs.uiMode() == "pc") "电脑版" else "手机版"
        rows["zoom"]?.text = "${(Prefs.pageZoom() * 100).toInt()}%"
        rows["engine"]?.text = if (Prefs.engine() == "ruffle") "Ruffle" else "Waflash"
        rows["quality"]?.text = when (Prefs.quality()) { "low" -> "低"; "medium" -> "中"; "high" -> "高"; "best" -> "最高"; else -> Prefs.quality() }
        rows["aspect"]?.text = when (Prefs.aspect()) { "fit" -> "保持比例"; "fill" -> "铺满"; "stretch" -> "拉伸"; else -> Prefs.aspect() }
        rows["padType"]?.text = if (Prefs.padType() == "joystick") "摇杆" else "方向键"
        rows["keyMode"]?.text = if (Prefs.padKeyMode() == "wsad") "WASD" else "上下左右"
        rows["cache"]?.text = when (Prefs.cacheMode()) { "always" -> "优先缓存"; "no" -> "无缓存"; else -> "默认" }
        rows["ua"]?.text = when (Prefs.userAgent()) { "pc" -> "桌面"; "mobile" -> "手机"; else -> "系统默认" }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun pickTheme(): Boolean {
        val labels = arrayOf("跟随系统", "浅色", "深色")
        val vals = arrayOf("system", "light", "dark")
        AlertDialog.Builder(requireContext())
            .setTitle("主题")
            .setSingleChoiceItems(labels, vals.indexOf(Prefs.darkMode()).coerceAtLeast(0)) { d, w ->
                Prefs.setDarkMode(vals[w])
                AppCompatDelegate.setDefaultNightMode(
                    when (vals[w]) {
                        "light" -> AppCompatDelegate.MODE_NIGHT_NO
                        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
                refresh(); d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return true
    }

    private fun setHomepage(): Boolean {
        val isPc = Prefs.uiMode() == "pc"
        val et = EditText(requireContext()).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(if (isPc) Prefs.homePc() else Prefs.homeMobile())
        }
        AlertDialog.Builder(requireContext())
            .setTitle(if (isPc) "电脑版主页" else "手机版主页")
            .setView(et)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val u = et.text.toString().trim()
                if (u.isNotEmpty()) { if (isPc) Prefs.setHomePc(u) else Prefs.setHomeMobile(u) }
            }
            .show()
        return true
    }

    private fun setZoom(): Boolean {
        val v = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText((Prefs.pageZoom() * 100).toInt().toString())
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.webpage_zoom)
            .setView(v)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val num = v.text.toString().toIntOrNull()?.coerceIn(50, 200) ?: 100
                Prefs.setPageZoom(num / 100f); refresh()
            }
            .show()
        return true
    }

    private fun pickQuality(): Boolean {
        val opts = arrayOf("low", "medium", "high", "best")
        val labels = arrayOf(getString(R.string.quality_low), getString(R.string.quality_medium), getString(R.string.quality_high), getString(R.string.quality_best))
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.quality)
            .setSingleChoiceItems(labels, opts.indexOf(Prefs.quality()).coerceAtLeast(0)) { d, w ->
                Prefs.setQuality(opts[w]); refresh(); d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return true
    }

    private fun pickAspect(): Boolean {
        val opts = arrayOf("fit", "fill", "stretch")
        val labels = arrayOf(getString(R.string.aspect_fit), getString(R.string.aspect_fill), getString(R.string.aspect_stretch))
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.aspect_ratio)
            .setSingleChoiceItems(labels, opts.indexOf(Prefs.aspect()).coerceAtLeast(0)) { d, w ->
                Prefs.setAspect(opts[w]); refresh(); d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return true
    }

    private fun pickPadType(): Boolean {
        val opts = arrayOf("joystick", "dpad")
        val labels = arrayOf(getString(R.string.joystick_label), getString(R.string.dpad_label))
        AlertDialog.Builder(requireContext())
            .setTitle("移动方式")
            .setSingleChoiceItems(labels, opts.indexOf(Prefs.padType()).coerceAtLeast(0)) { d, w ->
                Prefs.setPadType(opts[w]); refresh(); d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return true
    }

    private fun pickKeyMode(): Boolean {
        val opts = arrayOf("wsad", "arrows")
        val labels = arrayOf("WASD", "上下左右")
        AlertDialog.Builder(requireContext())
            .setTitle("按键映射")
            .setSingleChoiceItems(labels, opts.indexOf(Prefs.padKeyMode()).coerceAtLeast(0)) { d, w ->
                Prefs.setPadKeyMode(opts[w]); refresh(); d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return true
    }

    private fun pickCache(): Boolean {
        val opts = arrayOf("default", "always", "no")
        val labels = arrayOf("默认", "优先缓存", "无缓存")
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_cache)
            .setSingleChoiceItems(labels, opts.indexOf(Prefs.cacheMode()).coerceAtLeast(0)) { d, w ->
                Prefs.setCacheMode(opts[w]); refresh(); d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return true
    }

    private fun pickUA(): Boolean {
        val opts = arrayOf("system", "pc", "mobile")
        val labels = arrayOf("系统默认", "桌面", "手机")
        AlertDialog.Builder(requireContext())
            .setTitle("User Agent")
            .setSingleChoiceItems(labels, opts.indexOf(Prefs.userAgent()).coerceAtLeast(0)) { d, w ->
                Prefs.setUserAgent(opts[w]); refresh(); d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return true
    }

    private fun enginePicker(): Boolean {
        val labels = arrayOf(getString(R.string.engine_ruffle), getString(R.string.engine_waflash))
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.engine_select)
            .setSingleChoiceItems(labels, if (Prefs.engine() == "ruffle") 0 else 1) { d, w ->
                Prefs.setEngine(if (w == 0) "ruffle" else "waflash"); refresh(); d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return true
    }

    private fun openPadTest(): Boolean {
        // 加载 assets 里的 ruffle_player.html 跑个空白 swf 测试
        val url = "file:///android_asset/web/ruffle_player.html?src=swf/empty.swf"
        val i = Intent(requireContext(), BrowserActivity::class.java)
        i.data = android.net.Uri.parse(url)
        startActivity(i)
        return true
    }

    private fun clearCache(): Boolean {
        try {
            requireContext().cacheDir.walkBottomUp().forEach { if (it.isFile) it.delete() }
            com.game4399.app.App.instance.deleteDatabase("webview.db")
            com.game4399.app.App.instance.deleteDatabase("webviewCache.db")
        } catch (_: Throwable) {}
        Toast.makeText(requireContext(), "已清除缓存", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun clearAll(): Boolean {
        AlertDialog.Builder(requireContext())
            .setMessage("确认清除全部数据?")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                Prefs.clearHistory()
                Prefs.setFavorites(emptyList())
                Prefs.setLocals(emptyList())
                clearCache()
                refresh()
            }
            .show()
        return true
    }

    private fun showAbout(): Boolean {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.app_name)
            .setMessage("版本: ${BuildConfig.VERSION_NAME}\n两个引擎: Ruffle + Waflash\n本地 / 在线 / 收藏 / 历史 / 拦截 / 虚拟按键")
            .setPositiveButton(R.string.ok, null)
            .show()
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
