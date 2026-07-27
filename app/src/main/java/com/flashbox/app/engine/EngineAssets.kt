package com.flashbox.app.engine

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Manages the on-disk location of the bundled engine assets.
 *
 * Engines live under assets/engines/<name>. Because WebView needs to resolve
 * relative resource URLs (wasm/data/css) we copy the engine folder to internal
 * storage once and serve everything from there.
 *
 * If Ruffle assets are missing from the APK (download failed at build time),
 * we attempt a runtime download as a fallback.
 */
object EngineAssets {

    private const val TAG = "EngineAssets"
    private const val ROOT = "engines"

    // Ruffle selfhosted download URL (nightly build)
    private const val RUFFLE_DOWNLOAD_URL =
        "https://github.com/ruffle-rs/ruffle/releases/download/nightly-2024-01-28/ruffle-nightly-2024_01_28-web-selfhosted.zip"

    fun engineDir(context: Context, engine: EngineType): File {
        return File(context.filesDir, "$ROOT/${engine.id}")
    }

    fun playerHtml(context: Context): File {
        return File(context.filesDir, "player.html")
    }

    fun injectDir(context: Context): File {
        return File(context.filesDir, "inject").apply { mkdirs() }
    }

    /** Check if engine has its core JS file available (either in assets or filesDir). */
    fun isEngineAvailable(context: Context, engine: EngineType): Boolean {
        val dir = engineDir(context, engine)
        val coreFile = when (engine) {
            EngineType.RUFFLE -> File(dir, "ruffle.js")
            EngineType.WAFLASH -> File(dir, "waflash.js")
        }
        if (coreFile.exists() && coreFile.length() > 0) return true
        // Check assets
        val assetList = try { context.assets.list("$ROOT/${engine.id}") } catch (e: Exception) { null }
        return assetList?.contains(coreFile.name) == true
    }

    /** Ensures engine assets + player.html + injection scripts are on disk.
     *  Returns the directory that should be used as the WebView base URL. */
    fun ensurePrepared(context: Context, engine: EngineType): File {
        val target = engineDir(context, engine)
        val coreFile = when (engine) {
            EngineType.RUFFLE -> File(target, "ruffle.js")
            EngineType.WAFLASH -> File(target, "waflash.js")
        }

        if (!coreFile.exists() || coreFile.length() == 0L) {
            target.mkdirs()
            copyAssetDir(context, "$ROOT/${engine.id}", target)
        }

        // If Ruffle is still missing after copy, try runtime download
        if (engine == EngineType.RUFFLE && (!coreFile.exists() || coreFile.length() == 0L)) {
            Log.w(TAG, "Ruffle assets not found in APK, attempting runtime download...")
            try {
                downloadAndExtractRuffle(context)
            } catch (e: Exception) {
                Log.e(TAG, "Runtime Ruffle download failed", e)
            }
        }

        // Log engine status
        if (coreFile.exists() && coreFile.length() > 0) {
            Log.i(TAG, "Engine ${engine.id} ready at ${target.absolutePath} (${coreFile.name} = ${coreFile.length()} bytes)")
        } else {
            Log.w(TAG, "Engine ${engine.id} NOT ready - ${coreFile.name} missing at ${target.absolutePath}")
        }

        // player.html + inject scripts (shared, always copy to ensure latest)
        copyAssetFile(context, "player.html", playerHtml(context), force = true)
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

    /**
     * Downloads Ruffle selfhosted zip and extracts to engine directory.
     * Runs on a background thread - caller should handle threading.
     */
    private fun downloadAndExtractRuffle(context: Context) {
        val targetDir = engineDir(context, EngineType.RUFFLE)
        targetDir.mkdirs()
        val cacheFile = File(context.cacheDir, "ruffle-selfhosted.zip")

        // Download
        val url = URL(RUFFLE_DOWNLOAD_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true
        conn.connect()

        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            throw RuntimeException("Download failed: HTTP ${conn.responseCode}")
        }

        FileOutputStream(cacheFile).use { output ->
            conn.inputStream.use { input -> input.copyTo(output) }
        }
        conn.disconnect()
        Log.i(TAG, "Ruffle downloaded: ${cacheFile.length()} bytes")

        // Extract
        ZipInputStream(cacheFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name.substringAfterLast('/'))
                if (!entry.isDirectory && outFile.name.endsWith(".js") || outFile.name.endsWith(".wasm")) {
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    Log.i(TAG, "Extracted: ${outFile.name} (${outFile.length()} bytes)")
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        cacheFile.delete()
    }

    private fun copyAssetDir(context: Context, assetPath: String, dest: File) {
        val list = try { context.assets.list(assetPath) } catch (e: Exception) {
            Log.w(TAG, "assets.list('$assetPath') failed: ${e.message}")
            null
        }
        if (list == null) return
        if (list.isEmpty()) {
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

    private fun copyAssetFile(context: Context, assetPath: String, dest: File, force: Boolean = false) {
        try {
            dest.parentFile?.mkdirs()
            if (!force && dest.exists() && dest.length() > 0) return
            context.assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "copyAssetFile('$assetPath' -> '${dest.name}') failed: ${e.message}")
        }
    }
}
