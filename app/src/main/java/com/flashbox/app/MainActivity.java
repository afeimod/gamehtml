package com.flashbox.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.flashbox.app.bridge.AppBridge;
import com.flashbox.app.bridge.LocalFileBridge;
import com.flashbox.app.webview.AdBlocker;
import com.flashbox.app.webview.FlashBoxWebView;
import com.flashbox.app.webview.WebViewConfigurator;

/**
 * Main host activity.
 *
 * Renders the embedded `assets/www/index.html` inside a single fullscreen
 * WebView. All UI - home, history, favorites, settings, virtual gamepad -
 * is implemented as in-page HTML / JS modules. The native side is just a
 * thin container + JavaScript bridge.
 */
public class MainActivity extends AppCompatActivity {

    private FlashBoxWebView webView;
    private AdBlocker adBlocker;
    private AppBridge appBridge;
    private LocalFileBridge localFileBridge;

    // Storage permission launcher (Android 13+ READ_MEDIA_*, below = READ_EXTERNAL_STORAGE)
    private final ActivityResultLauncher<String[]> storagePermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        // Whatever the result, JS can still use SAF (ACTION_OPEN_DOCUMENT_TREE)
                        if (webView != null) {
                            StringBuilder sb = new StringBuilder("{");
                            boolean first = true;
                            for (java.util.Map.Entry<String, Boolean> e : result.entrySet()) {
                                if (!first) sb.append(',');
                                first = false;
                                sb.append('"').append(e.getKey().replace("\"", "\\\"")).append("\":")
                                  .append(Boolean.TRUE.equals(e.getValue()));
                            }
                            sb.append('}');
                            final String payload = sb.toString();
                            webView.evaluateJavascript(
                                "window.dispatchEvent(new CustomEvent('perm-result', {detail: " +
                                payload + "}));", null);
                        }
                    });

    // Manage all files launcher (Android 11+)
    private final ActivityResultLauncher<Intent> manageAllFilesLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (webView != null) {
                            webView.evaluateJavascript(
                                "window.dispatchEvent(new CustomEvent('saf-result'));", null);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fullscreen edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Set up WebView container
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);

        // Set up ad blocker (shared, also referenced by WebView client)
        adBlocker = new AdBlocker(this);

        // Configure WebView (settings, hardware accel, JS, etc.)
        WebViewConfigurator.configure(this, webView, adBlocker);

        // Bridges for JS <-> native
        appBridge = new AppBridge(this, webView);
        localFileBridge = new LocalFileBridge(this, webView);
        webView.addJavascriptInterface(appBridge, "FlashBox");
        webView.addJavascriptInterface(localFileBridge, "FlashBoxFile");

        // Handle intent (open with .swf etc.)
        handleIntent(getIntent());

        // Initial load
        webView.loadUrl("file:///android_asset/www/index.html");

        // Apply translucent system bars
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        // Optional runtime permission request (non-blocking, only requested on demand from JS)
        ensureStoragePermission();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    /**
     * Handle intents like `am start -d http://example.com/game.swf`.
     */
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data != null && webView != null) {
            final String url = data.toString();
            // Pass URL via JSON to avoid any quote-escape problems
            final String jsonUrl = "\"" + url.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            webView.post(() ->
                webView.evaluateJavascript(
                    "if(window.FlashBox && window.FlashBox.onExternalSwf) {" +
                    "  window.FlashBox.onExternalSwf(" + jsonUrl + ");" +
                    "}", null));
        }
    }

    /**
     * Request storage permissions lazily. The actual result is delivered
     * to JS via a custom event so it can prompt the user only when needed.
     */
    public void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                manageAllFilesLauncher.launch(intent);
                return;
            } catch (Exception e) {
                // Fallback to general settings
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    manageAllFilesLauncher.launch(intent);
                } catch (Exception ignored) {}
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            storagePermLauncher.launch(new String[]{
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            });
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            storagePermLauncher.launch(new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE
            });
        }
    }

    private void ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return; // SAF
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0xB1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (webView != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('perm-result', {detail: {granted: " + granted + "}}));", null);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Forward hardware keys to webview as JS events (for virtual gamepad)
        if (webView != null) {
            String js = String.format(
                "window.dispatchEvent(new CustomEvent('hwkey', {detail: {code: %d, action: 'down'}}));",
                keyCode);
            webView.evaluateJavascript(js, null);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (webView != null) {
            String js = String.format(
                "window.dispatchEvent(new CustomEvent('hwkey', {detail: {code: %d, action: 'up'}}));",
                keyCode);
            webView.evaluateJavascript(js, null);
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Let JS handle back (it may pop a sub-page or exit fullscreen player first)
        if (webView != null) {
            webView.evaluateJavascript(
                "if(window.FlashBox && window.FlashBox.onBack) window.FlashBox.onBack();" +
                "else if(history.length>1) history.back();" +
                "else window.FlashBox && window.FlashBox.confirmExit && window.FlashBox.confirmExit();",
                value -> {
                    if ("exit".equals(value)) {
                        MainActivity.super.onBackPressed();
                    }
                });
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); }
    @Override
    protected void onPause() { super.onPause(); if (webView != null) webView.onPause(); }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("FlashBox");
            webView.removeJavascriptInterface("FlashBoxFile");
            webView.destroy();
            webView = null;
        }
        if (adBlocker != null) adBlocker.destroy();
        super.onDestroy();
    }
}
