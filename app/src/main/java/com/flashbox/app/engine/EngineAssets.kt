package com.flashbox.app.engine

import android.content.Context
import java.io.File

/**
 * Manages the on-disk location of the bundled engine assets.
 *
 * Engines live under assets/engines/<name>. Because WebView needs to resolve
 * relative resource URLs (wasm/data/css) we copy the engine folder to internal
 * storage once and serve everything from there.
 */
object EngineAssets {

    private const val ROOT = "engines"

    fun engineDir(context: Context, engine: EngineType): File {
        return File(context.filesDir, "$ROOT/${engine.id}")
    }

    fun playerHtml(context: Context): File {
        return File(context.filesDir, "player.html")
    }

    fun injectDir(context: Context): File {
        return File(context.filesDir, "inject").apply { mkdirs() }
    }

    /** Ensures engine assets + player.html + injection scripts are on disk.
     *  Returns the directory that should be used as the WebView base URL. */
    fun ensurePrepared(context: Context, engine: EngineType): File {
        val target = engineDir(context, engine)
        if (!target.exists() || target.listFiles()?.isEmpty() == true) {
            target.mkdirs()
            copyAssetDir(context, "$ROOT/${engine.id}", target)
        }
        // player.html + inject scripts (shared)
        copyAssetFile(context, "player.html", playerHtml(context))
        val inject = injectDir(context)
        copyAssetFile(context, "inject/ruffle_polyfill.js", File(inject, "ruffle_polyfill.js"))
        copyAssetFile(context, "inject/adblock.css", File(inject, "adblock.css"))
        copyAssetFile(context, "inject/desktop_compat.js", File(inject, "desktop_compat.js"))
        // waflash page-level helpers
        val cssDir = File(context.filesDir, "css").apply { mkdirs() }
        copyAssetFile(context, "css/embed.css", File(cssDir, "embed.css"))
        val jsDir = File(context.filesDir, "js").apply { mkdirs() }
        copyAssetFile(context, "js/flash_detect.js", File(jsDir, "flash_detect.js"))
        return target
    }

    fun baseUrl(context: Context): String {
        return "file://${context.filesDir.absolutePath}/"
    }

    private fun copyAssetDir(context: Context, assetPath: String, dest: File) {
        val list = try { context.assets.list(assetPath) } catch (e: Exception) { null } ?: return
        if (list.isEmpty()) {
            // it's a file
            copyAssetFile(context, assetPath, dest)
            return
        }
        dest.mkdirs()
        for (name in list) {
            val childAsset = "$assetPath/$name"
            val sub = try { context.assets.list(childAsset) } catch (e: Exception) { null }
            if (sub == null || sub.isEmpty()) {
                copyAssetFile(context, childAsset, File(dest, name))
            } else {
                copyAssetDir(context, childAsset, File(dest, name))
            }
        }
    }

    private fun copyAssetFile(context: Context, assetPath: String, dest: File) {
        try {
            dest.parentFile?.mkdirs()
            if (dest.exists() && dest.length() > 0) return
            context.assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            // asset may not exist for some engines; ignore
        }
    }
}
