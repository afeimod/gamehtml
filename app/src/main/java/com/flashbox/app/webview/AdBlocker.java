package com.flashbox.app.webview;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebResourceResponse;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight in-process ad blocker.
 *
 * - Loads user-defined + bundled block rules.
 * - Returns an empty 204 for matched URLs in `shouldInterceptRequest`.
 *
 * The rule set is also exposed to JS via the WebView so the in-page
 * adblock module can DOM-hide things that the network layer cannot.
 */
public class AdBlocker {
    private static final String TAG = "AdBlocker";

    private final Context ctx;
    private final SharedPreferences prefs;
    private final Set<String> hostRules = new HashSet<>();
    private final Set<String> urlRules = new HashSet<>();
    private final List<String> jsSelectors = new ArrayList<>();

    public AdBlocker(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = ctx.getSharedPreferences("flashbox_adblock", Context.MODE_PRIVATE);
        loadDefaults();
        loadUserRules();
    }

    // ------------------------------------------------------------------ rules

    private void loadDefaults() {
        // Hosts commonly seen on 4399 / 7k7k / 17yy / flash-game sites
        String[] HOSTS = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "googletagservices.com", "googletagmanager.com",
            "google-analytics.com", "googletags.com", "adsymptotic.com",
            "adnxs.com", "adsrvr.org", "adform.net", "serving-sys.com",
            "pubmatic.com", "rubiconproject.com", "openx.net", "criteo.com",
            "criteo.net", "taboola.com", "outbrain.com", "mgid.com",
            "scorecardresearch.com", "quantserve.com", "adsafeprotected.com",
            "moatads.com", "doublepimp.com", "popads.net", "propellerads.com",
            "exoclick.com", "trafficjunky.net", "juicyads.com", "clickadu.com",
            "hilltopads.net", "adsterra.com", "ad-maven.com",
            "baidustatic.com/abc-fe", "hm.baidu.com", "pos.baidu.com",
            "cpro.baidu.com", "cbjs.baidu.com", "dup.baidu.com",
            "static.mediav.com", "m.simba.taobao.com", "tns.simba.taobao.com",
            "alimama.com", "alibaba.com/ads", "tanx.com", "alogs.umeng.com",
            "au.umeng.com", "e.umeng.com", "err.umeng.com", "ad.umeng.com",
            "ads.union.jd.com", "gia.jd.com", "csc.beibei.com.cn",
            "api.tanx.com", "pcookie.360doc.com", "m.360doc.com/ads",
            "sax.sina.com.cn", "*.miaozhen.com", "*.gridsum.com",
            "ads.linkedin.com", "partner.googleadservices.com",
            "static.sojern.com", "px.ads.linkedin.com",
            "h5.gameyin.com", "h5.gamer.qq.com/ads", "ossweb-img.qq.com/ads",
            "union.mi.com", "mipush.cn/ads", "data.mi.com/ads",
            "track.adform.net", "ads.yahoo.com", "advertising.com",
            "amazon-adsystem.com", "adsystem.amazon.com",
            "msn.com/ads", "live.com/ads", "s0.2mdn.net",
            "imasdk.googleapis.com", "pagead2.googlesyndication.com",
            "static.ads-twitter.com", "ads-twitter.com", "ads.youtube.com",
            "sentry.io/api", "adcolony.com", "applovin.com", "chartbeat.com",
            "unityads.unity3d.com", "rayjump.com", "vungle.com",
            "startapp.com", "ironsrc.com", "taplk.com", "mintegral.com",
            "inmobi.com", "mopub.com", "smartadserver.com", "yandex.ru/ads",
            "mc.yandex.ru", "hotjar.com", "mouseflow.com", "fullstory.com",
            "mixpanel.com", "amplitude.com", "segment.io", "kissmetrics.com"
        };
        hostRules.addAll(Arrays.asList(HOSTS));
        String[] URLS = {
            "/ads/", "/ad/", "/adv/", "/advert", "/banner", "/popunder",
            "/popup", "/promote", "/promotion", "/sponsor",
            "adsbygoogle", "adsense", "adfox", "adition",
            "track.php", "/tracker/", "/track/", "/tracking/",
            "log.php", "/logger/", "stat.php", "/statistics/", "/stats/",
            "telemetry", "beacon", "pixel.gif", "pixel.png",
            "/imp?", "/click?", "/redirect?", "/adclick?",
            "aff_track", "/aff/", "/affiliate/"
        };
        urlRules.addAll(Arrays.asList(URLS));
        jsSelectors.addAll(Arrays.asList(
            "[id*='ad-']", "[class*='ad-']", "[id*='banner']", "[class*='banner']",
            "ins.adsbygoogle", "iframe[src*='ads']", "iframe[src*='ad-']",
            "div[id*='google_ads']", "div[class*='advert']"
        ));
    }

    private void loadUserRules() {
        String custom = prefs.getString("extra_hosts", "");
        if (!custom.isEmpty()) {
            for (String h : custom.split(",")) {
                if (!h.trim().isEmpty()) hostRules.add(h.trim());
            }
        }
        String customU = prefs.getString("extra_urls", "");
        if (!customU.isEmpty()) {
            for (String h : customU.split(",")) {
                if (!h.trim().isEmpty()) urlRules.add(h.trim());
            }
        }
    }

    public void addHost(String h) {
        hostRules.add(h);
        appendPref("extra_hosts", h);
    }

    public void addUrl(String u) {
        urlRules.add(u);
        appendPref("extra_urls", u);
    }

    public void clearCustom() {
        prefs.edit().remove("extra_hosts").remove("extra_urls").apply();
        // Reload only defaults
        hostRules.clear(); urlRules.clear(); jsSelectors.clear();
        loadDefaults();
    }

    private void appendPref(String key, String val) {
        String old = prefs.getString(key, "");
        if (old.contains(val)) return;
        prefs.edit().putString(key, old.isEmpty() ? val : old + "," + val).apply();
    }

    // --------------------------------------------------------- public api

    public boolean isBlocked(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String s : urlRules) {
            if (lower.contains(s)) return true;
        }
        String host = hostOf(lower);
        if (host == null) return false;
        for (String rule : hostRules) {
            if (host.equals(rule) || host.endsWith("." + rule)) return true;
        }
        return false;
    }

    public String jsSelectors() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < jsSelectors.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(jsSelectors.get(i).replace("\"", "\\\"")).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    @Nullable
    public WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "utf-8",
                new ByteArrayInputStream(new byte[0]));
    }

    public void destroy() { /* no-op */ }

    private static String hostOf(String url) {
        try {
            int s = url.indexOf("://");
            if (s < 0) return null;
            int e = url.indexOf('/', s + 3);
            if (e < 0) e = url.indexOf('?', s + 3);
            if (e < 0) e = url.length();
            return url.substring(s + 3, e);
        } catch (Exception e) { return null; }
    }
}
