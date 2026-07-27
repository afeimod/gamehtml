package com.flashbox.app.webview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

/**
 * Centralised WebView settings and client wiring.
 *
 * - JS / DOM storage / zoom
 * - Mixed content allowed (Flash archives often have http embedded)
 * - Hardware accelerated
 * - Ad blocker
 * - File / content URL access
 * - Wide viewport
 * - Cache
 * - User-agent
 */
public class WebViewConfigurator {

    private static final String FB_UA = "Mozilla/5.0 (Linux; Android %d; FlashBox/%s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    @SuppressLint({"SetJavaScriptEnabled", "AllowContentVideoCapture"})
    public static void configure(Activity activity, FlashBoxWebView webView, AdBlocker adBlocker) {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);
        s.setBlockNetworkImage(false);
        s.setBlockNetworkLoads(false);

        // Mixed content (very common for old flash pages)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // Cache
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAppCacheEnabled(true);
        s.setAppCachePath(activity.getCacheDir().getAbsolutePath() + "/fb_appcache");
        s.setAppCacheMaxSize(50 * 1024 * 1024);

        // Cookies
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // UA
        try {
            String ua = String.format(FB_UA,
                    Build.VERSION.SDK_INT, "1.0.0");
            s.setUserAgentString(ua);
        } catch (Throwable t) {}

        // WebView client: handles ad blocking, intent URLs, external links
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                              @NonNull WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (adBlocker != null && adBlocker.isBlocked(url)) {
                    return adBlocker.emptyResponse();
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, @NonNull WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("intent://")) {
                    // route to external app
                    try {
                        android.content.Intent i = android.content.Intent.parseUri(url,
                                android.content.Intent.URI_INTENT_SCHEME);
                        activity.startActivity(i);
                        return true;
                    } catch (Exception ignored) {}
                }
                if (url.startsWith("market://") || url.startsWith("mailto:")
                        || url.startsWith("tel:") || url.startsWith("sms:")) {
                    try {
                        android.content.Intent i = new android.content.Intent(
                                android.content.Intent.ACTION_VIEW, request.getUrl());
                        activity.startActivity(i);
                        return true;
                    } catch (Exception ignored) {}
                }
                // Otherwise: load in this webview
                return false;
            }
        });

        // Long-press: don't trigger default text selection menu in canvas
        webView.setOnLongClickListener(v -> false);
    }
}
