package com.flashbox.app.web

import android.content.Context
import com.flashbox.app.R
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Lightweight ad blocker. Loads a host/keyword blacklist from assets and
 * decides whether a request URL should be blocked.
 *
 * Rules support:
 *  - exact domain match (e.g. "ads.example.com")
 *  - wildcard suffix (e.g. "*.doubleclick.net")
 *  - path keyword (e.g. "/ads/")
 */
class Adblocker {

    private val hosts = HashSet<String>()
    private val wildcardSuffixes = ArrayList<String>()
    private val pathKeywords = ArrayList<String>()

    var enabled: Boolean = true

    fun load(context: Context) {
        try {
            val res = context.resources.openRawResource(R.raw.adblock_hosts)
            BufferedReader(InputStreamReader(res)).use { reader ->
                reader.forEachLine { parseLine(it) }
            }
        } catch (e: Exception) {
            // built-in list missing; rely on keyword fallbacks
        }
        // always-on common ad path keywords
        listOf("/ads/", "/ad/", "/banner", "/popunder", "/pop.js", "doubleclick",
            "googlesyndication", "googletagmanager", "googletagservices",
            "adservice", "pagead", "adsystem", "adnxs", "moatads", "scorecardresearch",
            "analytics", "umeng", "cnzz", "baiducssrt", "bdstatic.*.js"
        ).forEach { pathKeywords.add(it) }
    }

    private fun parseLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) return
        val host = trimmed.split(Regex("\\s+"))[0]
        when {
            host.startsWith("*.") -> wildcardSuffixes.add(host.substring(2))
            host.startsWith("/") -> pathKeywords.add(host)
            host.contains("/") -> pathKeywords.add(host)
            else -> hosts.add(host.lowercase())
        }
    }

    fun shouldBlock(url: String): Boolean {
        if (!enabled) return false
        val lower = url.lowercase()
        // path keywords
        for (kw in pathKeywords) {
            if (lower.contains(kw)) return true
        }
        // host extraction
        val host = extractHost(url) ?: return false
        if (hosts.contains(host)) return true
        for (suffix in wildcardSuffixes) {
            if (host == suffix || host.endsWith(".$suffix")) return true
        }
        return false
    }

    private fun extractHost(url: String): String? {
        return try {
            val noScheme = url.substringAfter("://").substringAfter("://", url)
            val hostPort = noScheme.substringBefore("/")
            val host = hostPort.substringBefore(":")
            host.lowercase()
        } catch (e: Exception) {
            null
        }
    }
}
